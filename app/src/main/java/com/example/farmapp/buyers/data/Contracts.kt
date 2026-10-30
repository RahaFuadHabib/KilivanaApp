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
data class DeliveryQuote(
    val quoteId: String,
    val addressId: Long?,
    val subtotal: java.math.BigDecimal,
    val deliveryFee: java.math.BigDecimal,
    val total: java.math.BigDecimal,
    val currency: String,
    val expiresAt: String,
    val deliveryMethod: String,
    val pickupLocation: String? = null
)

/** Current deployed contract; station IDs are NOT accepted in pickup quotes. No write is executed here. */
data class DeliveryQuoteRequest(val deliveryMethod:String,val addressId:Long?=null,val pickupLocation:String?=null) {
    init {
        require(deliveryMethod=="DOOR_DELIVERY" && addressId!=null && addressId>0 && pickupLocation==null ||
            deliveryMethod=="PICKUP_STATION" && addressId==null && !pickupLocation.isNullOrBlank() && pickupLocation.length<=500)
    }
}
fun verifiedQuote(quote:DeliveryQuote,request:DeliveryQuoteRequest,now:java.time.Instant):DeliveryQuote {
    require(quote.deliveryMethod==request.deliveryMethod && quote.addressId==request.addressId && quote.pickupLocation==request.pickupLocation)
    require(quote.quoteId.isNotBlank() && quote.currency=="KES" && quote.subtotal.signum()>=0 && quote.deliveryFee.signum()>=0)
    require(quote.subtotal.add(quote.deliveryFee).compareTo(quote.total)==0 && java.time.Instant.parse(quote.expiresAt).isAfter(now))
    return quote
}
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
