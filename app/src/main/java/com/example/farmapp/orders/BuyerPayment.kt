package com.example.farmapp.orders

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.BuildConfig
import com.example.farmapp.buyer.buyerScope
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.*
import com.example.farmapp.payments.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal

internal fun buyerMpesaPhone(value: String) = com.example.farmapp.payments.mpesaPhone(value)
internal fun buyerPaymentOutcome(status: String?) = com.example.farmapp.payments.paymentOutcome(status)
internal typealias BuyerPaymentAttemptStore = com.example.farmapp.payments.PaymentAttemptStore
internal class BuyerPaymentPreferences(context: Context, origin: String, user: String) : BuyerPaymentAttemptStore {
    private val preferences = context.applicationContext.getSharedPreferences("buyer-payment-" + buyerScope(origin, user), Context.MODE_PRIVATE)
    private val delegate = com.example.farmapp.payments.DurablePaymentAttempts(object : com.example.farmapp.payments.PaymentAttemptPersistence {
        override fun started(orderId: Long) = preferences.getBoolean(orderId.toString(), false)
        override fun markStarted(orderId: Long) {
            check(preferences.edit().putBoolean(orderId.toString(), true).commit())
        }
        override fun clearStarted(orderId: Long) {
            preferences.edit().remove(orderId.toString()).apply()
        }
    }, lock)
    override fun started(orderId: Long) = delegate.started(orderId)
    override fun reserve(orderId: Long) = delegate.reserve(orderId)
    override fun release(orderId: Long) = delegate.release(orderId)
    companion object { private val lock = Any() }
}
internal typealias BuyerPaymentState = com.example.farmapp.payments.PurchaserPaymentState<PaymentDto>
internal class BuyerPaymentController(private val api: BuyerOrdersApi, sessions: SessionStore,
    expected: SessionSnapshot, attempts: BuyerPaymentAttemptStore, private val scope: CoroutineScope,
    paymentAllowed: () -> Boolean = BuyerMpesaEnvironment::ready) {
    private val guard = com.example.farmapp.checkout.CheckoutSessionGuard(sessions, expected, com.example.farmapp.checkout.BuyerPurchaserPolicy)
    private val delegate = com.example.farmapp.payments.PurchaserPaymentController(
        BuyerPaymentRepository(api, sessions, expected, paymentAllowed), BuyerPaymentContract,
        sessions, expected, com.example.farmapp.checkout.BuyerPurchaserPolicy, attempts, scope, paymentAllowed)
    private val _timedOut = MutableStateFlow(false)
    val timedOut = _timedOut.asStateFlow()
    val state = delegate.state
    private val _preOrderState = MutableStateFlow<PurchaserPaymentState<PaymentDto>?>(null)
    val preOrderState = _preOrderState.asStateFlow()
    fun refresh(id: Long) = delegate.refresh(id)
    fun initiate(id: Long, phoneInput: String, reviewedTotal: BigDecimal? = state.value.total) =
        delegate.initiate(id, phoneInput, reviewedTotal)
    fun notifyTimeout() { _timedOut.value = true }
    /** Poll a pre-order checkout payment. Returns terminal status, or null while unresolved. */
    suspend fun pollPayment(paymentId: Long): String? {
        val session = guard.current(com.example.farmapp.checkout.CheckoutOperation.READ_PAYMENTS)
        val response = api.payment(paymentId, session)
        if (!response.isSuccessful) { response.errorBody()?.close(); return null }
        val payment = response.body()?.data ?: return null
        val status = payment.status?.lowercase() ?: return null
        if (status !in paymentStatuses) return null
        val terminal = status in setOf("paid", "cancelled", "failed", "held", "settled", "refunded")
        _preOrderState.value = PurchaserPaymentState<PaymentDto>(
            orderId = null, busy = false, canInitiate = false,
            payments = listOf(payment), total = payment.amount,
            notice = when (status) {
                "paid" -> "Payment confirmed. Your order is being created."
                "cancelled" -> "Payment was cancelled. No order was created."
                "failed" -> "Payment failed. No order was created."
                else -> "Payment status: $status"
            }
        )
        return if (terminal) status else null
    }
    fun pollLatest(orderId: Long) {
        scope.launch {
            try {
                val session = guard.current(com.example.farmapp.checkout.CheckoutOperation.READ_PAYMENTS)
                val response = api.payments(orderId, session)
                if (response.isSuccessful) {
                    val payments = response.body()?.data ?: return@launch
                    val latest = payments.maxByOrNull { it.id ?: 0L } ?: return@launch
                    if (latest.status?.lowercase() !in paymentStatuses) return@launch
                    if (state.value.orderId != orderId) return@launch
                    val existing = state.value.payments.firstOrNull { it.id == latest.id }
                    val normalized = latest.copy(status = latest.status?.lowercase())
                    if (existing == null || (existing.status == "pending" && normalized.status in setOf("paid", "cancelled", "failed")))
                        delegate.refresh(orderId)
                }
            } catch (_: Exception) { /* silent; full refresh handles recovery */ }
        }
    }
}
internal class BuyerPaymentModel(context: Context) : ViewModel() {
    private val expected = TokenManager.session.value
    val controller = BuyerPaymentController(ApiClient.buyerOrdersApi, TokenManager, expected,
        BuyerPaymentPreferences(context, BuildConfig.API_BASE_URL, expected.auth!!.userId), viewModelScope,BuyerMpesaEnvironment::ready)
    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return BuyerPaymentModel(context.applicationContext) as T
            }
        }
    }
}
/** Pre-order polling session for async checkout. Polls by paymentId until terminal. */
@Composable internal fun BuyerPaymentSessionAsync(paymentId: Long,
    orderCheck: suspend () -> Boolean = { false }, onTerminal: (String) -> Unit,
    content: @Composable (PurchaserPaymentState<PaymentDto>, Boolean) -> Unit) {
    val session by TokenManager.session.collectAsStateWithLifecycle()
    val model: BuyerPaymentModel = viewModel(key = "buyer-payment-async-${session.generation}-$paymentId", factory = BuyerPaymentModel.factory(LocalContext.current))
    val preState by model.controller.preOrderState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var timeoutShown by remember { mutableStateOf(false) }
    LaunchedEffect(paymentId, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val deadline = System.currentTimeMillis() + 120_000L
            while (true) {
                delay(if (timeoutShown) 10_000L else 3_000L)
                val orderCreated = runCatching { orderCheck() }.getOrDefault(false)
                if (orderCreated) { onTerminal("paid"); return@repeatOnLifecycle }
                val terminal = model.controller.pollPayment(paymentId)
                if (terminal != null) { onTerminal(terminal); return@repeatOnLifecycle }
                if (!timeoutShown && System.currentTimeMillis() > deadline) {
                    timeoutShown = true
                }
            }
        }
    }
    content(preState ?: PurchaserPaymentState(), timeoutShown)
}
/** The same controller is shared by list/details/payment for an order; navigation only performs reads. */
@Composable internal fun BuyerPaymentSession(order: BuyerOrderDto,
    content: @Composable (BuyerPaymentState, Boolean, () -> Unit, (String, BigDecimal?) -> Unit) -> Unit) {
    val session by TokenManager.session.collectAsStateWithLifecycle()
    val model: BuyerPaymentModel = viewModel(key = "buyer-payment-${session.generation}-${order.id}", factory = BuyerPaymentModel.factory(LocalContext.current))
    val state by model.controller.state.collectAsStateWithLifecycle()
    val timedOut by model.controller.timedOut.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(order.id, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.controller.refresh(order.id!!)
            val deadline = System.currentTimeMillis() + 120_000L
            var timeoutShown = false
            while (true) {
                delay(if (timeoutShown) 10_000L else 3_000L)
                val latest = model.controller.state.value
                val terminalStatus = latest.payments.lastOrNull()?.status
                if (terminalStatus in setOf("paid", "cancelled", "failed")) break
                val noPaymentPossible = !latest.canInitiate && latest.payments.isEmpty() && !latest.busy
                if (noPaymentPossible) break
                if (!timeoutShown && latest.payments.any { it.status == "pending" } && System.currentTimeMillis() > deadline) {
                    model.controller.notifyTimeout()
                    timeoutShown = true
                }
                // Keep checking after the local timeout. The backend reconciles stale STK
                // requests asynchronously; once it reports failed/cancelled, retry unlocks
                // on this same order without requiring the buyer to leave and rebuild it.
                model.controller.pollLatest(order.id!!)
            }
        }
    }
    content(state, timedOut, { model.controller.refresh(order.id!!) }, { phone, total -> model.controller.initiate(order.id!!, phone, total) })
}
@Composable internal fun BuyerPaymentPanel(order: BuyerOrderDto, refreshOrder: () -> Unit) {
    BuyerPaymentSession(order) { state, timedOut, refresh, initiate -> BuyerPaymentControls(order, state, timedOut, { refresh(); refreshOrder() }, initiate) }
}
@Composable internal fun BuyerPaymentPage(order: BuyerOrderDto, state: BuyerPaymentState, timedOut: Boolean, back: () -> Unit,
    refresh: () -> Unit, initiate: (String, BigDecimal?) -> Unit) {
    Column(Modifier.fillMaxSize().background(com.example.farmapp.buyers.ui.theme.NeutralBackground)) {
        OrderHeader("Pay with M-Pesa", back, green = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp).testTag("payment-page")) {
            BuyerPaymentControls(order, state, timedOut, refresh, initiate, startExpanded = true)
        }
    }
}
@Composable internal fun BuyerPaymentControls(order: BuyerOrderDto, state: BuyerPaymentState,
    timedOut: Boolean = false, refresh: () -> Unit, initiate: (String, BigDecimal?) -> Unit, startExpanded: Boolean = false) {
    var phone by rememberSaveable(order.id) { mutableStateOf("") }
    var showPayment by rememberSaveable(order.id) { mutableStateOf(startExpanded) }
    var confirm by remember(order.id) { mutableStateOf(false) }
    var confirmTotal by remember(order.id) { mutableStateOf<BigDecimal?>(null) }
    val latestPayment = state.payments.lastOrNull()
    val displayStatus = latestPayment?.status ?: order.paymentStatus?.lowercase()
    val orderPayable = order.status in setOf("placed", "confirmed") && displayStatus in setOf("pending", "failed", "cancelled")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ── Payment summary card with colored status ──────────────────────
        val statusConfig: Triple<Color, Color, ImageVector>? = when (displayStatus) {
            "paid" -> Triple(com.example.farmapp.buyers.ui.theme.SuccessSurface, com.example.farmapp.buyers.ui.theme.Green600, Icons.Default.CheckCircle)
            "pending" -> Triple(com.example.farmapp.buyers.ui.theme.AmberSurface, com.example.farmapp.buyers.ui.theme.AmberIcon, Icons.Default.PhoneAndroid)
            "cancelled" -> Triple(com.example.farmapp.buyers.ui.theme.CancelledSurface, com.example.farmapp.buyers.ui.theme.CancelledText, Icons.Default.Cancel)
            "failed" -> Triple(com.example.farmapp.buyers.ui.theme.DangerSurface, com.example.farmapp.buyers.ui.theme.ErrorRed, Icons.Default.Cancel)
            "held" -> Triple(com.example.farmapp.buyers.ui.theme.AmberSurface, com.example.farmapp.buyers.ui.theme.AmberText, Icons.Default.PauseCircle)
            "settled" -> Triple(com.example.farmapp.buyers.ui.theme.SuccessSurface, com.example.farmapp.buyers.ui.theme.Green600, Icons.Default.VerifiedUser)
            "refunded" -> Triple(Color(0xFFEEEEEE), Color(0xFF444444), Icons.Default.Refresh)
            else -> null
        }
        Surface(shape = RoundedCornerShape(16.dp), color = statusConfig?.first ?: Color.White, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    statusConfig?.let { (bg, tint, icon) ->
                        Box(Modifier.size(42.dp).background(tint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(24.dp), tint = tint)
                        }
                    }
                    Column {
                        Text("Order payment", style = MaterialTheme.typography.labelMedium, color = com.example.farmapp.buyers.ui.theme.TextSecondary)
                        Text(orderMoney(state.total ?: order.total), color = statusConfig?.second ?: OrderGreen, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    statusConfig?.let { (_, tint, _) ->
                        Surface(color = tint.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)) {
                            Text(displayStatus?.uppercase() ?: "UNKNOWN", color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }
        // ── Payment details card ──────────────────────────────────────────
        if (state.orderId == order.id) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val hasPendingPayment = state.payments.any { it.status == "pending" }
                    val statusBody: String? = when (displayStatus) {
                        "paid" -> {
                            val sb = StringBuilder()
                            latestPayment?.mpesaReceiptNumber?.let { sb.append("Receipt: $it\n") }
                            latestPayment?.amount?.let { sb.append("Amount: ${orderMoney(it)}\n") }
                            (latestPayment?.paidAt ?: order.paidAt)?.let { sb.append("Paid: ${com.example.farmapp.buyer.buyerDate(it)}") }
                            sb.toString().ifBlank { null }
                        }
                        "pending" -> if (hasPendingPayment)
                            "M-Pesa request sent for order #${order.id}. Enter your PIN on your phone and wait for confirmation. This payment stays on the same order; another prompt is available after this attempt finishes." else null
                        "cancelled" -> "You cancelled the M-Pesa payment for order #${order.id}. The order is unpaid; retry payment here when you are ready."
                        "failed" -> "Payment for order #${order.id} was unsuccessful. Retry here to pay this order without creating another one."
                        "held" -> "Payment is held in escrow pending delivery confirmation."
                        "settled" -> "Payment has been released to the seller."
                        "refunded" -> "Payment has been refunded."
                        else -> null
                    }
                    statusBody?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = com.example.farmapp.buyers.ui.theme.TextSecondary, lineHeight = 22.sp)
                    }
                    state.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                    if (timedOut && displayStatus == "pending" && hasPendingPayment)
                        Text("Confirmation is taking longer than expected. Check your M-Pesa messages and refresh this order after a few minutes. Once the backend marks this attempt failed or cancelled, you can enter your number and retry on this same order.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = OrderGreen)
                }
            }
            if (displayStatus == "pending" && state.payments.any { it.status == "pending" } && !state.busy)
                OrderPrimaryButton("Check payment for this order", refresh)
            if (displayStatus in setOf("cancelled", "failed") && orderPayable && !state.busy)
                OrderPrimaryButton("Retry payment for order #${order.id}", { showPayment = true; refresh() })
            if (!state.busy && !state.canInitiate && state.payments.isEmpty() && state.notice == null && displayStatus == null)
                Text("Payment is not available for this order. Refresh to check its current status.")
            if (state.canInitiate) {
                if (!showPayment) OrderPrimaryButton("Pay with M-Pesa", { showPayment = true }, !state.busy)
                else {
                    Surface(shape = RoundedCornerShape(16.dp), color = com.example.farmapp.buyers.ui.theme.MessaTint, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhoneAndroid, null, tint = com.example.farmapp.buyers.ui.theme.MessaGreen)
                                Text("M-Pesa Payment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = com.example.farmapp.buyers.ui.theme.MessaGreen)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.VerifiedUser, null, tint = OrderGreen, modifier = Modifier.padding(top = 2.dp))
                                Text("You will receive an M-Pesa prompt on your phone. Enter your M-Pesa PIN there to complete the payment.", style = MaterialTheme.typography.bodyMedium, color = com.example.farmapp.buyers.ui.theme.TextSecondary)
                            }
                            OutlinedTextField(phone, { phone = it }, label = { Text("M-Pesa phone number") },
                                placeholder = { Text("07XXXXXXXX or 2547XXXXXXXX") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(10.dp), enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = com.example.farmapp.buyers.ui.theme.MessaGreen))
                            if (phone.isNotBlank() && runCatching { buyerMpesaPhone(phone) }.isFailure)
                                Text("Enter a valid Kenyan mobile number.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            OrderPrimaryButton("Send M-Pesa Prompt", { confirmTotal = state.total; confirm = true },
                                !state.busy && runCatching { buyerMpesaPhone(phone) }.isSuccess)
                        }
                    }
                }
            }
        }
        if (showPayment) Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = com.example.farmapp.buyers.ui.theme.TextSecondary)
                    Text("Payment Security", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text("\u2022 Secure payment via Safaricom M-Pesa\n\u2022 You will be prompted on your phone\n\u2022 Enter your PIN only in the M-Pesa prompt\n\u2022 Never share your M-Pesa PIN\n\u2022 Payment status updates after confirmation", style = MaterialTheme.typography.bodyMedium, lineHeight = 23.sp, color = com.example.farmapp.buyers.ui.theme.TextSecondary)
            }
        }
        TextButton(onClick = refresh, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Refresh payment and order") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Send M-Pesa prompt?") },
        text = { Text("Payment: ${orderMoney(confirmTotal)} to ${runCatching { buyerMpesaPhone(phone) }.getOrDefault("")}. Confirm only if you want to initiate payment.") },
        confirmButton = { TextButton(onClick = { confirm = false; initiate(phone, confirmTotal) }) { Text("Confirm STK Push") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
