package com.example.farmapp.payments

/** A reservation is never cleared by this boundary, including after a timeout or failed callback. */
internal interface PaymentAttemptStore {
    fun started(orderId: Long): Boolean
    fun reserve(orderId: Long): Boolean
}

internal interface PaymentAttemptPersistence {
    fun started(orderId: Long): Boolean
    /** Must complete durably, or throw before the caller can send a payment request. */
    fun markStarted(orderId: Long)
}

/** Adapters sharing a storage namespace must supply the same lock across instances. */
internal class DurablePaymentAttempts(private val persistence: PaymentAttemptPersistence, private val lock: Any) : PaymentAttemptStore {
    override fun started(orderId: Long) = persistence.started(orderId)
    override fun reserve(orderId: Long): Boolean = synchronized(lock) {
        if (started(orderId)) false else {
            persistence.markStarted(orderId)
            true
        }
    }
}
