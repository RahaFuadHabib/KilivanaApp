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
