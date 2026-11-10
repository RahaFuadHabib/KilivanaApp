package com.example.farmapp.checkout

import com.example.farmapp.buyer.buyerScope
import com.example.farmapp.buyers.CheckoutJournal
import com.example.farmapp.buyers.CheckoutJournalJson
import com.example.farmapp.buyers.data.DeliveryQuote
import com.example.farmapp.buyers.data.DeliveryQuoteRequest
import com.example.farmapp.buyers.data.verifiedQuote
import com.example.farmapp.network.canonical.CanonicalJson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CheckoutCompatibilityTest {
    private val oldQuote = """{"quoteId":"saved-quote","addressId":8,"subtotal":3.21,"deliveryFee":150.00,"total":153.21,"currency":"KES","expiresAt":"2026-11-05T12:15:00Z","deliveryMethod":"DOOR_DELIVERY"}"""
    private val oldRequest = """{"deliveryMethod":"DOOR_DELIVERY","addressId":8}"""

    @Test fun preExtractionJournalJsonRoundTripsWithoutAddingOrRenamingFields() {
        for (phase in listOf("UPLOAD_PENDING", "CHECKOUT_PENDING", "QUOTE_READY", "ORDER_PENDING", "ORDER_RETURNED", "ORDER_VERIFIED")) {
            val orderId = if (phase in setOf("ORDER_RETURNED", "ORDER_VERIFIED")) ",\"orderId\":105" else ""
            val json = """{"phase":"$phase","items":{"7":3},"expectedSubtotal":3.21,"cartId":10,"quote":$oldQuote,"request":$oldRequest,"beforeOrderIds":[90]$orderId}"""
            val journal = CheckoutJournalJson.decode(json)
            assertEquals(phase, journal.phase)
            assertEquals(mapOf(7L to 3), journal.items)
            assertEquals(BigDecimal("150.00"), journal.quote!!.deliveryFee)
            assertEquals(JsonParser.parseString(json), JsonParser.parseString(CheckoutJournalJson.encode(journal)))
        }
    }

    @Test fun savedStationAndTypedPickupQuotesRetainTheirExistingShape() {
        val quotes = listOf(
            """{"quoteId":"station","subtotal":3.21,"deliveryFee":50,"total":53.21,"currency":"KES","expiresAt":"2026-11-05T12:15:00Z","deliveryMethod":"PICKUP_STATION","pickupStationId":3}""",
            """{"quoteId":"typed","subtotal":3.21,"deliveryFee":50,"total":53.21,"currency":"KES","expiresAt":"2026-11-05T12:15:00Z","deliveryMethod":"PICKUP_STATION","pickupLocation":"Synthetic pickup"}"""
        )
        for (json in quotes) {
            val quote = CanonicalJson.gson.fromJson(json, DeliveryQuote::class.java)
            assertEquals(JsonParser.parseString(json), CanonicalJson.gson.toJsonTree(quote))
        }
    }

    @Test fun existingBuyerEntryPointsUseSharedQuotesWithoutChangingValidation() {
        val quote: DeliveryQuote = CanonicalJson.gson.fromJson(oldQuote, DeliveryQuote::class.java)
        val request: DeliveryQuoteRequest = CanonicalJson.gson.fromJson(oldRequest, DeliveryQuoteRequest::class.java)
        assertSame(quote, verifiedQuote(quote, request, Instant.parse("2026-11-05T12:00:00Z")))
        assertThrows(IllegalArgumentException::class.java) { verifiedQuote(quote, request, Instant.parse(quote.expiresAt)) }
    }

    @Test fun existingBuyerPreferenceScopeMatchesThePreExtractionKey() {
        assertEquals("c63a630dcf4ef0895a3917dd84e4a1ae65baf2fc4d47f13c8fb48c79184b1f8a",
            buyerScope("https://synthetic.test/api/v1/", "42"))
    }
}
