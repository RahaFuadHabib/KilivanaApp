package com.example.farmapp.buyer

import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.supplier.supplierAuth
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType

class BuyerCheckoutTest {
    @Test fun metadataCannotAuthorizeOrderCreationAndForeignCartsAreRejected() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var owner = 42L; var validates = 0
            val api = object : BuyerCheckoutApi {
                override suspend fun quote(request: BuyerDeliveryQuoteRequest, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerDeliveryQuote>> = error("No quote for an unmatched cart")
                override suspend fun buyerCart(buyerId: Long, session: SessionSnapshot): Response<CanonicalApiResponse<com.google.gson.JsonObject>> {
                    assertEquals(42L, buyerId)
                    return Response.success(CanonicalApiResponse(true, data = com.google.gson.JsonObject().apply {
                        addProperty("id", 1); addProperty("buyerId", owner); addProperty("status", "ACTIVE")
                    }))
                }
                override suspend fun validate(session: SessionSnapshot): Response<CanonicalApiResponse<BuyerCartMetadata>> {
                    validates++; error("Metadata cannot prove local/server cart equality")
                }
            }
            val sessions = signedInStore(supplierAuth().copy(role = "BUYER"))
            val c = BuyerCheckoutReviewController(api, sessions, sessions.session.value, scope)
            c.check(); assertTrue(c.state.value.message!!.contains("only cart metadata")); assertEquals(0, validates)
            owner = 99; c.check(); assertTrue(c.state.value.message!!.startsWith("Server cart could not be verified"))
            assertFalse(c.state.value.busy)
        } finally { scope.cancel() }
    }
    @Test fun buyerCartInspectionRetainsNestedUndocumentedShapeWithoutGuessingItsMeaning() {
        val json = com.google.gson.JsonParser.parseString("""{"id":1,"buyerId":42,"status":"ACTIVE","syntheticDetails":{"lines":[{"productId":7,"quantity":2}]}}""").asJsonObject
        val evidence = buyerCartEvidence(json, 42)
        assertTrue(evidence.hasUndocumentedProperties)
        assertTrue("syntheticDetails.lines[].productId" in evidence.propertyPaths)
        assertTrue("syntheticDetails.lines[].quantity" in evidence.propertyPaths)
        assertFalse(evidence.propertyPaths.any { it.contains("42") })
    }
    @Test fun foreignOrFractionalIdentifiersCannotAuthorizeCartUse() {
        for (raw in listOf("""{"id":1,"buyerId":99,"status":"ACTIVE"}""",
            """{"id":1,"buyerId":42.5,"status":"ACTIVE"}""", """{"id":0,"buyerId":42,"status":"ACTIVE"}"""))
            assertTrue(runCatching { buyerCartEvidence(com.google.gson.JsonParser.parseString(raw).asJsonObject, 42) }.isFailure)
    }
    @Test fun buyerSpecificReadMapsExactPathAndPreservesAdditionalResponseFields() = runBlocking {
        var request: okhttp3.Request? = null
        val body = """{"success":true,"data":{"id":1,"buyerId":42,"status":"ACTIVE","syntheticDetails":{"lines":[]}}}"""
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            request = chain.request()
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = retrofit2.Retrofit.Builder().baseUrl("https://synthetic.example.test/api/v1/").client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create(com.example.farmapp.network.canonical.CanonicalJson.gson))
            .build().create(BuyerCheckoutApi::class.java)
        val sessions = signedInStore(supplierAuth().copy(role = "BUYER"))
        val response = api.buyerCart(42, sessions.session.value)
        assertEquals("GET", request!!.method)
        assertEquals("/api/v1/carts/buyer/42", request!!.url.encodedPath)
        assertTrue(response.body()!!.requireData().has("syntheticDetails"))
    }

}
