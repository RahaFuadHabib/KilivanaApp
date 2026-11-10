package com.example.farmapp.buyers

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.*
import androidx.test.platform.app.InstrumentationRegistry
import com.example.farmapp.BuildConfig
import com.example.farmapp.buyer.*
import com.example.farmapp.buyers.data.CartViewModel
import com.example.farmapp.buyers.data.DeliveryQuoteRequest
import com.example.farmapp.buyers.model.*
import com.example.farmapp.buyers.ui.screens.*
import com.example.farmapp.buyers.ui.theme.KilivanabuyerTheme
import com.example.farmapp.data.model.AuthResponse
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.orders.*
import com.example.farmapp.tracking.*
import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Real components, Buyer adapters/controllers and order routes, wired to in-process APIs only.
 * This harness intentionally does not start the globally authenticated BrianBuyerApp/ViewModels.
 */
class SharedBuyerJourneyTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val user = "batch6-journey-" + UUID.randomUUID()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val expected = SessionSnapshot(AuthResponse("42", "synthetic@example.invalid", "Synthetic Buyer", "BUYER",
        "synthetic-access", "synthetic-refresh", "2099-01-01T00:00:00Z", backendContract = "canonical",
        sessionOrigin = "https://synthetic.example.test/api/v1/"), generation = 1)
    private val session = MutableStateFlow(expected)
    // Only session.value is consumed. No global session is initialized, read or changed.
    private val sessions = object : SessionStore by TokenManager { override val session = this@SharedBuyerJourneyTest.session }
    private val disk = BuyerPreferences(context, BuildConfig.API_BASE_URL, user)
    private val attempts = BuyerPaymentPreferences(context, BuildConfig.API_BASE_URL, user)
    private val now = Instant.now()
    private val product = Product("7", "Synthetic produce", "Vegetables", BigDecimal("1.07"), "kg", "", null, null, StockStatus.InStock, BigDecimal.ONE)
    private val cart = listOf(CartItem(product, 3))
    private val quote = com.example.farmapp.checkout.CheckoutQuote("q1", null, BigDecimal("3.21"), BigDecimal("50.00"), BigDecimal("53.21"), "KES", now.plusSeconds(3600).toString(), "PICKUP_STATION", pickupStationId = 3, cartId = 10, items = listOf(com.example.farmapp.checkout.CheckoutQuoteItem(7, 3, BigDecimal("1.07"), BigDecimal("3.21"))))
    private val intendedOrder = BuyerOrderDto(105, 42, "placed", quote.total, "pending", logisticsJobId = 10,
        items = listOf(BuyerOrderItemDto(1, 7, 9, product.name, "kg", 3, product.pricePerUnit, quote.subtotal)),
        subtotal = quote.subtotal, deliveryFee = quote.deliveryFee, pickupStationId = 3)
    private val backend = Backend()
    private fun journal() = CheckoutPreferences(context, user).read()
    private fun checkout() = BrianCheckoutController(backend, backend, sessions, expected, CheckoutPreferences(context, user), scope) { now }

    private inner class Backend : BrianCheckoutApi, BuyerOrdersApi, BuyerPickupApi, DeliveryTrackingApi {
        var created: BuyerOrderDto? = null
        var records = emptyList<PaymentDto>()
        var processing = false
        var creates = 0; var stk = 0; var uploads = 0
        var badJob = false; var badCoordinates = false
        val station = BuyerPickupStation(3, "Synthetic station", "Synthetic place", region = "North", isAvailable = true)
        val association = BuyerSavedPickup(1003, 42, 3, station.name, station.address, station.region, true, "Home", true)
        private fun <T> ok(value: T) = Response.success(CanonicalApiResponse(true, data = value))
        private fun tag(session: SessionSnapshot) { assertEquals(expected, session) }
        private fun metadata() = JsonParser.parseString("""{"id":10,"buyerId":42,"status":"${if(processing)"CHECKOUT_PROCESSING" else "ACTIVE"}"}""").asJsonObject
        override suspend fun cart(buyerId: Long, session: SessionSnapshot): Response<CanonicalApiResponse<JsonObject>> { tag(session); assertEquals(42L, buyerId); return ok(metadata()) }
        override suspend fun add(item: CartUpload, session: SessionSnapshot): Response<CanonicalApiResponse<JsonObject>> {
            tag(session); assertEquals("UPLOAD_PENDING", journal().phase); assertEquals(CartUpload(7, 3), item); uploads++; return ok(metadata())
        }
        override suspend fun quantity(cartId: Long, productId: Long, quantity: Int, session: SessionSnapshot): Response<CanonicalApiResponse<JsonObject>> {
            tag(session)
            assertEquals("QUANTITY_PENDING", journal().phase)
            assertEquals(10L, cartId)
            assertEquals(7L, productId)
            assertEquals(3, quantity)
            return ok(metadata())
        }
        override suspend fun checkout(session: SessionSnapshot): Response<CanonicalApiResponse<Unit>> { tag(session); assertEquals("CHECKOUT_PENDING", journal().phase); processing = true; return ok(Unit) }
        override suspend fun quote(request: DeliveryQuoteRequest, session: SessionSnapshot): Response<CanonicalApiResponse<com.example.farmapp.checkout.CheckoutQuote>> {
            tag(session); assertTrue(processing); assertEquals(3L, request.pickupStationId); assertNull(request.addressId); assertNull(request.cartId); return ok(quote)
        }
        override suspend fun order(request: QuoteOrderRequest, session: SessionSnapshot): Response<CanonicalApiResponse<JsonElement>> {
            tag(session); assertEquals("ORDER_PENDING", journal().phase); assertEquals("q1", request.quoteId); assertEquals("254712345678", request.phoneNumber)
            creates++; created = intendedOrder; return ok<JsonElement>(JsonPrimitive(105))
        }
        override suspend fun orders(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<List<BuyerOrderDto>>> { tag(session); assertEquals(42L, id); return ok(listOfNotNull(created)) }
        override suspend fun order(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerOrderDto>> { tag(session); assertEquals(105L, id); return ok(checkNotNull(created)) }
        override suspend fun timeline(id: Long, session: SessionSnapshot) = ok(listOf(OrderEventDto(1, id, "placed")))
        override suspend fun payments(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<List<PaymentDto>>> { tag(session); assertEquals(105L, id); return ok(records) }
        override suspend fun payment(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<PaymentDto>> { tag(session); return ok(records.single { it.id == id }) }
        override suspend fun receipt(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerOrderDto>> = error("No receipt mutation")
        override suspend fun initiateBuyerStk(request: BuyerStkRequest, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerStkResponse>> {
            tag(session); assertTrue(attempts.started(105)); assertEquals(105L, request.orderId); assertEquals("254712345678", request.phoneNumber)
            stk++; records = listOf(PaymentDto(55, 105, "mpesa", quote.total, "pending"))
            return ok(BuyerStkResponse(55, 105, quote.total, "PAID")) // Must still display pending.
        }
        override suspend fun availability(session: SessionSnapshot) = ok(true)
        override suspend fun available(session: SessionSnapshot) = ok(listOf(station))
        override suspend fun region(region: String, session: SessionSnapshot) = ok(listOf(station))
        override suspend fun detail(stationId: Long, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerPickupStation>> { tag(session); assertEquals(3L, stationId); return ok(station) }
        override suspend fun saved(session: SessionSnapshot) = ok(listOf(association))
        override suspend fun save(request: BuyerSavePickupRequest, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerSavedPickup>> = error("No station write")
        override suspend fun remove(userStationId: Long, session: SessionSnapshot): Response<CanonicalApiResponse<Unit>> = error("No station write")
        override suspend fun setDefault(userStationId: Long, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerSavedPickup>> = error("No station write")
        override suspend fun jobs(order: Long, session: SessionSnapshot): Response<CanonicalApiResponse<List<DeliveryJobDto>>> {
            tag(session); assertEquals(105L, order); return ok(listOf(DeliveryJobDto(10, if(badJob)999 else 105, 88, "in_transit")))
        }
        override suspend fun events(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<List<DeliveryEventDto>>> {
            tag(session); assertEquals(10L, id); return ok(listOf(DeliveryEventDto(1, 10, 88, "in_transit", note = "Synthetic dispatch update")))
        }
        override suspend fun location(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<DeliveryLocationDto>> {
            tag(session); assertEquals(10L, id)
            return if (badCoordinates) ok(DeliveryLocationDto(10, 105, 88, Double.NaN, 36.8))
            else Response.error(404, "".toResponseBody()) // No fake zero coordinates; no map/provider calls.
        }
    }

    @After fun cleanup() {
        scope.cancel()
        for (prefix in listOf("buyer-shopping-", "buyer-checkout-", "buyer-payment-")) context.deleteSharedPreferences(prefix + buyerScope(BuildConfig.API_BASE_URL, user))
    }

    @Composable private fun Journey(allowSyntheticPayment: Boolean) {
        val checkout = remember { checkout() }
        val payment = remember { BuyerPaymentController(backend, sessions, expected, attempts, scope) { allowSyntheticPayment } }
        val pickup = remember { BuyerPickupController(backend, sessions, expected, disk, scope) }
        val checked by checkout.state.collectAsState()
        val paymentState by payment.state.collectAsState()
        val pickupState by pickup.state.collectAsState()
        val nav = rememberNavController()
        LaunchedEffect(checked.order?.id) { checked.order?.id?.let { nav.navigate("payment/$it") } }
        KilivanabuyerTheme {
            NavHost(nav, startDestination = "cart") {
                composable("cart") { CartScreen(cart, CartViewModel(quote.subtotal, {}, {}, {})) { nav.navigate("checkout") } }
                composable("checkout") {
                    var confirm by remember { mutableStateOf<Pair<String, String>?>(null) }
                    CheckoutScreen(cart, emptyList(), false, null, checked.quote, {}, {}, {}, { id, _, phone -> confirm = id to phone }, { nav.popBackStack() },
                        submitting = checked.busy, feedback = checked.notice,
                        deliveryMethodContent = {
                            Text("Select delivery method")
                            Button(onClick = { nav.navigate("pickup") }) { Text("Choose saved station") }
                        }, deliveryContent = {
                            Text("Pickup: ${pickupState.selected?.name ?: "Select a station"}")
                            Button(onClick = { checkout.prepare(disk.read().cart, quote.subtotal, DeliveryQuoteRequest("PICKUP_STATION", pickupStationId = disk.selectedPickup())) },
                                enabled = pickupState.selected != null && !checked.busy) { Text("Calculate delivery") }
                        })
                    confirm?.let { (id, phone) -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Create this order?") },
                        text = { Text("Verified total: ${quote.total}") }, confirmButton = { TextButton(onClick = { confirm = null; checkout.create(disk.read().cart, id, phone) }) { Text("Confirm order") } },
                        dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }) }
                }
                composable("pickup") {
                    LaunchedEffect(Unit) { pickup.refresh() }
                    Column {
                        Text("Pickup Stations")
                        pickupState.saved.forEach { BuyerSavedPickupCard(it, pickupState, BuyerPickupActions(select = pickup::select)) }
                        Button(onClick = { nav.popBackStack() }, enabled = pickupSelectionValid(pickupState)) { Text("Use selected station location") }
                    }
                }
                buyerOrderDestinations(
                    ordersContent = { OrdersScreen(listOfNotNull(backend.created).map { brianOrder(it) }, { nav.navigate("order/${it.id}") }, {}) },
                    orderContent = { id -> OrderDetailScreen(brianOrder(checkNotNull(backend.created).also { check(it.id == id) }), {},
                        payment = { Button(onClick = { nav.navigate("tracking/$id") }) { Text("Track delivery") } }, back = { nav.popBackStack() }) },
                    paymentContent = { id ->
                        LaunchedEffect(id) { payment.refresh(id) }
                        Column {
                            Text("Order verified")
                            Box(Modifier.weight(1f)) { BrianPaymentReview(checkNotNull(checked.order), paymentState, { nav.popBackStack() }, { payment.refresh(id) },
                                { phone, total -> payment.initiate(id, phone, total) }, checked.phone) }
                            Button(onClick = { nav.navigate("orders") }) { Text("Order history") }
                        }
                    },
                    trackingContent = { id -> DeliveryTrackingContent(checkNotNull(backend.created).also { check(it.id == id) }, sessions, backend) { nav.popBackStack() } }
                )
            }
        }
    }

    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
        node.performClick(); compose.waitForIdle()
    }
    private fun journey(allowSyntheticPayment: Boolean) {
        disk.write(BuyerSelection(cart = mapOf(7L to 3)))
        compose.setContent { Journey(allowSyntheticPayment) }
        click("Proceed to checkout")
        compose.onNodeWithText("Select delivery method").assertIsDisplayed()
        click("Choose saved station")
        compose.onNodeWithTag("saved-pickup-1003").assertExists()
        click("Use selected station location")
        compose.onNodeWithText("Kenyan mobile number").performScrollTo().performTextInput("0712345678")
        click("Calculate delivery")
        compose.onNodeWithText("KES 53.21").assertExists()
        click("Create order"); assertEquals(0, backend.creates); click("Confirm order")
        compose.onNodeWithText("Order verified").assertIsDisplayed()
        assertEquals("ORDER_VERIFIED", journal().phase); assertEquals(105L, journal().orderId)
        assertEquals(mapOf(7L to 3), journal().items); assertEquals(3L, journal().quote!!.pickupStationId)
        assertEquals(1003L, backend.association.id); assertEquals(3L, disk.selectedPickup()); assertEquals(0, backend.stk)
        if (allowSyntheticPayment) {
            click("Send M-Pesa prompt"); assertEquals(0, backend.stk); click("Cancel"); assertEquals(0, backend.stk)
            click("Send M-Pesa prompt"); click("Confirm STK Push")
            compose.onNodeWithText("Payment pending").assertIsDisplayed(); assertEquals(1, backend.stk)
            // Explicit synthetic backend confirmation, never an initiation response.
            compose.runOnIdle { backend.records = backend.records.map { it.copy(status = "held") }; backend.created = backend.created!!.copy(paymentStatus = "held") }
            click("Check payment status")
            compose.onNodeWithText("Payment confirmed").assertIsDisplayed()
        } else {
            compose.onNodeWithText("Send M-Pesa prompt").performScrollTo().assertIsNotEnabled()
            assertFalse(attempts.started(105))
        }
        click("Order history")
        compose.onNodeWithText("My orders").assertIsDisplayed()
        // The real order card opens its production order/{id} destination.
        compose.onNodeWithText("View order", substring = true).performClick()
        click("Track delivery")
        compose.onNodeWithText("Delivery tracking").assertIsDisplayed()
        compose.onNodeWithText("No driver location has been recorded yet.").assertExists()
        compose.onNodeWithText("Synthetic dispatch update", substring = true).assertExists()
        assertEquals(1, backend.creates); assertEquals(1, backend.uploads)
        // Recreate both controllers against the real preference implementations; no replay.
        compose.runOnIdle {
            val recovered = checkout(); recovered.recover(); assertEquals(105L, recovered.state.value.order!!.id)
            val payment = BuyerPaymentController(backend, sessions, expected, BuyerPaymentPreferences(context, BuildConfig.API_BASE_URL, user), scope) { allowSyntheticPayment }
            payment.refresh(105); if (allowSyntheticPayment) payment.initiate(105, "0712345678")
        }
        assertEquals(1, backend.creates); assertEquals(if(allowSyntheticPayment)1 else 0, backend.stk)
        assertEquals(mapOf(7L to 3), disk.read().cart)
    }

    @Test fun fullBuyerJourneyKeepsUnconfirmedEnvironmentDisabledAndPreservesRecords() = journey(false)
    @Test fun fullBuyerJourneyUsesExplicitSyntheticPaymentAndRecoversWithoutDuplicateWrites() = journey(true)

    @Test fun trackingRejectsForeignJobsAndInvalidFixesWithoutInventingLocationData() {
        backend.created = intendedOrder; backend.badJob = true
        compose.setContent { KilivanabuyerTheme { DeliveryTrackingContent(intendedOrder, sessions, backend) {} } }
        compose.onNodeWithText("Delivery job could not be linked to this order.").assertExists()
        compose.runOnIdle { backend.badJob = false }
        click("Refresh delivery")
        compose.onNodeWithText("No driver location has been recorded yet.").assertExists()
        compose.runOnIdle { backend.badCoordinates = true }
        click("Refresh delivery")
        compose.onNodeWithText("Latest driver location is unavailable. Any shown position is the last successful report.").assertExists()
        compose.onNodeWithText("Last driver report:", substring = true).assertDoesNotExist()
    }
}
