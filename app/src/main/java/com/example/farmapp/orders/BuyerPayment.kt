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
import com.example.farmapp.network.canonical.CanonicalApiResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import java.util.Locale
import java.math.BigDecimal

internal fun buyerMpesaPhone(value: String): String {
    val text = value.trim()
    require(Regex("[+0-9 ()-]+").matches(text))
    val digits = text.filter { it in '0'..'9' }
    val phone = if (digits.startsWith("0")) "254" + digits.drop(1) else digits
    require(Regex("254[71][0-9]{8}").matches(phone))
    return phone
}
internal fun buyerPaymentOutcome(status: String?): String = when (status?.lowercase(Locale.ROOT)) {
    "paid", "held", "settled" -> "confirmed"
    "pending" -> "pending"
    "failed" -> "failed"
    "refunded" -> "refunded"
    else -> "unverified"
}
/** Durable marker written BEFORE the STK call. An unknown outcome never enables a second attempt. */
internal interface BuyerPaymentAttemptStore {
    fun started(orderId: Long): Boolean
    fun reserve(orderId: Long): Boolean
}
internal class BuyerPaymentPreferences(context: Context, origin: String, user: String) : BuyerPaymentAttemptStore {
    private val preferences = context.applicationContext.getSharedPreferences("buyer-payment-" + buyerScope(origin, user), Context.MODE_PRIVATE)
    override fun started(orderId: Long) = preferences.getBoolean(orderId.toString(), false)
    override fun reserve(orderId: Long): Boolean = synchronized(lock) {
        if (started(orderId)) false else {
            check(preferences.edit().putBoolean(orderId.toString(), true).commit())
            true
        }
    }
    companion object { private val lock = Any() }
}
internal data class BuyerPaymentState(val orderId: Long? = null, val busy: Boolean = false,
    val canInitiate: Boolean = false, val payments: List<PaymentDto> = emptyList(), val notice: String? = null, val total: BigDecimal? = null,
    val submissionEnabled:Boolean=true,val submitted:Boolean=false)
