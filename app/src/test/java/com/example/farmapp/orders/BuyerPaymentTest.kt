package com.example.farmapp.orders

import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import com.example.farmapp.supplier.supplierAuth
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import java.math.BigDecimal

class BuyerPaymentTest {
    private class Attempts : BuyerPaymentAttemptStore {
        val ids = mutableSetOf<Long>(); var fail = false
        override fun started(orderId: Long) = orderId in ids
        override fun reserve(orderId: Long): Boolean { check(!fail); return ids.add(orderId) }
    }
    private class Api : BuyerOrdersApi {
        var readsFail = false
        var calls = 0; var timeout = false; var gate: CompletableDeferred<Unit>? = null
        var readGate:CompletableDeferred<Unit>?=null
        var row = BuyerOrderDto(10, 42, "placed", BigDecimal("125.57"), "pending", items = emptyList())
        var records = emptyList<PaymentDto>()
        override suspend fun orders(id: Long, session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = listOf(row)))
        override suspend fun order(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerOrderDto>> {
            if (readsFail) throw java.net.SocketTimeoutException()
            readGate?.await()
            return Response.success(CanonicalApiResponse(true, data = row))
        }
        override suspend fun timeline(id: Long, session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = emptyList<OrderEventDto>()))
        override suspend fun payments(id: Long, session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = records))
        override suspend fun payment(id: Long, session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data = records.first { it.id == id }))
        override suspend fun receipt(id: Long, session: SessionSnapshot) = error("Never confirm receipt")
        override suspend fun initiateBuyerStk(request: BuyerStkRequest, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerStkResponse>> {
            calls++; gate?.await(); if (timeout) throw java.net.SocketTimeoutException()
            return Response.success(CanonicalApiResponse(true, data = BuyerStkResponse(9, request.orderId, BigDecimal("125.57"), "PAID")))
        }
    }
    private fun sessions() = signedInStore(supplierAuth().copy(role = "BUYER"))
    @Test fun pendingRefreshKeepsWaitingStateAndNeverEnablesAnotherPrompt() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val api=Api();val sessions=sessions();val c=BuyerPaymentController(api,sessions,sessions.session.value,Attempts(),scope,{true})
            c.refresh(10);c.initiate(10,"0712345678");api.readGate=CompletableDeferred()
            c.refresh(10)
            assertTrue(c.state.value.busy);assertTrue(c.state.value.submitted)
            assertEquals("pending",c.state.value.payments.single().status);assertFalse(c.state.value.canInitiate)
            api.readGate!!.complete(Unit);assertEquals(1,api.calls)
        }finally{scope.cancel()}
    }
    @Test fun unconfirmedEnvironmentCannotSendOrReservePayment() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val api=Api();val sessions=sessions();val disk=Attempts()
            val c=BuyerPaymentController(api,sessions,sessions.session.value,disk,scope,{false})
            c.refresh(10);assertTrue(c.state.value.canInitiate);assertFalse(c.state.value.submissionEnabled)
            repeat(3){c.initiate(10,"0712345678")}
            assertEquals(0,api.calls);assertFalse(disk.started(10));assertEquals(MPESA_ENVIRONMENT_NOTICE,c.state.value.notice)
        }finally{scope.cancel()}
    }
    @Test fun phoneNormalizationRejectsLettersUnicodeAndNonKenyanNumbers() {
        assertEquals("254712345678", buyerMpesaPhone("+254 712 345 678"))
        assertEquals("254112345678", buyerMpesaPhone("0112345678"))
        for (value in listOf("+123456789", "07abc12345678", "０７１２３４５６７８", ""))
            assertTrue(runCatching { buyerMpesaPhone(value) }.isFailure)
    }
    @Test fun initiationIsSingleFlightAndHttpSuccessNeverMeansPaid() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Api().apply { gate = CompletableDeferred() }; val sessions = sessions(); val disk = Attempts()
            val c = BuyerPaymentController(api, sessions, sessions.session.value, disk, scope, { true })
            c.refresh(10); assertTrue(c.state.value.canInitiate)
            c.initiate(10, "0712345678"); repeat(10) { c.initiate(10, "0712345678") }
            assertEquals(1, api.calls); assertTrue(disk.started(10))
            api.gate!!.complete(Unit)
            assertEquals("pending", c.state.value.payments.single().status)
            assertFalse(c.state.value.canInitiate)
        } finally { scope.cancel() }
    }
    @Test fun timeoutSurvivesRestartAndCannotResubmitEvenIfListIsEmpty() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Api().apply { timeout = true }; val sessions = sessions(); val disk = Attempts()
            val c = BuyerPaymentController(api, sessions, sessions.session.value, disk, scope, { true })
            c.refresh(10); c.initiate(10, "0712345678")
            val restored = BuyerPaymentController(api, sessions, sessions.session.value, disk, scope, { true })
            restored.refresh(10); restored.initiate(10, "0712345678")
            assertEquals(1, api.calls); assertFalse(restored.state.value.canInitiate)
            assertTrue(restored.state.value.notice!!.contains("may have reached"))
            api.records = listOf(PaymentDto(9, 10, "mpesa", BigDecimal("125.57"), "paid"))
            restored.refresh(10)
            assertEquals("confirmed", buyerPaymentOutcome(restored.state.value.payments.single().status))
            assertFalse(restored.state.value.canInitiate)
        } finally { scope.cancel() }
    }
    @Test fun foreignOrderExistingPaymentsStorageFailureAndLogoutCannotSend() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Api(); val sessions = sessions(); val disk = Attempts()
            val c = BuyerPaymentController(api, sessions, sessions.session.value, disk, scope, { true })
            api.row = api.row.copy(buyerId = 99); c.refresh(10); c.initiate(10, "0712345678"); assertEquals(0, api.calls)
            api.row = api.row.copy(buyerId = 42); api.records = listOf(PaymentDto(9, 10, "mpesa", BigDecimal.ONE, "pending"))
            c.refresh(10); assertFalse(c.state.value.canInitiate)
            api.records = emptyList(); c.refresh(10); disk.fail = true; c.initiate(10, "0712345678"); assertEquals(0, api.calls)
            disk.fail = false; c.refresh(10); sessions.beginLogout(); c.initiate(10, "0712345678"); assertEquals(0, api.calls)
        } finally { scope.cancel() }
    }
    @Test fun changedServerTotalAndInvalidPhoneNeverInitiate() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Api(); val sessions = sessions(); val disk = Attempts()
            val c = BuyerPaymentController(api, sessions, sessions.session.value, disk, scope, { true })
            c.refresh(10); c.initiate(10, "invalid"); assertEquals(0, api.calls)
            api.row = api.row.copy(total = BigDecimal("126.57")); c.initiate(10, "0712345678")
            assertEquals(0, api.calls); assertFalse(disk.started(10))
        } finally { scope.cancel() }
    }
    @Test fun paymentStatesAndCancellationNeverFabricateSuccess() {
        assertEquals("pending", buyerPaymentOutcome("PENDING"))
        for (s in listOf("paid", "held", "settled")) assertEquals("confirmed", buyerPaymentOutcome(s))
        assertEquals("failed", buyerPaymentOutcome("failed"))
        assertEquals("refunded", buyerPaymentOutcome("refunded"))
        for (s in listOf("cancelled", "SUCCESS", "COMPLETED", "unknown")) assertEquals("unverified", buyerPaymentOutcome(s))
    }
    @Test fun stkRequestUsesExactBuyerRouteAndIdentifiersWithoutClientAmount() = runBlocking {
        var request: Request? = null
        val api = Retrofit.Builder().baseUrl("https://synthetic.example.test/api/v1/")
            .addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson))
            .client(OkHttpClient.Builder().addInterceptor { chain ->
                request = chain.request()
                okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("""{"success":true,"data":{"paymentId":9,"orderId":10,"amount":125.57,"status":"PENDING"}}""".toResponseBody("application/json".toMediaType())).build()
            }.build()).build().create(BuyerOrdersApi::class.java)
        api.initiateBuyerStk(BuyerStkRequest(10, buyerMpesaPhone("0712345678")), sessions().session.value)
        assertEquals("/api/v1/payments/stk-push/buyer", request!!.url.encodedPath)
        val json = com.google.gson.JsonParser.parseString(Buffer().also { request!!.body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertEquals(setOf("orderId", "phoneNumber"), json.keySet())
        assertEquals("254712345678", json["phoneNumber"].asString)
        assertFalse(BuyerStkRequest(10, "254712345678").toString().contains("254"))
    }
    @Test fun pendingNetworkLossAndFailedCallbackRecoverWithoutAnotherStk() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Api(); val sessions = sessions(); val disk = Attempts()
            val c = BuyerPaymentController(api, sessions, sessions.session.value, disk, scope, { true })
            c.refresh(10); c.initiate(10, "0712345678")
            api.records = listOf(PaymentDto(9, 10, "mpesa", BigDecimal("125.57"), "pending"))
            c.refresh(10); assertEquals("pending", buyerPaymentOutcome(c.state.value.payments.single().status))
            api.readsFail = true; c.refresh(10)
            assertFalse(c.state.value.canInitiate); assertTrue(c.state.value.notice!!.contains("could not be verified"))
            c.initiate(10, "0712345678"); assertEquals(1, api.calls)
            api.readsFail = false; api.records = api.records.map { it.copy(status = "failed") }
            val restored = BuyerPaymentController(api, sessions, sessions.session.value, disk, scope, { true })
            restored.refresh(10)
            assertEquals("failed", buyerPaymentOutcome(restored.state.value.payments.single().status))
            restored.initiate(10, "0712345678"); assertEquals(1, api.calls)
        } finally { scope.cancel() }
    }
    @Test fun undocumentedCancellationAndForeignPaymentFailClosed() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Api(); val sessions = sessions()
            val c = BuyerPaymentController(api, sessions, sessions.session.value, Attempts(), scope, { true })
            for (record in listOf(PaymentDto(9, 10, "mpesa", BigDecimal.ONE, "cancelled"),
                PaymentDto(9, 99, "mpesa", BigDecimal.ONE, "paid"))) {
                api.records = listOf(record); c.refresh(10)
                assertFalse(c.state.value.canInitiate); assertTrue(c.state.value.payments.isEmpty())
                assertNotNull(c.state.value.notice)
            }
            assertEquals(0, api.calls)
        } finally { scope.cancel() }
    }
    /** Contract seam scenario, not a claim that production cart/order orchestration exists. */
    @Test fun syntheticCartDeliveryQuoteAndReturnedOrderFeedExplicitPendingThenConfirmedPayment() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val cart = com.example.farmapp.buyer.BuyerSelection(cart = mapOf(25L to 2))
            val request = com.example.farmapp.buyer.BuyerDeliveryQuoteRequest.door(8)
            val now = java.time.Instant.parse("2026-10-29T12:00:00Z")
            val quote = com.example.farmapp.buyer.validatedDeliveryQuote(
                com.example.farmapp.buyer.BuyerDeliveryQuote("synthetic-quote", 8, BigDecimal("100.57"),
                    BigDecimal("25.00"), BigDecimal("125.57"), "KES", now.plusSeconds(900).toString(), "DOOR_DELIVERY"), request, now)
            val api = Api().apply { row = row.copy(total = quote.total,
                items = listOf(BuyerOrderItemDto(1, 25, 70, "Synthetic", "kg", 2, BigDecimal("50.285")))) }
            val sessions = sessions(); val disk = Attempts()
            val c = BuyerPaymentController(api, sessions, sessions.session.value, disk, scope, { true })
            c.refresh(10); assertEquals(0, api.calls); assertEquals(quote.total, c.state.value.total)
            c.initiate(10, "0712345678", quote.total)
            assertEquals("pending", buyerPaymentOutcome(c.state.value.payments.single().status))
            api.records = listOf(PaymentDto(9, 10, "mpesa", quote.total, "held"))
            c.refresh(10); assertEquals("confirmed", buyerPaymentOutcome(c.state.value.payments.single().status))
            assertEquals(mapOf(25L to 2), cart.cart); assertEquals(1, api.calls)
        } finally { scope.cancel() }
    }

}
