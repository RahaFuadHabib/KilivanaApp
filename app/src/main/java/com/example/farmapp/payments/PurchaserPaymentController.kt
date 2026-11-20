package com.example.farmapp.payments

import com.example.farmapp.checkout.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import java.math.BigDecimal

/** Shared workflow; durable reservations survive every uncertain result. UI owns lifecycle polling. */
internal class PurchaserPaymentController<Order, Payment, Initiation>(
    private val repository: PurchaserPaymentRepository<Order, Payment, Initiation>,
    private val contract: PurchaserPaymentContract<Order, Payment, Initiation>,
    sessions: SessionStore, expected: SessionSnapshot, policy: PurchaserPolicy,
    private val attempts: PaymentAttemptStore, private val scope: CoroutineScope,
    private val paymentAllowed: () -> Boolean
) {
    private val guard = CheckoutSessionGuard(sessions, expected, policy)
    private val mutable = MutableStateFlow(PurchaserPaymentState<Payment>())
    val state = mutable.asStateFlow()
    private fun current(operation: CheckoutOperation = CheckoutOperation.READ_PAYMENTS) = guard.current(operation)
    private suspend fun <T> request(operation: CheckoutOperation, action: suspend () -> Response<CanonicalApiResponse<T>>): Response<CanonicalApiResponse<T>> {
        current(operation)
        val response = action()
        try { current(operation) } catch (failure: Exception) { response.errorBody()?.close(); throw failure }
        return response
    }
    private fun <T> read(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) {
            val message = runCatching {
                val body = response.errorBody()?.string() ?: return@runCatching null
                val json = com.google.gson.JsonParser.parseString(body)
                if (json.isJsonObject)
                    json.asJsonObject.get("message")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                else null
            }.getOrNull()
            error(message ?: "Payment request was rejected (HTTP ${response.code()}).")
        }
        return response.body()?.requireData() ?: error("Incomplete response")
    }
    /** Lightweight read used by refresh(): fetches order + payment list only, no per-payment status calls. */
    private suspend fun inspectList(id: Long): Pair<Order, List<Payment>> {
        val order = contract.validateOrder(read(request(CheckoutOperation.READ_ORDERS) { repository.order(id) }), id, current().auth!!.userId.toLong())
        val rows = read(request(CheckoutOperation.READ_PAYMENTS) { repository.payments(id) })
        check(rows.map { contract.paymentId(it) }.distinct().size == rows.size)
        val checked = rows.map { row -> contract.validateReference(row, id); contract.validateStatus(row, row, id) }
        current()
        return order to checked
    }
    /** Full inspect used by initiate(): also fetches each payment's individual status for pre-flight verification. */
    private suspend fun inspect(id: Long): Pair<Order, List<Payment>> {
        val order = contract.validateOrder(read(request(CheckoutOperation.READ_ORDERS) { repository.order(id) }), id, current().auth!!.userId.toLong())
        val rows = read(request(CheckoutOperation.READ_PAYMENTS) { repository.payments(id) })
        check(rows.map { contract.paymentId(it) }.distinct().size == rows.size)
        val checked = rows.map { row ->
            contract.validateReference(row, id)
            val latest = read(request(CheckoutOperation.READ_PAYMENTS) { repository.payment(contract.paymentId(row)!!) })
            contract.validateStatus(latest, row, id)
        }
        current()
        return order to checked
    }
    private fun payable(order: Order) = contract.payable(order)
    fun refresh(id: Long) {
        if (mutable.value.busy || id <= 0) return
        try { current() } catch (_: Exception) { return }
        val startedBeforeRead=runCatching{attempts.started(id)}.getOrDefault(true)
        mutable.update { if(it.orderId==id)it.copy(busy=true,canInitiate=false,submitted=it.submitted || startedBeforeRead)
            else PurchaserPaymentState<Payment>(orderId=id,busy=true,submitted=startedBeforeRead,submissionEnabled=paymentAllowed()) }
        scope.launch {
            try {
                val (order, rows) = inspectList(id)
                val started = attempts.started(id)
                val allTerminal = rows.isNotEmpty() && rows.all { contract.status(it) in setOf("failed", "cancelled") }
                // Stale lock: server has no payments and order is payable, OR all payments are terminal and order is still payable.
                val staleLock = started && (rows.isEmpty() || allTerminal) && payable(order)
                // Release the durable lock so reserve() succeeds on the next initiation attempt.
                if (staleLock) runCatching { attempts.release(id) }
                mutable.value = PurchaserPaymentState<Payment>(id, canInitiate = payable(order) && (rows.isEmpty() || allTerminal) && (!started || staleLock),
                    payments = rows, total = contract.total(order), submitted = started && !staleLock, submissionEnabled = paymentAllowed(),
                    notice = if (!paymentAllowed()) MPESA_ENVIRONMENT_NOTICE else null)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (runCatching { current() }.isSuccess) mutable.update { it.copy(busy=false,canInitiate=false,
                payments=it.payments.filter{p->paymentOutcome(contract.status(p))=="pending"},
                notice = "Payment status could not be verified. Check your session and connection, then refresh. No new payment was sent.") } }
        }
    }
    /** Called only by the explicit confirmation button; no automatic submission or write retry. */
    fun initiate(id: Long, phoneInput: String, reviewedTotal: BigDecimal? = mutable.value.total) {
        if(!paymentAllowed()){mutable.update{it.copy(submissionEnabled=false,notice=MPESA_ENVIRONMENT_NOTICE)};return}
        if (mutable.value.busy || mutable.value.orderId != id || !mutable.value.canInitiate) return
        if (reviewedTotal == null || mutable.value.total?.compareTo(reviewedTotal) != 0) {
            mutable.update { it.copy(notice = "The order total changed. Refresh and review again.") }; return
        }
        val phone = try { mpesaPhone(phoneInput) } catch (_: Exception) {
            mutable.update { it.copy(notice = "Enter a Kenyan mobile number beginning 07, 01, +2547 or +2541.") }; return
        }
        mutable.update { it.copy(busy = true, canInitiate = false, notice = null) }
        scope.launch {
            try {
                val (order, existing) = inspect(id)
                val retryable = existing.all { contract.status(it) in setOf("failed", "cancelled") }
                check(payable(order) && (existing.isEmpty() || retryable) && contract.total(order).compareTo(reviewedTotal) == 0)
                current()
                check(paymentAllowed())
                check(attempts.reserve(id))
                mutable.update{it.copy(submitted=true,notice="Sending M-Pesa prompt. Waiting for payment status.")}
                current()
                val response = read(request(CheckoutOperation.INITIATE_PAYMENT) { check(paymentAllowed()); repository.initiate(id, phone) })
                current()
                // The role adapter validates identifiers and amount, and always returns pending.
                val pending = contract.pendingInitiation(response, id, order)
                mutable.value = PurchaserPaymentState<Payment>(id, payments = listOf(pending),
                    total=contract.total(order),submitted=true,submissionEnabled=paymentAllowed(),notice = "Check your phone for the M-Pesa prompt. Payment is pending.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (runCatching { current() }.isFailure) return@launch
                val message = e.message ?: ""
                when {
                    // Web: "already being processed" → treat as pending and poll, never fail.
                    message.contains("already being processed", ignoreCase = true) -> {
                        val rows = runCatching { inspect(id).second }.getOrElse { emptyList() }
                        val pendingRows = rows.filter { contract.status(it) == "pending" }
                        mutable.update { it.copy(busy = false, canInitiate = false,
                            payments = pendingRows.ifEmpty { it.payments },
                            notice = "A payment request is already being processed. Enter your PIN if prompted while we check its status.") }
                    }
                    // Web: 403 / "not authorized to request payment for this order" → specific message.
                    message.contains("not authorized to request payment", ignoreCase = true) ||
                    (e is java.io.IOException && message.contains("403")) -> {
                        mutable.update { it.copy(busy = false, canInitiate = false,
                            notice = "The payment service did not authorise this account for the order. No payment was taken. Check that you are signed in with the account that placed this order, or contact support.") }
                    }
                    else -> {
                        val detail = message.takeIf { it.isNotBlank() && it != "Request not confirmed" }
                            ?: "Initiation was not confirmed. Check payment status before taking any further action; no automatic retry was made."
                        mutable.update { it.copy(busy = false, canInitiate = false, notice = detail) }
                    }
                }
            }
        }
    }
}

