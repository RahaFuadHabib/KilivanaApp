package com.example.farmapp.farmerpurchases

import com.example.farmapp.checkout.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.orders.*
import com.example.farmapp.payments.mpesaPhone
import com.example.farmapp.payments.paymentOutcome
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.http.*
import java.time.Instant
import java.util.UUID

internal data class FarmerPreparedCart(val id: Long, val buyerId: Long, val status: String)
internal data class FarmerQuoteRequest(val deliveryMethod: String, val addressId: Long?, val pickupLocation: String?,
    val pickupStationId: Long?)
internal data class FarmerQuoteOrder(val quoteId: String, val phoneNumber: String?) {
    override fun toString() = "FarmerQuoteOrder(redacted)"
}
internal data class FarmerIntentRequest(val orderId: Long, val phoneNumber: String, val requestId: String) {
    override fun toString() = "FarmerIntentRequest(redacted)"
}
internal interface FarmerTransactionApi {
    @POST("checkout/validate") suspend fun prepare( @Tag session: SessionSnapshot): Response<CanonicalApiResponse<FarmerPreparedCart>>
    @POST("carts/{cartId}/checkout") suspend fun start(@Path("cartId") cartId: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<Unit>>
    @POST("checkout/delivery-quote") suspend fun quote(@Body request: FarmerQuoteRequest, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<CheckoutQuote>>
    @POST("checkout/order") suspend fun order(@Body request: FarmerQuoteOrder, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<BuyerStkResponse>>
}
internal data class FarmerTransactionRecord(val key: String,
    @field:com.google.gson.annotations.JsonAdapter(JournalProductMapAdapter::class) val items: Map<Long, Int>, val delivery: CheckoutQuoteRequest,
    val phase: String = "PREPARING", val quote: CheckoutQuote? = null, val orderId: Long? = null,
    val paymentKey: String? = null, val paymentId: Long? = null, val quoteRequestId: String? = null, val cartId: Long? = null,
    val phoneNumber: String? = null, val beforeOrderIds: Set<Long>? = emptySet())
internal interface FarmerTransactionStore {
    fun read(): FarmerTransactionRecord?
    fun write(record: FarmerTransactionRecord)
}
internal data class FarmerTransactionState(val busy: Boolean = false, val record: FarmerTransactionRecord? = null,
    val order: BuyerOrderDto? = null, val payment: PaymentDto? = null, val notice: String? = null)

/** The quote checkout endpoint sends STK and creates the order only after Daraja confirms payment. */
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
    private fun requirePickup(selection: DeliverySelection) {
        when (selection) {
            is DeliverySelection.Station -> check(selection.pickupStationId > 0)
            is DeliverySelection.TypedPickup, is DeliverySelection.Door ->
                throw ClientValidation("Select an available pickup station before requesting a quote or creating an order.")
        }
    }
    private fun requirePickup(request: CheckoutQuoteRequest) {
        if (request.deliveryMethod != "PICKUP_STATION" || request.addressId != null ||
            request.pickupStationId == null || request.pickupLocation != null) {
            throw ClientValidation("Select an available pickup station before requesting a quote or creating an order.")
        }
        check(request.pickupStationId > 0)
    }
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
    private fun verifiedQuote(quote: CheckoutQuote, cartId: Long, items: Map<Long, Int>,
        delivery: CheckoutQuoteRequest): CheckoutQuote {
        try { verifiedCheckoutQuote(quote, delivery, now()) }
        catch (_: Exception) { throw ClientValidation("The delivery quote did not match the selected delivery details, totals, or expiry.") }
        val hasCartId = quote.cartId != null
        val hasItems = quote.items != null
        if (hasCartId != hasItems) throw ClientValidation("The delivery quote returned partial cart evidence; it must return both cartId and priced item lines, or neither.")
        if (hasCartId) try { verifyQuotedBasket(quote, cartId, items) }
        catch (_: Exception) { throw ClientValidation("The quoted cart or priced item lines did not match the saved basket. No purchase order was created.") }
        return quote
    }
    private fun run(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, notice = null) }
        scope.launch {
            try { current(); action() }
            catch (e: CancellationException) { throw e }
            catch (failure: Exception) {
                reportFailure(failure)
                val saved = state.value.record
                val notice = when {
                    failure is ClientValidation -> failure.message
                    saved?.phase == "PAYMENT_PENDING" && saved.paymentId != null ->
                        "Payment was requested, but its result could not be verified. Refresh checkout status before trying again."
                    else -> "Checkout is pending or could not be verified. Refresh to reconcile the saved request before trying again."
                }
                mutable.update { it.copy(notice = notice) }
            }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun prepare(items: Map<Long, Int>, delivery: DeliverySelection) = run {
        check(state.value.record == null || state.value.record?.phase in setOf("CONFIRMED", "PAYMENT_FAILED", "PAYMENT_CANCELLED")) { "Recover saved checkout first" }
        require(items.isNotEmpty() && items.size <= 100 && items.all { it.key > 0 && it.value > 0 })
        requirePickup(delivery)
        save(FarmerTransactionRecord(UUID.randomUUID().toString(), items.toSortedMap(), delivery.toQuoteRequest()))
        prepareSaved()
    }
    private suspend fun prepareSaved() {
        val record = state.value.record!!
        requirePickup(record.delivery)
        val cartId = if (record.cartId == null) {
            val cart = request { api.prepare(it) }
            check(cart.id > 0 && cart.buyerId == expected.auth!!.userId.toLong() && cart.status == "ACTIVE")
            save(record.copy(cartId = cart.id, phase = "START_PENDING"))
            val response = api.start(cart.id, current())
            current()
            check(response.isSuccessful && response.body()?.success == true) { "Cart preparation was not acknowledged. Refresh cart and orders before continuing." }
            save(state.value.record!!.copy(phase = "PREPARING"))
            cart.id
        } else record.cartId
        val selection = record.delivery
        val quote = request { api.quote(FarmerQuoteRequest(selection.deliveryMethod, selection.addressId,
            selection.pickupLocation, selection.pickupStationId), it) }
        val verified = verifiedQuote(quote, cartId, record.items, selection)
        save(state.value.record!!.copy(phase = "QUOTE_READY", quote = verified))
    }
    fun submitOrder(phoneInput: String) = run {
        val record = state.value.record!!
        check(record.phase in setOf("QUOTE_READY", "PAYMENT_FAILED", "PAYMENT_CANCELLED"))
        requirePickup(record.delivery)
        val phone = try { mpesaPhone(phoneInput) }
        catch (_: Exception) { throw ClientValidation("Enter a valid Kenyan M-Pesa phone number before requesting payment.") }
        verifiedQuote(record.quote!!, record.cartId ?: throw ClientValidation("The saved cart ID is unavailable. Reconcile checkout before continuing."),
            record.items, record.delivery)
        val quote = record.quote!!
        check(com.example.farmapp.orders.BuyerMpesaEnvironment.ready()) { "Sandbox payment configuration is required." }
        val beforeOrderIds = request { orders.orders(expected.auth!!.userId.toLong(), it) }
            .mapNotNull { it.id }.toSet()
        save(record.copy(phase = "PAYMENT_PENDING", phoneNumber = phone, paymentId = null,
            orderId = null, beforeOrderIds = beforeOrderIds))
        val result = request { api.order(FarmerQuoteOrder(quote.quoteId, phone), it) }
        check(result.paymentId != null && result.paymentId > 0 && result.orderId == null &&
            result.amount?.compareTo(quote.total) == 0) {
            "The backend payment response did not match the reviewed quote. Refresh checkout status before retrying."
        }
        save(state.value.record!!.copy(paymentId = result.paymentId))
        reconcilePayment()
        poll()
    }
    fun refreshQuote() = run {
        val record = state.value.record!!
        check(record.phase in setOf("QUOTE_READY", "PAYMENT_FAILED", "PAYMENT_CANCELLED"))
        save(record.copy(phase = "PREPARING", quote = null, paymentId = null, orderId = null,
            quoteRequestId = UUID.randomUUID().toString()))
        prepareSaved()
    }
    private suspend fun recoverOrder() = reconcilePayment()
    private suspend fun readOrder(): BuyerOrderDto {
        val record = state.value.record!!
        val orderId = record.orderId!!
        val purchaserId = expected.auth!!.userId.toLong()
        val order = try { request { orders.order(orderId, it) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            throw ClientValidation("Purchase order #$orderId was created, but its details could not be loaded. Refresh order details; no payment prompt was sent.")
        }
        if (order.id != orderId || order.buyerId != purchaserId)
            throw ClientValidation("Purchase order #$orderId was created, but its identity or owner could not be verified. No payment prompt was sent.")
        try { validateFarmerPurchaseOrder(order, purchaserId) }
        catch (_: Exception) {
            throw ClientValidation("Purchase order #$orderId was created, but its amounts, delivery details, or ownership could not be verified. No payment prompt was sent.")
        }
        try { FarmerCheckoutContract.verifyOrder(order, purchaserId, record.items, record.quote!!) }
        catch (_: Exception) {
            throw ClientValidation("Purchase order #$orderId was created, but its details did not match the reviewed quote. No payment prompt was sent.")
        }
        mutable.update { it.copy(order = order) }
        return order
    }
    fun initiate(phone: String) = submitOrder(phone)
    private suspend fun reconcilePayment() {
        val record = state.value.record!!
        val paymentId = record.paymentId
        // This flow's order appears only after the callback succeeds. Match against the saved
        // quote and basket before exposing it as the completed purchase.
        val rows = request { orders.orders(expected.auth!!.userId.toLong(), it) }
        val candidate = rows.firstOrNull { order ->
            order.id !in record.beforeOrderIds.orEmpty() && paymentOutcome(order.paymentStatus) == "confirmed" &&
                runCatching { FarmerCheckoutContract.verifyOrder(order, expected.auth!!.userId.toLong(), record.items, record.quote!!) }.isSuccess
        }
        if (candidate != null) {
            val id = candidate.id!!
            val verified = request { orders.order(id, it) }
            FarmerCheckoutContract.verifyOrder(verified, expected.auth!!.userId.toLong(), record.items, record.quote!!)
            mutable.update { it.copy(order = verified) }
            save(record.copy(phase = "CONFIRMED", orderId = id, paymentId = paymentId))
            return
        }
        // A new order exists with confirmed payment but it does not match the reviewed quote.
        // Do not keep polling forever; surface the mismatch so the user can contact support.
        val unmatched = rows.firstOrNull { order ->
            order.id !in record.beforeOrderIds.orEmpty() && paymentOutcome(order.paymentStatus) == "confirmed"
        }
        if (unmatched != null) {
            save(record.copy(phase = "PAYMENT_FAILED"))
            mutable.update { it.copy(notice = "Payment was confirmed but the created order does not match your reviewed basket. Do not pay again; contact support.") }
            return
        }
        paymentId ?: return
        val latest = request { orders.payment(paymentId, it) }
        mutable.update { it.copy(payment = latest) }
        val outcome = paymentOutcome(latest.status)
        when (outcome) {
            "failed" -> save(record.copy(phase = "PAYMENT_FAILED", paymentId = latest.id))
            "cancelled" -> save(record.copy(phase = "PAYMENT_CANCELLED", paymentId = latest.id))
            "confirmed" -> mutable.update { it.copy(notice = "Payment confirmed. Waiting for your order to appear.") }
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
            "ORDER_PENDING" -> recoverOrder()
            "ORDER_CREATED" -> readOrder()
            "PAYMENT_PENDING" -> { reconcilePayment(); poll() }
            "PAYMENT_FAILED", "PAYMENT_CANCELLED", "CONFIRMED" -> reconcilePayment()
        }
    }
    private fun poll() {
        if (polling?.isActive == true) return
        polling = scope.launch {
            val deadline = System.currentTimeMillis() + 120_000L
            var timeoutShown = false
            while (isActive && state.value.record?.phase == "PAYMENT_PENDING") {
                delay(if (timeoutShown) maxOf(pollMs, 10_000L) else pollMs)
                if (state.value.busy) continue
                if (!timeoutShown && System.currentTimeMillis() > deadline) {
                    timeoutShown = true
                    mutable.update { it.copy(notice = "Payment is taking longer than expected. Checking this same order for the backend's final status; no duplicate prompt was sent.") }
                }
                mutable.update { it.copy(busy = true) }
                try { current(); reconcilePayment() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutable.update { it.copy(notice = "Waiting for authoritative payment reconciliation. No new prompt was sent.") } }
                finally { mutable.update { it.copy(busy = false) } }
            }
        }
    }
}
