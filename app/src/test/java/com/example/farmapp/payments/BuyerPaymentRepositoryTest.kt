package com.example.farmapp.payments

import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalJson
import com.example.farmapp.orders.*
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

class BuyerPaymentRepositoryTest {
    private class Fixture {
        val origin = "https://payment.example.test/api/v1/"
        private val delegate = signedInStore(supplierAuth().copy(role = "BUYER", sessionOrigin = origin))
        val session = MutableStateFlow(delegate.session.value)
        val sessions = object : SessionStore by delegate { override val session = this@Fixture.session }
        val expected = session.value
        var body = """{"success":true,"data":null}"""
        var status = 200; var timeout = false; var allowed = true
        var hook: () -> Unit = {}
        val requests = mutableListOf<Request>()
        val api = Retrofit.Builder().baseUrl(origin).addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false)
                .addInterceptor(AuthTokenInterceptor(origin.toHttpUrl(), sessions, RefreshCoordinator(StubAuthService(), sessions)))
                .addInterceptor { chain ->
                    requests += chain.request(); hook()
                    if (timeout) throw SocketTimeoutException("Synthetic timeout")
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Synthetic")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                }.build()).build().create(BuyerOrdersApi::class.java)
        val repository = BuyerPaymentRepository(api, sessions, expected) { allowed }
        fun operations(): List<suspend () -> Unit> = listOf({ repository.order(10); Unit }, { repository.payments(10); Unit },
            { repository.payment(9); Unit }, { repository.initiate(10, "254712345678"); Unit })
    }
    private suspend fun rejects(action: suspend () -> Unit) {
        try { action(); fail("Invalid session or request accepted") } catch (_: IllegalStateException) { } catch (_: IllegalArgumentException) { }
    }
    @Test fun existingBuyerMethodsPathsBodyAuthenticationAndSessionTagsArePreserved() = runBlocking {
        val f = Fixture(); f.operations().forEach { it() }
        assertEquals(listOf("GET", "GET", "GET", "POST"), f.requests.map { it.method })
        assertEquals(listOf("orders/10", "payments/order/10", "payments/9/status", "payments/stk-push/buyer"), f.requests.map { it.url.encodedPath.removePrefix("/api/v1/") })
        val json = JsonParser.parseString(Buffer().also { f.requests.last().body!!.writeTo(it) }.readUtf8()).asJsonObject
        assertEquals(setOf("orderId", "phoneNumber"), json.keySet()); assertEquals(10L, json["orderId"].asLong)
        assertEquals("254712345678", json["phoneNumber"].asString)
        f.requests.forEach { assertEquals(f.expected, it.tag(SessionSnapshot::class.java)); assertEquals("Bearer ${f.expected.auth!!.accessToken}", it.header("Authorization")) }
    }
    @Test fun staleSessionsAndOtherRolesCannotUseBuyerPaymentEndpoints() = runBlocking {
        for (kind in listOf("account", "origin", "generation", "contract", "demo", "logout", "signedOut", "FARMER", "SUPPLIER", "INSPECTOR")) {
            val f = Fixture(); val auth = f.expected.auth!!
            f.session.value = when (kind) {
                "account" -> f.expected.copy(auth = auth.copy(userId = "99"))
                "origin" -> f.expected.copy(auth = auth.copy(sessionOrigin = "https://other.test/"))
                "generation" -> f.expected.copy(generation = f.expected.generation + 1)
                "contract" -> f.expected.copy(auth = auth.copy(backendContract = "legacy"))
                "demo" -> f.expected.copy(isDemo = true)
                "logout" -> f.expected.copy(loggingOut = true)
                "signedOut" -> f.expected.copy(auth = null)
                else -> f.expected.copy(auth = auth.copy(role = kind))
            }
            f.operations().forEach { rejects(it) }; assertTrue("Transport called for $kind", f.requests.isEmpty())
        }
    }
    @Test fun lateResponsesAfterLogoutAreRejectedWithoutReplayingTheRequest() = runBlocking {
        for (index in 0..3) {
            val f = Fixture(); f.hook = { f.session.value = f.expected.copy(loggingOut = true) }
            rejects(f.operations()[index]); assertEquals(1, f.requests.size)
        }
    }
    @Test fun environmentAndInvalidIdsOrUnnormalizedPhonesFailBeforeTransport() = runBlocking {
        val f = Fixture(); f.allowed = false; rejects { f.repository.initiate(10, "254712345678") }
        f.allowed = true
        for (operation in listOf<suspend () -> Unit>({ f.repository.order(0); Unit }, { f.repository.payments(0); Unit },
            { f.repository.payment(0); Unit }, { f.repository.initiate(0, "254712345678"); Unit }, { f.repository.initiate(10, "0712345678"); Unit })) rejects(operation)
        assertTrue(f.requests.isEmpty())
    }
    @Test fun timeoutsAndHttpErrorsAreNotRetriedOrReinterpreted() = runBlocking {
        val f = Fixture(); f.timeout = true
        try { f.repository.initiate(10, "254712345678"); fail("Expected timeout") } catch (_: SocketTimeoutException) { }
        assertEquals(1, f.requests.size)
        f.timeout = false; f.status = 409; f.body = """{"error":{"code":"PAYMENT_EXISTS"}}"""
        val response = f.repository.initiate(10, "254712345678")
        assertEquals(409, response.code()); assertEquals(f.body, response.errorBody()!!.string()); assertEquals(2, f.requests.size)
    }
    @Test fun canonicalNumericAdapterStillRejectsStringFractionalAndOverflowPaymentIds() = runBlocking {
        for (id in listOf("\"9\"", "9.5", "9223372036854775808")) {
            val f = Fixture(); f.body = """{"success":true,"data":{"paymentId":$id,"orderId":10,"amount":125.57}}"""
            try { f.repository.initiate(10, "254712345678"); fail("Malformed ID accepted") } catch (_: JsonParseException) { }
        }
    }
}
