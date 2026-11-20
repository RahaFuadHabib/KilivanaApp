package com.example.farmapp.buyers

import com.example.farmapp.buyer.buyerCartEvidence
import com.example.farmapp.checkout.CheckoutCartMetadata
import com.example.farmapp.checkout.CheckoutContract
import com.example.farmapp.checkout.CheckoutQuote
import com.example.farmapp.orders.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Keeps the accepted Buyer validators and purchaser semantics without broadening permissions. */
internal object BuyerCheckoutContract : CheckoutContract<JsonObject, BuyerStkResponse, BuyerOrderDto> {
    override fun cartMetadata(value: JsonObject, purchaserId: Long): CheckoutCartMetadata {
        val row = buyerCartEvidence(value, purchaserId).metadata
        return CheckoutCartMetadata(row.id!!, row.status!!)
    }
    override fun cartItems(value: JsonObject): Map<Long, Int>? {
        val rows = value.getAsJsonArray("items") ?: return null
        val items = rows.map { it.asJsonObject.let { row -> row.get("productId").asLong to row.get("quantity").asInt } }
        require(items.all { it.first > 0 && it.second > 0 } && items.map { it.first }.distinct().size == items.size)
        return items.toMap()
    }
    override fun createdOrderId(value: BuyerStkResponse): Long = value.orderId ?: throw IllegalStateException("Async checkout returned no orderId; use paymentId() instead.")
    override fun isAsyncCheckout(value: BuyerStkResponse): Boolean = value.orderId == null && value.paymentId != null && value.paymentId > 0
    override fun paymentId(value: BuyerStkResponse): Long? = value.paymentId?.takeIf { it > 0 }
    override fun existingOrderId(value: BuyerStkResponse): Long? = value.orderId?.takeIf { it > 0 }
    override fun validateOrder(order: BuyerOrderDto, purchaserId: Long) = validateBuyerOrder(order, purchaserId)
    override fun verifyOrder(order: BuyerOrderDto, purchaserId: Long, items: Map<Long, Int>, quote: CheckoutQuote) =
        verifyPurchasedOrder(order, purchaserId, items, quote)
    override fun orderId(order: BuyerOrderDto) = order.id!!
    override fun isPayable(order: BuyerOrderDto) = order.status in setOf("placed", "confirmed")
    override fun isPaid(order: BuyerOrderDto) = order.paymentStatus?.lowercase() == "paid"
    override fun isFinished(order: BuyerOrderDto) =
        buyerPaymentOutcome(order.paymentStatus) in setOf("confirmed", "refunded", "cancelled", "failed") || order.status == "cancelled"
    override fun isAwaitingPayment(order: BuyerOrderDto) = buyerPaymentOutcome(order.paymentStatus) == "pending"
    override fun normalizePhone(input: String) = buyerMpesaPhone(input)
}

/** Door delivery uses an owned saved address ID. Coordinates are optional address metadata. */
internal fun buyerDoorDeliveryIssue(address: com.example.farmapp.buyer.BuyerAddressDto?): String? {
    if (address == null || address.id == null || address.id <= 0) return "Select an existing delivery address."
    return null
}
