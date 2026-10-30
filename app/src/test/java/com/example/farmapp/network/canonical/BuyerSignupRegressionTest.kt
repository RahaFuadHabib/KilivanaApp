package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.*
import com.example.farmapp.repository.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic Retrofit responses only. No real registration, login or session is used. */
class BuyerSignupRegressionTest {
    private class Clock : java.time.Clock() {
        var now = Instant.parse("2026-10-29T12:00:00Z")
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId) = this
    }
    private class Backend {
        var code = 409; var body = """{"success":false,"error":{"code":"CONFLICT"}}"""
        var retry: String? = null; var offline = false; var gate: CountDownLatch? = null
        val entered = CountDownLatch(1); val calls = CopyOnWriteArrayList<String>()
        val clock = Clock()
        val api = Retrofit.Builder().baseUrl("https://synthetic.example.test/api/v1/")
            .addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).addInterceptor { chain ->
                calls += chain.request().url.encodedPath
                entered.countDown(); gate?.let { check(it.await(5,TimeUnit.SECONDS)) }
                if (offline) throw java.net.SocketTimeoutException("private-body-must-not-escape")
                okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Synthetic")
                    .apply { retry?.let { header("Retry-After",it) } }.body(body.toResponseBody()).build()
            }.build()).build().create(CanonicalBackendApi::class.java)
        val repo = AuthRepository(CanonicalAuthService(api, "https://synthetic.example.test/api/v1/"),
            SessionState().apply { initialize(MemoryPersistence()) }, signupClock = clock)
    }
    private suspend fun reject(block: suspend () -> Unit): AuthFailure {
        try { block() } catch (e: AuthFailure) { return e }
        error("Expected safe rejection")
    }
    @Test fun conflictReasonIsSpecificOnlyWhenBackendIdentifiesIt() = runBlocking {
        for ((message, fragment) in listOf("Email already exists, please log in instead" to "this email already exists",
            "Phone number already exists, please log in instead" to "phone number is already in use")) {
            val b = Backend().apply { body = """{"success":false,"message":"$message","error":{"code":"CONFLICT"}}""" }
            val failure = reject { b.repo.signup(signupInput()) }
            assertTrue(failure.message!!.contains(fragment)); assertEquals(409, failure.diagnostic!!.httpStatus)
            assertEquals(listOf("/api/v1/auth/register"), b.calls.toList())
        }
        val b = Backend(); val message = reject { b.repo.signup(signupInput()) }.message!!
        assertTrue(message.contains("conflicting details")); assertFalse(message.contains("already exists"))
    }
    @Test fun retryAfterSurvivesServiceMappingAndSuppressesRequestsUntilDeadline() = runBlocking {
        val b = Backend().apply { code = 429; retry = "60" }
        assertTrue(reject { b.repo.signup(signupInput()) }.message!!.contains("60 seconds"))
        b.clock.now = b.clock.now.plusSeconds(10)
        assertTrue(reject { b.repo.signup(signupInput()) }.message!!.contains("50 seconds"))
        assertEquals(1,b.calls.size)
        b.clock.now = b.clock.now.plusSeconds(50)
        reject { b.repo.signup(signupInput()) }; assertEquals(2,b.calls.size)
    }
    @Test fun retryAfterDatesAndMalformedHeadersAreHandledWithoutLeakingValues() {
        val now = Instant.parse("2026-10-29T12:00:00Z")
        assertEquals(now.plusSeconds(60),signupRetryAfter("Thu, 29 Oct 2026 12:01:00 GMT",now))
        for (value in listOf("-1","not-a-date","99999999999999999999999999999999999","Thu, 29 Oct 2026 11:00:00 GMT")) assertNull(signupRetryAfter(value,now))
    }
    @Test fun validationRateLimitAndServerErrorsStayDistinctAndNoAutomaticRetries() = runBlocking {
        for (code in listOf(400,422,429,500,503)) {
            val b = Backend().apply { this.code = code; body = """{"success":false,"message":"private-body-must-not-escape","data":{"email":"private-email"},"error":{"code":"VALIDATION_ERROR"}}""" }
            val message = reject { b.repo.signup(signupInput()) }.message!!
            assertFalse(message.contains("private")); assertFalse(message.contains("already exists")); assertEquals(1,b.calls.size)
            when(code) { 400,422 -> assertTrue(message.contains("email")); 429 -> assertTrue(message.contains("rate limited")); else -> assertTrue(message.contains("could not be confirmed")) }
        }
    }
    @Test fun networkTimeoutNeverSuggestsDefiniteDuplicateAndDoesNotReplay() = runBlocking {
        val b = Backend().apply { offline = true }
        val message = reject { b.repo.signup(signupInput()) }.message!!
        assertTrue(message.contains("may have reached")); assertFalse(message.contains("private")); assertEquals(1,b.calls.size)
    }
    @Test fun simultaneousIntentionalSubmissionsReachRegisterOnlyOnce() = runBlocking {
        val b = Backend().apply { gate = CountDownLatch(1) }
        val first = async(Dispatchers.Default) { reject { b.repo.signup(signupInput()) } }
        try {
            assertTrue(b.entered.await(5,TimeUnit.SECONDS))
            assertTrue(reject { b.repo.signup(signupInput()) }.message!!.contains("already in progress"))
            assertEquals(1,b.calls.size)
        } finally { b.gate!!.countDown() }
        first.await(); assertEquals(1,b.calls.size)
    }
    @Test fun normalizationPreservesEmailPhoneAndRoleWithoutInventingIdentity() {
        val prepared = SignupValidation.prepare(signupInput().copy(email = " Buyer@Example.test ", phoneNumber = " 0712345678 ", username = " Buyer_Code "))
        assertEquals("Buyer@Example.test",prepared.email); assertEquals("0712345678",prepared.phoneNumber)
        assertEquals("buyer_code",prepared.username); assertEquals("BUYER",prepared.role)
        assertTrue(runCatching { SignupValidation.prepare(signupInput().copy(fullName = "X")) }.isFailure)
    }
}
