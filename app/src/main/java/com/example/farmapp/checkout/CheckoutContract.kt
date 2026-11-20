package com.example.farmapp.checkout

/** Validated cart metadata only; it does not claim that server item lines were read. */
internal data class CheckoutCartMetadata(val id: Long, val status: String)

/** Role-specific response validation stays separate from the reusable workflow. */
internal interface CheckoutContract<CartData, CreatedOrderId, OrderData> {
    fun cartMetadata(value: CartData, purchaserId: Long): CheckoutCartMetadata
    fun cartItems(value: CartData): Map<Long, Int>? = null
    fun createdOrderId(value: CreatedOrderId): Long
    /** True when the checkout endpoint returns a paymentId with null orderId (async order creation). */
    fun isAsyncCheckout(value: CreatedOrderId): Boolean = false
    /** Extract the paymentId from an async checkout response; null for sync checkout. */
    fun paymentId(value: CreatedOrderId): Long? = null
    /** Extract an existing orderId from a sync checkout response; null for async. */
    fun existingOrderId(value: CreatedOrderId): Long? = null
    fun validateOrder(order: OrderData, purchaserId: Long): OrderData
    fun verifyOrder(order: OrderData, purchaserId: Long, items: Map<Long, Int>, quote: CheckoutQuote): OrderData
    fun orderId(order: OrderData): Long
    fun isPayable(order: OrderData): Boolean
    /** True only when this order was created from a completed checkout payment. */
    fun isPaid(order: OrderData): Boolean = false
    fun isFinished(order: OrderData): Boolean
    /** True when the owned order has an outstanding payment attempt awaiting confirmation (e.g. STK push sent). */
    fun isAwaitingPayment(order: OrderData): Boolean = false
    fun normalizePhone(input: String): String
}
