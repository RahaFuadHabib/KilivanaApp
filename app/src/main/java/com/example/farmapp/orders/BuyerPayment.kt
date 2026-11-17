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
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Lock
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
    fun refresh(id: Long) = delegate.refresh(id)
    fun initiate(id: Long, phoneInput: String, reviewedTotal: BigDecimal? = state.value.total) =
        delegate.initiate(id, phoneInput, reviewedTotal)
    fun notifyTimeout() { _timedOut.value = true }
    fun pollLatest(orderId: Long) {
        scope.launch {
            try {
                val session = guard.current(com.example.farmapp.checkout.CheckoutOperation.READ_PAYMENTS)
                val response = api.payments(orderId, session)
                if (response.isSuccessful) {
                    val payments = response.body()?.data ?: return@launch
                    val latest = payments.maxByOrNull { it.id ?: 0L }?.copy(status = payments.maxByOrNull { it.id ?: 0L }?.status?.lowercase()) ?: return@launch
                    if (latest.status !in paymentStatuses) return@launch
                    if (state.value.orderId != orderId) return@launch
                    val existing = state.value.payments.firstOrNull { it.id == latest.id }
                    if (existing?.status == "pending" && latest.status in setOf("paid", "cancelled", "failed"))
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
            while (true) {
                delay(3_000)
                val latest = model.controller.state.value
                val terminalStatus = latest.payments.lastOrNull()?.status
                if (terminalStatus in setOf("paid", "cancelled", "failed")) break
                val noPaymentPossible = !latest.canInitiate && latest.payments.isEmpty() && !latest.busy
                if (noPaymentPossible) break
                if (latest.payments.any { it.status == "pending" } && System.currentTimeMillis() > deadline) {
                    model.controller.notifyTimeout()
                    break
                }
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
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White)) {
        OrderHeader("Pay with M-Pesa", back)
        HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFFE8EBEE))
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
        OrderSection {
            Text("Order payment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(orderMoney(state.total ?: order.total), color = OrderGreen, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            when (displayStatus) {
                "paid" -> OrderStatusBadge("PAID", "paid")
                "pending" -> OrderStatusBadge("PENDING", "pending")
                "cancelled" -> OrderStatusBadge("CANCELLED", "cancelled")
                "failed" -> OrderStatusBadge("FAILED", "failed")
                "held" -> OrderStatusBadge("HELD", "held")
                "settled" -> OrderStatusBadge("SETTLED", "settled")
                "refunded" -> OrderStatusBadge("REFUNDED", "refunded")
                else -> OrderStatusBadge("UNKNOWN", "unknown")
            }
        }
        if (state.orderId == order.id) {
            OrderSection("Payment Status") {
                when (displayStatus) {
                    "paid" -> {
                        latestPayment?.mpesaReceiptNumber?.let { Text("Receipt: $it", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                        latestPayment?.amount?.let { Text("Amount: ${orderMoney(it)}", style = MaterialTheme.typography.bodyMedium) }
                        (latestPayment?.paidAt ?: order.paidAt)?.let { Text("Paid: ${com.example.farmapp.buyer.buyerDate(it)}", style = MaterialTheme.typography.bodySmall) }
                    }
                    "pending" -> Text("M-Pesa request sent. Please enter your PIN on your phone and wait while we confirm your payment.", style = MaterialTheme.typography.bodyMedium)
                    "cancelled" -> Text("You cancelled the M-Pesa payment. Your order has not been paid. You can try again when you are ready.", style = MaterialTheme.typography.bodyMedium)
                    "failed" -> Text("Payment was unsuccessful.", style = MaterialTheme.typography.bodyMedium)
                    "held" -> Text("Payment is held in escrow pending delivery confirmation.", style = MaterialTheme.typography.bodyMedium)
                    "settled" -> Text("Payment has been released to the seller.", style = MaterialTheme.typography.bodyMedium)
                    "refunded" -> Text("Payment has been refunded.", style = MaterialTheme.typography.bodyMedium)
                    else -> if (state.notice != null) Text(state.notice, style = MaterialTheme.typography.bodyMedium)
                }
                if (timedOut && displayStatus == "pending")
                    Text("Payment confirmation timed out. Please check your M-Pesa messages or try again.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (displayStatus in setOf("cancelled", "failed") && orderPayable && !state.busy)
                OrderPrimaryButton("Retry Payment", { showPayment = true })
            if (!state.busy && !state.canInitiate && state.payments.isEmpty() && state.notice == null && displayStatus == null)
                Text("Payment is not available for this order. Refresh to check its current status.")
            if (state.canInitiate) {
                if (!showPayment) OrderPrimaryButton("Pay with M-Pesa", { showPayment = true }, !state.busy)
                else OrderSection("M-Pesa Payment") {
                    Surface(color = OrderPaleGreen, shape = RoundedCornerShape(9.dp)) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.VerifiedUser, null, tint = OrderGreen)
                            Text("You will receive an M-Pesa prompt on your phone. Enter your M-Pesa PIN there to complete the payment.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text("Phone Number", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(phone, { phone = it }, label = { Text("M-Pesa phone number") },
                        placeholder = { Text("07XXXXXXXX or 2547XXXXXXXX") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(8.dp), enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                    if (phone.isNotBlank() && runCatching { buyerMpesaPhone(phone) }.isFailure)
                        Text("Enter a valid Kenyan mobile number.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OrderPrimaryButton("Send M-Pesa Prompt", { confirmTotal = state.total; confirm = true },
                        !state.busy && runCatching { buyerMpesaPhone(phone) }.isSuccess)
                }
            }
        }
        if (showPayment) OrderSection {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = androidx.compose.ui.graphics.Color(0xFF526074))
                Text("Payment Security", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text("\u2022 Secure payment via Safaricom M-Pesa\n\u2022 You will be prompted on your phone\n\u2022 Enter your PIN only in the M-Pesa prompt\n\u2022 Never share your M-Pesa PIN\n\u2022 Payment status updates after confirmation", style = MaterialTheme.typography.bodyMedium, lineHeight = 23.sp)
        }
        TextButton(onClick = refresh, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Refresh payment and order") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Send M-Pesa prompt?") },
        text = { Text("Payment: ${orderMoney(confirmTotal)} to ${runCatching { buyerMpesaPhone(phone) }.getOrDefault("")}. Confirm only if you want to initiate payment.") },
        confirmButton = { TextButton(onClick = { confirm = false; initiate(phone, confirmTotal) }) { Text("Confirm STK Push") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
