package com.example.farmapp.farmerpurchases

import com.example.farmapp.checkout.*
import com.example.farmapp.network.canonical.toFarmerProduct
import com.example.farmapp.network.canonical.CanonicalJson
import com.example.farmapp.data.model.canonical.CanonicalProductDto
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class FarmerPurchaseControllerTest {
    private fun product() = CanonicalJson.gson.fromJson(JsonParser.parseString(FarmerPurchaseFixture.productJson)
        .asJsonObject["data"], CanonicalProductDto::class.java).toFarmerProduct()

    @Test fun localBasketReloadsSupplierPricesAndQuantitiesAfterRestart() = runBlocking {
        val f = FarmerPurchaseFixture(); val first = f.controller(); first.add(product(), 3)
        assertTrue(f.requests.isEmpty()); assertEquals(mapOf(7L to 3), f.basketValues)
        val restarted = f.controller(); assertNull(restarted.state.value.estimatedSubtotal)
        restarted.reviewBasket(); f.idle(restarted)
        assertEquals(BigDecimal("376.50"), restarted.state.value.estimatedSubtotal)
        assertEquals(listOf("/api/v1/products/7"), f.requests.map { it.url.encodedPath })
    }
    @Test fun ownProductsWrongSectorAndInvalidStockCannotEnterBasket() {
        val f = FarmerPurchaseFixture(); val c = f.controller()
        listOf(product().copy(sellerId = "42"), product().copy(sector = "FARM_PRODUCE"),
            product().copy(availableQuantity = BigDecimal.ZERO)).forEach { c.add(it, 1) }
        c.add(product(), 0); c.add(product(), 11)
        assertTrue(c.state.value.items.isEmpty()); assertEquals(0, f.basketWrites)
    }
    @Test fun unavailableOrWrongSellerReadbackCannotVerifyBasket() = runBlocking {
        for (response in listOf(FarmerPurchaseFixture.productJson.replace("supplier", "farmer"),
            FarmerPurchaseFixture.productJson.replace("active", "pending"),
            FarmerPurchaseFixture.productJson.replace("\"id\":7", "\"id\":8"))) {
            val f = FarmerPurchaseFixture(); f.basketValues = mapOf(7L to 3); f.handler = { response }
            val c = f.controller(); c.reviewBasket(); f.idle(c)
            assertFalse(c.state.value.basketVerified); assertNull(c.state.value.estimatedSubtotal)
            assertEquals(mapOf(7L to 3), f.basketValues)
        }
    }
    @Test fun corruptedLocalBasketIsPreservedAndCannotBeOverwrittenByNewSelections() {
        val f = FarmerPurchaseFixture(); f.corruptBasket = true
        val c = f.controller(); c.add(product(), 3); c.remove(7)
        assertEquals(0, f.basketWrites); assertNotNull(c.state.value.notice)
    }
    @Test fun deliverySelectionUsesPlatformStationAndRejectsAssociationId() = runBlocking {
        val f = FarmerPurchaseFixture(); val c = f.controller()
        c.delivery(); f.idle(c)
        c.selectStation(91); f.idle(c); assertNull(c.state.value.selection)
        c.selectStation(3); f.idle(c); assertEquals(DeliverySelection.Station(3), c.state.value.selection)
        c.selectAddress(8); assertEquals(DeliverySelection.Door(8), c.state.value.selection)
        assertTrue(f.requests.all { it.method == "GET" })
    }
    @Test fun unknownCheckoutAndPaymentReservationsSurviveRepeatedControllerCallsAndRestart() = runBlocking {
        val f = FarmerPurchaseFixture()
        f.journalValue = CheckoutJournal("ORDER_PENDING", items = mapOf(7L to 3))
        f.reservations += 105
        val c = f.controller()
        repeat(2) {
            c.checkout.prepare(mapOf(7L to 3), BigDecimal("376.50"), CheckoutQuoteRequest("PICKUP_STATION", pickupStationId = 3))
            c.checkout.create(mapOf(7L to 3), "quote", "254700000000")
            c.checkout.recover(); c.payments.initiate(105, "0700000000")
        }
        assertEquals("ORDER_PENDING", f.journalValue.phase); assertEquals(0, f.journalWrites)
        assertTrue(f.controller().checkout.state.value.uncertain)
        assertTrue(f.reservations.contains(105)); assertTrue(f.requests.isEmpty())
    }
    @Test fun historyDetailsAndTrackingUsePurchaserReadsWithoutWrites() = runBlocking {
        val f = FarmerPurchaseFixture(); val c = f.controller()
        c.history(); f.idle(c); assertEquals(105L, c.state.value.orders.single().id)
        c.openOrder(105); f.idle(c); assertEquals(42L, c.state.value.selected!!.buyerId)
        assertFalse(c.payments.state.value.submissionEnabled)
        c.track(); f.idle(c); assertEquals(10L, c.state.value.jobs.single().id)
        assertTrue(c.state.value.tracking.any { it.startsWith("Driver location:") })
        assertTrue(f.requests.all { it.method == "GET" })
        assertTrue(f.requests.none { it.url.encodedPath.contains("orders/seller") || it.url.encodedPath.contains("stk-push") })
    }
    @Test fun staleReadCannotPublishHistoryOrModifyLocalBasket() = runBlocking {
        val f = FarmerPurchaseFixture(); val c = f.controller(); f.hook = f::stale
        c.history(); f.idle(c); assertTrue(c.state.value.orders.isEmpty())
        c.add(product(), 3); assertEquals(0, f.basketWrites)
    }
    @Test fun missingLocationRemainsUnavailableWithoutInventedCoordinates() = runBlocking {
        val f = FarmerPurchaseFixture(); val original = f.handler
        f.handler = { request -> if (request.url.encodedPath.endsWith("/location")) {
            f.status = 404; """{"success":false,"message":"No location"}"""
        } else original(request) }
        val c = f.controller(); c.openOrder(105); f.idle(c); c.track(); f.idle(c)
        assertTrue(c.state.value.tracking.contains("Driver location unavailable."))
        assertTrue(c.state.value.tracking.none { it.startsWith("Driver location:") })
    }
}
