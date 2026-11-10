package com.example.farmapp.buyer

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.farmapp.BuildConfig
import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Unique synthetic preference files only; no account, network or device-location access. */
@RunWith(AndroidJUnit4::class)
class DeliveryRecordCompatibilityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val user = "batch4-delivery-" + UUID.randomUUID()
    private val origin = BuildConfig.API_BASE_URL
    private fun name(origin: String = this.origin, user: String = this.user) = "buyer-shopping-${buyerScope(origin, user)}"

    @After fun cleanup() {
        context.deleteSharedPreferences(name())
        context.deleteSharedPreferences(name(user = user + "-other"))
        context.deleteSharedPreferences(name(origin = "https://other.example.test/api/v1/"))
    }

    @Test fun existingSelectedAddressStationAndUncertainAddressSaveSurviveRecreation() {
        val prefs = context.getSharedPreferences(name(), Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putLong("delivery-address-id", 8).putLong("pickup-station-id", 11)
            .putBoolean("address-save-pending", true).putStringSet("cart", setOf("7:3")).commit())
        repeat(2) {
            val store = BuyerPreferences(context, origin, user)
            assertEquals(8L, store.selectedAddress()); assertEquals(11L, store.selectedPickup())
            assertTrue(store.addressSavePending()); assertEquals(mapOf(7L to 3), store.read().cart)
        }
        assertTrue(prefs.getBoolean("address-save-pending", false))
    }

    @Test fun oldPickupReservationsRetainPlatformAndAssociationIdsAndJsonFields() {
        val prefs = context.getSharedPreferences(name(), Context.MODE_PRIVATE)
        for (kind in listOf("SAVE", "DEFAULT", "REMOVE")) {
            val json = """{"kind":"$kind","platformId":11,"userStationId":1011,"label":"Home","isDefault":true}"""
            assertTrue(prefs.edit().putString("pickup-write-pending", json).commit())
            val restored = BuyerPreferences(context, origin, user).pendingPickup()!!
            assertEquals(kind, restored.kind); assertEquals(11L, restored.platformId)
            assertEquals(1011L, restored.userStationId); assertEquals("Home", restored.label)
            BuyerPreferences(context, origin, user).pendingPickup(restored)
            // ART may enumerate fields in a different order; JSON object order is not schema.
            assertEquals(JsonParser.parseString(json), JsonParser.parseString(prefs.getString("pickup-write-pending", null)!!))
            assertEquals(restored, BuyerPreferences(context, origin, user).pendingPickup())
        }
    }

    @Test fun deliveryReservationsRemainIsolatedByAccountAndBackendOrigin() {
        val first = BuyerPreferences(context, origin, user)
        first.markAddressSave(true); first.pendingPickup(BuyerPickupMutation("REMOVE", 11, 1011))
        first.selectAddress(8); first.selectPickup(11)
        for (other in listOf(BuyerPreferences(context, origin, user + "-other"),
            BuyerPreferences(context, "https://other.example.test/api/v1/", user))) {
            assertFalse(other.addressSavePending()); assertNull(other.pendingPickup())
            assertNull(other.selectedAddress()); assertNull(other.selectedPickup())
        }
        assertTrue(BuyerPreferences(context, origin, user).addressSavePending())
        assertEquals(1011L, first.pendingPickup()!!.userStationId)
    }
}
