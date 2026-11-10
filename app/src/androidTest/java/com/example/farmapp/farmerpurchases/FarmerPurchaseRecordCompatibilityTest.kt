package com.example.farmapp.farmerpurchases

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.example.farmapp.buyer.buyerScope
import com.example.farmapp.checkout.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

class FarmerPurchaseRecordCompatibilityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val user = "farmer-records-" + UUID.randomUUID()
    private val origin = "https://synthetic.example.test/api/v1/"
    private val names = mutableSetOf<String>()
    private fun name(prefix: String = "farmer-purchases-", id: String = user, url: String = origin) =
        (prefix + buyerScope(url, id)).also { names += it }
    private fun disk(id: String = user, url: String = origin): FarmerPurchasePersistence {
        name(id = id, url = url); return FarmerPurchasePersistence(context, id, url)
    }
    @After fun cleanup() { names.forEach { context.deleteSharedPreferences(it) } }
    @Test fun basketJournalAndPaymentReservationsSurviveRestartWithoutCrossingBuyerNamespaces() {
        val buyer = context.getSharedPreferences(name("buyer-checkout-"), Context.MODE_PRIVATE)
        assertTrue(buyer.edit().putLong("version", 9).putString("journal", "synthetic-existing-buyer").commit())
        val saved = buyer.all.toMap()
        val first = disk(); first.saveBasket(mapOf(7L to 3)); first.journal.read()
        first.journal.write(CheckoutJournal(items = mapOf(7L to 3), expectedSubtotal = java.math.BigDecimal("376.50")))
        assertTrue(first.attempts.reserve(105))
        val second = disk()
        assertEquals(mapOf(7L to 3), second.basket()); assertEquals(mapOf(7L to 3), second.journal.read().items)
        assertFalse(second.attempts.reserve(105)); assertEquals(saved, buyer.all)
        assertTrue(disk(user + "-other").basket().isEmpty())
        assertFalse(disk(url = "https://other.example.test/api/v1/").attempts.started(105))
    }
    @Test fun corruptedJournalAndUnknownOutcomeAreNotDeletedOrRetried() {
        val prefs = context.getSharedPreferences(name(), Context.MODE_PRIVATE)
        val corrupt = """{"phase":"ORDER_PENDING","items":{"7":3,"7":4}}"""
        assertTrue(prefs.edit().putString("journal", corrupt).putLong("version", 8).putBoolean("payment-105", true).commit())
        assertEquals("UNRESOLVED", disk().journal.read().phase)
        assertFalse(disk().attempts.reserve(105))
        assertEquals(corrupt, prefs.getString("journal", null)); assertEquals(8L, prefs.getLong("version", 0))
    }
    @Test fun atomicJournalVersionsStillRejectStaleWriters() {
        val first = disk().journal; val second = disk().journal
        first.read(); second.read(); first.write(CheckoutJournal(items = mapOf(7L to 3), expectedSubtotal = java.math.BigDecimal("376.50")))
        try { second.write(CheckoutJournal(items = mapOf(8L to 1), expectedSubtotal = java.math.BigDecimal.ONE)); fail("Expected stale writer rejection") }
        catch (_: IllegalStateException) { }
        assertEquals(mapOf(7L to 3), disk().journal.read().items)
    }
    @Test fun malformedLocalBasketFailsWithoutErasingStoredSelection() {
        val prefs = context.getSharedPreferences(name(), Context.MODE_PRIVATE)
        val corrupt = """{"07":3}"""
        assertTrue(prefs.edit().putString("local-basket", corrupt).commit())
        try { disk().basket(); fail("Expected corrupt basket rejection") } catch (_: Exception) { }
        assertEquals(corrupt, prefs.getString("local-basket", null))
    }
}
