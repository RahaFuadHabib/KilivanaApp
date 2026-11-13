package com.example.farmapp.checkout

import java.math.BigDecimal
import java.time.Instant
import java.util.Collections

internal enum class PurchasingFlow { BUYER_FROM_FARMER, FARMER_FROM_SUPPLIER }

/** Local intent only: this snapshot is NOT evidence of the server cart's contents. */
internal class CheckoutCartSnapshot(items: Map<Long, Int>, val estimatedSubtotal: BigDecimal) {
    val items: Map<Long, Int> = Collections.unmodifiableMap(items.toMap())
    init {
        require(this.items.isNotEmpty() && this.items.all { (id, quantity) -> id > 0 && quantity > 0 })
        require(estimatedSubtotal.signum() >= 0)
    }
}

/** A station ID identifies the platform station, never a user's saved association. */
internal sealed interface DeliverySelection {
    data class Door(val addressId: Long) : DeliverySelection {
        init { require(addressId > 0) }
    }
    data class Station(val pickupStationId: Long) : DeliverySelection {
        init { require(pickupStationId > 0) }
    }
    data class TypedPickup(val pickupLocation: String) : DeliverySelection {
        init { require(pickupLocation.isNotBlank() && pickupLocation.length <= 500) }
    }
}

/** Existing quote wire fields are retained verbatim for Retrofit and saved checkout journals. */
data class CheckoutQuote(
    val quoteId: String,
    val addressId: Long?,
    val subtotal: BigDecimal,
    val deliveryFee: BigDecimal,
    val total: BigDecimal,
    val currency: String,
    val expiresAt: String,
    val deliveryMethod: String,
    val pickupLocation: String? = null,
    val pickupStationId: Long? = null,
    val cartId: Long? = null,
    val items: List<CheckoutQuoteItem>? = null
)

data class CheckoutQuoteItem(
    val productId: Long,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val lineSubtotal: BigDecimal
)

data class CheckoutQuoteRequest(
    val deliveryMethod: String,
    val addressId: Long? = null,
    val pickupLocation: String? = null,
    val pickupStationId: Long? = null,
    val cartId: Long? = null
) {
    init {
        val door = deliveryMethod == "DOOR_DELIVERY" && addressId != null && addressId > 0 && pickupLocation == null && pickupStationId == null
        val station = deliveryMethod == "PICKUP_STATION" && addressId == null && pickupStationId != null && pickupStationId > 0 && pickupLocation == null
        val location = deliveryMethod == "PICKUP_STATION" && addressId == null && pickupStationId == null && !pickupLocation.isNullOrBlank() && pickupLocation.length <= 500
        require(door || station || location)
        require(cartId == null || cartId > 0)
    }
}

internal fun DeliverySelection.toQuoteRequest(): CheckoutQuoteRequest = when (this) {
    is DeliverySelection.Door -> CheckoutQuoteRequest("DOOR_DELIVERY", addressId = addressId)
    is DeliverySelection.Station -> CheckoutQuoteRequest("PICKUP_STATION", pickupStationId = pickupStationId)
    is DeliverySelection.TypedPickup -> CheckoutQuoteRequest("PICKUP_STATION", pickupLocation = pickupLocation)
}

/** Pure validation extracted without changing Buyer quote acceptance rules. */
fun verifiedCheckoutQuote(quote: CheckoutQuote, request: CheckoutQuoteRequest, now: Instant): CheckoutQuote {
    require(quote.deliveryMethod == request.deliveryMethod && quote.addressId == request.addressId &&
        quote.pickupLocation == request.pickupLocation && quote.pickupStationId == request.pickupStationId)
    require(request.cartId == null || (request.cartId > 0 && quote.cartId == request.cartId))
    require(quote.quoteId.isNotBlank() && quote.currency == "KES" && quote.subtotal.signum() >= 0 && quote.deliveryFee.signum() >= 0)
    require(quote.subtotal.add(quote.deliveryFee).compareTo(quote.total) == 0 && Instant.parse(quote.expiresAt).isAfter(now))
    return quote
}

/** Verify the exact basket, including coincidentally equal totals from different products. */
internal fun verifyQuotedBasket(quote: CheckoutQuote, cartId: Long, items: Map<Long, Int>) {
    require(cartId > 0 && quote.cartId == cartId)
    val lines = requireNotNull(quote.items)
    require(lines.isNotEmpty() && lines.all { it.productId > 0 && it.quantity > 0 && it.unitPrice.signum() >= 0 })
    require(lines.map { it.productId }.distinct().size == lines.size)
    require(lines.associate { it.productId to it.quantity } == items)
    require(lines.all { it.unitPrice.multiply(it.quantity.toBigDecimal()).compareTo(it.lineSubtotal) == 0 })
    require(lines.fold(BigDecimal.ZERO) { sum, line -> sum.add(line.lineSubtotal) }.compareTo(quote.subtotal) == 0)
}

/** Backend read projections for later adapters; constructing one does not verify an order. */
internal data class PurchaseOrderLine(
    val productId: Long, val sellerId: Long, val quantity: Int,
    val unitPrice: BigDecimal, val subtotal: BigDecimal
)

internal data class PurchaseOrderSnapshot(
    val id: Long, val purchaserId: Long, val items: List<PurchaseOrderLine>,
    val subtotal: BigDecimal, val deliveryFee: BigDecimal, val total: BigDecimal,
    val delivery: DeliverySelection, val status: String, val paymentStatus: String
)

/** Not persisted. A compatibility alias keeps the existing Buyer state fields and callbacks. */
    internal data class CheckoutState<OrderData>(
        val busy: Boolean = false, val quote: CheckoutQuote? = null, val order: OrderData? = null,
        val notice: String? = null, val uncertain: Boolean = false, val needsRecovery: Boolean = false,
        val justCreated: Boolean = false, val cancellable: Boolean = false, val phone: String = ""
    ) {
        override fun toString() = "CheckoutState(redacted)"
    }
