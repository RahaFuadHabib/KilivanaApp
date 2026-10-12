package com.example.farmapp.network

import com.example.farmapp.data.model.*
import com.example.farmapp.repository.AuthRepository
import com.example.farmapp.repository.AuthFailure
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class AuthRepositoryTest {
    @Test fun signupPersistsServerResponseAndOmitsIrrelevantFields() = runBlocking {
        val disk = MemoryPersistence()
        val store = SessionState().apply { initialize(disk) }
        var sent: SignupRequest? = null
        val auth = testAuth(role = "INSPECTOR").copy(verificationStatus = "PENDING")
        val repository = AuthRepository(object : StubAuthService() {
            override suspend fun signup(request: SignupRequest): Response<AuthResponse> {
                sent = request
                return Response.success(auth)
            }
        }, store)
        repository.signup(signupInput())
        assertNull(sent!!.employeeCode)
        assertNull(sent!!.farmName)
        assertEquals(auth, disk.saved)
        assertEquals(auth, store.session.value.auth)
    }

    @Test fun signupErrorsAreSafeAndNeverEstablishSession() = runBlocking {
        mapOf(400 to "check your signup details", 409 to "already exist", 429 to "Too many signup attempts",
            503 to "temporarily unavailable").forEach { (code, message) ->
            val store = SessionState().apply { initialize(MemoryPersistence()) }
            val repo = AuthRepository(object : StubAuthService() {
                override suspend fun signup(request: SignupRequest): Response<AuthResponse> =
                    Response.error(code, "private stack trace".toResponseBody())
            }, store)
            try { repo.signup(signupInput()); fail() } catch (e: AuthFailure) {
                assertTrue(e.message!!.contains(message))
                assertFalse(e.message!!.contains("private"))
            }
            assertNull(store.session.value.auth)
        }
    }

    @Test fun demoSignupNeverCallsTransportAndRealSignupCancellationPropagates() = runBlocking {
        val store = SessionState().apply { initialize(MemoryPersistence()) }
        var calls = 0
        val service = object : StubAuthService() {
            override suspend fun signup(request: SignupRequest): Response<AuthResponse> {
                calls++
                throw CancellationException()
            }
        }
        try { AuthRepository(service, store, true).signup(signupInput()); fail() } catch (_: AuthFailure) { }
        assertEquals(0, calls)
        try { AuthRepository(service, store).signup(signupInput()); fail() } catch (_: CancellationException) { }
        assertEquals(1, calls)
        assertNull(store.session.value.auth)
    }

    @Test fun loginUsesOnlyEnteredCredentialsAndServerIdentity() = runBlocking {
        val store = SessionState().apply { initialize(MemoryPersistence()) }
        var sent: LoginRequest? = null
        val repository = AuthRepository(object : StubAuthService() {
            override suspend fun login(request: LoginRequest): Response<AuthResponse> {
                sent = request
                return Response.success(testAuth(role = "BUYER"))
            }
        }, store)
        repository.login(" person@example.test ", " entered password ")
        assertEquals(LoginRequest("person@example.test", " entered password "), sent)
        assertEquals("BUYER", store.session.value.auth!!.role)
        assertFalse(store.session.value.isDemo)
    }

    @Test fun phoneIdentifierTrimmedAndBlankIdentifierRejectedBeforeTransport() = runBlocking {
        val store = SessionState().apply { initialize(MemoryPersistence()) }
        var sent: LoginRequest? = null
        var calls = 0
        val repo = AuthRepository(object : StubAuthService() {
            override suspend fun login(request: LoginRequest): Response<AuthResponse> {
                calls++
                sent = request
                return Response.success(testAuth())
            }
        }, store)
        try { repo.login("   ", "password"); fail() } catch (_: AuthFailure) { }
        assertEquals(0, calls)
        repo.login(" +254712345678 ", " password ")
        assertEquals(LoginRequest("+254712345678", " password "), sent)
    }

    @Test fun invalidUsernameNeverReachesTransport() = runBlocking {
        val store = SessionState().apply { initialize(MemoryPersistence()) }
        var calls = 0
        val repo = AuthRepository(object : StubAuthService() {
            override suspend fun signup(request: SignupRequest): Response<AuthResponse> {
                calls++
                return Response.success(testAuth())
            }
        }, store)
        try { repo.signup(signupInput().copy(username = "123farmer")); fail() }
        catch (e: AuthFailure) { assertTrue(e.message!!.contains("Username")) }
        assertEquals(0, calls)
        assertNull(store.session.value.auth)
    }

    @Test fun failedProductionLoginDoesNotFallBackToMock() = runBlocking {
        val store = SessionState().apply { initialize(MemoryPersistence()) }
        val repository = AuthRepository(object : StubAuthService() {
            override suspend fun login(request: LoginRequest): Response<AuthResponse> =
                Response.error(401, "{}".toResponseBody())
        }, store)
        try { repository.login("farmer@kilivana.com", "1234"); fail() } catch (_: AuthFailure) { }
        assertNull(store.session.value.auth)
    }

    @Test fun logoutSendsStoredRefreshTokenAndClearsEvenWhenOffline() = runBlocking {
        val disk = MemoryPersistence(testAuth())
        val store = SessionState().apply { initialize(disk) }
        var sent: LogoutRequest? = null
        val repository = AuthRepository(object : StubAuthService() {
            override suspend fun logout(request: LogoutRequest): Response<Unit> {
                sent = request
                throw IOException("offline")
            }
        }, store)
        repository.logout()
        assertEquals("refresh-old", sent!!.refreshToken)
        assertNull(store.session.value.auth)
        assertNull(disk.saved)
    }

    @Test fun cancelledLogoutStillClearsLocallyAndRethrowsCancellation() = runBlocking {
        val store = signedInStore()
        val repository = AuthRepository(object : StubAuthService() {
            override suspend fun logout(request: LogoutRequest): Response<Unit> = throw CancellationException()
        }, store)
        try { repository.logout(); fail() } catch (_: CancellationException) { }
        assertNull(store.session.value.auth)
    }

    @Test fun cancelledLoginDoesNotCreateASession() = runBlocking {
        val store = SessionState().apply { initialize(MemoryPersistence()) }
        val repository = AuthRepository(object : StubAuthService() {
            override suspend fun login(request: LoginRequest): Response<AuthResponse> = throw CancellationException()
        }, store)
        try { repository.login("email", "password"); fail() } catch (_: CancellationException) { }
        assertNull(store.session.value.auth)
    }
}
