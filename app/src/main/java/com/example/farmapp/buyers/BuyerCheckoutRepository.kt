package com.example.farmapp.buyers

import com.example.farmapp.buyers.data.DeliveryQuote
import com.example.farmapp.buyers.data.DeliveryQuoteRequest
import com.example.farmapp.checkout.*
import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.SessionStore
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.orders.BuyerOrderDto
import com.example.farmapp.orders.BuyerOrdersApi
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.*

internal data class CartUpload(val productId:Long,val quantity:Int)
internal data class QuoteOrderRequest(val quoteId:String,val phoneNumber:String) { override fun toString()="QuoteOrderRequest(redacted)" }
internal interface BrianCheckoutApi {
    @GET("carts/buyer/{buyerId}") suspend fun cart(@Path("buyerId") buyerId:Long,@Tag session:SessionSnapshot):Response<CanonicalApiResponse<JsonObject>>
    @POST("cart/items") suspend fun add(@Body item:CartUpload,@Tag session:SessionSnapshot):Response<CanonicalApiResponse<JsonObject>>
    @PUT("carts/{cartId}/items/{productId}") suspend fun quantity(@Path("cartId") cartId:Long,@Path("productId") productId:Long,@Query("quantity") quantity:Int,@Tag session:SessionSnapshot):Response<CanonicalApiResponse<JsonObject>>
    @POST("checkout") suspend fun checkout(@Tag session:SessionSnapshot):Response<CanonicalApiResponse<Unit>>
    @POST("checkout/delivery-quote") suspend fun quote(@Body request:DeliveryQuoteRequest,@Tag session:SessionSnapshot):Response<CanonicalApiResponse<DeliveryQuote>>
    @POST("checkout/order") suspend fun order(@Body request:QuoteOrderRequest,@Tag session:SessionSnapshot):Response<CanonicalApiResponse<JsonElement>>
}

/** Buyer-only adapter. No Farmer permission or endpoint substitution is inferred. */
internal class BuyerCheckoutRepository(
    private val api: BrianCheckoutApi,
    private val orders: BuyerOrdersApi,
    sessions: SessionStore,
    private val expected: SessionSnapshot,
    private val marketplace: com.example.farmapp.marketplace.MarketplaceRepository? = null
) : CheckoutRepository<JsonObject, JsonElement, BuyerOrderDto> {
    override val requiresCartIdentity = true
    private val guard = CheckoutSessionGuard(sessions, expected, BuyerPurchaserPolicy)

    private suspend fun <T> call(
        operation: CheckoutOperation,
        action: suspend (SessionSnapshot) -> Response<CanonicalApiResponse<T>>
    ): Response<CanonicalApiResponse<T>> {
        val response = action(guard.current(operation))
        try { guard.current(operation) }
        catch (failure: Exception) { response.errorBody()?.close(); throw failure }
        return response
    }

    override suspend fun refreshedSubtotal(items: Map<Long, Int>): java.math.BigDecimal? {
        val catalogue = marketplace ?: return null
        var subtotal = java.math.BigDecimal.ZERO
        for ((id, quantity) in items.toSortedMap()) {
            val product = catalogue.detail(guard.current(CheckoutOperation.READ_CART), id)
            guard.current(CheckoutOperation.READ_CART)
            BuyerPurchaserPolicy.validateQuantity(product, quantity)
            check(product.productId == id.toString() && product.sellerId != expected.auth!!.userId)
            subtotal = subtotal.add(product.unitPrice.multiply(quantity.toBigDecimal()))
        }
        return subtotal
    }

    override suspend fun readCart(purchaserId: Long) = call(CheckoutOperation.READ_CART) { session ->
        check(purchaserId > 0 && session.auth!!.userId.toLongOrNull() == purchaserId) { "Sign in as the cart buyer." }
        api.cart(purchaserId, session)
    }

    override suspend fun addItem(productId: Long, quantity: Int) = call(CheckoutOperation.UPLOAD_CART) { session ->
        require(productId > 0 && quantity > 0)
        api.add(CartUpload(productId, quantity), session)
    }

    override suspend fun setQuantity(cartId: Long, productId: Long, quantity: Int) = call(CheckoutOperation.SET_QUANTITY) { session ->
        require(cartId > 0 && productId > 0 && quantity > 0)
        api.quantity(cartId, productId, quantity, session)
    }

    override suspend fun startCheckout() = call(CheckoutOperation.START_CHECKOUT) { api.checkout(it) }
    override suspend fun deliveryQuote(request: CheckoutQuoteRequest) = call(CheckoutOperation.REQUEST_QUOTE) {
        api.quote(request.copy(cartId = null), it)
    }
    override suspend fun createOrder(quoteId: String, phoneNumber: String) = call(CheckoutOperation.CREATE_ORDER) {
        api.order(QuoteOrderRequest(quoteId, phoneNumber), it)
    }

    override suspend fun listOrders(purchaserId: Long) = call(CheckoutOperation.READ_ORDERS) { session ->
        check(purchaserId > 0 && session.auth!!.userId.toLongOrNull() == purchaserId) { "Sign in as the order buyer." }
        orders.orders(purchaserId, session)
    }

    override suspend fun readOrder(orderId: Long) = call(CheckoutOperation.READ_ORDERS) { session ->
        require(orderId > 0)
        orders.order(orderId, session)
    }
}
