package com.example.farmapp.checkout

import com.example.farmapp.buyers.BrianCheckoutApi
import com.example.farmapp.buyers.BuyerCheckoutRepository
import com.example.farmapp.buyers.verifiedCheckoutOrderId
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalJson
import com.example.farmapp.orders.BuyerOrdersApi
import com.example.farmapp.supplier.supplierAuth
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.SocketTimeoutException

class CheckoutRepositoryContractTest {
    private class Fixture {
        val origin = "https://checkout.example.test/api/v1/"
        private val delegate = signedInStore(supplierAuth().copy(role="BUYER", sessionOrigin=origin))
        val state = MutableStateFlow(delegate.session.value)
        val sessions = object : SessionStore by delegate { override val session = state }
        val expected = state.value
        val requests = mutableListOf<Request>()
        var body = """{"success":true,"data":{"id":10,"buyerId":42,"status":"ACTIVE"}}"""
        var status = 200
        var interrupt = false
        var beforeResponse: () -> Unit = {}
        private val retrofit = Retrofit.Builder().baseUrl(origin)
            .addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false)
                .addInterceptor(AuthTokenInterceptor(origin.toHttpUrl(), sessions, RefreshCoordinator(StubAuthService(), sessions)))
                .addInterceptor { chain ->
                    requests += chain.request()
                    if (interrupt) throw SocketTimeoutException("Synthetic interrupted request")
                    beforeResponse()
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Synthetic fixture")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                }.build()).build()
        val repository = BuyerCheckoutRepository(retrofit.create(BrianCheckoutApi::class.java), retrofit.create(BuyerOrdersApi::class.java), sessions, expected)
        fun pricedRepository() = BuyerCheckoutRepository(retrofit.create(BrianCheckoutApi::class.java), retrofit.create(BuyerOrdersApi::class.java), sessions, expected,
            com.example.farmapp.marketplace.MarketplaceRepository(retrofit.create(com.example.farmapp.marketplace.MarketplaceApi::class.java), sessions))
        val quoteRequest = CheckoutQuoteRequest("DOOR_DELIVERY", addressId=8)
        fun requestJson(index: Int) = JsonParser.parseString(Buffer().also { requests[index].body!!.writeTo(it) }.readUtf8())
        fun operations(): List<suspend () -> Unit> = listOf(
            { repository.readCart(42); Unit }, { repository.addItem(7, 3); Unit },
            { repository.setQuantity(10, 7, 3); Unit }, { repository.startCheckout(); Unit },
            { repository.deliveryQuote(quoteRequest); Unit }, { repository.createOrder("q1", "254712345678"); Unit },
            { repository.listOrders(42); Unit }, { repository.readOrder(105); Unit }
        )
    }

    @Test fun buyerAdapterPreservesAllEightVerifiedMethodsPathsBodiesAndSessionTags() = runBlocking {
        val f = Fixture(); val repo = f.repository
        assertEquals(10L, repo.readCart(42).body()!!.data!!.get("id").asLong)
        repo.addItem(7, 3); repo.setQuantity(10, 7, 3)
        f.body="""{"success":true}"""; assertTrue(repo.startCheckout().body()!!.success!!)
        f.body="""{"success":true,"data":{"quoteId":"q1","addressId":8,"subtotal":3.21,"deliveryFee":150.00,"total":153.21,"currency":"KES","expiresAt":"2026-11-05T12:15:00Z","deliveryMethod":"DOOR_DELIVERY"}}"""
        assertEquals("153.21", repo.deliveryQuote(f.quoteRequest).body()!!.data!!.total.toPlainString())
        f.body="""{"success":true,"data":105}"""; assertEquals(105L, verifiedCheckoutOrderId(repo.createOrder("q1", "254712345678").body()!!.data!!))
        f.body="""{"success":true,"data":[]}"""; assertTrue(repo.listOrders(42).body()!!.data!!.isEmpty())
        f.body="""{"success":true,"data":{"id":105,"buyerId":42}}"""; assertEquals(105L, repo.readOrder(105).body()!!.data!!.id)
        assertEquals(listOf("GET", "POST", "PUT", "POST", "POST", "POST", "GET", "GET"), f.requests.map { it.method })
        assertEquals(listOf("carts/buyer/42", "cart/items", "carts/10/items/7", "checkout", "checkout/delivery-quote", "checkout/order", "orders/buyer/42", "orders/105"),
            f.requests.map { it.url.encodedPath.removePrefix("/api/v1/") })
        assertEquals("quantity=3", f.requests[2].url.encodedQuery)
        assertEquals(JsonParser.parseString("""{"productId":7,"quantity":3}"""), f.requestJson(1))
        assertEquals(0L, f.requests[3].body!!.contentLength())
        assertEquals(JsonParser.parseString("""{"deliveryMethod":"DOOR_DELIVERY","addressId":8}"""), f.requestJson(4))
        assertEquals(JsonParser.parseString("""{"quoteId":"q1","phoneNumber":"254712345678"}"""), f.requestJson(5))
        f.requests.forEach {
            assertEquals(f.expected, it.tag(SessionSnapshot::class.java))
            assertEquals("Bearer ${f.expected.auth!!.accessToken}", it.header("Authorization"))
        }
    }

