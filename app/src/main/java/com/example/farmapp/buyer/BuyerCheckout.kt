package com.example.farmapp.buyer

import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.http.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Advertised metadata only. Runtime buyer-cart reads use JsonObject so undeclared properties are observable. */
internal data class BuyerCartMetadata(val id: Long? = null, val buyerId: Long? = null, val status: String? = null)
internal data class BuyerCartEvidence(val metadata: BuyerCartMetadata, val propertyPaths: Set<String>, val hasUndocumentedProperties: Boolean)
/** Structure only, never credentials, prices, addresses or raw response values in diagnostics. */
internal fun buyerCartEvidence(data: JsonObject, expectedBuyer: Long): BuyerCartEvidence {
    fun id(name: String): Long {
        val value = data.get(name)
        check(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asBigDecimal.longValueExact().also { check(it > 0) }
    }
    val buyer = id("buyerId")
    check(buyer == expectedBuyer)
    val status = data.get("status")
    check(status != null && status.isJsonPrimitive && status.asJsonPrimitive.isString &&
        status.asString in setOf("ACTIVE", "CHECKOUT_PROCESSING", "ABANDONED"))
    val paths = mutableSetOf<String>()
    fun visit(value: JsonElement, prefix: String, depth: Int) {
        if (depth > 6) return
        if (value.isJsonObject) value.asJsonObject.entrySet().take(100).forEach { (name, child) ->
            val path = if (prefix.isBlank()) name else "$prefix.$name"
            paths += path
            visit(child, path, depth + 1)
        } else if (value.isJsonArray) value.asJsonArray.firstOrNull()?.let { visit(it, "$prefix[]", depth + 1) }
    }
    visit(data, "", 0)
    val known = setOf("id", "buyerId", "status", "createdAt", "updatedAt")
    return BuyerCartEvidence(BuyerCartMetadata(id("id"), buyer, status.asString), paths,
        data.keySet().any { it !in known })
}
internal data class BuyerDeliveryQuoteRequest(val deliveryMethod: String, val addressId: Long? = null, val pickupStationId: Long? = null) {
    init {
        require(deliveryMethod == "DOOR_DELIVERY" && addressId != null && addressId > 0 && pickupStationId == null ||
            deliveryMethod == "PICKUP_STATION" && pickupStationId != null && pickupStationId > 0 && addressId == null)
    }
    companion object {
        fun door(id: Long) = BuyerDeliveryQuoteRequest("DOOR_DELIVERY", addressId = id)
        fun pickup(id: Long) = BuyerDeliveryQuoteRequest("PICKUP_STATION", pickupStationId = id)
    }
}
internal data class BuyerDeliveryQuote(val quoteId: String? = null, val addressId: Long? = null,
    val subtotal: java.math.BigDecimal? = null, val deliveryFee: java.math.BigDecimal? = null,
    val total: java.math.BigDecimal? = null, val currency: String? = null, val expiresAt: String? = null,
    val deliveryMethod: String? = null, val pickupStationId: Long? = null)
internal fun validatedDeliveryQuote(quote: BuyerDeliveryQuote, addressId: Long, now: java.time.Instant): BuyerDeliveryQuote =
    validatedDeliveryQuote(quote, BuyerDeliveryQuoteRequest.door(addressId), now)
internal fun validatedDeliveryQuote(quote: BuyerDeliveryQuote, request: BuyerDeliveryQuoteRequest, now: java.time.Instant): BuyerDeliveryQuote {
    require(!quote.quoteId.isNullOrBlank() && quote.deliveryMethod == request.deliveryMethod &&
        quote.addressId == request.addressId && quote.pickupStationId == request.pickupStationId && quote.currency == "KES")
    require(quote.subtotal != null && quote.deliveryFee != null && quote.total != null &&
        quote.subtotal.signum() >= 0 && quote.deliveryFee.signum() >= 0 &&
        quote.subtotal.add(quote.deliveryFee).compareTo(quote.total) == 0)
    require(java.time.Instant.parse(quote.expiresAt).isAfter(now))
    return quote
}
internal interface BuyerCheckoutApi {
    @POST("checkout/delivery-quote") suspend fun quote(@Body request: BuyerDeliveryQuoteRequest,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<BuyerDeliveryQuote>>
    @GET("carts/buyer/{buyerId}") suspend fun buyerCart(@Path("buyerId") buyerId: Long,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<JsonObject>>
    @POST("checkout/validate") suspend fun validate(@Tag session: SessionSnapshot): Response<CanonicalApiResponse<BuyerCartMetadata>>
}
internal const val BUYER_CART_CONTRACT_GAP = "The published buyer-cart schema does not describe items, quantities or totals. Matching this local cart to a server order cannot be verified. No order or payment was submitted. Existing orders can be paid from Orders."
internal data class BuyerCheckoutReview(val busy: Boolean = false, val message: String? = null, val evidence: BuyerCartEvidence? = null)
internal class BuyerCheckoutReviewController(private val api: BuyerCheckoutApi, private val sessions: SessionStore,
    private val expected: SessionSnapshot, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(BuyerCheckoutReview())
    val state = mutable.asStateFlow()
    private fun current(): SessionSnapshot {
        val now = sessions.session.value
        check(now.generation == expected.generation && !now.loggingOut && !now.isDemo && now.auth?.role == "BUYER" &&
            now.auth.backendContract == "canonical" && now.auth.userId == expected.auth?.userId && now.auth.sessionOrigin == expected.auth?.sessionOrigin)
        return now
    }
    fun check() {
        if (mutable.value.busy) return
        try { current() } catch (_: Exception) { return }
        mutable.value = BuyerCheckoutReview(busy = true)
        scope.launch {
            try {
                val session = current()
                val buyerId = session.auth!!.userId.toLong()
                check(buyerId > 0 && buyerId.toString() == session.auth.userId)
                val response = api.buyerCart(buyerId, session)
                if (!response.isSuccessful) { response.errorBody()?.close(); error("Cart read not confirmed") }
                val evidence = buyerCartEvidence(response.body()?.requireData() ?: error("Incomplete cart"), buyerId)
                current()
                mutable.value = BuyerCheckoutReview(evidence = evidence, message =
                    if (evidence.hasUndocumentedProperties)
                        "Additional server cart details were returned, but their item mapping has not been verified. No order or payment was submitted."
                    else "Server cart #${evidence.metadata.id}: ${evidence.metadata.status}. The response contains only cart metadata. " + BUYER_CART_CONTRACT_GAP)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (runCatching { current() }.isSuccess) mutable.value = BuyerCheckoutReview(
                message = "Server cart could not be verified. Check your session and connection. " + BUYER_CART_CONTRACT_GAP) }
        }
    }
}
