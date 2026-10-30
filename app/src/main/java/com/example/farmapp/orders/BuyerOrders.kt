package com.example.farmapp.orders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.http.*
import com.example.farmapp.buyer.buyerDate
import java.math.BigDecimal

data class BuyerOrderDto(val id: Long? = null, val buyerId: Long? = null, val status: String? = null,
    val total: BigDecimal? = null, val paymentStatus: String? = null, val createdAt: String? = null,
    val logisticsJobId: Long? = null, val driverId: Long? = null, val items: List<BuyerOrderItemDto>? = null,
    val subtotal: BigDecimal? = null, val deliveryFee: BigDecimal? = null, val deliveryAddress: String? = null,
    val paymentMethod: String? = null, val paidAt: String? = null,
    val addressId:Long?=null,val pickupLocation:String?=null)
data class BuyerOrderItemDto(val id: Long? = null, val productId: Long? = null, val sellerId: Long? = null,
    val productName: String? = null, val unit: String? = null, val quantity: Int? = null, val unitPrice: BigDecimal? = null,
    val subtotal: BigDecimal? = null)
data class OrderEventDto(val id: Long? = null, val orderId: Long? = null, val status: String? = null, val note: String? = null, val createdAt: String? = null)
data class PaymentDto(val id: Long? = null, val orderId: Long? = null, val method: String? = null, val amount: BigDecimal? = null,
    val status: String? = null, val updatedAt: String? = null)
data class BuyerStkRequest(val orderId: Long, val phoneNumber: String) {
    override fun toString() = "BuyerStkRequest(redacted)"
}
data class BuyerStkResponse(val paymentId: Long? = null, val orderId: Long? = null,
    val amount: BigDecimal? = null, val status: String? = null)
