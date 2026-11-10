package com.example.farmapp.buyers

import com.example.farmapp.buyer.buyerCartEvidence
import com.example.farmapp.checkout.CheckoutCartMetadata
import com.example.farmapp.checkout.CheckoutContract
import com.example.farmapp.checkout.CheckoutQuote
import com.example.farmapp.orders.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Keeps the accepted Buyer validators and purchaser semantics without broadening permissions. */
internal object BuyerCheckoutContract : CheckoutContract<JsonObject, JsonElement, BuyerOrderDto> {
    override fun cartMetadata(value: JsonObject, purchaserId: Long): CheckoutCartMetadata {
        val row = buyerCartEvidence(value, purchaserId).metadata
        return CheckoutCartMetadata(row.id!!, row.status!!)
    }
    override fun createdOrderId(value: JsonElement) = verifiedCheckoutOrderId(value)
    override fun validateOrder(order: BuyerOrderDto, purchaserId: Long) = validateBuyerOrder(order, purchaserId)
    override fun verifyOrder(order: BuyerOrderDto, purchaserId: Long, items: Map<Long, Int>, quote: CheckoutQuote) =
        verifyPurchasedOrder(order, purchaserId, items, quote)
    override fun orderId(order: BuyerOrderDto) = order.id!!
    override fun isPayable(order: BuyerOrderDto) = order.status in setOf("placed", "confirmed")
    override fun isFinished(order: BuyerOrderDto) =
        buyerPaymentOutcome(order.paymentStatus) in setOf("confirmed", "refunded") || order.status == "cancelled"
    override fun normalizePhone(input: String) = buyerMpesaPhone(input)
}

/** Door delivery uses an owned saved address ID. Coordinates are optional address metadata. */
internal fun buyerDoorDeliveryIssue(address: com.example.farmapp.buyer.BuyerAddressDto?): String? {
    if (address == null || address.id == null || address.id <= 0) return "Select an existing delivery address."
    return null
}