    @Test fun stationAndTypedPickupRequestsKeepTheirExclusiveWireFields() = runBlocking {
        val f=Fixture();f.body="""{"success":true,"data":null}"""
        f.repository.deliveryQuote(CheckoutQuoteRequest("PICKUP_STATION", pickupStationId=3, cartId=10))
        f.repository.deliveryQuote(CheckoutQuoteRequest("PICKUP_STATION", pickupLocation="Synthetic pickup"))
        assertEquals(JsonParser.parseString("""{"deliveryMethod":"PICKUP_STATION","pickupStationId":3}"""), f.requestJson(0))
        assertEquals(JsonParser.parseString("""{"deliveryMethod":"PICKUP_STATION","pickupLocation":"Synthetic pickup"}"""), f.requestJson(1))
    }

    @Test fun httpConflictsAndFailedEnvelopesReachTheControllerUnchangedWithoutRetries() = runBlocking {
        val f=Fixture();f.status=409;f.body="""{"message":"Product already in cart"}"""
        val response=f.repository.addItem(7,3)
        assertEquals(409,response.code());assertEquals(f.body,response.errorBody()!!.string())
        assertEquals(1,f.requests.size)
        f.status=200;f.body="""{"success":false,"error":{"code":"CART_CONFLICT"}}"""
        assertFalse(f.repository.startCheckout().body()!!.success!!)
        assertEquals(2,f.requests.size)
    }

    @Test fun numericOrderIdEvidenceIsNotRoundedOrReplacedWithAnInventedId() = runBlocking {
        val f=Fixture();f.body="""{"success":true,"data":105.5}"""
        val id=f.repository.createOrder("q1","254712345678").body()!!.data!!
        assertThrows(ArithmeticException::class.java){verifiedCheckoutOrderId(id)}
        assertEquals(1,f.requests.size)
        f.body="""{"success":true,"data":{"id":"105","buyerId":42}}"""
        try { f.repository.readOrder(105); fail("String backend IDs must remain invalid") }
        catch (_: JsonParseException) { }
    }

    @Test fun foreignPurchaserAndInvalidIdentifiersNeverReachTheTransport() = runBlocking {
        val f=Fixture()
        val invalid:List<suspend ()->Unit> = listOf(
            {f.repository.readCart(99);Unit},{f.repository.listOrders(99);Unit},
            {f.repository.addItem(0,1);Unit},{f.repository.addItem(7,0);Unit},
            {f.repository.setQuantity(0,7,1);Unit},{f.repository.readOrder(0);Unit})
        invalid.forEach { operation -> try {operation();fail("Invalid operation reached transport")} catch (_:IllegalArgumentException) {} catch (_:IllegalStateException) {} }
        assertTrue(f.requests.isEmpty())
    }

