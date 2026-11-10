package com.example.farmapp.checkout

import com.example.farmapp.network.canonical.CanonicalJson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CheckoutModelsTest {
    private val now = Instant.parse("2026-11-05T12:00:00Z")
    private val quote = CheckoutQuote("saved-quote", null, BigDecimal("3.21"), BigDecimal("50.00"),
        BigDecimal("53.21"), "KES", "2026-11-05T12:15:00Z", "PICKUP_STATION", pickupStationId=3)

    @Test fun deliveryAlternativesProduceTheExistingMutuallyExclusiveJson() {
        val cases = listOf(
            DeliverySelection.Door(8) to """{"deliveryMethod":"DOOR_DELIVERY","addressId":8}""",
            DeliverySelection.Station(3) to """{"deliveryMethod":"PICKUP_STATION","pickupStationId":3}""",
            DeliverySelection.TypedPickup("Synthetic pickup") to """{"deliveryMethod":"PICKUP_STATION","pickupLocation":"Synthetic pickup"}"""
        )
        cases.forEach { (selection, json) ->
            assertEquals(JsonParser.parseString(json), CanonicalJson.gson.toJsonTree(selection.toQuoteRequest()))
        }
    }

    @Test fun conflictingDeliveryFieldsAndInvalidSelectionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { CheckoutQuoteRequest("PICKUP_STATION", pickupStationId=3, pickupLocation="Both") }
        assertThrows(IllegalArgumentException::class.java) { CheckoutQuoteRequest("DOOR_DELIVERY", addressId=8, pickupStationId=3) }
        assertThrows(IllegalArgumentException::class.java) { CheckoutQuoteRequest("PICKUP_STATION") }
        assertThrows(IllegalArgumentException::class.java) { DeliverySelection.Door(0) }
        assertThrows(IllegalArgumentException::class.java) { DeliverySelection.Station(-1) }
        assertThrows(IllegalArgumentException::class.java) { DeliverySelection.TypedPickup(" ") }
        assertThrows(IllegalArgumentException::class.java) { DeliverySelection.TypedPickup("x".repeat(501)) }
    }

    @Test fun quoteValidationKeepsTheServerAmountsAndDecimalScale() {
        assertSame(quote, verifiedCheckoutQuote(quote, DeliverySelection.Station(3).toQuoteRequest(), now))
        assertEquals(BigDecimal("50.00"), quote.deliveryFee)
        assertEquals(BigDecimal("53.21"), quote.total)
    }

    @Test fun quoteRejectsChangedStationAmountsCurrencyAndExpiry() {
        val invalid = listOf(
            quote.copy(pickupStationId=4), quote.copy(total=BigDecimal("53.22")),
            quote.copy(deliveryFee=BigDecimal("-1")), quote.copy(currency="USD"),
            quote.copy(expiresAt=now.toString()), quote.copy(quoteId=""), quote.copy(addressId=8)
        )
        invalid.forEach { row ->
            assertThrows(IllegalArgumentException::class.java) { verifiedCheckoutQuote(row, DeliverySelection.Station(3).toQuoteRequest(), now) }
        }
    }

    @Test fun localCartSnapshotCannotChangeWhenTheUiBasketChanges() {
        val basket = mutableMapOf(7L to 3)
        val snapshot = CheckoutCartSnapshot(basket, BigDecimal("3.21"))
        basket[7] = 8
        basket[9] = 1
        assertEquals(mapOf(7L to 3), snapshot.items)
        assertThrows(UnsupportedOperationException::class.java) { (snapshot.items as MutableMap)[7] = 9 }
    }

    @Test fun localCartRejectsInvalidIdentifiersQuantitiesAndEstimates() {
        for (basket in listOf(emptyMap(), mapOf(0L to 1), mapOf(7L to 0), mapOf(7L to -1))) {
            assertThrows(IllegalArgumentException::class.java) { CheckoutCartSnapshot(basket, BigDecimal.ONE) }
        }
        assertThrows(IllegalArgumentException::class.java) { CheckoutCartSnapshot(mapOf(7L to 1), BigDecimal("-0.01")) }
    }
}
