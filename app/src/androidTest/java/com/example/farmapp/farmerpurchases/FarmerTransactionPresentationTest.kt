package com.example.farmapp.farmerpurchases

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.farmapp.checkout.*
import com.example.farmapp.data.model.AuthResponse
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.orders.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import retrofit2.Response
import java.lang.reflect.Proxy
import java.math.BigDecimal

/** Production transaction panel/controller with synthetic transport. No real session is established. */
class FarmerTransactionPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val expected = SessionSnapshot(AuthResponse("42", "synthetic@example.invalid", "Synthetic Farmer", "FARMER",
        "synthetic-access", "synthetic-refresh", "2099-01-01T00:00:00Z", backendContract = "canonical",
        sessionOrigin = "https://synthetic.example.test/api/v1/"), generation = 1)
    private val session = MutableStateFlow(expected)
    private val sessions = object : SessionStore by TokenManager { override val session = this@FarmerTransactionPresentationTest.session }
    private val quote = CheckoutQuote("synthetic-quote", null, BigDecimal("200.00"), BigDecimal("1.00"), BigDecimal("201.00"),
        "KES", "2099-01-01T00:00:00Z", "PICKUP_STATION", pickupStationId = 3)
    private var record = FarmerTransactionRecord("synthetic-key", mapOf(7L to 2), DeliverySelection.Station(3).toQuoteRequest(), "QUOTE_READY", quote)
    private var orderCalls = 0
    private var paymentCalls = 0
    private val order = BuyerOrderDto(id = 105, buyerId = 42, status = "placed", total = quote.total, paymentStatus = "pending",
        subtotal = quote.subtotal, deliveryFee = quote.deliveryFee, pickupStationId = 3,
        items = listOf(BuyerOrderItemDto(id = 1, productId = 7, sellerId = 9, quantity = 2,
            unitPrice = BigDecimal("100.00"), subtotal = BigDecimal("200.00"))))
    private fun <T> ok(value: T) = Response.success(CanonicalApiResponse(true, data = value))
    private val api = object : FarmerTransactionApi {
        override suspend fun prepare(request: FarmerPreparation, session: SessionSnapshot) = error("Unexpected prepare")
        override suspend fun quote(request: FarmerQuoteRequest, session: SessionSnapshot) = error("Unexpected quote")
        override suspend fun order(request: FarmerQuoteOrder, session: SessionSnapshot): Response<CanonicalApiResponse<Long>> { orderCalls++; return ok(105L) }
        override suspend fun pay(request: FarmerIntentRequest, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerStkResponse>> {
            paymentCalls++; return ok(BuyerStkResponse(501, 105, quote.total, "pending"))
        }
    }
    private val orders = Proxy.newProxyInstance(BuyerOrdersApi::class.java.classLoader, arrayOf(BuyerOrdersApi::class.java)) { _, method, _ ->
        when (method.name) {
            "order" -> ok(order)
            "payments" -> ok(listOf(PaymentDto(501, 105, "mpesa", quote.total, "pending")))
            "payment" -> ok(PaymentDto(501, 105, "mpesa", quote.total, "pending"))
            else -> error("Unexpected transport")
        }
    } as BuyerOrdersApi
    @After fun close() { scope.cancel() }
    @Test fun orderAndStkRequireSeparateConfirmationsAndPendingIsDisplayed() {
        val controller = FarmerTransactionController(api, orders, sessions, expected, object : FarmerTransactionStore {
            override fun read() = record
            override fun write(record: FarmerTransactionRecord) { this@FarmerTransactionPresentationTest.record = record }
        }, scope, enabled = true)
        compose.setContent { MaterialTheme { FarmerTransactionPanel(controller, mapOf(7L to 2), DeliverySelection.Station(3)) } }
        compose.onNodeWithText("Create purchase order").performClick()
        compose.runOnIdle { assertEquals(0, orderCalls) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, orderCalls) }
        compose.onNodeWithText("Create purchase order").performClick()
        compose.onNodeWithText("Submit order").performClick()
        compose.runOnIdle { assertEquals(1, orderCalls); assertEquals(0, paymentCalls) }
        compose.onNodeWithText("M-Pesa phone number").performTextInput("0700000001")
        compose.onNodeWithText("Send M-Pesa prompt").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(0, paymentCalls) }
        compose.onNodeWithText("Confirm STK Push").performClick()
        compose.runOnIdle { assertEquals(1, paymentCalls) }
        compose.onNodeWithText("Payment pending. Status is checked automatically; an uncertain prompt is never repeated.").assertExists()
    }
}
