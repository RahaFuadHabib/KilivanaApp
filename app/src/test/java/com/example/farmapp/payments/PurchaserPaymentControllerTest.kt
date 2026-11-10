package com.example.farmapp.payments

import com.example.farmapp.checkout.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.orders.*
import com.example.farmapp.supplier.supplierAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.net.SocketTimeoutException

class PurchaserPaymentControllerTest {
    private class Fixture(role: String = "BUYER") {
        private val delegate = signedInStore(supplierAuth().copy(role = "BUYER"))
        val session = MutableStateFlow(delegate.session.value.let { it.copy(auth = it.auth!!.copy(role = role)) })
        val sessions = object : SessionStore by delegate { override val session = this@Fixture.session }
        val expected = session.value
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val markers = mutableSetOf<Long>()
        var diskFailure = false; var readFailure = false; var allowed = true
        val attempts = object : PaymentAttemptStore {
            override fun started(orderId: Long): Boolean { check(!readFailure); return orderId in markers }
            override fun reserve(orderId: Long): Boolean { check(!diskFailure); return markers.add(orderId) }
        }
        var row = BuyerOrderDto(10, 42, "placed", BigDecimal("125.57"), "pending", items = emptyList())
        var records = emptyList<PaymentDto>()
        var latest: PaymentDto? = null
        var initiation = BuyerStkResponse(9, 10, row.total, "PAID")
        val calls = mutableListOf<String>()
        var hook: (String) -> Unit = {}
        var timeout: String? = null
        var cancel: String? = null
        var gate: CompletableDeferred<Unit>? = null
        var status = 200
        var success = true
        private suspend fun called(stage: String) {
            calls += stage; hook(stage)
            if (stage == "initiate") { assertTrue("Must reserve BEFORE sending", 10L in markers); gate?.await() }
            if (timeout == stage) throw SocketTimeoutException("Synthetic interrupted $stage")
            if (cancel == stage) throw CancellationException("Synthetic cancellation")
        }
        private fun <T> response(value: T): Response<CanonicalApiResponse<T>> =
            if (status == 204) Response.success<CanonicalApiResponse<T>>(204, null) else Response.success(CanonicalApiResponse(success, data = value))
        val repository = object : PurchaserPaymentRepository<BuyerOrderDto, PaymentDto, BuyerStkResponse> {
            override suspend fun order(orderId: Long): Response<CanonicalApiResponse<BuyerOrderDto>> { called("order"); return response(row) }
            override suspend fun payments(orderId: Long): Response<CanonicalApiResponse<List<PaymentDto>>> { called("payments"); return response(records) }
            override suspend fun payment(paymentId: Long): Response<CanonicalApiResponse<PaymentDto>> { called("status"); return response(latest ?: records.first { it.id == paymentId }) }
            override suspend fun initiate(orderId: Long, phone: String): Response<CanonicalApiResponse<BuyerStkResponse>> {
                called("initiate"); assertEquals(10L, orderId); assertEquals("254712345678", phone); return response(initiation)
            }
        }
        fun controller(policy: PurchaserPolicy = BuyerPurchaserPolicy) = PurchaserPaymentController(repository, BuyerPaymentContract, sessions, expected, policy, attempts, scope) { allowed }
        val controller = controller()
        fun begin() { controller.refresh(10); controller.initiate(10, "0712345678") }
        fun payment(status: String) = PaymentDto(9, 10, "mpesa", row.total, status)
    }
    private fun scenario(role: String = "BUYER", action: Fixture.() -> Unit) {
        val f = Fixture(role); try { f.action() } finally { f.scope.cancel() }
    }
    @Test fun sharedWorkflowReservesBeforeSendingAndNeverConfirmsFromInitiation() = scenario {
        begin(); assertTrue(controller.state.value.submitted); assertFalse(controller.state.value.canInitiate)
        assertEquals("pending", controller.state.value.payments.single().status)
        assertEquals(listOf("order", "payments", "order", "payments", "initiate"), calls)
        records = listOf(payment("held")); controller.refresh(10)
        assertEquals("confirmed", paymentOutcome(controller.state.value.payments.single().status)); assertEquals(1, calls.count { it == "initiate" })
    }
    @Test fun duplicateTapsAndIndependentControllersShareTheSameReservation() = scenario {
        gate = CompletableDeferred(); begin(); repeat(5) { controller.initiate(10, "0712345678") }
        val other = controller(); other.refresh(10); other.initiate(10, "0712345678")
        assertEquals(1, calls.count { it == "initiate" }); gate!!.complete(Unit)
    }
    @Test fun timedOutSubmissionStaysUnresolvedAcrossRestartAndEmptyBackendList() = scenario {
        timeout = "initiate"; begin(); assertTrue(10L in markers)
        val restarted = controller(); restarted.refresh(10); restarted.initiate(10, "0712345678")
        assertTrue(restarted.state.value.submitted); assertTrue(restarted.state.value.notice!!.contains("may have reached"))
        assertEquals(1, calls.count { it == "initiate" })
    }
    @Test fun cancellationKeepsDurableMarkerAndRestartCannotSubmitAgain() = scenario {
        cancel = "initiate"; begin(); assertTrue(10L in markers)
        val restarted = controller(); restarted.refresh(10); restarted.initiate(10, "0712345678")
        assertFalse(restarted.state.value.canInitiate); assertEquals(1, calls.count { it == "initiate" })
    }
    @Test fun staleSessionsCannotReadReserveOrSubmit() {
        for (kind in listOf("account", "origin", "generation", "logout", "demo", "contract", "signedOut", "FARMER")) scenario {
            val auth = expected.auth!!
            session.value = when (kind) {
                "account" -> expected.copy(auth = auth.copy(userId = "99"))
                "origin" -> expected.copy(auth = auth.copy(sessionOrigin = "https://other.test/"))
                "generation" -> expected.copy(generation = expected.generation + 1)
                "logout" -> expected.copy(loggingOut = true)
                "demo" -> expected.copy(isDemo = true)
                "contract" -> expected.copy(auth = auth.copy(backendContract = "legacy"))
                "signedOut" -> expected.copy(auth = null)
                else -> expected.copy(auth = auth.copy(role = kind))
            }
            begin(); assertTrue(calls.isEmpty()); assertTrue(markers.isEmpty())
        }
    }
    @Test fun logoutDuringSubmissionKeepsReservationWithoutPublishingLateResponse() = scenario {
        hook = { if (it == "initiate") session.value = expected.copy(loggingOut = true) }
        begin(); assertTrue(markers.contains(10)); assertTrue(controller.state.value.payments.isEmpty())
        session.value = expected; val restarted = controller(); restarted.refresh(10)
        assertFalse(restarted.state.value.canInitiate); assertEquals(1, calls.count { it == "initiate" })
    }
    @Test fun unverifiedFarmerPolicyNeverTouchesPaymentTransportOrStorage() = scenario("FARMER") {
        val farmer = controller(UnverifiedFarmerPurchaserPolicy); farmer.refresh(10); farmer.initiate(10, "0712345678")
        assertTrue(calls.isEmpty()); assertTrue(markers.isEmpty())
    }
    @Test fun environmentAndStorageFailuresPreventInitiation() {
        scenario { allowed = false; begin(); assertTrue(markers.isEmpty()); assertFalse(calls.contains("initiate")); assertFalse(controller.state.value.submissionEnabled) }
        scenario { diskFailure = true; begin(); assertFalse(calls.contains("initiate")); assertFalse(controller.state.value.canInitiate) }
        scenario { readFailure = true; begin(); assertFalse(calls.contains("initiate")); assertTrue(controller.state.value.submitted) }
        scenario { controller.refresh(10); hook = { if (it == "payments") allowed = false }; controller.initiate(10, "0712345678"); assertTrue(markers.isEmpty()); assertFalse(calls.contains("initiate")) }
    }
    @Test fun ownershipOrderIdentityAmountChangesAndInvalidPhonesPreventSubmission() {
        scenario { row = row.copy(buyerId = 99); begin(); assertTrue(markers.isEmpty()) }
        scenario { row = row.copy(id = 11); begin(); assertTrue(markers.isEmpty()) }
        scenario { controller.refresh(10); row = row.copy(total = BigDecimal("126.57")); controller.initiate(10, "0712345678"); assertTrue(markers.isEmpty()) }
        scenario { controller.refresh(10); controller.initiate(10, "invalid"); controller.initiate(10, "0712345678", BigDecimal.ONE); assertTrue(markers.isEmpty()) }
    }
    @Test fun malformedInitiationCannotPublishPendingOrEnableRetry() {
        val invalid = listOf(BuyerStkResponse(null, 10, BigDecimal.ONE), BuyerStkResponse(0, 10, BigDecimal.ONE),
            BuyerStkResponse(9, 99, BigDecimal.ONE), BuyerStkResponse(9, 10, null), BuyerStkResponse(9, 10, BigDecimal.ZERO), BuyerStkResponse(9, 10, BigDecimal("200")))
        for (bad in invalid) scenario { initiation = bad; begin(); assertTrue(markers.contains(10)); assertTrue(controller.state.value.payments.isEmpty()); controller.refresh(10); assertFalse(controller.state.value.canInitiate) }
    }
    @Test fun malformedStatusReferencesAndDuplicateRecordsFailClosed() {
        for (bad in listOf(PaymentDto(null, 10), PaymentDto(9, 99), PaymentDto(9, 10, amount = BigDecimal.ONE, status = "cancelled"),
            PaymentDto(9, 10, amount = BigDecimal("-1"), status = "paid"), PaymentDto(9, 10, status = "paid"))) scenario {
            records = listOf(bad); controller.refresh(10); assertFalse(controller.state.value.canInitiate); assertTrue(controller.state.value.payments.isEmpty()); assertFalse(calls.contains("initiate"))
        }
        scenario { records = listOf(payment("pending"), payment("pending")); controller.refresh(10); assertTrue(controller.state.value.payments.isEmpty()) }
        scenario { records = listOf(payment("pending")); latest = payment("paid").copy(id = 99); controller.refresh(10); assertTrue(controller.state.value.payments.isEmpty()) }
    }
    @Test fun failedAndRefundedPaymentsAndNetworkLossNeverPermitAnotherAttempt() = scenario {
        begin(); records = listOf(payment("pending")); controller.refresh(10)
        timeout = "order"; controller.refresh(10); assertEquals("pending", controller.state.value.payments.single().status)
        timeout = null
        for (status in listOf("failed", "refunded", "settled")) {
            records = listOf(payment(status)); val restarted = controller(); restarted.refresh(10); restarted.initiate(10, "0712345678")
            assertEquals(status, restarted.state.value.payments.single().status); assertFalse(restarted.state.value.canInitiate)
        }
        assertEquals(1, calls.count { it == "initiate" })
    }
    @Test fun missingAndFailedEnvelopesCannotEnableSubmission() {
        scenario { status = 204; begin(); assertTrue(markers.isEmpty()); assertTrue(controller.state.value.payments.isEmpty()) }
        scenario { success = false; begin(); assertTrue(markers.isEmpty()); assertFalse(controller.state.value.canInitiate) }
    }
}