interface BuyerOrdersApi {
    @POST("payments/stk-push/buyer") suspend fun initiateBuyerStk(@Body request: BuyerStkRequest,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<BuyerStkResponse>>
    @GET("orders/buyer/{id}") suspend fun orders(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<BuyerOrderDto>>>
    @GET("orders/{id}") suspend fun order(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<BuyerOrderDto>>
    @GET("orders/{id}/timeline") suspend fun timeline(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<OrderEventDto>>>
    @GET("payments/order/{id}") suspend fun payments(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<PaymentDto>>>
    @GET("payments/{id}/status") suspend fun payment(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<PaymentDto>>
    @POST("orders/{id}/confirm-receipt") suspend fun receipt(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<BuyerOrderDto>>
}
internal val orderStatuses = setOf("placed", "confirmed", "in_transit", "delivered", "completed", "disputed", "cancelled")
internal val paymentStatuses = setOf("pending", "paid", "held", "settled", "failed", "refunded")
internal fun validateBuyerOrder(order: BuyerOrderDto, buyerId: Long): BuyerOrderDto {
    check(order.id != null && order.id > 0 && order.buyerId == buyerId && order.status in orderStatuses &&
        order.paymentStatus in paymentStatuses && order.total != null && order.total.signum() >= 0 && order.items != null) { "Order ownership or response could not be verified." }
    check(order.items.all { it.id != null && it.id > 0 && it.productId != null && it.productId > 0 &&
        it.sellerId != null && it.sellerId > 0 && it.quantity != null && it.quantity > 0 && it.unitPrice != null && it.unitPrice.signum() >= 0 })
    return order
}
internal data class BuyerOrdersState(val rows: List<BuyerOrderDto> = emptyList(), val busy: Boolean = false, val error: String? = null,
    val selected: BuyerOrderDto? = null, val events: List<OrderEventDto> = emptyList(), val payments: List<PaymentDto> = emptyList(),
    val detailBusy: Boolean = false, val detailError: String? = null, val writeBusy: Boolean = false, val writeNotice: String? = null,
    val receiptUncertain: Boolean = false)

internal class BuyerOrdersModel : ViewModel() {
    private val expected = TokenManager.session.value
    private val api = ApiClient.buyerOrdersApi
    private val mutable = MutableStateFlow(BuyerOrdersState())
    val state = mutable.asStateFlow()
    init { refresh() }
    private fun current(): SessionSnapshot {
        val now = TokenManager.session.value
        check(now.generation == expected.generation && !now.loggingOut && !now.isDemo && now.auth?.role == "BUYER" &&
            now.auth.backendContract == "canonical" && now.auth.userId == expected.auth?.userId) { "Sign in as the order buyer." }
        return now
    }
    private fun <T> read(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            error(when(response.code()) { 401, 403 -> "This account cannot access this order. Sign in again."; 409 -> "The order changed. Reload before continuing."; else -> "Order request failed. Previously loaded data is retained." })
        }
        return response.body()?.requireData() ?: error("Incomplete order response.")
    }
    fun refresh() {
        if (mutable.value.busy || mutable.value.writeBusy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val id = current().auth!!.userId.toLong()
                val rows = read(api.orders(id, current())).map { validateBuyerOrder(it, id) }
                check(rows.map { it.id }.distinct().size == rows.size)
                current(); mutable.update { it.copy(rows = rows.sortedByDescending { row -> row.id }) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = message(e)) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun open(order: BuyerOrderDto) {
        if (mutable.value.detailBusy || mutable.value.writeBusy) return
        val id = order.id ?: return
        mutable.update { it.copy(selected = order, detailBusy = true, detailError = null,
            events = if (it.selected?.id == id) it.events else emptyList(), payments = if (it.selected?.id == id) it.payments else emptyList()) }
        viewModelScope.launch {
            try {
                val row = validateBuyerOrder(read(api.order(id, current())), current().auth!!.userId.toLong())
                check(row.id == id)
                current()
                mutable.update { if (it.selected?.id == id) it.copy(selected = row, receiptUncertain = false, rows = it.rows.map { old -> if (old.id == id) row else old }) else it }
                val events = read(api.timeline(id, current()))
                check(events.all { it.orderId == id && it.status in orderStatuses })
                current(); mutable.update { if (it.selected?.id == id) it.copy(events = events) else it }
                val payments = read(api.payments(id, current()))
                check(payments.all { it.orderId == id && it.id != null && it.id > 0 && it.status in paymentStatuses })
                // Status endpoint is authoritative for pending attempts; never infer success from initiation.
                val latest = payments.map { payment ->
                    if (payment.status != "pending") payment else read(api.payment(payment.id!!, current())).also {
                        check(it.id == payment.id && it.orderId == id && it.status in paymentStatuses)
                    }
                }
                current(); mutable.update { if (it.selected?.id == id) it.copy(payments = latest) else it }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { if (it.selected?.id == id) it.copy(detailError = message(e)) else it } }
            finally { mutable.update { it.copy(detailBusy = false) } }
        }
    }
    fun close() { if (!mutable.value.writeBusy) mutable.update { it.copy(selected = null, detailError = null, writeNotice = null) } }
    fun receipt() {
        val row = mutable.value.selected ?: return
        if (mutable.value.writeBusy || mutable.value.detailBusy || mutable.value.receiptUncertain || row.status != "delivered") return
        mutable.update { it.copy(writeBusy = true, writeNotice = null, receiptUncertain = true) }
        viewModelScope.launch {
            try {
                val saved = validateBuyerOrder(read(api.receipt(row.id!!, current())), current().auth!!.userId.toLong())
                check(saved.id == row.id && saved.status == "completed")
                current(); mutable.update { it.copy(selected = saved, rows = it.rows.map { old -> if (old.id == saved.id) saved else old }, receiptUncertain = false,
                    writeNotice = "Receipt confirmed by the backend.") }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(writeNotice = "Receipt was not confirmed. Reload the order; no automatic retry was made.") } }
            finally { mutable.update { it.copy(writeBusy = false) } }
        }
    }
    private fun message(e: Exception) = if (e is IllegalStateException) e.message else "Unable to load orders. Check your connection and try again."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun BuyerOrdersScreen(modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    val session by TokenManager.session.collectAsStateWithLifecycle()
    val model: BuyerOrdersModel = viewModel(key = "buyer-orders-${session.generation}")
    val state by model.state.collectAsStateWithLifecycle()
    var paymentFor by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmReceipt by remember { mutableStateOf(false) }
    var tracking by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, state.selected?.id, tracking) {
        if (state.selected != null && !tracking) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(15_000)
                val order = model.state.value.selected ?: break
                if (order.status in setOf("completed", "cancelled") && model.state.value.payments.none { it.status == "pending" }) break
                model.open(order)
            }
        }
    }
    fun back() { if (paymentFor != null) paymentFor = null else model.close() }
    BackHandler(state.selected != null && !tracking) { back() }
    if (tracking && state.selected != null) {
        com.example.farmapp.tracking.DeliveryTrackingScreen(state.selected!!, modifier) { tracking = false }; return
    }
    BuyerOrdersTheme {
        Column(modifier.fillMaxSize()) {
            state.error?.let { Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error); TextButton(onClick = model::refresh) { Text("Retry orders") } }
            PullToRefreshBox(isRefreshing = state.busy || state.detailBusy,
                onRefresh = { state.selected?.let(model::open) ?: model.refresh() }, modifier = Modifier.weight(1f)) {
                val row = state.selected
                if (row == null) BuyerOrdersListDesign(state.rows, onBack) { order ->
                    BuyerPaymentSession(order) { payment, _, _ ->
                        BuyerOrderCard(order, payment, { paymentFor = null; model.open(order) }, { model.open(order); paymentFor = order.id })
                    }
                } else BuyerPaymentSession(row) { payment, refresh, initiate ->
                    if (paymentFor == row.id) BuyerPaymentPage(row, payment, { paymentFor = null }, { refresh(); model.open(row) }, initiate)
                    else BuyerOrderDetailsDesign(row, payment, ::back, { paymentFor = row.id }) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.detailError?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { model.open(row) }) { Text("Retry order details") } }
                            payment.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (payment.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (!payment.canInitiate && !orderPaymentConfirmed(row, payment)) Text("Refresh to check payment availability and status.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { refresh(); model.open(row) }) { Text("Refresh payment and order") }
                            OutlinedButton(onClick = { tracking = true }, modifier = Modifier.fillMaxWidth()) { Text("Track delivery") }
                            if (state.events.isNotEmpty()) OrderSection("Order timeline") {
                                state.events.forEach { Text("${it.status?.replace('_', ' ')} ? ${buyerDate(it.createdAt)}\n${it.note.orEmpty()}", style = MaterialTheme.typography.bodySmall) }
                            }
                            if (row.status == "delivered") OutlinedButton(onClick = { confirmReceipt = true },
                                enabled = !state.writeBusy && !state.detailBusy && !state.receiptUncertain, modifier = Modifier.fillMaxWidth()) { Text("Confirm receipt") }
                            state.writeNotice?.let { Text(it) }
                        }
                    }
                }
            }
        }
        if (confirmReceipt) AlertDialog(onDismissRequest = { confirmReceipt = false }, title = { Text("Confirm delivery received?") },
            text = { Text("Confirm only after receiving your goods. The backend may release held payment to the seller.") },
            confirmButton = { TextButton(onClick = { confirmReceipt = false; model.receipt() }) { Text("Confirm receipt") } },
            dismissButton = { TextButton(onClick = { confirmReceipt = false }) { Text("Cancel") } })
    }
}
/** Small summary kept for existing callers and formatting regression coverage. */
@Composable internal fun BuyerOrderSummary(row: BuyerOrderDto) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Order", style = MaterialTheme.typography.titleLarge)
        Text(buyerDate(row.createdAt))
        Text(orderMoney(row.total))
    }
}
