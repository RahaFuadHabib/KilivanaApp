package com.example.farmapp.buyer

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.ZoneId

class BuyerUiRefinementTest {
    @Test fun welcomeUsesFirstNameAndNaturalFallback() {
        assertEquals("Welcome, Amina", buyerWelcome("  Amina Test Buyer  "))
        assertEquals("Welcome", buyerWelcome(null)); assertEquals("Welcome", buyerWelcome(" "))
    }
    @Test fun serviceChargeIsAnEstimateOnlyAndNeverARoundedDouble() {
        assertEquals(BigDecimal("281.14"), buyerEstimatedTotalBeforeDelivery(BigDecimal("251.14")))
        assertNull(buyerEstimatedTotalBeforeDelivery(null))
    }
    @Test fun timestampsRespectOffsetsAndDoNotInventAZoneForLocalDates() {
        val zone = ZoneId.of("Africa/Nairobi")
        assertEquals("28 Oct 2026, 3:00 PM", buyerDate("2026-10-28T12:00:00Z", zone))
        assertEquals("28 Oct 2026, 3:00 PM", buyerDate("2026-10-28T17:00:00+05:00", zone))
        assertEquals("28 Oct 2026, 12:00 PM", buyerDate("2026-10-28T12:00:00.123", zone))
        assertEquals("Date unavailable", buyerDate("broken", zone)); assertEquals("Date unavailable", buyerDate(null, zone))
    }
    @Test fun productReferenceIsStableOriginalServerIdentity() {
        assertEquals("7", buyerProductReference("7")); assertEquals(buyerProductReference("7"), buyerProductReference("7"))
        assertNotEquals(buyerProductReference("7"), buyerProductReference("8"))
    }
}
