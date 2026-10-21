package com.example.farmapp.supplier

import com.example.farmapp.data.model.*
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import com.example.farmapp.repository.*
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SupplierSignupTest {
    private fun request() = signupInput("SUPPLIER").copy(email = "supplier@example.test", fullName = "Synthetic Supplier",
        businessName = "Synthetic business", businessLocation = "Shop 4, Synthetic Road", region = "Nairobi")
    private class Backend {
        val calls = mutableListOf<String>()
        var registered = supplierUser()
        var loggedIn = supplierUser()
        var registerCode = 201
        var loginCode = 200
        var profileCode = 201
        var lookupCode = 404
        var foundProfile: CanonicalCreatedSupplierProfileDto? = null
        var profileResult = CanonicalCreatedSupplierProfileDto(5, 42)
        var registerRequest: CanonicalRegisterRequest? = null
        var profileRequest: CanonicalSupplierProfileRequest? = null
        var profileBearer: String? = null
        var hook: (String) -> Unit = {}
        val api = Proxy.newProxyInstance(CanonicalBackendApi::class.java.classLoader, arrayOf(CanonicalBackendApi::class.java)) { _, method, args ->
            calls += method.name
            hook(method.name)
            fun <T> result(code: Int, value: T): Response<CanonicalApiResponse<T>> = if (code in 200..299)
                Response.success(code, CanonicalApiResponse(true, data = value)) else Response.error(code, "Synthetic rejection".toResponseBody())
            when (method.name) {
                "register" -> { registerRequest = args[0] as CanonicalRegisterRequest; result(registerCode, registered) }
                "login" -> result(loginCode, CanonicalAuthTokenDto("temporary-access", "temporary-refresh", "Bearer", 3600, loggedIn))
                "createSupplierProfile" -> {
                    assertEquals(42L, args[0]); profileBearer = args[1] as String; profileRequest = args[2] as CanonicalSupplierProfileRequest
                    result(profileCode, profileResult)
                }
                "supplierProfile" -> { assertEquals(42L, args[0]); foundProfile?.let { result(200, it) } ?: Response.error<CanonicalApiResponse<CanonicalCreatedSupplierProfileDto>>(lookupCode, "{}".toResponseBody()) }
                else -> error("Unexpected route ${method.name}")
            }
        } as CanonicalBackendApi
        fun service() = CanonicalAuthService(api, SUPPLIER_TEST_ORIGIN)
    }
    private fun store() = SessionState().apply { initialize(MemoryPersistence()) }
    private suspend fun rejected(block: suspend () -> Unit): AuthFailure {
        try { block() } catch (e: AuthFailure) { return e }
        throw AssertionError("Expected safe setup failure")
    }

    @Test fun supplierRegistersLogsInCreatesProfileThenEstablishesSession() = runBlocking {
        val backend = Backend(); val store = store(); val repo = AuthRepository(backend.service(), store)
        backend.hook = { assertNull(store.session.value.auth) }
        repo.signup(request())
        assertEquals(listOf("register", "login", "createSupplierProfile"), backend.calls)
        assertEquals("SUPPLIER", store.session.value.auth!!.role)
        assertEquals("42", store.session.value.auth!!.userId)
        assertEquals(SUPPLIER_TEST_ORIGIN, store.session.value.auth!!.sessionOrigin)
        assertEquals("Bearer temporary-access", backend.profileBearer)
        assertEquals("Shop 4, Synthetic Road", backend.profileRequest!!.location)
        assertEquals("Nairobi City", backend.registerRequest!!.region)
        assertFalse(repo.supplierRegistrationNeedsRecovery)
        val json = com.google.gson.JsonParser.parseString(CanonicalJson.gson.toJson(backend.registerRequest)).asJsonObject
        assertEquals(setOf("name", "email", "phone", "password", "role", "username", "region"), json.keySet())
        val profile = com.google.gson.JsonParser.parseString(CanonicalJson.gson.toJson(backend.profileRequest)).asJsonObject
        assertEquals(setOf("businessName", "location"), profile.keySet())
    }
    @Test fun optionalSupplierUsernameIsOmittedAndCountyIsRequired() = runBlocking {
        val backend = Backend()
        rejected { backend.service().signup(request().copy(region = "")) }
        assertTrue(backend.calls.isEmpty())
        backend.service().signup(request().copy(username = ""))
        assertNull(backend.registerRequest!!.username); assertEquals("Nairobi City", backend.registerRequest!!.region)
    }
    @Test fun invalidSupplierInputNeverRegisters() = runBlocking {
        val backend = Backend()
        for (input in listOf(request().copy(businessName = " "), request().copy(businessLocation = null),
            request().copy(fullName = "x"), request().copy(email = "bad"), request().copy(password = "short"))) {
            rejected { backend.service().signup(input) }
        }
        assertTrue(backend.calls.isEmpty())
    }
    @Test fun inspectorSignupNeverCallsAnyCanonicalEndpoint() = runBlocking {
        val backend = Backend()
        rejected { backend.service().signup(signupInput("INSPECTOR")) }
        assertTrue(backend.calls.isEmpty())
    }
    @Test fun loginFailureAfterRegistrationCanRecoverWithoutRegisteringAgain() = runBlocking {
        val backend = Backend().apply { loginCode = 401 }; val store = store(); val repo = AuthRepository(backend.service(), store)
        assertTrue(rejected { repo.signup(request()) }.message!!.contains("Do not register again"))
        assertNull(store.session.value.auth); assertTrue(repo.supplierRegistrationNeedsRecovery)
        rejected { repo.signup(request()) }
        assertEquals(1, backend.calls.count { it == "register" })
        backend.loginCode = 200
        repo.recoverSupplierSignup(request())
        assertEquals(1, backend.calls.count { it == "register" })
        assertEquals("42", store.session.value.auth!!.userId)
    }
    @Test fun profileRejectionCanRecoverByCreatingOnlyTheMissingProfile() = runBlocking {
        val backend = Backend().apply { profileCode = 422 }; val store = store(); val repo = AuthRepository(backend.service(), store)
        rejected { repo.signup(request()) }; assertNull(store.session.value.auth)
        backend.profileCode = 201
        repo.recoverSupplierSignup(request())
        assertEquals(1, backend.calls.count { it == "register" })
        assertEquals(2, backend.calls.count { it == "createSupplierProfile" })
        assertFalse(repo.supplierRegistrationNeedsRecovery)
    }
    @Test fun lostProfileResponseRecoveryReadsExistingProfileWithoutAnotherWrite() = runBlocking {
        val backend = Backend(); val store = store(); val repo = AuthRepository(backend.service(), store)
        backend.hook = { if (it == "createSupplierProfile") { backend.foundProfile = CanonicalCreatedSupplierProfileDto(5, 42); throw IOException("private diagnostic") } }
        val failure = rejected { repo.signup(request()) }
        assertFalse(failure.message!!.contains("private diagnostic"))
        backend.hook = {}
        repo.recoverSupplierSignup(request())
        assertEquals(1, backend.calls.count { it == "createSupplierProfile" })
        assertEquals(1, backend.calls.count { it == "register" })
        assertNotNull(store.session.value.auth)
    }
    @Test fun wrongLoginRoleOrIdNeverCreatesProfileOrSession() = runBlocking {
        for (user in listOf(supplierUser().copy(role = "FARMER"), supplierUser(99))) {
            val backend = Backend().apply { loggedIn = user }; val store = store()
            rejected { AuthRepository(backend.service(), store).signup(request()) }
            assertFalse(backend.calls.contains("createSupplierProfile")); assertNull(store.session.value.auth)
        }
    }
    @Test fun wrongRegisteredIdentityNeverLogsIn() = runBlocking {
        val backend = Backend().apply { registered = supplierUser().copy(role = "BUYER") }
        rejected { backend.service().signup(request()) }
        assertEquals(listOf("register"), backend.calls)
    }
    @Test fun wrongProfileOwnershipNeverEstablishesSession() = runBlocking {
        val backend = Backend().apply { profileResult = CanonicalCreatedSupplierProfileDto(5, 99) }; val store = store()
        rejected { AuthRepository(backend.service(), store).signup(request()) }
        assertNull(store.session.value.auth)
    }
    @Test fun lostRegistrationResponseBlocksRepeatingRegistration() = runBlocking {
        val backend = Backend().apply { hook = { if (it == "register") throw IOException("Synthetic lost response") } }
        val repo = AuthRepository(backend.service(), store())
        rejected { repo.signup(request()) }; rejected { repo.signup(request()) }
        assertEquals(listOf("register"), backend.calls)
    }
    @Test fun definiteValidationRejectionAllowsCorrectedRegistration() = runBlocking {
        val backend = Backend().apply { registerCode = 400 }; val repo = AuthRepository(backend.service(), store())
        rejected { repo.signup(request()) }; assertFalse(repo.supplierRegistrationNeedsRecovery)
        backend.registerCode = 201
        repo.signup(request())
        assertEquals(2, backend.calls.count { it == "register" })
    }
    @Test fun recoveryAcceptsExistingAccountWithoutNamePhoneOrCounty() = runBlocking {
        val backend = Backend().apply { foundProfile = CanonicalCreatedSupplierProfileDto(5, 42) }
        val store = store()
        AuthRepository(backend.service(), store).recoverSupplierSignup(request().copy(fullName = "", phoneNumber = "", region = "", username = "", password = "older6"))
        assertEquals(listOf("login", "supplierProfile"), backend.calls)
        assertNotNull(store.session.value.auth)
    }
    @Test fun duplicateSubmitIsSingleFlightAndDoesNotCreateTwoAccounts() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val backend = Backend().apply { hook = { if (it == "register") { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) } } }
        val repo = AuthRepository(backend.service(), store())
        val first = async(Dispatchers.Default) { repo.signup(request()) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            rejected { repo.signup(request()) }
            assertEquals(1, backend.calls.count { it == "register" })
        } finally { release.countDown() }
        first.await()
    }
    @Test fun accountSwitchDuringLoginPreventsProfileCreationAndSessionReplacement() = runBlocking {
        val store = store(); val backend = Backend()
        backend.hook = { if (it == "login") store.establish(store.session.value, supplierAuth("99"), false) }
        rejected { AuthRepository(backend.service(), store).signup(request()) }
        assertFalse(backend.calls.contains("createSupplierProfile"))
        assertEquals("99", store.session.value.auth!!.userId)
    }
    @Test fun accountSwitchAfterProfileWriteRejectsLateCompletion() = runBlocking {
        val store = store(); val backend = Backend()
        backend.hook = { if (it == "createSupplierProfile") store.establish(store.session.value, supplierAuth("99"), false) }
        rejected { AuthRepository(backend.service(), store).signup(request()) }
        assertEquals("99", store.session.value.auth!!.userId)
    }
    @Test fun recoveryRejectsWrongProfileOwnerWithoutOverwritingOrCreating() = runBlocking {
        val backend = Backend().apply { foundProfile = CanonicalCreatedSupplierProfileDto(5, 99) }; val store = store()
        rejected { AuthRepository(backend.service(), store).recoverSupplierSignup(request()) }
        assertNull(store.session.value.auth)
        assertEquals(listOf("login", "supplierProfile"), backend.calls)
    }
    @Test fun forbiddenRecoveryLookupDoesNotFallBackToCreatingProfile() = runBlocking {
        val backend = Backend().apply { lookupCode = 403 }; val repo = AuthRepository(backend.service(), store())
        rejected { repo.recoverSupplierSignup(request()) }
        assertTrue(repo.supplierRegistrationNeedsRecovery)
        assertEquals(listOf("login", "supplierProfile"), backend.calls)
    }
    @Test fun conflictAndServerFailureRequireRecoveryWithoutRepeatingRegistration() = runBlocking {
        for (code in listOf(409, 500)) {
            val backend = Backend().apply { registerCode = code }; val repo = AuthRepository(backend.service(), store())
            rejected { repo.signup(request()) }; rejected { repo.signup(request()) }
            assertTrue(repo.supplierRegistrationNeedsRecovery)
            assertEquals(listOf("register"), backend.calls)
        }
    }
    @Test fun cancellationAfterRegistrationStartsKeepsRecoveryLatch() = runBlocking {
        val backend = Backend().apply { hook = { if (it == "register") throw CancellationException("Synthetic cancellation") } }
        val repo = AuthRepository(backend.service(), store())
        try { repo.signup(request()); fail() } catch (_: CancellationException) { }
        assertTrue(repo.supplierRegistrationNeedsRecovery)
        rejected { repo.signup(request()) }
        assertEquals(listOf("register"), backend.calls)
    }
}
