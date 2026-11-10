package com.example.farmapp.orders

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.farmapp.BuildConfig
import com.example.farmapp.buyer.buyerScope
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Synthetic account-scoped preferences only; no API, session or STK calls. */
@RunWith(AndroidJUnit4::class)
class PaymentRecordCompatibilityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val origin = BuildConfig.API_BASE_URL
    private val user = "batch5-payment-" + UUID.randomUUID()
    private fun name(origin: String = this.origin, user: String = this.user) = "buyer-payment-${buyerScope(origin, user)}"
    @After fun cleanup() {
        context.deleteSharedPreferences(name()); context.deleteSharedPreferences(name(user = user + "-other"))
        context.deleteSharedPreferences(name(origin = "https://other.example.test/api/v1/"))
    }
    @Test fun existingBooleanReservationsStillBlockAfterRecreationWithoutRewritingRecords() {
        val prefs = context.getSharedPreferences(name(), Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putBoolean("105", true).commit())
        repeat(2) {
            val store = BuyerPaymentPreferences(context, origin, user)
            assertTrue(store.started(105)); assertFalse(store.reserve(105))
        }
        assertEquals(mapOf("105" to true), prefs.all)
    }
    @Test fun twoCompatibilityAdaptersShareOneAtomicReservationAndKeepTheExistingKeyFormat() {
        val first = BuyerPaymentPreferences(context, origin, user); val second = BuyerPaymentPreferences(context, origin, user)
        assertTrue(first.reserve(105)); assertFalse(second.reserve(105)); assertTrue(second.reserve(106))
        assertEquals(mapOf("105" to true, "106" to true), context.getSharedPreferences(name(), Context.MODE_PRIVATE).all)
        assertFalse(BuyerPaymentPreferences(context, origin, user).reserve(105))
    }
    @Test fun paymentReservationsRemainIsolatedByBackendAndAccount() {
        val first = BuyerPaymentPreferences(context, origin, user); assertTrue(first.reserve(105))
        val otherAccount = BuyerPaymentPreferences(context, origin, user + "-other")
        val otherOrigin = BuyerPaymentPreferences(context, "https://other.example.test/api/v1/", user)
        assertFalse(otherAccount.started(105)); assertFalse(otherOrigin.started(105))
        assertTrue(otherAccount.reserve(105)); assertTrue(otherOrigin.reserve(105)); assertTrue(first.started(105))
    }
}
