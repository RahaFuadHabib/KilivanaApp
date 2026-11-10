package com.example.farmapp.farmerpurchases

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.example.farmapp.BuildConfig
import com.example.farmapp.buyer.buyerScope
import com.example.farmapp.checkout.CheckoutQuoteRequest
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Synthetic namespace only. No production session, network or payment operation. */
class FarmerTransactionPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val user = "transaction-test-" + UUID.randomUUID()
    private val prefs = context.getSharedPreferences("farmer-transactions-v1-" + buyerScope(BuildConfig.API_BASE_URL, user), Context.MODE_PRIVATE)
    private fun store() = FarmerTransactionPreferences(context, user)
    private fun record() = FarmerTransactionRecord("synthetic-key", mapOf(9007199254740993L to 3), CheckoutQuoteRequest("PICKUP_STATION", pickupStationId = 3))
    @After fun clean() { check(prefs.edit().clear().commit()) }
    @Test fun controllerRestartRetainsExactIdsAndOriginalRequestKey() {
        val first = store(); assertNull(first.read()); first.write(record())
        assertEquals(record(), store().read())
    }
    @Test fun competingControllerCannotOverwriteAnotherSavedCheckout() {
        val first = store(); val second = store(); first.read(); second.read(); first.write(record())
        try { second.write(record().copy(key = "conflicting-key")); fail("Expected version conflict") }
        catch (_: IllegalStateException) { }
        assertEquals(record(), store().read())
    }
    @Test fun corruptRecordIsPreservedForRecovery() {
        check(prefs.edit().putString("record", "{broken").commit())
        try { store().read(); fail("Expected corrupt-record rejection") }
        catch (_: RuntimeException) { }
        assertEquals("{broken", prefs.getString("record", null))
    }
}
