package com.example.farmapp.orders

import com.example.farmapp.checkout.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.payments.*
import retrofit2.Response

/** Uses only the existing Buyer order/payment API; no retry or alternate purchaser route. */
internal class BuyerPaymentRepository(private val api: BuyerOrdersApi, sessions: SessionStore, expected: SessionSnapshot,
    private val paymentAllowed: () -> Boolean = BuyerMpesaEnvironment::ready) :
    PurchaserPaymentRepository<BuyerOrderDto, PaymentDto, BuyerStkResponse> {
    private val guard = CheckoutSessionGuard(sessions, expected, BuyerPurchaserPolicy)
    private suspend fun <T> call(operation: CheckoutOperation, action: suspend (SessionSnapshot) -> Response<CanonicalApiResponse<T>>): Response<CanonicalApiResponse<T>> {
        val response = action(guard.current(operation))
        try { guard.current(operation) } catch (failure: Exception) { response.errorBody()?.close(); throw failure }
        return response
    }
    override suspend fun order(orderId: Long): Response<CanonicalApiResponse<BuyerOrderDto>> {
        require(orderId > 0)
        return call(CheckoutOperation.READ_ORDERS) { api.order(orderId, it) }
    }
    override suspend fun payments(orderId: Long): Response<CanonicalApiResponse<List<PaymentDto>>> {
        require(orderId > 0)
        return call(CheckoutOperation.READ_PAYMENTS) { api.payments(orderId, it) }
    }
    override suspend fun payment(paymentId: Long): Response<CanonicalApiResponse<PaymentDto>> {
        require(paymentId > 0)
        return call(CheckoutOperation.READ_PAYMENTS) { api.payment(paymentId, it) }
    }
    override suspend fun initiate(orderId: Long, phone: String): Response<CanonicalApiResponse<BuyerStkResponse>> {
        require(orderId > 0 && mpesaPhone(phone) == phone)
        check(paymentAllowed())
        return call(CheckoutOperation.INITIATE_PAYMENT) { api.initiateBuyerStk(BuyerStkRequest(orderId, phone), it) }
    }
}

internal object BuyerPaymentContract : PurchaserPaymentContract<BuyerOrderDto, PaymentDto, BuyerStkResponse> {
    override fun validateOrder(order: BuyerOrderDto, orderId: Long, purchaserId: Long) = validateBuyerOrder(order, purchaserId).also { check(it.id == orderId) }
    override fun total(order: BuyerOrderDto) = order.total!!
    override fun payable(order: BuyerOrderDto) = order.status in setOf("placed", "confirmed") &&
        order.paymentStatus in setOf("pending", "failed") && order.total!!.signum() > 0
    override fun paymentId(payment: PaymentDto) = payment.id
    override fun status(payment: PaymentDto) = payment.status
    override fun validateReference(payment: PaymentDto, orderId: Long) {
        check(payment.id != null && payment.id > 0 && payment.orderId == orderId)
    }
    override fun validateStatus(latest: PaymentDto, listed: PaymentDto, orderId: Long): PaymentDto {
        val normalized = latest.copy(status = latest.status?.lowercase())
        check(normalized.id == listed.id && normalized.orderId == orderId && normalized.amount != null && normalized.amount.signum() >= 0 && normalized.status in paymentStatuses)
        return normalized
    }
    override fun pendingInitiation(response: BuyerStkResponse, orderId: Long, order: BuyerOrderDto): PaymentDto {
        check(response.orderId == orderId && response.paymentId != null && response.paymentId > 0 &&
            response.amount != null && response.amount.signum() > 0 && response.amount <= order.total)
        return PaymentDto(response.paymentId, orderId, "mpesa", response.amount, "pending")
    }
}
