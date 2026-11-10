package com.example.farmapp.payments

import com.example.farmapp.orders.buyerMpesaPhone
import com.example.farmapp.orders.buyerPaymentOutcome
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class PaymentInfrastructureTest {
    @Test fun sharedAndBuyerPhoneNormalizationRemainIdentical() {
        for ((input, expected) in listOf("0712345678" to "254712345678", "0112345678" to "254112345678",
            "+254 712 345 678" to "254712345678", " (0712) 345-678 " to "254712345678", "254112345678" to "254112345678")) {
            assertEquals(expected, mpesaPhone(input)); assertEquals(expected, buyerMpesaPhone(input))
        }
    }
    @Test fun invalidForeignUnicodeAndIncompletePhonesAreRejected() {
        for (input in listOf("", "07abc12345678", "＋２５４７１２３４５６７８", "+123456789", "254212345678", "071234567", "07123456789", "0712345678\nsecret"))
            assertThrows(IllegalArgumentException::class.java) { mpesaPhone(input) }
    }
    @Test fun onlyDocumentedPaymentStatesAreConfirmed() {
        for ((status, outcome) in mapOf("PAID" to "confirmed", "held" to "confirmed", "settled" to "confirmed", "PENDING" to "pending", "failed" to "failed", "refunded" to "refunded")) {
            assertEquals(outcome, paymentOutcome(status)); assertEquals(outcome, buyerPaymentOutcome(status))
        }
        for (status in listOf(null, "cancelled", "SUCCESS", "COMPLETED", "unknown", " paid ")) assertEquals("unverified", paymentOutcome(status))
    }
    @Test fun environmentRequiresExplicitModeAndExactConfirmedOrigin() {
        val origin = "https://synthetic.example.test/api/v1/"
        for (mode in listOf("SANDBOX", "PRODUCTION")) assertTrue(verifiedMpesaEnvironment(mode, origin, origin))
        for (mode in listOf("UNKNOWN", "sandbox", "", "LIVE")) assertFalse(verifiedMpesaEnvironment(mode, origin, origin))
        for (confirmed in listOf("", origin.dropLast(1), "http://synthetic.example.test/api/v1/", origin + "other/")) assertFalse(verifiedMpesaEnvironment("SANDBOX", confirmed, origin))
    }
    @Test fun durableReservationsAreAtomicAcrossInstancesAndSurviveRecreation() {
        val ids = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
        val persistence = object : PaymentAttemptPersistence {
            override fun started(orderId: Long) = orderId in ids
            override fun markStarted(orderId: Long) { ids.add(orderId) }
        }
        val lock = Any(); val first = DurablePaymentAttempts(persistence, lock); val second = DurablePaymentAttempts(persistence, lock)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll((1..20).map { Callable { (if (it % 2 == 0) first else second).reserve(10) } })
            assertEquals(1, results.count { it.get() })
            val restarted = DurablePaymentAttempts(persistence, lock)
            assertTrue(restarted.started(10)); assertFalse(restarted.reserve(10)); assertTrue(restarted.reserve(11))
        } finally { executor.shutdownNow() }
    }
    @Test fun failedDurableCommitNeverReturnsAReservation() {
        val store = DurablePaymentAttempts(object : PaymentAttemptPersistence {
            override fun started(orderId: Long) = false
            override fun markStarted(orderId: Long) { error("Synthetic disk failure") }
        }, Any())
        assertThrows(IllegalStateException::class.java) { store.reserve(10) }
    }
}
