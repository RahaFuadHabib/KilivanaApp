package com.example.farmapp.buyers.data
data class EmailVerificationChallenge(
    val challengeId: String,
    val expiresInSeconds: Int,
    val resendAfterSeconds: Int
)

data class CheckoutOrder(val id: Long, val message: String)
data class DeliveryAddress(
    val id: Long,
    val userId: Long,
    val addressText: String,
    val latitude: Double?,
    val longitude: Double?,
    val label: String
)
// Preserve existing Kotlin callers and JSON field names while sharing the quote implementation.
typealias DeliveryQuote = com.example.farmapp.checkout.CheckoutQuote
typealias DeliveryQuoteRequest = com.example.farmapp.checkout.CheckoutQuoteRequest
fun verifiedQuote(quote:DeliveryQuote,request:DeliveryQuoteRequest,now:java.time.Instant):DeliveryQuote =
    com.example.farmapp.checkout.verifiedCheckoutQuote(quote, request, now)
data class StkPushResult(val paymentId: Long, val status: String, val customerMessage: String?)
data class PaymentStatus(val status: String, val amount: java.math.BigDecimal?, val reference: String?)
data class CheckoutSubmission(
    val orderId: Long? = null,
    val paymentId: Long? = null,
    val paymentMessage: String? = null,
    val error: String? = null
)


/** Presentation facade over the ONE persisted FarmApp cart. Not a second ViewModel/store. */
class CartViewModel(val subtotal: java.math.BigDecimal?, private val plus:(String)->Unit, private val minus:(String)->Unit, private val removeItem:(String)->Unit) {
    fun increment(id:String)=plus(id)
    fun decrement(id:String)=minus(id)
    fun remove(id:String)=removeItem(id)
}
