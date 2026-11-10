package com.example.farmapp.farmerpurchases

import android.content.Context
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.farmapp.buyer.*
import com.example.farmapp.checkout.*
import com.example.farmapp.data.model.AuthResponse
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.marketplace.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.network.canonical.toFarmerProduct
import com.example.farmapp.orders.*
import com.example.farmapp.tracking.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import retrofit2.Response
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.util.UUID

/** Production Farmer presentation/controllers and storage, in-process APIs only. */
class FarmerPurchasesPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val user = "farmer-ui-" + UUID.randomUUID()
    private val origin = "https://synthetic.example.test/api/v1/"
    private val expected = SessionSnapshot(AuthResponse("42", "synthetic@example.invalid", "Synthetic Farmer", "FARMER",
        "synthetic-access", "synthetic-refresh", "2099-01-01T00:00:00Z", backendContract = "canonical", sessionOrigin = origin), generation = 1)
    private val session = MutableStateFlow(expected)
    private val sessions = object : SessionStore by TokenManager { override val session = this@FarmerPurchasesPresentationTest.session }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val calls = mutableListOf<String>()
    private val disk = FarmerPurchasePersistence(context, user, origin)
    private val product = CanonicalProductDto(7, 9, "supplier", 2, "Synthetic seed", unit = "bag", price = BigDecimal("125.50"),
        stockQty = 12, reservedQty = 2, soldQty = 0, minimumOrderQty = 1, status = "active", images = emptyList())
    private val order = BuyerOrderDto(105, 42, "placed", BigDecimal("426.50"), "pending", items = listOf(
        BuyerOrderItemDto(1, 7, 9, "Synthetic seed", "bag", 3, BigDecimal("125.50"), BigDecimal("376.50"))),
        subtotal = BigDecimal("376.50"), deliveryFee = BigDecimal("50"), pickupStationId = 3)
    private inline fun <reified T> fake(crossinline handle: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            calls += "${T::class.java.simpleName}.${method.name}"
            handle(method.name, args ?: emptyArray())
        } as T
    private fun <T> ok(value: T) = Response.success(CanonicalApiResponse(true, data = value))
    private val orderApi = fake<BuyerOrdersApi> { method, args -> when (method) {
        "orders" -> { assertEquals(42L, args[0]); ok(listOf(order)) }
        "order" -> ok(order)
        "payments" -> ok(emptyList<PaymentDto>())
        else -> error("Unexpected order/payment mutation: $method")
    } }
    private val orders = FarmerCheckoutRepository(orderApi, sessions, expected)
    private fun createController(cart: FarmerCartRepository? = null, basket: FarmerBasketStore = disk) = FarmerPurchaseController(
        MarketplaceRepository(fake<MarketplaceApi> { method, _ -> check(method == "detail"); ok(product) }, sessions), orders,
        FarmerAddressRepository(fake<BuyerAddressApi> { method, _ -> check(method == "list"); ok(listOf(BuyerAddressDto(8, 42, addressText = "Synthetic farm", latitude = -1.2, longitude = 36.8))) }, sessions, expected),
        FarmerPickupRepository(fake<BuyerPickupApi> { method, _ -> when (method) {
            "availability" -> ok(true)
            "available" -> ok(listOf(BuyerPickupStation(3, "Synthetic station", "Farm Road", -1.2, 36.8, "Region", true)))
            "detail" -> ok(BuyerPickupStation(3, "Synthetic station", "Farm Road", -1.2, 36.8, "Region", true))
            "saved" -> ok(listOf(BuyerSavedPickup(91, 42, 3, pickupStationAvailable = true, isDefault = true)))
            else -> error("Unexpected station write: $method")
        } }, sessions, expected),
        FarmerTrackingRepository(fake<DeliveryTrackingApi> { method, _ -> when (method) {
            "jobs" -> ok(listOf(DeliveryJobDto(10, 105, null, "assigned")))
            "events" -> ok(listOf(DeliveryEventDto(1, 10, status = "assigned")))
            else -> error("No driver location is available")
        } }, orders, sessions, expected),
        FarmerPaymentRepository(orderApi, sessions, expected), sessions, expected, disk.journal, disk.attempts, basket, scope, cart)

    private val controller = createController()

    @After fun cleanup() { scope.cancel(); context.deleteSharedPreferences("farmer-purchases-" + buyerScope(origin, user)) }
    private fun show() { compose.setContent { MaterialTheme { FarmerPurchasesContent(controller) { add ->
        Button(onClick = { add(product.toFarmerProduct(), 3) }) { Text("Select synthetic seed") }
    } } } }
    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
    @Test fun catalogueLocalBasketDeliveryAndDisabledCheckoutRemainReadOnly() {
        show(); compose.onNodeWithText("Select synthetic seed").performClick()
        compose.onNodeWithText("Basket (1)").performClick()
        compose.onNodeWithText("Estimated items: KES 376.50").assertExists()
        click("Review delivery options"); click("Station #3: Synthetic station")
        compose.onNodeWithText("Selected: Station(pickupStationId=3)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Get delivery quote").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Create purchase order").performScrollTo().assertIsNotEnabled()
        assertEquals(mapOf(7L to 3), disk.basket())
        assertEquals("NONE", disk.journal.read().phase)
        assertTrue(calls.none { it.contains("initiate") || it.endsWith(".create") || it.endsWith(".checkout") })
    }
    @Test fun purchaserHistoryDetailsPaymentReviewAndTrackingDoNotUseSellerWorkflows() {
        show(); compose.onNodeWithText("My purchases").performClick()
        compose.onNodeWithText("Purchase #105: KES 426.50 — placed").performClick()
        compose.onNodeWithText("Purchaser #42").assertExists()
        compose.onNodeWithText("Send M-Pesa prompt").performScrollTo().assertIsNotEnabled()
        click("View purchase tracking")
        compose.onAllNodesWithText("Delivery #10: assigned").onFirst().performScrollTo().assertIsDisplayed()
        assertTrue(calls.none { it.contains("initiate") || it.contains("receipt") || it.contains("seller") })
    }
    @Test fun sessionChangePreventsBasketWrites() {
        show(); compose.runOnIdle { session.value = expected.copy(generation = 2) }
        compose.onNodeWithText("Select synthetic seed").performClick()
        assertTrue(disk.basket().isEmpty()); assertTrue(calls.isEmpty())
    }
    @Test fun decimalMinimumQuantityEnablesRealAddToCartAction() {
        var selected: Int? = null
        compose.setContent {
            MaterialTheme {
                FarmerProductSelection(product.toFarmerProduct().copy(minimumOrderQuantity = BigDecimal("1.00")), true) { _, quantity -> selected = quantity }
            }
        }
        compose.onNodeWithText("Add to cart").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, selected) }
    }
    @Test fun supplierAddAndRemoveUseServerCartAndPublishAcknowledgedUi() {
        var adds = 0
        var removes = 0
        val cartData = com.google.gson.JsonParser.parseString("""{"id":11,"buyerId":42,"status":"ACTIVE"}""").asJsonObject
        val cartApi = fake<PurchaserCartApi> { method, args -> when (method) {
            "cart" -> ok(cartData)
            "add" -> {
                assertEquals(com.example.farmapp.buyers.CartUpload(7, 3), args[0])
                adds++
                ok(cartData)
            }
            "remove" -> { assertEquals(11L, args[0]); assertEquals(7L, args[1]); removes++; ok(Unit) }
            else -> error("Unexpected cart method")
        } }
        disk.saveBasket(mapOf(99L to 2)) // Historical local intent is preserved, not treated as a server acknowledgement.
        val liveController = createController(FarmerCartRepository(cartApi, sessions, expected, disk.cartWrites), disk.serverBasket)
        var opens = 0
        compose.setContent { MaterialTheme {
            val state by liveController.state.collectAsState()
            FarmerPurchasesContent(liveController) { add ->
                Card(onClick = { opens++ }) {
                    val view = product.toFarmerProduct().copy(minimumOrderQuantity = BigDecimal("3"))
                    MarketplaceProduct(view) { FarmerCardCartAction(view, state, add) }
                }
            }
        } }
        compose.onNodeWithContentDescription("Add Synthetic seed to cart").performClick()
        compose.onNodeWithText("Added (3)").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, opens) }
        compose.onNodeWithText("Cart (3)").performClick()
        compose.onNodeWithText("Server cart #11. Item quantities are not included in the published cart response.").assertExists()
        compose.onNodeWithText("Synthetic seed \u2014 quantity 3").assertExists()
        compose.runOnIdle { assertEquals(1, adds); assertEquals(mapOf(7L to 3), disk.serverBasket.basket()); assertEquals(mapOf(99L to 2), disk.basket()) }
        compose.runOnIdle {
            val restarted = createController(FarmerCartRepository(cartApi, sessions, expected, disk.cartWrites), disk.serverBasket)
            assertEquals("Synthetic seed", restarted.state.value.labels[7L])
            assertEquals(mapOf(7L to 3), restarted.state.value.items)
        }
        compose.onNodeWithText("Remove #7").performClick()
        compose.runOnIdle { assertEquals(1, removes); assertTrue(disk.serverBasket.basket().isEmpty()) }
        compose.onNodeWithText("Cart (0)").assertExists()
    }
    @Test fun uncertainCardWriteShowsErrorWithoutAddedStateOrRepeat() {
        var posts = 0
        val cartData = com.google.gson.JsonParser.parseString("""{"id":11,"buyerId":42,"status":"ACTIVE"}""").asJsonObject
        val cartApi = fake<PurchaserCartApi> { method, _ -> when (method) {
            "cart" -> ok(cartData)
            "add" -> { posts++; throw java.io.IOException("Synthetic lost acknowledgement") }
            else -> error("Unexpected cart method")
        } }
        val c = createController(FarmerCartRepository(cartApi, sessions, expected, disk.cartWrites), disk.serverBasket)
        compose.setContent { MaterialTheme {
            val state by c.state.collectAsState()
            FarmerPurchasesContent(c) { add -> MarketplaceProduct(product.toFarmerProduct()) {
                FarmerCardCartAction(product.toFarmerProduct(), state, add)
            } }
        } }
        compose.onNodeWithContentDescription("Add Synthetic seed to cart").performClick()
        compose.onNodeWithText("Check cart").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add Synthetic seed to cart").assertIsNotEnabled()
        compose.onNodeWithText("Cart (0)").assertExists()
        compose.onAllNodesWithText("Added", substring = true).assertCountEquals(0)
        compose.runOnIdle { assertEquals(1, posts); assertTrue(disk.serverBasket.basket().isEmpty()); assertTrue(disk.cartWrites.pending()) }
    }
    @Test fun cardReflectsPendingStateAndDoesNotAllowDuplicateTap() {
        var calls = 0
        compose.setContent { MaterialTheme {
            MarketplaceProduct(product.toFarmerProduct()) {
                FarmerCardCartAction(product.toFarmerProduct(), FarmerPurchaseState(busy = true, cartActionId = 7), { _, _ -> calls++ })
            }
        } }
        compose.onNodeWithText("Adding").assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).onFirst().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, calls) }
    }
}
