package com.example.farmapp.checkout

import com.example.farmapp.network.canonical.CanonicalApiResponse
import retrofit2.Response

/**
 * Role-specific adapters retain their cart, created-ID and order wire types. HTTP/envelope
 * evidence is deliberately preserved so this extraction does not change controller error handling.
 * No payment initiation or seller collection belongs to this checkout boundary.
 */
internal interface CheckoutRepository<CartData, CreatedOrderId, OrderData> {
    val requiresCartIdentity: Boolean get() = false
    suspend fun startCheckout(cartId: Long): Response<CanonicalApiResponse<Unit>> = startCheckout()
    suspend fun refreshedSubtotal(items: Map<Long, Int>): java.math.BigDecimal? = null
    suspend fun readCart(purchaserId: Long): Response<CanonicalApiResponse<CartData>>
    suspend fun addItem(productId: Long, quantity: Int): Response<CanonicalApiResponse<CartData>>
    suspend fun setQuantity(cartId: Long, productId: Long, quantity: Int): Response<CanonicalApiResponse<CartData>>
    suspend fun removeItem(cartId: Long, productId: Long): Response<CanonicalApiResponse<Unit>> = error("Server cart reconciliation is unavailable")
    suspend fun startCheckout(): Response<CanonicalApiResponse<Unit>>
    suspend fun deliveryQuote(request: CheckoutQuoteRequest): Response<CanonicalApiResponse<CheckoutQuote>>
    suspend fun createOrder(quoteId: String, phoneNumber: String): Response<CanonicalApiResponse<CreatedOrderId>>
    suspend fun listOrders(purchaserId: Long): Response<CanonicalApiResponse<List<OrderData>>>
    suspend fun readOrder(orderId: Long): Response<CanonicalApiResponse<OrderData>>
}
