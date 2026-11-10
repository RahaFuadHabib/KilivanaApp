package com.example.farmapp.farmerpurchases

import com.example.farmapp.buyers.*
import com.example.farmapp.buyer.*
import com.example.farmapp.orders.*
import com.example.farmapp.checkout.CheckoutQuoteRequest
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

/** Captured public OpenAPI + pinned Java + Angular service/test disagreements are deliberate fixtures.
 * Synthetic HTTP interceptor terminates EVERY request; no backend network is used by these tests.
 */
class FarmerApiAlignmentTest {
    private val spec = JsonParser.parseReader(checkNotNull(javaClass.getResourceAsStream(
        "/farmerpurchases/deployed-contract.json")).bufferedReader()).asJsonObject
    @Test fun reusedRetrofitMethodsAreAdvertisedByDeployedOpenApi() {
        listOf(BrianCheckoutApi::class.java, BuyerOrdersApi::class.java, BuyerAddressApi::class.java,
            BuyerPickupApi::class.java, PurchaserOrderSearchApi::class.java).forEach { api ->
            api.declaredMethods.forEach { method ->
                val path = method.getAnnotation(GET::class.java)?.value ?: method.getAnnotation(POST::class.java)?.value
                    ?: method.getAnnotation(PUT::class.java)?.value ?: return@forEach
                val verb = if (method.isAnnotationPresent(GET::class.java)) "get" else if (method.isAnnotationPresent(POST::class.java)) "post" else "put"
                // Path variable names differ between existing Android services and Spring's specification.
                val normalized = path.replace(Regex("\\{[^}]+}"), "{}")
                val published = spec["paths"].asJsonObject.entrySet().firstOrNull {
                    it.key.removePrefix("/api/v1/").replace(Regex("\\{[^}]+}"), "{}") == normalized
                }
                assertNotNull("Missing $verb $path", published)
                assertTrue("Wrong method $verb $path", published!!.value.asJsonObject.has(verb))
            }
        }
    }
    @Test fun angularLegacyCheckoutPayloadIsNotSilentlySentToAnotherRoute() {
        val paths = spec["paths"].asJsonObject
        assertFalse(paths.has("/api/v1/orders/checkout")) // Angular order.spec.ts expects this stale route.
        assertTrue(paths.has("/api/v1/orders")) // Angular current api.config.ts CREATE uses this.
        assertTrue(paths.has("/api/v1/checkout/order")) // Existing Android quote engine uses this.
        val request = spec["schemas"].asJsonObject["CheckoutOrderRequest"].asJsonObject["properties"].asJsonObject
        assertTrue(request.has("quoteId")); assertFalse(request.has("recipientName"))
        assertEquals("orders/buyer/{id}", BuyerOrdersApi::class.java.getDeclaredMethods().single { it.name == "orders" }.getAnnotation(GET::class.java)!!.value)
    }
    @Test fun cartSchemasExposeMetadataNotFabricatedLineEvidence() {
        val fields = spec["schemas"].asJsonObject["CartResponse"].asJsonObject["properties"].asJsonObject
        assertTrue(fields.has("id") && fields.has("buyerId") && fields.has("status"))
        assertFalse(fields.has("items")); assertFalse(fields.has("total"))
        assertEquals("int64", fields["buyerId"].asJsonObject["format"].asString)
        val response = spec["paths"].asJsonObject["/api/v1/checkout/order"].asJsonObject["post"].asJsonObject[
            "responses"].asJsonObject["200"].asJsonObject["content"].asJsonObject["*/*"].asJsonObject["schema"].asJsonObject
        assertEquals("#/components/schemas/ApiResponseLong", response["\$ref"].asString)
    }
    @Test fun existingCartAndQuoteApiPathsBodiesAndNumericIdsRemainExact() = runBlocking {
        val f = FarmerPurchaseFixture(); f.handler = { """{"success":true,"data":{"id":10,"buyerId":42,"status":"ACTIVE"}}""" }
        val api = f.api<BrianCheckoutApi>()
        api.cart(42, f.expected); api.add(CartUpload(7, 3), f.expected)
        api.quantity(10, 7, 3, f.expected)
        f.handler = { """{"success":true,"data":{"quoteId":"synthetic-quote","subtotal":376.50,"deliveryFee":50,"total":426.50,"currency":"KES","expiresAt":"2099-01-01T00:00:00Z","deliveryMethod":"PICKUP_STATION","pickupStationId":3}}""" }
        api.quote(CheckoutQuoteRequest("PICKUP_STATION", pickupStationId = 3), f.expected)
        f.handler = { """{"success":true,"data":105}""" }
        api.order(QuoteOrderRequest("synthetic-quote", "254700000000"), f.expected)
        assertEquals(listOf("carts/buyer/42", "cart/items", "carts/10/items/7", "checkout/delivery-quote", "checkout/order"),
            f.requests.map { it.url.encodedPath.removePrefix("/api/v1/") })
        assertEquals("quantity=3", f.requests[2].url.encodedQuery)
        fun json(index: Int) = JsonParser.parseString(Buffer().also { f.requests[index].body!!.writeTo(it) }.readUtf8())
        assertEquals(JsonParser.parseString("""{"productId":7,"quantity":3}"""), json(1))
        assertEquals(JsonParser.parseString("""{"deliveryMethod":"PICKUP_STATION","pickupStationId":3}"""), json(3))
        assertEquals(JsonParser.parseString("""{"quoteId":"synthetic-quote","phoneNumber":"254700000000"}"""), json(4))
    }
    @Test fun purchaserStkRouteMatchesAngularBuyerVariantNotSellerCollection() = runBlocking {
        val f = FarmerPurchaseFixture(); f.handler = { """{"success":true,"data":{"paymentId":5,"orderId":105,"amount":426.50,"status":"pending"}}""" }
        f.api<BuyerOrdersApi>().initiateBuyerStk(BuyerStkRequest(105, "254700000000"), f.expected)
        assertEquals("/api/v1/payments/stk-push/buyer", f.requests.single().url.encodedPath)
        assertEquals("POST", f.requests.single().method)
        assertTrue(spec["paths"].asJsonObject.has("/api/v1/payments/stk-push/buyer"))
    }
}