internal class BuyerPaymentController(private val api: BuyerOrdersApi, private val sessions: SessionStore,
    private val expected: SessionSnapshot, private val attempts: BuyerPaymentAttemptStore, private val scope: CoroutineScope,
    private val paymentAllowed:()->Boolean=BuyerMpesaEnvironment::ready) {
    private val mutable = MutableStateFlow(BuyerPaymentState())
    val state = mutable.asStateFlow()
    private fun current(): SessionSnapshot {
        val now = sessions.session.value
        check(now.generation == expected.generation && !now.loggingOut && !now.isDemo && now.auth?.role == "BUYER" &&
            now.auth.backendContract == "canonical" && now.auth.userId == expected.auth?.userId && now.auth.sessionOrigin == expected.auth?.sessionOrigin)
        return now
    }
    private fun <T> read(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) { response.errorBody()?.close(); error("Request not confirmed") }
        return response.body()?.requireData() ?: error("Incomplete response")
    }
    private suspend fun inspect(id: Long): Pair<BuyerOrderDto, List<PaymentDto>> {
        val order = validateBuyerOrder(read(api.order(id, current())), current().auth!!.userId.toLong())
        check(order.id == id)
        val rows = read(api.payments(id, current()))
        check(rows.map { it.id }.distinct().size == rows.size)
        val checked = rows.map { row ->
            check(row.id != null && row.id > 0 && row.orderId == id)
            val latest = read(api.payment(row.id, current()))
            check(latest.id == row.id && latest.orderId == id && latest.amount != null && latest.amount.signum() >= 0 &&
                latest.status in paymentStatuses)
            latest
        }
        current()
        return order to checked
    }
    private fun payable(order: BuyerOrderDto) = order.status in setOf("placed", "confirmed") &&
        order.paymentStatus in setOf("pending", "failed") && order.total!!.signum() > 0
    fun refresh(id: Long) {
        if (mutable.value.busy || id <= 0) return
        try { current() } catch (_: Exception) { return }
        val startedBeforeRead=runCatching{attempts.started(id)}.getOrDefault(true)
        mutable.update { if(it.orderId==id)it.copy(busy=true,canInitiate=false,submitted=it.submitted || startedBeforeRead)
            else BuyerPaymentState(orderId=id,busy=true,submitted=startedBeforeRead,submissionEnabled=paymentAllowed()) }
        scope.launch {
            try {
                val (order, rows) = inspect(id)
                val started = attempts.started(id)
                mutable.value = BuyerPaymentState(id, canInitiate = payable(order) && rows.isEmpty() && !started,
                    payments = rows, total = order.total, submitted=started, submissionEnabled=paymentAllowed(), notice = if (started && rows.isEmpty()) "An initiation may have reached the server. Keep checking status; do not submit another payment." else if(!paymentAllowed()) MPESA_ENVIRONMENT_NOTICE else null)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (runCatching { current() }.isSuccess) mutable.update { it.copy(busy=false,canInitiate=false,
                payments=it.payments.filter{p->buyerPaymentOutcome(p.status)=="pending"},
                notice = "Payment status could not be verified. Check your session and connection, then refresh. No new payment was sent.") } }
        }
    }
    /** Called only by the explicit confirmation button; no automatic submission or write retry. */
    fun initiate(id: Long, phoneInput: String, reviewedTotal: BigDecimal? = mutable.value.total) {
        if(!paymentAllowed()){mutable.update{it.copy(submissionEnabled=false,notice=MPESA_ENVIRONMENT_NOTICE)};return}
        if (mutable.value.busy || mutable.value.orderId != id || !mutable.value.canInitiate) return
        if (reviewedTotal == null || mutable.value.total?.compareTo(reviewedTotal) != 0) {
            mutable.update { it.copy(notice = "The order total changed. Refresh and review again.") }; return
        }
        val phone = try { buyerMpesaPhone(phoneInput) } catch (_: Exception) {
            mutable.update { it.copy(notice = "Enter a Kenyan mobile number beginning 07, 01, +2547 or +2541.") }; return
        }
        mutable.update { it.copy(busy = true, canInitiate = false, notice = null) }
        scope.launch {
            try {
                val (order, existing) = inspect(id)
                check(payable(order) && existing.isEmpty() && order.total!!.compareTo(reviewedTotal) == 0)
                current()
                check(attempts.reserve(id))
                mutable.update{it.copy(submitted=true,notice="Sending M-Pesa prompt. Waiting for payment status.")}
                current()
                val response = read(api.initiateBuyerStk(BuyerStkRequest(id, phone), current()))
                current()
                check(response.orderId == id && response.paymentId != null && response.paymentId > 0 &&
                    response.amount != null && response.amount.signum() > 0 && response.amount <= order.total)
                // An initiation response is never evidence of a completed payment, even if its status says so.
                mutable.value = BuyerPaymentState(id, payments = listOf(PaymentDto(response.paymentId, id, "mpesa", response.amount, "pending")),
                    total=order.total,submitted=true,submissionEnabled=paymentAllowed(),notice = "Check your phone for the M-Pesa prompt. Payment is pending.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (runCatching { current() }.isSuccess) mutable.update{it.copy(busy=false,canInitiate=false,
                    notice = "Initiation was not confirmed. Check payment status before taking any further action; no automatic retry was made.")}
            }
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
    content: @Composable (BuyerPaymentState, () -> Unit, (String, BigDecimal?) -> Unit) -> Unit) {
    val session by TokenManager.session.collectAsStateWithLifecycle()
    val model: BuyerPaymentModel = viewModel(key = "buyer-payment-${session.generation}-${order.id}", factory = BuyerPaymentModel.factory(LocalContext.current))
    val state by model.controller.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(order.id, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.controller.refresh(order.id!!)
            while (true) {
                delay(15_000)
                val latest = model.controller.state.value
                if (latest.payments.isNotEmpty() && latest.payments.all { buyerPaymentOutcome(it.status) != "pending" }) break
                model.controller.refresh(order.id!!)
            }
        }
    }
    content(state, { model.controller.refresh(order.id!!) }, { phone, total -> model.controller.initiate(order.id!!, phone, total) })
}
@Composable internal fun BuyerPaymentPanel(order: BuyerOrderDto, refreshOrder: () -> Unit) {
    BuyerPaymentSession(order) { state, refresh, initiate -> BuyerPaymentControls(order, state, { refresh(); refreshOrder() }, initiate) }
}
@Composable internal fun BuyerPaymentPage(order: BuyerOrderDto, state: BuyerPaymentState, back: () -> Unit,
    refresh: () -> Unit, initiate: (String, BigDecimal?) -> Unit) {
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White)) {
        OrderHeader("Pay with M-Pesa", back)
        HorizontalDivider(color = androidx.compose.ui.graphics.Color(0xFFE8EBEE))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(16.dp).testTag("payment-page")) {
            BuyerPaymentControls(order, state, refresh, initiate, startExpanded = true)
        }
    }
}
@Composable internal fun BuyerPaymentControls(order: BuyerOrderDto, state: BuyerPaymentState,
    refresh: () -> Unit, initiate: (String, BigDecimal?) -> Unit, startExpanded: Boolean = false) {
    var phone by rememberSaveable(order.id) { mutableStateOf("") }
    var showPayment by rememberSaveable(order.id) { mutableStateOf(startExpanded) }
    var confirm by remember(order.id) { mutableStateOf(false) }
    var confirmTotal by remember(order.id) { mutableStateOf<BigDecimal?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OrderSection {
            Text("Order payment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(orderMoney(state.total ?: order.total), color = OrderGreen, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val status = state.payments.lastOrNull()?.status ?: order.paymentStatus
            val outcome = buyerPaymentOutcome(status)
            OrderStatusBadge(when(outcome) { "confirmed" -> if (orderPaymentConfirmed(order, state)) "Paid" else "Part payment confirmed"; "pending" -> "Payment Pending"; "failed" -> "Payment Failed"; else -> "Status unavailable" },
                if (outcome == "confirmed") "paid" else status.orEmpty())
        }
        if (state.orderId == order.id) {
            if (state.notice != null || state.payments.isNotEmpty() || state.busy) OrderSection("Payment Status") {
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                state.payments.forEach { Text("Payment: ${buyerPaymentOutcome(it.status)} - KSh ${it.amount?.toPlainString()}") }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (!state.busy && !state.canInitiate && state.payments.isEmpty() && state.notice == null)
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
