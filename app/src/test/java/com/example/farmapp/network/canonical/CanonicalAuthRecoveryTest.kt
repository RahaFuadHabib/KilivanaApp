package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.canonical.CanonicalAuthTokenDto
import com.example.farmapp.data.model.canonical.CanonicalUserDto
import com.example.farmapp.network.*
import com.example.farmapp.repository.AuthFailure
import com.example.farmapp.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

/** Real Retrofit/Gson decoding with synthetic OkHttp responses; the interceptor never uses the network. */
class CanonicalAuthRecoveryTest {
    private class Backend {
        val calls = mutableListOf<String>()
        var user = CanonicalUserDto(42, "Synthetic Supplier", "supplier@example.test", "+254700000000",
            role = "SUPPLIER", status = "ACTIVE", verificationStatus = "NOT_REQUIRED")
        var registerCode = 201
        var registerJson: String? = null
        var loginCode = 200
        var loginJson: String? = null
        var profileCode = 200
        var profileJson = """{"success":true,"data":{"id":5,"userId":42}}"""
        var failure: IOException? = null
        var hook: (Request) -> Unit = {}
        val api = Retrofit.Builder().baseUrl("https://auth.example.test/api/v1/")
            .client(OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val route = request.method + " " + request.url.encodedPath.removePrefix("/api/v1/")
                calls += route
                hook(request)
                failure?.let { throw it }
                val (code, body) = when (route) {
                    "POST auth/register" -> registerCode to (registerJson ?: CanonicalJson.gson.toJson(CanonicalApiResponse(true, data = user)))
                    "POST auth/login" -> loginCode to (loginJson ?: CanonicalJson.gson.toJson(CanonicalApiResponse(true,
                        data = CanonicalAuthTokenDto("synthetic-access", "synthetic-refresh", "Bearer", 3600, user))))
                    "GET profiles/suppliers/42" -> profileCode to profileJson
                    "POST profiles/suppliers/42" -> 201 to """{"success":true,"data":{"id":5,"userId":42}}"""
                    else -> error("Unexpected synthetic route")
                }
                okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Synthetic")
                    .body(body.toResponseBody()).build()
            }.build()).addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson)).build()
            .create(CanonicalBackendApi::class.java)
        val store = SessionState().apply { initialize(MemoryPersistence()) }
        val repo = AuthRepository(CanonicalAuthService(api, "https://auth.example.test/api/v1/"), store)
    }

    private fun signup() = signupInput("SUPPLIER").copy(email = "supplier@example.test", fullName = "Synthetic Supplier",
        businessName = "Synthetic shop", businessLocation = "Shop 4, Synthetic Road", region = "Nairobi")
    private suspend fun rejected(block: suspend () -> Unit): AuthFailure {
        try { block() } catch (e: AuthFailure) { return e }
        throw AssertionError("Expected a safe failure")
    }
    private fun error(code: String, message: String) =
        """{"success":false,"message":"$message","error":{"code":"$code","details":"$message"}}"""

    @Test fun registrationValidationUsesOnlyKnownFieldLabelsAndAllowsExplicitCorrection() = runBlocking {
        val backend = Backend().apply {
            registerCode = 400
            registerJson = """{"success":false,"error":{"code":"VALIDATION_ERROR"},"data":{"email":"private-token-value","password":"private-code-value","unexpectedSecret":"private"}}"""
        }
        val message = rejected { backend.repo.signup(signup()) }.message!!
        assertEquals("Please check these signup fields: email, password.", message)
        assertFalse(backend.repo.supplierRegistrationNeedsRecovery)
        assertNull(backend.store.session.value.auth)
        backend.registerCode = 201; backend.registerJson = null
        backend.repo.signup(signup())
        assertEquals(2, backend.calls.count { it == "POST auth/register" })
        assertNotNull(backend.store.session.value.auth)
    }

    @Test fun knownHttpRejectionsAreNotReportedAsNetworkOrUncertainWrites() = runBlocking {
        for ((code, fragment) in mapOf(401 to "not permitted", 403 to "not permitted", 415 to "request format", 422 to "signup details", 429 to "Too many")) {
            val backend = Backend().apply { registerCode = code; registerJson = error("BAD_REQUEST", "private-secret") }
            val message = rejected { backend.repo.signup(signup()) }.message!!
            assertTrue(message.contains(fragment)); assertFalse(message.contains("private-secret"))
            assertFalse(backend.repo.supplierRegistrationNeedsRecovery)
            assertEquals(listOf("POST auth/register"), backend.calls)
        }
    }

    @Test fun unexpectedHttpStatusesNeverPermitAutomaticRegistrationRetry() = runBlocking {
        for (code in listOf(302, 404, 405, 408, 409, 500, 502, 503)) {
            val backend = Backend().apply { registerCode = code; registerJson = error("SERVICE_UNAVAILABLE", "private-secret") }
            val message = rejected { backend.repo.signup(signup()) }.message!!
            assertTrue(message.contains("Do not register again")); assertFalse(message.contains("private-secret"))
            assertTrue(backend.repo.supplierRegistrationNeedsRecovery)
            rejected { backend.repo.signup(signup()) }
            assertEquals(listOf("POST auth/register"), backend.calls)
            assertNull(backend.store.session.value.auth)
        }
    }

    @Test fun conflictGivesSafeGuidanceButKeepsRegistrationBlocked() = runBlocking {
        for ((detail, fragment) in listOf("Email already exists, please log in instead" to "this email",
            "Phone number already exists, please log in instead" to "phone number")) {
            val backend = Backend().apply { registerCode = 409; registerJson = error("CONFLICT", detail) }
            assertTrue(rejected { backend.repo.signup(signup()) }.message!!.contains(fragment))
            assertTrue(backend.repo.supplierRegistrationNeedsRecovery)
            rejected { backend.repo.signup(signup()) }
            assertEquals(1, backend.calls.size)
        }
    }

    @Test fun transportFailureAndMalformedRegistrationResponseRemainDistinctAndUncertain() = runBlocking {
        val offline = Backend().apply { failure = IOException("private-password") }
        val transport = rejected { offline.repo.signup(signup()) }.message!!
        assertTrue(transport.contains("connection")); assertTrue(transport.contains("could not be confirmed"))
        assertFalse(transport.contains("private-password"))
        val malformed = Backend().apply { registerJson = "not-json-private-token" }
        val parse = rejected { malformed.repo.signup(signup()) }.message!!
        assertTrue(parse.contains("response format")); assertFalse(parse.contains("connection"))
        assertFalse(parse.contains("private-token"))
        for (backend in listOf(offline, malformed)) {
            assertTrue(backend.repo.supplierRegistrationNeedsRecovery)
            rejected { backend.repo.signup(signup()) }; assertEquals(1, backend.calls.size)
        }
    }

    @Test fun incompleteAndUnsuccessfulSuccessEnvelopesDoNotEstablishAccountsOrRetry() = runBlocking {
        for (body in listOf("{}", """{"success":true}""", """{"success":false,"error":{"code":"BAD_REQUEST"}}""",
            """{"success":true,"data":{"id":42,"role":"SUPPLIER"}}""")) {
            val backend = Backend().apply { registerJson = body }
            assertTrue(rejected { backend.repo.signup(signup()) }.message!!.contains("response format"))
            assertTrue(backend.repo.supplierRegistrationNeedsRecovery)
            assertNull(backend.store.session.value.auth)
            assertEquals(listOf("POST auth/register"), backend.calls)
        }
    }

    @Test fun postCreationLoginFailureReportsCreatedAccountAndDoesNotRepeatRegistration() = runBlocking {
        val backend = Backend().apply { loginCode = 401; loginJson = error("UNAUTHORIZED", "Invalid email or password") }
        val message = rejected { backend.repo.signup(signup()) }.message!!
        assertTrue(message.contains("account was created")); assertTrue(message.contains("Invalid email or password"))
        assertTrue(backend.repo.supplierRegistrationNeedsRecovery)
        rejected { backend.repo.signup(signup()) }
        assertEquals(listOf("POST auth/register", "POST auth/login"), backend.calls)
    }

    @Test fun canonicalLoginSeparatesCredentialsRestrictionsValidationAndUnavailableService() = runBlocking {
        val cases = listOf(Triple(401, error("UNAUTHORIZED", "Invalid email or password"), "Invalid email or password"),
            Triple(401, error("UNAUTHORIZED", "This account is suspended"), "account is suspended"),
            Triple(401, error("UNAUTHORIZED", "This account is inactive"), "account is inactive"),
            Triple(403, error("FORBIDDEN", "private-token"), "not permitted"),
            Triple(400, error("VALIDATION_ERROR", "private-validation-value"), "sign-in details"),
            Triple(429, error("TOO_MANY_REQUESTS", "private-token"), "Too many"),
            Triple(503, error("SERVICE_UNAVAILABLE", "private-token"), "HTTP 503"))
        for ((code, body, fragment) in cases) {
            val backend = Backend().apply { loginCode = code; loginJson = body }
            val message = rejected { backend.repo.login("supplier@example.test", "synthetic-password") }.message!!
            assertTrue(message.contains(fragment)); assertFalse(message.contains("private-"))
            assertNull(backend.store.session.value.auth)
            assertEquals(listOf("POST auth/login"), backend.calls)
        }
    }

    @Test fun verifiedEmailGateDoesNotEstablishSessionOrRepeatLogin() = runBlocking {
        val backend = Backend().apply { loginCode = 403; loginJson = error("FORBIDDEN", "Email verification is required before login") }
        val message = rejected { backend.repo.login("supplier@example.test", "synthetic-password") }.message!!
        assertTrue(message.contains("verify your email")); assertNull(backend.store.session.value.auth)
        assertEquals(listOf("POST auth/login"), backend.calls)
    }

    @Test fun gatewayForbiddenIsServiceFailureWithoutAccountClaimsOrSession() = runBlocking {
        val bodies = listOf("ERR_NGROK_725 private-token bandwidth limit", "<html>Forbidden private-password</html>",
            "{}", """{"success":false,"error":{"code":"UNKNOWN_GATEWAY"}}""",
            """{"success":true,"error":{"code":"FORBIDDEN"}}""")
        for (body in bodies) {
            val backend = Backend().apply { loginCode = 403; loginJson = body }
            val failure = rejected { backend.repo.login("farmer@example.test", "synthetic-password") }
            assertTrue(failure.message!!.contains("service is unavailable (HTTP 403)"))
            assertFalse(failure.message!!.contains("account"))
            assertFalse(failure.message!!.contains("private-"))
            assertEquals(AuthStage.LOGIN_REQUEST, failure.diagnostic!!.stage)
            assertEquals(403, failure.diagnostic.httpStatus)
            assertNull(backend.store.session.value.auth)
        }
    }

    @Test fun loginParsingErrorsAreNotConnectionFailuresAndNeverExposeServerContent() = runBlocking {
        for (body in listOf("not-json-private-password", "{}", """{"success":true,"data":{"accessToken":"private-token"}}""")) {
            val backend = Backend().apply { loginJson = body }
            val message = rejected { backend.repo.login("supplier@example.test", "synthetic-password") }.message!!
            assertTrue(message.contains("response format")); assertFalse(message.contains("connection"))
            assertFalse(message.contains("private-")); assertNull(backend.store.session.value.auth)
        }
    }

    @Test fun loginTransportFailureHasConnectionGuidanceWithoutExceptionDetails() = runBlocking {
        val backend = Backend().apply { failure = IOException("private-token") }
        val message = rejected { backend.repo.login("supplier@example.test", "synthetic-password") }.message!!
        assertTrue(message.contains("connection")); assertFalse(message.contains("private-token"))
        assertNull(backend.store.session.value.auth)
    }

    @Test fun malformedAndOversizedErrorsUseSafeHttpFallbacks() = runBlocking {
        for (body in listOf("<html>private-password</html>", "x".repeat(20_000), """{"success":false,"error":{"code":{"password":"private"}}}""")) {
            val backend = Backend().apply { loginCode = 401; loginJson = body }
            assertEquals("Invalid email or password.", rejected { backend.repo.login("supplier@example.test", "synthetic-password") }.message)
        }
    }

    @Test fun farmerAndBuyerLoginDoNotRequestSupplierProfiles() = runBlocking {
        for (role in listOf("FARMER", "BUYER")) {
            val backend = Backend().apply { user = user.copy(role = role) }
            backend.repo.login("supplier@example.test", "synthetic-password")
            assertEquals(role, backend.store.session.value.auth!!.role)
            assertEquals(listOf("POST auth/login"), backend.calls)
        }
    }

    @Test fun existingSupplierProfileIsVerifiedBeforeSessionPersistence() = runBlocking {
        val backend = Backend()
        backend.hook = { assertNull(backend.store.session.value.auth) }
        backend.repo.login("supplier@example.test", "synthetic-password")
        assertEquals("SUPPLIER", backend.store.session.value.auth!!.role)
        assertEquals(listOf("POST auth/login", "GET profiles/suppliers/42"), backend.calls)
    }

    @Test fun missingSupplierProfileGuidesRecoveryWithoutSessionRegistrationOrAutomaticWrite() = runBlocking {
        val backend = Backend().apply { profileCode = 404 }
        assertTrue(rejected { backend.repo.login("supplier@example.test", "synthetic-password") } is SupplierProfileSetupRequired)
        assertNull(backend.store.session.value.auth)
        assertTrue(backend.repo.supplierRegistrationNeedsRecovery)
        assertEquals(listOf("POST auth/login", "GET profiles/suppliers/42"), backend.calls)
        backend.repo.recoverSupplierSignup(signup()) // Explicit user submission, with a fresh read before creating.
        assertNotNull(backend.store.session.value.auth)
        assertEquals(0, backend.calls.count { it == "POST auth/register" })
        assertEquals(2, backend.calls.count { it == "GET profiles/suppliers/42" })
        assertEquals(1, backend.calls.count { it == "POST profiles/suppliers/42" })
    }

    @Test fun recoveryReadsProfileAgainAndAvoidsDuplicateWhenAnotherClientCompletedSetup() = runBlocking {
        val backend = Backend().apply { profileCode = 404 }
        rejected { backend.repo.login("supplier@example.test", "synthetic-password") }
        backend.profileCode = 200
        backend.repo.recoverSupplierSignup(signup())
        assertNotNull(backend.store.session.value.auth)
        assertFalse(backend.repo.supplierRegistrationNeedsRecovery)
        assertFalse(backend.calls.any { it.startsWith("POST profiles/") || it == "POST auth/register" })
    }

    @Test fun deniedUnavailableMalformedOrWrongOwnerProfilesNeverTriggerSetupOrWrites() = runBlocking {
        for ((code, body) in listOf(403 to "{}", 500 to "{}", 200 to "not-json", 200 to "{}",
            200 to """{"success":true,"data":{"id":5,"userId":99}}""")) {
            val backend = Backend().apply { profileCode = code; profileJson = body }
            assertFalse(rejected { backend.repo.login("supplier@example.test", "synthetic-password") } is SupplierProfileSetupRequired)
            assertNull(backend.store.session.value.auth)
            assertFalse(backend.repo.supplierRegistrationNeedsRecovery)
            assertEquals(listOf("POST auth/login", "GET profiles/suppliers/42"), backend.calls)
        }
    }

    @Test fun sessionSwitchDuringProfileCheckCannotOpenRecoveryForTheNewAccount() = runBlocking {
        val backend = Backend().apply { profileCode = 404 }
        backend.hook = { if (it.method == "GET") backend.store.establish(backend.store.session.value, testAuth(role = "BUYER"), false) }
        assertFalse(rejected { backend.repo.login("supplier@example.test", "synthetic-password") } is SupplierProfileSetupRequired)
        assertEquals("BUYER", backend.store.session.value.auth!!.role)
        assertFalse(backend.repo.supplierRegistrationNeedsRecovery)
    }
}
