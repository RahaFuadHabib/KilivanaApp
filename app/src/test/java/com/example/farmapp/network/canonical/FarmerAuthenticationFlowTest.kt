package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.*
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.*
import com.example.farmapp.repository.*
import com.example.farmapp.ui.screens.auth.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.Proxy
import java.net.SocketTimeoutException
import java.util.UUID

class FarmerAuthenticationFlowTest {
    private val origin = "https://auth.example.test/api/v1/"
    private val challenge = UUID.fromString("00000000-0000-4000-8000-000000000042")
    private fun user(role: String = "FARMER", verified: Boolean? = true) = CanonicalUserDto(42, "Synthetic Person", "person@example.test", "+254700000000",
        role = role, status = "ACTIVE", verificationStatus = "NOT_REQUIRED", emailVerified = verified)
    private fun tokens(role: String = "FARMER") = CanonicalAuthTokenDto("synthetic-access", "synthetic-refresh", "Bearer", 3600, user(role))
    private fun api(handler: (String, Array<out Any?>) -> Any?): CanonicalBackendApi = Proxy.newProxyInstance(javaClass.classLoader,
        arrayOf(CanonicalBackendApi::class.java)) { _, method, args -> handler(method.name, args ?: emptyArray()) } as CanonicalBackendApi
    private suspend fun failure(action: suspend () -> Unit): AuthFailure {
        try { action(); fail("Expected auth failure") } catch (e: AuthFailure) { return e }; error("Unreachable")
    }
    private fun raw(body: ResponseBody, status: Int = 200, before: (() -> Unit)? = null): CanonicalLoginTransport = Retrofit.Builder()
        .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create(CanonicalJson.gson))
        .baseUrl(origin).client(OkHttpClient.Builder().addInterceptor(AuthLoginDiagnosticInterceptor()).addInterceptor { chain ->
            before?.invoke()
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Synthetic").body(body).build()
        }.build()).build().create(CanonicalLoginTransport::class.java)
    private fun service(transport: CanonicalLoginTransport) = CanonicalAuthService(api { _, _ -> error("Typed login must not be used") }, origin, transport)
    private fun json(dto: CanonicalAuthTokenDto) = CanonicalJson.gson.toJson(CanonicalApiResponse(true, data = dto)).toResponseBody("application/json".toMediaType())
    @Test fun farmerLoginSavesSessionWithoutAnyFarmerProfileRequest() = runBlocking {
        val calls = mutableListOf<String>(); val disk = MemoryPersistence(); val store = SessionState().apply { initialize(disk) }
        val service = CanonicalAuthService(api { method, _ -> calls += method; check(method == "login"); Response.success(CanonicalApiResponse(true, data = tokens())) }, origin)
        AuthRepository(service, store).login("person@example.test", "synthetic-password")
        assertEquals(listOf("login"), calls); assertEquals("FARMER", store.session.value.auth!!.role); assertEquals(store.session.value.auth, disk.saved)
    }
    @Test fun streamedLoginPreservesFarmerIdentityAndOrigin() = runBlocking {
        val auth = service(raw(json(tokens()))).login(LoginRequest("person@example.test", "synthetic-password")).body()!!
        assertEquals("FARMER", auth.role); assertEquals(origin, auth.sessionOrigin)
    }
    @Test fun requestAndBodyTimeoutsHaveDifferentPrivateDiagnostics() = runBlocking {
        val request = failure { service(raw("{}".toResponseBody(), before = { throw SocketTimeoutException("private-password") })).login(LoginRequest("person@example.test", "pw")) }
        assertEquals(AuthStage.LOGIN_REQUEST, request.diagnostic!!.stage); assertEquals(AuthFailureKind.TIMEOUT, request.diagnostic.kind)
        val broken = object : ResponseBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = 100L
            override fun source(): BufferedSource = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long = throw SocketTimeoutException("private-token")
                override fun timeout() = Timeout.NONE
                override fun close() {}
            }.buffer()
        }
        val read = failure { service(raw(broken)).login(LoginRequest("person@example.test", "pw")) }
        assertEquals(AuthStage.RESPONSE_READ, read.diagnostic!!.stage); assertEquals(200, read.diagnostic.httpStatus)
        assertEquals(AuthFailureKind.TIMEOUT, read.diagnostic.kind)
        assertFalse(request.message!!.contains("private")); assertFalse(read.message!!.contains("private"))
    }
    @Test fun httpJsonRoleAndTokenFailuresHaveDistinctStages() = runBlocking {
        val http = failure { service(raw("<html>private-token</html>".toResponseBody(), 502)).login(LoginRequest("person@example.test", "pw")) }
        assertEquals(AuthFailureKind.HTTP, http.diagnostic!!.kind); assertEquals(502, http.diagnostic.httpStatus)
        val malformed = failure { service(raw("not-json-private-token".toResponseBody())).login(LoginRequest("person@example.test", "pw")) }
        assertEquals(AuthStage.JSON_DECODE, malformed.diagnostic!!.stage); assertEquals(200, malformed.diagnostic.httpStatus)
        val role = failure { service(raw(json(tokens().copy(user = user().copy(role = "UNKNOWN"))))).login(LoginRequest("person@example.test", "pw")) }
        assertEquals(AuthStage.ROLE_VALIDATION, role.diagnostic!!.stage)
        val token = failure { service(raw(json(tokens().copy(accessToken = null)))).login(LoginRequest("person@example.test", "pw")) }
        assertEquals(AuthStage.TOKEN_VALIDATION, token.diagnostic!!.stage)
        for (value in listOf(http, malformed, role, token)) assertFalse(value.message!!.contains("private-token"))
    }
    @Test fun sessionStorageFailureIsNotNetworkFailure() = runBlocking {
        val disk = MemoryPersistence().apply { failWrite = true }; val store = SessionState().apply { initialize(disk) }
        val failure = failure { AuthRepository(service(raw(json(tokens()))), store).login("person@example.test", "pw") }
        assertEquals(AuthStage.SESSION_SAVE, failure.diagnostic!!.stage); assertEquals(AuthFailureKind.STORAGE, failure.diagnostic.kind); assertNull(store.session.value.auth)
    }
    @Test fun verificationFlagIsHandledBeforeMissingTokensForEveryRole() = runBlocking {
        for (role in listOf("FARMER", "SUPPLIER", "BUYER", "DRIVER", "INSPECTOR", "ADMIN")) {
            val dto = CanonicalAuthTokenDto(user = user(role, false), emailVerificationRequired = true, emailVerificationChallengeId = challenge)
            val result = failure { service(raw(json(dto))).login(LoginRequest("person@example.test", "pw")) }
            assertTrue(result is EmailVerificationRequired); assertEquals(challenge, (result as EmailVerificationRequired).pending.challengeId)
        }
    }
    @Test fun verifiedRolesAndUnknownForbiddenDoNotTriggerOtp() = runBlocking {
        for (role in listOf("FARMER", "SUPPLIER", "BUYER", "DRIVER", "INSPECTOR", "ADMIN"))
            assertEquals(role, service(raw(json(tokens(role)))).login(LoginRequest("person@example.test", "pw")).body()!!.role)
        val result = failure { service(raw("""{"success":false,"message":"private text","error":{"code":"FORBIDDEN"}}""".toResponseBody(), 403)).login(LoginRequest("person@example.test", "pw")) }
        assertFalse(result is EmailVerificationRequired)
    }
    @Test fun exactVerifiedForbiddenContractTriggersOtp() = runBlocking {
        val result = failure { service(raw("""{"success":false,"message":"Email verification is required before login","error":{"code":"FORBIDDEN"}}""".toResponseBody(), 403)).login(LoginRequest("person@example.test", "pw")) }
        assertTrue(result is EmailVerificationRequired); assertNull((result as EmailVerificationRequired).pending.challengeId)
    }
    private fun signup(role: String) = SignupRequest("person@example.test", "synthetic-password", "Synthetic Person", "+254700000000", role,
        "synthetic_person", "Kiambu", farmName = "Synthetic farm", businessName = "Synthetic shop", businessLocation = "Synthetic location")
    @Test fun registrationResumesAfterChallengeWithoutRegisteringOrLoggingInTwice() = runBlocking {
        for (role in listOf("FARMER", "SUPPLIER", "BUYER")) {
            val calls = mutableListOf<String>(); var created = false
            val api = api { method, _ -> calls += method; when(method) {
                "register" -> Response.success(CanonicalApiResponse(true, data = user(role, false).copy(emailVerificationChallengeId = challenge)))
                "login" -> Response.success(CanonicalApiResponse(true, data = tokens(role)))
                "farmerProfile" -> if (created) Response.success(CanonicalApiResponse(true, data = CanonicalCreatedProfileDto(42))) else Response.error<CanonicalApiResponse<CanonicalCreatedProfileDto>>(404, "{}".toResponseBody())
                "supplierProfile" -> if (created) Response.success(CanonicalApiResponse(true, data = CanonicalCreatedSupplierProfileDto(5, 42))) else Response.error<CanonicalApiResponse<CanonicalCreatedSupplierProfileDto>>(404, "{}".toResponseBody())
                "createFarmerProfile" -> { created = true; Response.success(CanonicalApiResponse(true, data = CanonicalCreatedProfileDto(42))) }
                "createSupplierProfile" -> { created = true; Response.success(CanonicalApiResponse(true, data = CanonicalCreatedSupplierProfileDto(5, 42))) }
                else -> error("Unexpected request: $method")
            } }
            val store = SessionState().apply { initialize(MemoryPersistence()) }; val repo = AuthRepository(CanonicalAuthService(api, origin), store)
            val result = failure { repo.signup(signup(role)) } as EmailVerificationRequired
            assertEquals(listOf("register"), calls); assertNull(store.session.value.auth); assertEquals(challenge, result.pending.challengeId)
            failure { repo.signup(signup(role)) }; assertEquals(listOf("register"), calls)
            repo.completeVerifiedSignup(signup(role), result.pending.registeredUserId!!)
            assertEquals(role, store.session.value.auth!!.role); assertEquals(1, calls.count { it == "register" }); assertEquals(1, calls.count { it == "login" })
            assertEquals(if (role == "BUYER") 0 else 1, calls.count { it.startsWith("create") })
        }
    }
    private inner class OtpApi : EmailVerificationApi {
        var requests = 0; var verifications = 0; var success = true; var hold: CompletableDeferred<Unit>? = null
        override suspend fun request(request: EmailOtpRequest): Response<CanonicalApiResponse<EmailOtpChallenge>> {
            requests++; hold?.await(); return Response.success(CanonicalApiResponse(true, data = EmailOtpChallenge(challenge, 600, 60)))
        }
        override suspend fun verify(request: EmailOtpVerifyRequest): Response<CanonicalApiResponse<Unit>> { verifications++; return Response.success(CanonicalApiResponse(success)) }
    }
    @Test fun laterManualLoginCompletesKnownFarmerRegistrationWithoutAnotherAccount() = runBlocking {
        val calls = mutableListOf<String>()
        val api = api { method, _ -> calls += method; when (method) {
            "register" -> Response.success(CanonicalApiResponse(true, data = user(verified = false).copy(emailVerificationChallengeId = challenge)))
            "login" -> Response.success(CanonicalApiResponse(true, data = tokens()))
            "farmerProfile" -> Response.error<CanonicalApiResponse<CanonicalCreatedProfileDto>>(404, "{}".toResponseBody())
            "createFarmerProfile" -> Response.success(CanonicalApiResponse(true, data = CanonicalCreatedProfileDto(42)))
            else -> error("Unexpected request")
        } }
        val store = SessionState().apply { initialize(MemoryPersistence()) }; val repo = AuthRepository(CanonicalAuthService(api, origin), store)
        failure { repo.signup(signup("FARMER").copy(email = " person@example.test ")) }
        repo.login("person@example.test", "synthetic-password")
        assertEquals(listOf("register", "login", "farmerProfile", "createFarmerProfile"), calls)
        assertEquals("FARMER", store.session.value.auth!!.role)
    }
    @Test fun deniedPostVerificationProfileReadReportsProfileStageAndNeverCreatesProfile() = runBlocking {
        val calls = mutableListOf<String>()
        val api = api { method, _ -> calls += method; when (method) {
            "register" -> Response.success(CanonicalApiResponse(true, data = user(verified = false).copy(emailVerificationChallengeId = challenge)))
            "login" -> Response.success(CanonicalApiResponse(true, data = tokens()))
            "farmerProfile" -> Response.error<CanonicalApiResponse<CanonicalCreatedProfileDto>>(503, "private-body".toResponseBody())
            else -> error("No profile write is allowed")
        } }
        val store = SessionState().apply { initialize(MemoryPersistence()) }; val repo = AuthRepository(CanonicalAuthService(api, origin), store)
        failure { repo.signup(signup("FARMER")) }
        val result = failure { repo.completeVerifiedSignup(signup("FARMER"), 42) }
        assertEquals(AuthStage.FARMER_PROFILE, result.diagnostic!!.stage); assertEquals(503, result.diagnostic.httpStatus)
        assertFalse(calls.any { it.startsWith("create") }); assertNull(store.session.value.auth)
    }
    @Test fun issuedChallengeIsReusedAndCannotResendOnRecompositionOrDoubleTap() {
        val api = OtpApi(); var now = 1000L; val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = EmailVerificationController(api, scope) { now }; val pending = EmailVerificationPending("person@example.test", challenge)
            repeat(3) { controller.begin(pending) }; controller.request(); assertEquals(0, api.requests)
            now += 60_000; controller.request(); assertEquals(1, api.requests)
            controller.verify("123456"); assertTrue(controller.state.value.verified)
            controller.verify("123456"); controller.request(); assertEquals(1, api.verifications); assertEquals(1, api.requests)
        } finally { scope.cancel() }
    }
    @Test fun automaticRequestIsSingleFlightAndFailureOrExpiryIsNotVerification() {
        val api = OtpApi(); var now = 1000L; val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            api.hold = CompletableDeferred(); val controller = EmailVerificationController(api, scope) { now }
            val pending = EmailVerificationPending("person@example.test")
            repeat(3) { controller.begin(pending) }; controller.request(); assertEquals(1, api.requests); api.hold!!.complete(Unit)
            api.success = false; controller.verify("123456"); assertFalse(controller.state.value.verified)
            now += 601_000; controller.verify("123456"); assertEquals(1, api.verifications); assertFalse(controller.state.value.verified)
        } finally { scope.cancel() }
    }
}
