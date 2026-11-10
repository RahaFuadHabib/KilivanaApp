package com.example.farmapp.buyers

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.farmapp.BuildConfig
import com.example.farmapp.buyer.buyerScope
import com.example.farmapp.orders.BuyerPaymentPreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.UUID

/** Uses only unique synthetic preferences; never reads or changes a real account's records. */
@RunWith(AndroidJUnit4::class)
class CheckoutRecordCompatibilityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val user = "batch1-record-test-" + UUID.randomUUID()
    private val scope = buyerScope(BuildConfig.API_BASE_URL, user)
    private val checkoutName = "buyer-checkout-$scope"
    private val paymentName = "buyer-payment-$scope"

    @After fun cleanup() {
        context.deleteSharedPreferences(checkoutName)
        context.deleteSharedPreferences(paymentName)
        context.deleteSharedPreferences("buyer-checkout-" + buyerScope(BuildConfig.API_BASE_URL, user + "-other"))
    }

    @Test fun oldSavedStationJournalLoadsAndKeepsItsVersionAndOrderReservation() {
        val prefs = context.getSharedPreferences(checkoutName, Context.MODE_PRIVATE)
        val json = """{"phase":"ORDER_RETURNED","items":{"7":3},"expectedSubtotal":3.21,"cartId":10,"orderId":105,"beforeOrderIds":[],"request":{"deliveryMethod":"PICKUP_STATION","pickupStationId":3},"quote":{"quoteId":"saved","subtotal":3.21,"deliveryFee":50.00,"total":53.21,"currency":"KES","expiresAt":"2026-11-05T12:15:00Z","deliveryMethod":"PICKUP_STATION","pickupStationId":3}}"""
        assertTrue(prefs.edit().putString("journal", json).putLong("version", 4).commit())
        val store = CheckoutPreferences(context, user)
        val restored = store.read()
        assertEquals("ORDER_RETURNED", restored.phase)
        assertEquals(105L, restored.orderId)
        assertEquals(3L, restored.quote!!.pickupStationId)
        assertEquals(BigDecimal("53.21"), restored.quote.total)
        store.write(restored)
        assertEquals(5L, prefs.getLong("version", -1))
        assertEquals(restored, CheckoutPreferences(context, user).read())
    }

    @Test fun oldPaymentReservationStillPreventsAnotherSubmissionAfterRecreation() {
        val prefs = context.getSharedPreferences(paymentName, Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putBoolean("105", true).commit())
        val attempts = BuyerPaymentPreferences(context, BuildConfig.API_BASE_URL, user)
        assertTrue(attempts.started(105))
        assertFalse(attempts.reserve(105))
        assertFalse(BuyerPaymentPreferences(context, BuildConfig.API_BASE_URL, user).reserve(105))
        assertTrue(prefs.getBoolean("105", false))
    }

    @Test fun corruptSavedRecordsStayUnresolvedWithoutDeletingTheOriginalOrPaymentMarker() {
        val prefs = context.getSharedPreferences(checkoutName, Context.MODE_PRIVATE)
        val paymentPrefs = context.getSharedPreferences(paymentName, Context.MODE_PRIVATE)
        assertTrue(paymentPrefs.edit().putBoolean("105", true).commit())
        for (json in listOf(
            "not JSON", "{}", """{"phase":"NONE","items":{"7":3,"7":4}}""",
            """{"phase":"UPLOAD_PENDING","items":{"7":0},"expectedSubtotal":3.21}"""
        )) {
            assertTrue(prefs.edit().putString("journal", json).putLong("version", 8).commit())
            assertEquals("UNRESOLVED", CheckoutPreferences(context, user).read().phase)
            assertEquals("UNRESOLVED", CheckoutPreferences(context, user).read().phase)
            assertEquals(json, prefs.getString("journal", null))
            assertEquals(8L, prefs.getLong("version", -1))
            assertFalse(BuyerPaymentPreferences(context, BuildConfig.API_BASE_URL, user).reserve(105))
        }
    }

    @Test fun invalidWriteDoesNotEraseTheLastSavedJournalOrIncrementItsVersion() {
        val store = CheckoutPreferences(context, user)
        assertEquals(CheckoutJournal(), store.read())
        val valid = CheckoutJournal("UPLOAD_PENDING", mapOf(7L to 3), BigDecimal("3.21"), 10L)
        store.write(valid)
        val prefs = context.getSharedPreferences(checkoutName, Context.MODE_PRIVATE)
        val saved = prefs.getString("journal", null)
        val version = prefs.getLong("version", -1)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { store.write(valid.copy(items=mapOf(7L to 0))) }
        assertEquals(saved, prefs.getString("journal", null))
        assertEquals(version, prefs.getLong("version", -1))
        assertEquals(valid, CheckoutPreferences(context, user).read())
    }

    @Test fun twoPreferencesInstancesKeepTheExistingAtomicVersionCheck() {
        val first=CheckoutPreferences(context,user)
        val stale=CheckoutPreferences(context,user)
        first.read();stale.read()
        val pending=CheckoutJournal("UPLOAD_PENDING",mapOf(7L to 3),BigDecimal("3.21"),10L)
        first.write(pending)
        val prefs=context.getSharedPreferences(checkoutName,Context.MODE_PRIVATE)
        val saved=prefs.getString("journal",null)
        assertThrows(IllegalStateException::class.java){stale.write(CheckoutJournal())}
        assertEquals(saved,prefs.getString("journal",null));assertEquals(1L,prefs.getLong("version",-1))
        assertEquals(pending,stale.read());stale.write(pending)
        assertEquals(2L,prefs.getLong("version",-1))
    }

    @Test fun extractedPreferencesDoNotReadAnotherAccountJournal() {
        val first=CheckoutPreferences(context,user)
        first.read();first.write(CheckoutJournal("UPLOAD_PENDING",mapOf(7L to 3),BigDecimal("3.21"),10L))
        val other=CheckoutPreferences(context,user+"-other")
        assertEquals(CheckoutJournal(),other.read())
        other.write(CheckoutJournal())
        assertEquals("UPLOAD_PENDING",CheckoutPreferences(context,user).read().phase)
    }
}
