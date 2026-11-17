package com.example.farmapp.farmerpurchases

import com.example.farmapp.buyer.*
import com.example.farmapp.checkout.*
import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.orders.*
import com.example.farmapp.tracking.DeliveryJobDto
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

class FarmerPurchaseRepositoryTest {
    private suspend fun rejects(block: suspend () -> Unit) {
        try { block() } catch (_: Exception) { return }; fail("Expected rejection")
    }
    @Test fun purchaserHistoryUsesAuthenticatedUserNotSellerAndKeepsAuthAndTags() = runBlocking {
        val f = FarmerPurchaseFixture()
        assertEquals(42L, f.orders.listOrders(42).requirePurchaseData().single().buyerId)
        assertEquals(105L, f.orders.readOrder(105).requirePurchaseData().id)
        assertEquals(listOf("/api/v1/orders/buyer/42", "/api/v1/orders/105"), f.requests.map { it.url.encodedPath })
        f.requests.forEach { assertEquals(f.expected, it.tag(SessionSnapshot::class.java)); assertEquals("Bearer ${f.expected.auth!!.accessToken}", it.header("Authorization")) }
        rejects { f.orders.listOrders(9) }; assertEquals(2, f.requests.size)
    }
    @Test fun sellerOrderSummaryUsesAuthenticatedFarmerIdAndValidatesRows() = runBlocking {
        val f = FarmerPurchaseFixture()
        val rows = FarmerSellerOrdersRepository(f.api(), f.sessions, f.expected).list()
        assertEquals(105L, rows.single().id)
        assertEquals(2, rows.single().itemCount)
        assertEquals(java.math.BigDecimal("376.50"), rows.single().total)
        assertEquals("/api/v1/orders/seller/42", f.requests.single().url.encodedPath)
        assertEquals(f.expected, f.requests.single().tag(SessionSnapshot::class.java))
        assertEquals("Bearer ${f.expected.auth!!.accessToken}", f.requests.single().header("Authorization"))

        f.handler = { """{"success":true,"data":[{"id":105,"status":"CONFIRMED","total":376.50,"createdAt":"2026-11-12T10:00:00","itemCount":2},{"id":105,"status":"CONFIRMED","total":376.50,"itemCount":2}]}""" }
        rejects { FarmerSellerOrdersRepository(f.api(), f.sessions, f.expected).list() }
    }
    @Test fun legacyReadOnlyAdapterRemainsBlockedWhileDedicatedCartAdapterIsAuthorized() = runBlocking {
        val f = FarmerPurchaseFixture()
        listOf<suspend () -> Unit>({ f.orders.readCart(42) }, { f.orders.addItem(7, 3) },
            { f.orders.setQuantity(10, 7, 3) }, { f.orders.startCheckout() },
            { f.orders.deliveryQuote(CheckoutQuoteRequest("PICKUP_STATION", pickupStationId = 3)) },
            { f.orders.createOrder("quote", "254700000000") }, { f.payment.initiate(105, "254700000000") },
            { f.addresses.create(BuyerAddressRequest("Synthetic")) }, { f.pickup.save(BuyerSavePickupRequest(3)) },
            { f.pickup.remove(91) }, { f.pickup.setDefault(91) }).forEach { rejects(it) }
        assertTrue(f.requests.isEmpty())
        assertTrue(FarmerPurchaserPolicy.permits(CheckoutOperation.READ_CART))
        assertFalse(FarmerPurchaserPolicy.permits(CheckoutOperation.INITIATE_PAYMENT))
        assertTrue(BuyerPurchaserPolicy.permits(CheckoutOperation.CREATE_ORDER))
        assertTrue(CheckoutOperation.entries.none { UnverifiedFarmerPurchaserPolicy.permits(it) })
    }
    @Test fun sellerProjectionOrWrongOrderIdCannotBecomePurchaserOrder() = runBlocking {
        for (bad in listOf(FarmerPurchaseFixture.orderJson.replace("\"buyerId\":42", "\"buyerId\":9"),
            FarmerPurchaseFixture.orderJson.replace("\"id\":105", "\"id\":106"))) {
            val f = FarmerPurchaseFixture(); f.handler = { """{"success":true,"data":$bad}""" }
            rejects { f.orders.readOrder(105) }
        }
    }
    @Test fun duplicateHistoryAndMalformedNumbersAreRejected() = runBlocking {
        val f = FarmerPurchaseFixture()
        f.handler = { """{"success":true,"data":[${FarmerPurchaseFixture.orderJson},${FarmerPurchaseFixture.orderJson}]}""" }
        rejects { f.orders.listOrders(42) }
        for (id in listOf("\"105\"", "1.5", "9223372036854775808")) {
            f.handler = { """{"success":true,"data":${FarmerPurchaseFixture.orderJson.replace("\"id\":105", "\"id\":$id")}}""" }
            rejects { f.orders.readOrder(105) }
        }
    }
    @Test fun staleSessionBeforeAndDuringReadCannotPublishData() = runBlocking {
        val before = FarmerPurchaseFixture(); before.stale(); rejects { before.orders.listOrders(42) }; assertTrue(before.requests.isEmpty())
        val during = FarmerPurchaseFixture(); during.hook = during::stale
        rejects { during.orders.readOrder(105) }; assertEquals(1, during.requests.size)
    }
    @Test fun roleOriginDemoAndLogoutAreIsolated() = runBlocking {
        for (change in listOf<(SessionSnapshot) -> SessionSnapshot>(
            { it.copy(auth = it.auth!!.copy(role = "BUYER")) },
            { it.copy(auth = it.auth!!.copy(userId = "9")) },
            { it.copy(auth = it.auth!!.copy(sessionOrigin = "https://other.example.test/api/v1/")) },
            { it.copy(isDemo = true) }, { it.copy(loggingOut = true) })) {
            val f = FarmerPurchaseFixture(); f.session.value = change(f.expected)
            rejects { f.orders.listOrders(42) }; assertTrue(f.requests.isEmpty())
        }
    }
    @Test fun unauthorizedMissingAndFailedEnvelopesDoNotBecomeEmptySuccess() = runBlocking {
        for (code in listOf(401, 403, 404, 409, 503)) {
            val f = FarmerPurchaseFixture(); f.status = code; f.handler = { """{"success":false,"message":"Synthetic failure"}""" }
            rejects { f.orders.listOrders(42).requirePurchaseData() }; assertEquals(1, f.requests.size)
        }
        val f = FarmerPurchaseFixture(); f.handler = { """{"success":false,"data":[]}""" }
        rejects { f.orders.listOrders(42).requirePurchaseData() }
    }
    @Test fun interruptedReadsAreNotAutomaticallyRetried() = runBlocking {
        val f = FarmerPurchaseFixture(); f.handler = { throw SocketTimeoutException("Synthetic timeout") }
        rejects { f.orders.listOrders(42) }; assertEquals(1, f.requests.size)
    }
    @Test fun addressesRequireOwnershipAndFiniteCoordinates() = runBlocking {
        for (row in listOf("""{"id":8,"userId":9,"addressText":"Synthetic"}""",
            """{"id":8,"userId":42,"addressText":"Synthetic","latitude":91}""")) {
            val f = FarmerPurchaseFixture(); f.handler = { """{"success":true,"data":[$row]}""" }
            rejects { f.addresses.list() }
        }
    }
    @Test fun savedAssociation91MapsToPlatform3AndForeignAssociationsFail() = runBlocking {
        val f = FarmerPurchaseFixture()
        val saved = f.pickup.saved().requirePurchaseData().single()
        assertEquals(91L, saved.id); assertEquals(3L, saved.pickupStationId)
        assertEquals(3L, f.pickup.detail(saved.pickupStationId!!).requirePurchaseData().id)
        f.handler = { """{"success":true,"data":[{"id":91,"userId":9,"pickupStationId":3,"pickupStationAvailable":true,"isDefault":true}]}""" }
        rejects { f.pickup.saved() }
    }
    @Test fun paginatedSearchPreservesPageEnvelopeAndUsesBuyerIdNotSellerId() = runBlocking {
        val f = FarmerPurchaseFixture()
        f.handler = { """{"success":true,"data":{"content":[${FarmerPurchaseFixture.orderJson}],"number":0,"size":20,"totalElements":1,"totalPages":1,"last":true}}""" }
        val repo = FarmerOrderSearchRepository(f.api(), f.sessions, f.expected)
        assertEquals(1L, repo.page(0).requirePurchaseData().totalElements)
        assertEquals("buyerId=42&page=0&size=20", f.requests.single().url.encodedQuery)
        assertEquals("/api/v1/orders/search", f.requests.single().url.encodedPath)
        f.handler = { """{"success":true,"data":[]}""" }; rejects { repo.page(0) }
        f.handler = { """{"success":true,"data":{"content":[],"number":1,"size":20,"totalElements":0,"totalPages":0,"last":true}}""" }; rejects { repo.page(0) }
    }
    @Test fun trackingRequiresFreshOwnedOrderAndExactJobAssociations() = runBlocking {
        val f = FarmerPurchaseFixture()
        val job = f.tracking.jobs(105).requirePurchaseData().single()
        assertEquals(10L, f.tracking.location(job).requirePurchaseData().tripId)
        rejects { f.tracking.location(job.copy(driverId = 99)) }
        rejects { f.tracking.events(DeliveryJobDto(11, 105, 9, "assigned")) }
    }
    @Test fun badTrackingCoordinatesAndForeignOrdersAreRejected() = runBlocking {
        val f = FarmerPurchaseFixture(); val original = f.handler
        f.handler = { request -> original(request).replace("\"latitude\":-1.2", "\"latitude\":91") }
        rejects { f.tracking.location(DeliveryJobDto(10, 105, 9, "assigned")) }
        f.handler = { request -> original(request).replace("\"buyerId\":42", "\"buyerId\":9") }
        rejects { f.tracking.jobs(105) }
    }
    @Test fun paymentReadsRequireOrderOwnershipAndBoundedAmounts() = runBlocking {
        val f = FarmerPurchaseFixture(); val original = f.handler
        f.handler = { request -> if (request.url.encodedPath.endsWith("payments/order/105"))
            """{"success":true,"data":[{"id":5,"orderId":105,"amount":99999,"status":"paid"}]}""" else original(request) }
        rejects { f.payment.payments(105) }
        f.handler = { request -> original(request).replace("\"buyerId\":42", "\"buyerId\":9") }
        rejects { f.payment.payments(105) }
        assertTrue(f.requests.all { it.method == "GET" })
    }
    @Test fun orderAmountsQuantitiesAndExclusiveDeliveryIdentityMustAgree() = runBlocking {
        for (bad in listOf(
            FarmerPurchaseFixture.orderJson.replace("\"total\":426.50", "\"total\":426.51"),
            FarmerPurchaseFixture.orderJson.replace("\"quantity\":3", "\"quantity\":4"),
            FarmerPurchaseFixture.orderJson.replace("\"pickupStationId\":3", "\"pickupStationId\":3,\"addressId\":8"),
            FarmerPurchaseFixture.orderJson.replace("\"pickupStationId\":3", "\"pickupStationId\":0"))) {
            val f = FarmerPurchaseFixture(); f.handler = { """{"success":true,"data":$bad}""" }
            rejects { f.orders.readOrder(105) }
        }
    }
    @Test fun supplierSearchKeepsPaginationAndRejectsMixedSellerResponses() = runBlocking {
        val f = FarmerPurchaseFixture()
        val product = com.google.gson.JsonParser.parseString(FarmerPurchaseFixture.productJson).asJsonObject["data"]
        f.handler = { """{"success":true,"data":{"content":[$product],"number":0,"last":true,"totalPages":1}}""" }
        assertEquals("7", f.marketplace.page(f.expected, "seed", null, 0).first.single().productId)
        assertEquals("SUPPLIER", f.requests.single().url.queryParameter("sellerType"))
        assertNull(f.requests.single().url.queryParameter("sellerId"))
        f.handler = { """{"success":true,"data":{"content":[$product],"number":1,"last":true}}""" }; rejects { f.marketplace.page(f.expected, "", null, 0) }
        f.handler = { """{"success":true,"data":{"content":[${product.toString().replace("supplier", "farmer")}],"number":0,"last":true}}""" }
        rejects { f.marketplace.page(f.expected, "", null, 0) }
    }
}
