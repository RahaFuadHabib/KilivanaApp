package com.example.farmapp.farmerpurchases

import com.example.farmapp.checkout.DeliverySelection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class FarmerTransactionControllerTest {
    private val fixture = FarmerPurchaseFixture()
    private var saved: FarmerTransactionRecord? = null
    private var paymentStatus = "pending"
    private var paymentExists = false
    private var orderTimedOut = false
    private var payTimedOut = false
    private var lastFailure: Throwable? = null
    private val disk = object : FarmerTransactionStore {
        override fun read() = saved
        override fun write(record: FarmerTransactionRecord) { saved = record }
    }
    private fun controller(enabled: Boolean = true, pollMs: Long = 60_000) = FarmerTransactionController(fixture.api(), fixture.api(),
        fixture.sessions, fixture.expected, disk, fixture.scope, enabled, pollMs = pollMs, reportFailure = { lastFailure = it })
    init {
        fixture.handler = { request ->
            when (request.url.encodedPath.removePrefix("/api/v1/")) {
                "checkout/validate" -> """{"success":true,"data":{"id":11,"buyerId":42,"status":"CHECKOUT_PROCESSING","items":{"7":3}}}"""
                "checkout/delivery-quote" -> """{"success":true,"data":{"quoteId":"durable-quote","subtotal":376.50,"deliveryFee":50.50,"total":427.00,"currency":"KES","expiresAt":"2099-01-01T00:00:00Z","deliveryMethod":"PICKUP_STATION","pickupStationId":3}}"""
                "checkout/order" -> if (orderTimedOut) { orderTimedOut = false; throw IOException("synthetic lost order response") }
                    else """{"success":true,"data":105}"""
                "orders/105" -> """{"success":true,"data":${FarmerPurchaseFixture.orderJson.replace("426.50", "427.00").replace("\"deliveryFee\":50,", "\"deliveryFee\":50.50,")}}"""
                "payments/stk-push/buyer" -> {
                    paymentExists = true
                    if (payTimedOut) { payTimedOut = false; throw IOException("synthetic lost payment response") }
                    """{"success":true,"data":{"paymentId":501,"orderId":105,"amount":427.00,"status":"pending"}}"""
                }
                "payments/order/105" -> """{"success":true,"data":${if (paymentExists) "[${payment()}]" else "[]"}}"""
                "payments/501/status" -> """{"success":true,"data":${payment()}}"""
                else -> error("Unexpected synthetic request")
            }
        }
    }
    private fun payment() = """{"id":501,"orderId":105,"amount":427.00,"status":"$paymentStatus","method":"mpesa"}"""
    private fun wait(controller: FarmerTransactionController) = runBlocking {
        withTimeout(5000) { controller.state.first { !it.busy } }
    }
    private fun prepare(controller: FarmerTransactionController) {
        controller.prepare(mapOf(7L to 3), DeliverySelection.Station(3)); wait(controller)
        assertEquals("${lastFailure?.javaClass?.simpleName}: ${lastFailure?.message}", "QUOTE_READY", saved?.phase)
    }
    private fun create(controller: FarmerTransactionController) {
        prepare(controller); controller.submitOrder(); wait(controller)
        assertEquals("ORDER_CREATED", saved?.phase)
    }
    private fun writes(path: String) = fixture.requests.count { it.url.encodedPath == "/api/v1/$path" }
    @After fun close() { fixture.scope.cancel() }

    @Test fun completePurchaseUsesServerQuoteAndReadsAuthoritativeConfirmation() {
        val c = controller(); create(c)
        assertEquals("427.00", c.state.value.order?.total?.toPlainString())
        c.initiate("0700000001"); wait(c)
        assertEquals("PAYMENT_PENDING", saved?.phase)
        paymentStatus = "paid"; c.recover(); wait(c)
        assertEquals("CONFIRMED", saved?.phase)
        assertEquals(1, writes("checkout/order")); assertEquals(1, writes("payments/stk-push/buyer"))
    }
    @Test fun ambiguousOrderReplaysSameQuoteAfterControllerRestart() {
        val c = controller(); prepare(c); orderTimedOut = true
        c.submitOrder(); wait(c); assertEquals("ORDER_PENDING", saved?.phase)
        val quote = saved?.quote?.quoteId
        val restarted = controller(); restarted.recover(); wait(restarted)
        assertEquals(quote, saved?.quote?.quoteId); assertEquals(105L, saved?.orderId)
        assertEquals(2, writes("checkout/order")); assertEquals(1, writes("checkout/validate"))
    }
    @Test fun ambiguousStkSurvivesRestartAndNeverRepeatsInitiation() {
        val c = controller(); create(c); payTimedOut = true
        c.initiate("0700000001"); wait(c)
        assertEquals("PAYMENT_PENDING", saved?.phase); assertNotNull(saved?.paymentKey)
        val restarted = controller(); restarted.recover(); wait(restarted)
        assertEquals(501L, saved?.paymentId)
        paymentStatus = "paid"; restarted.recover(); wait(restarted)
        assertEquals("CONFIRMED", saved?.phase); assertEquals(1, writes("payments/stk-push/buyer"))
    }
    @Test fun repeatedSubmissionCannotCreateAnotherOrderOrPrompt() {
        val c = controller(); prepare(c)
        c.submitOrder(); c.submitOrder(); wait(c)
        assertEquals("ORDER_CREATED", saved?.phase)
        c.initiate("0700000001"); c.initiate("0700000001"); wait(c)
        assertEquals(1, writes("checkout/order")); assertEquals(1, writes("payments/stk-push/buyer"))
    }
    @Test fun disabledEnvironmentAndStaleSessionSendNoRequests() {
        val c = controller(false); c.prepare(mapOf(7L to 3), DeliverySelection.Station(3)); wait(c)
        assertTrue(fixture.requests.isEmpty()); assertNull(saved)
        val active = controller(); fixture.session.value = fixture.expected.copy(generation = fixture.expected.generation + 1)
        active.prepare(mapOf(7L to 3), DeliverySelection.Station(3)); wait(active)
        assertTrue(fixture.requests.isEmpty()); assertNull(saved)
    }
    @Test fun confirmedFailureRequiresExplicitUserRetryWithNewDurableKey() {
        val c = controller(); create(c); c.initiate("0700000001"); wait(c)
        val firstKey = saved?.paymentKey
        paymentStatus = "failed"; c.recover(); wait(c)
        assertEquals("PAYMENT_FAILED", saved?.phase); assertEquals(1, writes("payments/stk-push/buyer"))
        paymentStatus = "pending"; c.initiate("0700000001"); wait(c)
        assertNotEquals(firstKey, saved?.paymentKey); assertEquals(2, writes("payments/stk-push/buyer"))
    }
    @Test fun tokenRotationWithinSameSessionDoesNotInvalidateCheckout() {
        val c = controller()
        fixture.session.value = fixture.expected.copy(auth = fixture.expected.auth!!.copy(accessToken = "synthetic-rotated-access"))
        prepare(c)
        assertEquals("QUOTE_READY", saved?.phase)
    }
    @Test fun sessionChangeAfterPreparationBlocksDependentQuoteRequest() {
        val c = controller()
        fixture.hook = { fixture.session.value = fixture.expected.copy(generation = fixture.expected.generation + 1) }
        c.prepare(mapOf(7L to 3), DeliverySelection.Station(3)); wait(c)
        assertEquals("PREPARING", saved?.phase)
        assertEquals(1, writes("checkout/validate")); assertEquals(0, writes("checkout/delivery-quote"))
    }
    @Test fun persistedRecordCodecRetainsLargeProductIdsWithoutDoubleConversion() {
        val record = FarmerTransactionRecord("synthetic-key", mapOf(9007199254740993L to 3),
            com.example.farmapp.checkout.CheckoutQuoteRequest("PICKUP_STATION", pickupStationId = 3))
        val gson = com.example.farmapp.network.canonical.CanonicalJson.gson
        assertEquals(record, gson.fromJson(gson.toJson(record), FarmerTransactionRecord::class.java))
    }
    @Test fun lostInitiationResponseAutomaticallyReconcilesWithoutAnotherPrompt() {
        val c = controller(pollMs = 100)
        create(c)
        payTimedOut = true
        c.initiate("0712345678")
        wait(c)
        paymentStatus = "paid"
        runBlocking {
            withTimeout(5000) { c.state.first { it.record?.phase == "CONFIRMED" } }
        }
        assertEquals(1, writes("payments/stk-push/buyer"))
        assertEquals(501L, saved?.paymentId)
    }
}
