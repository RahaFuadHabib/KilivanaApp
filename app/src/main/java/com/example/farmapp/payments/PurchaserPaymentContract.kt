package com.example.farmapp.payments

import com.example.farmapp.network.canonical.CanonicalApiResponse
import retrofit2.Response
import java.math.BigDecimal

/** Role-specific wire contracts stay in adapters. No Farmer endpoint is inferred. */
internal interface PurchaserPaymentRepository<Order, Payment, Initiation> {
    suspend fun order(orderId: Long): Response<CanonicalApiResponse<Order>>
    suspend fun payments(orderId: Long): Response<CanonicalApiResponse<List<Payment>>>
    suspend fun payment(paymentId: Long): Response<CanonicalApiResponse<Payment>>
    suspend fun initiate(orderId: Long, phone: String): Response<CanonicalApiResponse<Initiation>>
}

internal interface PurchaserPaymentContract<Order, Payment, Initiation> {
    fun validateOrder(order: Order, orderId: Long, purchaserId: Long): Order
    fun total(order: Order): BigDecimal
    fun payable(order: Order): Boolean
    fun paymentId(payment: Payment): Long?
    fun status(payment: Payment): String?
    fun validateReference(payment: Payment, orderId: Long)
    fun validateStatus(latest: Payment, listed: Payment, orderId: Long): Payment
    /** Validate identifiers/amounts, then publish pending regardless of the initiation's status. */
    fun pendingInitiation(response: Initiation, orderId: Long, order: Order): Payment
}

internal data class PurchaserPaymentState<Payment>(val orderId: Long? = null, val busy: Boolean = false,
    val canInitiate: Boolean = false, val payments: List<Payment> = emptyList(), val notice: String? = null, val total: BigDecimal? = null,
    val submissionEnabled: Boolean = true, val submitted: Boolean = false)
