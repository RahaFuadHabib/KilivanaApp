package com.example.farmapp.checkout

/** Validated cart metadata only; it does not claim that server item lines were read. */
internal data class CheckoutCartMetadata(val id: Long, val status: String)

/** Role-specific response validation stays separate from the reusable workflow. */
internal interface CheckoutContract<CartData, CreatedOrderId, OrderData> {
    fun cartMetadata(value: CartData, purchaserId: Long): CheckoutCartMetadata
    fun createdOrderId(value: CreatedOrderId): Long
    fun validateOrder(order: OrderData, purchaserId: Long): OrderData
    fun verifyOrder(order: OrderData, purchaserId: Long, items: Map<Long, Int>, quote: CheckoutQuote): OrderData
    fun orderId(order: OrderData): Long
    fun isPayable(order: OrderData): Boolean
    fun isFinished(order: OrderData): Boolean
    fun normalizePhone(input: String): String
}
