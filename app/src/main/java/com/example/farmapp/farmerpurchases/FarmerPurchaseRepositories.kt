package com.example.farmapp.farmerpurchases

import com.example.farmapp.buyers.*
import com.example.farmapp.checkout.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.orders.*
import com.example.farmapp.payments.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.*

/** Spring Page envelope is NOT the List returned by orders/buyer/{id}. */
internal data class PurchaserOrderPage(val content: List<BuyerOrderDto>? = null,
    val number: Int? = null, val size: Int? = null, val totalElements: Long? = null,
    val totalPages: Int? = null, val last: Boolean? = null,
    val numberOfElements: Int? = null, val first: Boolean? = null, val empty: Boolean? = null,
    val pageable: JsonObject? = null, val sort: JsonObject? = null)

internal interface PurchaserOrderSearchApi {
    @GET("orders/search") suspend fun search(@Query("buyerId") purchaserId: Long,
        @Query("page") page: Int, @Query("size") size: Int,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<PurchaserOrderPage>>
}

/** Shares the existing session-tagged Retrofit services, not Buyer authorization. No retries. */
internal class FarmerPurchaseRequests(sessions: SessionStore, expected: SessionSnapshot) {
    private val guard = CheckoutSessionGuard(sessions, expected, FarmerPurchaserPolicy)
    fun current(operation: CheckoutOperation = CheckoutOperation.READ_ORDERS) = guard.current(operation)
    fun purchaserId() = current().auth!!.userId.toLong().also { require(it > 0) }
    suspend fun <T> call(operation: CheckoutOperation,
        action: suspend (SessionSnapshot) -> Response<CanonicalApiResponse<T>>,
        validate: (T) -> Unit = {}): Response<CanonicalApiResponse<T>> {
        val response = action(current(operation))
        try {
            current(operation)
            if (response.isSuccessful && response.body()?.success == true) response.body()?.data?.let(validate)
        } catch (failure: Exception) { response.errorBody()?.close(); throw failure }
        return response
    }
    fun blocked(): Nothing { current(); error(FARMER_CHECKOUT_BLOCKED) }
}

/** Same numeric-ID e-commerce contracts as Buyer; all unsafe cart/checkout operations denied. */
internal class FarmerCheckoutRepository(private val orders: BuyerOrdersApi,
    sessions: SessionStore, expected: SessionSnapshot) : CheckoutRepository<JsonObject, JsonElement, BuyerOrderDto> {
    private val requests = FarmerPurchaseRequests(sessions, expected)
    override suspend fun readCart(purchaserId: Long): Response<CanonicalApiResponse<JsonObject>> = requests.blocked()
    override suspend fun addItem(productId: Long, quantity: Int): Response<CanonicalApiResponse<JsonObject>> = requests.blocked()
    override suspend fun setQuantity(cartId: Long, productId: Long, quantity: Int): Response<CanonicalApiResponse<JsonObject>> = requests.blocked()
    override suspend fun startCheckout(): Response<CanonicalApiResponse<Unit>> = requests.blocked()
    override suspend fun deliveryQuote(request: CheckoutQuoteRequest): Response<CanonicalApiResponse<CheckoutQuote>> = requests.blocked()
    override suspend fun createOrder(quoteId: String, phoneNumber: String): Response<CanonicalApiResponse<JsonElement>> = requests.blocked()
    override suspend fun listOrders(purchaserId: Long) = requests.call(CheckoutOperation.READ_ORDERS, { session ->
        require(purchaserId == requests.purchaserId()) { "Use the authenticated purchaser ID." }
        orders.orders(purchaserId, session)
    }) { rows ->
        rows.forEach { validateFarmerPurchaseOrder(it, purchaserId) }
        check(rows.map { it.id }.distinct().size == rows.size)
    }
    override suspend fun readOrder(orderId: Long) = requests.call(CheckoutOperation.READ_ORDERS, {
        require(orderId > 0); orders.order(orderId, it)
    }) { validateFarmerPurchaseOrder(it, requests.purchaserId()); check(it.id == orderId) }
}

internal class FarmerOrderSearchRepository(private val api: PurchaserOrderSearchApi,
    sessions: SessionStore, expected: SessionSnapshot) {
    private val requests = FarmerPurchaseRequests(sessions, expected)
    suspend fun page(page: Int, size: Int = 20): Response<CanonicalApiResponse<PurchaserOrderPage>> {
        require(page >= 0 && size in 1..100)
        return requests.call(CheckoutOperation.READ_ORDERS, {
            api.search(requests.purchaserId(), page, size, it)
        }) { result ->
            check(result.number == page && result.size == size && result.content != null && result.last != null &&
                result.totalElements != null && result.totalElements >= 0 && result.totalPages != null && result.totalPages >= 0)
            check(result.content.size <= size && result.content.size.toLong() <= result.totalElements)
            val pages = if (result.totalElements == 0L) 0L else (result.totalElements - 1) / size + 1
            check(result.totalPages.toLong() == pages && result.last == (page.toLong() >= pages - 1))
            check(result.numberOfElements == null || result.numberOfElements == result.content.size)
            result.content.forEach { validateFarmerPurchaseOrder(it, requests.purchaserId()) }
            check(result.content.map { it.id }.distinct().size == result.content.size)
        }
    }
}

/** Wire validators are pure ownership/amount checks; Buyer policy is not reused. */
internal object FarmerCheckoutContract : CheckoutContract<JsonObject, JsonElement, BuyerOrderDto> by BuyerCheckoutContract
internal object FarmerPaymentContract : PurchaserPaymentContract<BuyerOrderDto, PaymentDto, BuyerStkResponse> by BuyerPaymentContract {
    override fun validateOrder(order: BuyerOrderDto, orderId: Long, purchaserId: Long) =
        validateFarmerPurchaseOrder(order, purchaserId).also { check(it.id == orderId) }
}

/** Historical orders do not advertise sellerType. Validate purchaser, IDs and money; never infer
 * Supplier eligibility from sellerId. New basket eligibility is checked against product detail.
 */
internal fun validateFarmerPurchaseOrder(order: BuyerOrderDto, purchaserId: Long): BuyerOrderDto {
    validateBuyerOrder(order, purchaserId)
    val items = order.items!!
    check(items.isNotEmpty() && items.map { it.id }.distinct().size == items.size &&
        items.map { it.productId }.distinct().size == items.size && items.none { it.sellerId == purchaserId })
    check(order.subtotal != null && order.subtotal.signum() >= 0 && order.deliveryFee != null && order.deliveryFee.signum() >= 0)
    val sum = items.fold(java.math.BigDecimal.ZERO) { amount, item ->
        val line = item.unitPrice!! * item.quantity!!.toBigDecimal()
        check(item.subtotal != null && item.subtotal.compareTo(line) == 0)
        amount + line
    }
    check(sum.compareTo(order.subtotal) == 0 && (sum + order.deliveryFee).compareTo(order.total) == 0)
    check(order.addressId == null || order.addressId > 0)
    check(order.pickupStationId == null || order.pickupStationId > 0)
    check(order.addressId == null || order.pickupStationId == null && order.pickupLocation == null)
    check(order.pickupStationId == null || order.pickupLocation == null)
    return order
}

/** Purchaser payment route is payments/stk-push/buyer, NEVER seller collections /farmer.
 * No initiation call is reachable in this batch, even when Buyer environment checks pass.
 */
internal class FarmerPaymentRepository(private val api: BuyerOrdersApi,
    sessions: SessionStore, expected: SessionSnapshot) : PurchaserPaymentRepository<BuyerOrderDto, PaymentDto, BuyerStkResponse> {
    private val requests = FarmerPurchaseRequests(sessions, expected)
    override suspend fun order(orderId: Long) = requests.call(CheckoutOperation.READ_ORDERS, {
        require(orderId > 0); api.order(orderId, it)
    }) { validateFarmerPurchaseOrder(it, requests.purchaserId()); check(it.id == orderId) }
    override suspend fun payments(orderId: Long): Response<CanonicalApiResponse<List<PaymentDto>>> {
        val owned = order(orderId).requirePurchaseData() // Explicit ownership before dependent reads.
        return requests.call(CheckoutOperation.READ_PAYMENTS, { api.payments(orderId, it) }) { rows ->
            check(rows.map { it.id }.distinct().size == rows.size)
            rows.forEach { FarmerPaymentContract.validateReference(it, orderId); checkedPayment(it); check(it.amount!! <= owned.total) }
        }
    }
    override suspend fun payment(paymentId: Long): Response<CanonicalApiResponse<PaymentDto>> {
        val response = requests.call(CheckoutOperation.READ_PAYMENTS, {
            require(paymentId > 0); api.payment(paymentId, it)
        }) { check(it.id == paymentId); checkedPayment(it) }
        if (response.isSuccessful && response.body()?.success == true) response.body()?.data?.let { payment ->
            val owned = order(payment.orderId!!).requirePurchaseData()
            check(payment.amount!! <= owned.total)
        }
        return response
    }
    override suspend fun initiate(orderId: Long, phone: String): Response<CanonicalApiResponse<BuyerStkResponse>> {
        requests.current(); error(FARMER_PAYMENT_BLOCKED)
    }
    private fun checkedPayment(row: PaymentDto) {
        check(row.orderId != null && row.orderId > 0 && row.amount != null && row.amount.signum() >= 0 && row.status in paymentStatuses)
    }
}

internal fun <T> Response<CanonicalApiResponse<T>>.requirePurchaseData(): T {
    if (!isSuccessful) {
        errorBody()?.close()
        error(when (code()) {
            401, 403 -> "This account cannot access these purchases. Sign in again or ask the backend team to confirm Farmer purchaser permissions."
            404 -> "This purchasing capability is unavailable on the configured backend."
            409 -> "The record changed. Reload before continuing."
            else -> "Purchasing data could not be verified. Retry the read when connected."
        })
    }
    return body()?.requireData() ?: error("Incomplete purchasing response.")
}
