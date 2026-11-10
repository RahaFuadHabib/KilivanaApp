package com.example.farmapp.farmerpurchases

import com.example.farmapp.checkout.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.orders.*
import com.example.farmapp.payments.mpesaPhone
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.http.*
import java.time.Instant
import java.util.UUID

internal data class FarmerPreparedCart(val id: Long, val buyerId: Long, val status: String,
    val items: List<FarmerPreparedItem> = emptyList())
internal data class FarmerPreparedItem(val productId: Long, val quantity: Int)
internal data class FarmerQuoteRequest(val deliveryMethod: String, val addressId: Long?, val pickupLocation: String?,
    val pickupStationId: Long?, val cartId: Long? = null)
internal data class FarmerQuoteOrder(val quoteId: String)
internal data class FarmerIntentRequest(val orderId: Long, val phoneNumber: String, val requestId: String) {
    override fun toString() = "FarmerIntentRequest(redacted)"
}
internal interface FarmerTransactionApi {
    @POST("checkout/validate") suspend fun prepare( @Tag session: SessionSnapshot): Response<CanonicalApiResponse<FarmerPreparedCart>>
    @POST("checkout") suspend fun start(@Query("cartId") cartId: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<Unit>>
    @POST("checkout/delivery-quote") suspend fun quote(@Body request: FarmerQuoteRequest, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<CheckoutQuote>>
    @POST("checkout/order") suspend fun order(@Body request: FarmerQuoteOrder, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<Long>>
    @POST("payments/stk-push/buyer") suspend fun pay(@Body request: FarmerIntentRequest, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<BuyerStkResponse>>
}
internal data class FarmerTransactionRecord(val key: String,
    @field:com.google.gson.annotations.JsonAdapter(JournalProductMapAdapter::class) val items: Map<Long, Int>, val delivery: CheckoutQuoteRequest,
    val phase: String = "PREPARING", val quote: CheckoutQuote? = null, val orderId: Long? = null,
    val paymentKey: String? = null, val paymentId: Long? = null, val quoteRequestId: String? = null, val cartId: Long? = null)
internal interface FarmerTransactionStore {
    fun read(): FarmerTransactionRecord?
    fun write(record: FarmerTransactionRecord)
}
internal data class FarmerTransactionState(val busy: Boolean = false, val record: FarmerTransactionRecord? = null,
    val order: BuyerOrderDto? = null, val payment: PaymentDto? = null, val notice: String? = null)

/** All uncertain writes retain their original keys. Automatic recovery performs keyed order replay
 * or payment reads only. The server alone decides whether another STK can safely be dispatched. */
internal class FarmerTransactionController(private val api: FarmerTransactionApi, private val orders: BuyerOrdersApi,
    private val sessions: SessionStore, private val expected: SessionSnapshot, private val disk: FarmerTransactionStore,
    private val scope: CoroutineScope, val enabled: Boolean, private val pollMs: Long = 5000,
    private val now: () -> Instant = Instant::now,
    private val reportFailure: (Throwable) -> Unit = {}) {
    private val mutable = MutableStateFlow(FarmerTransactionState())
    val state = mutable.asStateFlow()
    private var readable = true
    private var polling: Job? = null
    private val sessionGuard = CheckoutSessionGuard(sessions, expected, FarmerPurchaserPolicy)
    private class ClientValidation(message: String) : Exception(message)
    init {
        try { mutable.value = FarmerTransactionState(record = disk.read()) }
        catch (_: Exception) { readable = false; mutable.value = FarmerTransactionState(notice = "Saved checkout cannot be read. No record was replaced.") }
    }
    private fun current(): SessionSnapshot = sessionGuard.current(CheckoutOperation.READ_ORDERS).also {
        check(enabled && readable)
    }
    private fun save(record: FarmerTransactionRecord) {
        current(); disk.write(record); mutable.update { it.copy(record = record) }
    }
    private suspend fun <T> request(action: suspend (SessionSnapshot) -> Response<CanonicalApiResponse<T>>): T {
        val response = action(current())
        try { current() } catch (e: Exception) { response.errorBody()?.close(); throw e }
        return response.requirePurchaseData()
    }
    private fun run(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, notice = null) }
        scope.launch {
            try { current(); action() }
            catch (e: CancellationException) { throw e }
            catch (failure: Exception) {
                reportFailure(failure)
                mutable.update { it.copy(notice = if (failure is ClientValidation) failure.message else
                    "Checkout is pending or could not be verified. Refresh to reconcile the saved request before trying again.") }
            }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun prepare(items: Map<Long, Int>, delivery: DeliverySelection) = run {
        check(state.value.record == null || state.value.record?.phase == "CONFIRMED") { "Recover saved checkout first" }
        require(items.isNotEmpty() && items.size <= 100 && items.all { it.key > 0 && it.value > 0 })
        save(FarmerTransactionRecord(UUID.randomUUID().toString(), items.toSortedMap(), delivery.toQuoteRequest()))
        prepareSaved()
    }
    private suspend fun prepareSaved() {
        val record = state.value.record!!
        val cartId = if (record.cartId == null) {
            val cart = request { api.prepare(it) }
            check(cart.id > 0 && cart.buyerId == expected.auth!!.userId.toLong() && cart.status == "ACTIVE")
            check(cart.items.map { it.productId }.distinct().size == cart.items.size && cart.items.associate { it.productId to it.quantity } == record.items)
            save(record.copy(cartId = cart.id, phase = "START_PENDING"))
            val response = api.start(cart.id, current())
            current()
            check(response.isSuccessful && response.body()?.success == true) { "Cart preparation was not acknowledged. Refresh cart and orders before continuing." }
            save(state.value.record!!.copy(phase = "PREPARING"))
            cart.id
        } else record.cartId
        val selection = record.delivery
        val quote = request { api.quote(FarmerQuoteRequest(selection.deliveryMethod, selection.addressId,
            selection.pickupLocation, selection.pickupStationId, cartId), it) }
        verifiedCheckoutQuote(quote, selection.copy(cartId = cartId), now())
        verifyQuotedBasket(quote, cartId, record.items)
        save(state.value.record!!.copy(phase = "QUOTE_READY", quote = quote))
    }
    fun submitOrder() = run {
        val record = state.value.record!!
        check(record.phase == "QUOTE_READY")
        try { verifiedCheckoutQuote(record.quote!!, record.delivery, now()) }
        catch (_: Exception) { throw ClientValidation("This quote is no longer valid. Request a new quote and review it.") }
        save(record.copy(phase = "ORDER_PENDING"))
        recoverOrder()
    }
    fun refreshQuote() = run {
        val record = state.value.record!!
        check(record.phase == "QUOTE_READY")
        save(record.copy(phase = "PREPARING", quote = null, quoteRequestId = UUID.randomUUID().toString()))
        prepareSaved()
    }
    private suspend fun recoverOrder() {
        val record = state.value.record!!
        val id = request { api.order(FarmerQuoteOrder(record.quote!!.quoteId), it) }
        check(id > 0)
        save(record.copy(phase = "ORDER_CREATED", orderId = id))
        readOrder()
    }
    private suspend fun readOrder(): BuyerOrderDto {
        val record = state.value.record!!
        val order = request { orders.order(record.orderId!!, it) }
        validateFarmerPurchaseOrder(order, expected.auth!!.userId.toLong())
        FarmerCheckoutContract.verifyOrder(order, expected.auth.userId.toLong(), record.items, record.quote!!)
        mutable.update { it.copy(order = order) }
        return order
    }
    fun initiate(phone: String) = run {
        var record = state.value.record!!
        check(record.orderId != null && (record.phase == "ORDER_CREATED" || record.phase == "PAYMENT_FAILED"))
        val order = readOrder()
        check(order.status in setOf("placed", "confirmed") && order.paymentStatus in setOf("pending", "failed"))
        check(com.example.farmapp.orders.BuyerMpesaEnvironment.ready()) { "Sandbox payment configuration is required." }
        val normalized = try { mpesaPhone(phone) }
            catch (_: Exception) { throw ClientValidation("Enter a valid Kenyan M-Pesa phone number.") }
        try { check(order.total!!.intValueExact() > 0) }
        catch (_: Exception) { throw ClientValidation("M-Pesa requires an exact whole-KES total within provider limits. No prompt was requested.") }
        record = record.copy(phase = "PAYMENT_PENDING", paymentKey = UUID.randomUUID().toString(), paymentId = null)
        save(record) // Commit the key before transport. Never send a second initiation on a timeout.
        poll() // Read-only reconciliation must continue even if the initiation response is lost.
        val result = request { api.pay(FarmerIntentRequest(record.orderId!!, normalized, record.paymentKey!!), it) }
        check(result.orderId == record.orderId && result.paymentId != null && result.paymentId > 0 && result.amount?.compareTo(order.total) == 0)
        save(record.copy(paymentId = result.paymentId))
        reconcilePayment()
        poll()
    }
    private suspend fun reconcilePayment() {
        val record = state.value.record!!
        val order = readOrder()
        val rows = request { orders.payments(record.orderId!!, it) }
        check(rows.map { it.id }.distinct().size == rows.size)
        rows.forEach { check(it.id != null && it.id > 0 && it.orderId == order.id && it.amount != null && it.amount.signum() > 0 && it.amount <= order.total && it.status in paymentStatuses) }
        val row = if (record.paymentId != null) rows.singleOrNull { it.id == record.paymentId } else rows.maxByOrNull { it.id!! }
        if (row == null) { mutable.update { it.copy(notice = "Payment outcome is unresolved. No STK request was repeated.") }; return }
        val latest = request { orders.payment(row.id!!, it) }
        FarmerPaymentContract.validateStatus(latest, row, order.id!!)
        mutable.update { it.copy(payment = latest) }
        when (latest.status) {
            "paid", "held", "settled" -> save(record.copy(phase = "CONFIRMED", paymentId = latest.id))
            "failed" -> save(record.copy(phase = "PAYMENT_FAILED", paymentId = latest.id))
            else -> save(record.copy(paymentId = latest.id))
        }
    }
    fun recover() = run {
        when (state.value.record?.phase) {
            "START_PENDING" -> {
                val record = state.value.record!!
                val response = api.start(checkNotNull(record.cartId), current())
                current()
                check(response.isSuccessful && response.body()?.success == true)
                save(record.copy(phase = "PREPARING")); prepareSaved()
            }
            "PREPARING" -> prepareSaved() // Backend commits preparation/quote under original keys.
            "ORDER_PENDING" -> recoverOrder() // Atomic quote replay returns the same owned order.
            "ORDER_CREATED" -> readOrder()
            "PAYMENT_PENDING" -> { reconcilePayment(); poll() }
            "PAYMENT_FAILED", "CONFIRMED" -> reconcilePayment()
        }
    }
    private fun poll() {
        if (polling?.isActive == true) return
        polling = scope.launch {
            while (isActive && state.value.record?.phase == "PAYMENT_PENDING") {
                delay(pollMs)
                if (state.value.busy) continue
                mutable.update { it.copy(busy = true) }
                try { current(); reconcilePayment() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutable.update { it.copy(notice = "Waiting for authoritative payment reconciliation. No new prompt was sent.") } }
                finally { mutable.update { it.copy(busy = false) } }
            }
        }
    }
}
