package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.*
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.*
import com.example.farmapp.repository.*
import com.example.farmapp.ui.screens.auth.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy
import java.util.UUID
import java.net.UnknownHostException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

class BuyerAuthenticationPhaseOneTest {
    private val challenge = UUID.fromString("00000000-0000-4000-8000-000000000042")
    private fun fullUser() = CanonicalUserDto(42,"Synthetic Buyer","buyer@example.test","0712345678",role="BUYER",status="ACTIVE",verificationStatus="NOT_REQUIRED",emailVerified=true)
    @Test fun minimalRegisteredBuyerReusesOtpThenSignsInExactlyOnceWithoutProfileCreation() = runBlocking {
        val calls = mutableListOf<String>()
        val api = Proxy.newProxyInstance(javaClass.classLoader,arrayOf(CanonicalBackendApi::class.java)) { _,m,_ ->
            calls += m.name
            when(m.name) {
                "register" -> Response.success(CanonicalApiResponse(true,data=CanonicalUserDto(id=42,email="buyer@example.test",role="BUYER",emailVerified=false,emailVerificationChallengeId=challenge)))
                "login" -> Response.success(CanonicalApiResponse(true,data=CanonicalAuthTokenDto("synthetic-access","synthetic-refresh","Bearer",3600,fullUser())))
                else -> error("No Buyer profile or other request is allowed")
            }
        } as CanonicalBackendApi
        val disk = MemoryPersistence(); val sessions = SessionState().apply { initialize(disk) }
        val repo = AuthRepository(CanonicalAuthService(api,"https://synthetic.example.test/api/v1/"),sessions)
        val request = signupInput().copy(email="buyer@example.test")
        val pending = try { repo.signup(request); error("Expected OTP") } catch(e: EmailVerificationRequired) { e.pending }
        assertEquals(42L,pending.registeredUserId); assertEquals(challenge,pending.challengeId)
        assertNull(sessions.session.value.auth); assertNull(disk.saved)
        try { repo.signup(request); fail("Cannot register the same pending account again") } catch(_: AuthFailure) {}
        var verifies=0
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val otp = object : EmailVerificationApi {
                override suspend fun request(request: EmailOtpRequest): Response<CanonicalApiResponse<EmailOtpChallenge>> = error("Do not request a second challenge")
                override suspend fun verify(request: EmailOtpVerifyRequest): Response<CanonicalApiResponse<Unit>> {
                    verifies++; assertEquals(challenge,request.challengeId); return Response.success(CanonicalApiResponse(true))
                }
            }
            val c=EmailVerificationController(otp,scope) { 1000L }
            c.begin(pending); c.verify("123456"); assertTrue(c.state.value.verified)
            repo.completeVerifiedSignup(request,42)
            assertEquals(1,verifies); assertEquals(listOf("register","login"),calls)
            assertEquals("BUYER",sessions.session.value.auth!!.role); assertNotNull(disk.saved)
        } finally { scope.cancel() }
    }
    @Test fun mismatchedRegisteredBuyerIdentityNeverRequestsOtpOrSignsIn() = runBlocking {
        val api = Proxy.newProxyInstance(javaClass.classLoader,arrayOf(CanonicalBackendApi::class.java)) { _,m,_ ->
            check(m.name=="register")
            Response.success(CanonicalApiResponse(true,data=CanonicalUserDto(id=42,email="foreign@example.test",role="BUYER",emailVerified=false,emailVerificationChallengeId=challenge)))
        } as CanonicalBackendApi
        try { CanonicalAuthService(api,"https://synthetic.example.test/api/v1/").signup(signupInput()); fail() }
        catch(e: AuthFailure) { assertEquals(AuthFailureKind.CONTRACT,e.diagnostic!!.kind) }
    }
    @Test fun gatewayHeadersAreOriginScopedAndNeverCauseAReplay() {
        val origin = "https://synthetic.ngrok-free.dev/api/v1/".toHttpUrl()
        val captured=mutableListOf<Request>()
        val client=OkHttpClient.Builder().addInterceptor(AuthGatewayHeadersInterceptor(origin)).addInterceptor { chain ->
            captured+=chain.request(); okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic").body("{}".toResponseBody()).build()
        }.build()
        for(url in listOf(origin.toString()+"auth/login","https://other.example.test/api/v1/auth/login")) client.newCall(Request.Builder().url(url).build()).execute().close()
        assertEquals("1",captured[0].header("ngrok-skip-browser-warning")); assertEquals("application/json",captured[0].header("Accept"))
        assertNull(captured[1].header("ngrok-skip-browser-warning")); assertEquals(2,captured.size)
    }
    @Test fun traceRetainsOnlyTypedNetworkCategoriesAndDoesNotRetry() {
        for((error,kind) in listOf(UnknownHostException("private-email") to AuthNetworkCategory.DNS,
            SSLException("private-token") to AuthNetworkCategory.TLS, ConnectException("private-phone") to AuthNetworkCategory.CONNECT,
            SocketTimeoutException("private-password") to AuthNetworkCategory.TIMEOUT)) {
            val events=mutableListOf<AuthAttemptEvent>(); var calls=0
            val client=OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(SafeAuthTraceInterceptor { events+=it }).addInterceptor { calls++; throw error }.build()
            try { client.newCall(Request.Builder().url("https://synthetic.example.test/api/v1/auth/login").build()).execute(); fail() } catch(_: java.io.IOException) {}
            assertEquals(1,calls); assertEquals(kind,events.last().network)
            assertFalse(events.toString().contains("private")); assertEquals(2,events.size)
        }
    }
    @Test fun htmlResponseIsObservableWithoutRecordingBodyOrHeaders() {
        val events=mutableListOf<AuthAttemptEvent>()
        val client=OkHttpClient.Builder().addInterceptor(SafeAuthTraceInterceptor { events+=it }).addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic")
                .header("Content-Type","text/html").header("Set-Cookie","private-cookie").body("private-response".toResponseBody()).build()
        }.build()
        client.newCall(Request.Builder().url("https://synthetic.example.test/api/v1/auth/register").build()).execute().close()
        assertEquals(AuthResponseFormat.HTML,events.last().format); assertEquals(200,events.last().httpStatus)
        assertFalse(events.toString().contains("private"))
    }
    @Test fun otpHttpErrorsKeepTheirMeaningAnd429StopsVerifyAndResendUntilDeadline() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined);var now=1000L; var calls=0
        val wall=java.time.Clock.fixed(java.time.Instant.parse("2026-10-29T12:00:00Z"),java.time.ZoneOffset.UTC)
        var code=422
        val api=object: EmailVerificationApi {
            override suspend fun request(request: EmailOtpRequest): Response<CanonicalApiResponse<EmailOtpChallenge>> = error("No request before cooldown")
            override suspend fun verify(request: EmailOtpVerifyRequest): Response<CanonicalApiResponse<Unit>> {
                calls++
                val raw=okhttp3.Response.Builder().request(Request.Builder().url("https://synthetic.example.test/").build()).protocol(Protocol.HTTP_1_1).code(code).message("Synthetic").header("Retry-After","Thu, 29 Oct 2026 12:02:00 GMT").build()
                return Response.error("private-error".toResponseBody(),raw)
            }
        }
        try {
            val c=EmailVerificationController(api,scope,{now},wall);c.begin(EmailVerificationPending("buyer@example.test",challenge))
            c.verify("123456");assertTrue(c.state.value.error!!.contains("invalid or expired"));assertFalse(c.state.value.verified)
            code=429;c.verify("123456");assertEquals(121000L,c.state.value.retryAt)
            repeat(3){c.verify("123456");c.request()};assertEquals(2,calls)
            now=121000;c.verify("123456");assertEquals(3,calls)
        }finally{scope.cancel()}
    }
}