    @Test fun changedSessionsAndNonBuyerRolesCannotUseAnyBuyerRepositoryOperation() = runBlocking {
        for(kind in listOf("account","origin","generation","contract","demo","logout","signedOut","FARMER","SUPPLIER","INSPECTOR")) {
            val f=Fixture();val auth=f.expected.auth!!
            f.state.value=when(kind){
                "account"->f.expected.copy(auth=auth.copy(userId="99"))
                "origin"->f.expected.copy(auth=auth.copy(sessionOrigin="https://other.example.test/api/v1/"))
                "generation"->f.expected.copy(generation=f.expected.generation+1)
                "contract"->f.expected.copy(auth=auth.copy(backendContract="legacy"))
                "demo"->f.expected.copy(isDemo=true)
                "logout"->f.expected.copy(loggingOut=true)
                "signedOut"->f.expected.copy(auth=null)
                else->f.expected.copy(auth=auth.copy(role=kind))
            }
            for(operation in f.operations())try {operation();fail("Session $kind was accepted")} catch (_:IllegalStateException) {}
            assertTrue("Session $kind called transport",f.requests.isEmpty())
        }
    }

    @Test fun logoutWhileAWriteIsInFlightRejectsItsResponseAndNeverReplaysIt() = runBlocking {
        val f=Fixture();f.body="""{"success":true,"data":105}"""
        f.beforeResponse={f.state.value=f.expected.copy(loggingOut=true)}
        repeat(2){try{f.repository.createOrder("q1","254712345678");fail("Logout response accepted")}catch(_:IllegalStateException){}}
        assertEquals(1,f.requests.size)
    }

    @Test fun tokenRotationWithinTheSameSessionStillAllowsTheRead() = runBlocking {
        val f=Fixture()
        f.beforeResponse={f.state.value=f.expected.copy(auth=f.expected.auth!!.copy(accessToken="rotated-synthetic-token"))}
        assertEquals(10L,f.repository.readCart(42).body()!!.data!!.get("id").asLong)
        f.beforeResponse={}
        f.repository.readCart(42)
        assertEquals("Bearer rotated-synthetic-token",f.requests.last().header("Authorization"))
    }

    @Test fun uncertainNetworkWriteIsAttemptedOnlyOnce() = runBlocking {
        val f=Fixture();f.interrupt=true
        try{f.repository.addItem(7,3);fail("Expected timeout")}catch(_:SocketTimeoutException){}
        assertEquals(1,f.requests.size)
    }
    @Test fun freshSubtotalUsesExactAuthenticatedProductReadsOnly() = runBlocking {
        val f=Fixture()
        f.body="""{"success":true,"data":{"id":7,"sellerId":9,"sellerType":"farmer","categoryId":2,"name":"Synthetic produce","unit":"bag","price":0.07,"stockQty":12,"reservedQty":0,"soldQty":0,"minimumOrderQty":1,"status":"active","images":[]}}"""
        assertEquals(java.math.BigDecimal("0.21"),f.pricedRepository().refreshedSubtotal(mapOf(7L to 3)))
        assertEquals(listOf("GET"),f.requests.map { it.method })
        assertEquals("/api/v1/products/7",f.requests.single().url.encodedPath)
        assertEquals(f.expected,f.requests.single().tag(SessionSnapshot::class.java))
    }
    @Test fun buyerUsesDocumentedCheckoutAndDeliveryQuoteRequestShapes() = runBlocking {
        val f=Fixture()
        f.body="""{"success":true}"""
        assertTrue(f.repository.requiresCartIdentity)
        f.repository.startCheckout()
        f.repository.deliveryQuote(f.quoteRequest.copy(cartId=10))
        assertNull(f.requests[0].url.encodedQuery)
        assertEquals("/api/v1/checkout",f.requests[0].url.encodedPath)
        assertEquals(JsonParser.parseString("""{"deliveryMethod":"DOOR_DELIVERY","addressId":8}"""),f.requestJson(1))
        f.requests.forEach { assertEquals(f.expected,it.tag(SessionSnapshot::class.java)) }
    }

}
