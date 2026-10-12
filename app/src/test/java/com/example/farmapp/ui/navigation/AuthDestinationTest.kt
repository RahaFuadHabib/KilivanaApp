package com.example.farmapp.ui.navigation

import com.example.farmapp.network.*
import com.example.farmapp.data.model.*
import com.example.farmapp.repository.AuthRepository
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class AuthDestinationTest {
    @Test fun signupAndLoginUseSameFailClosedRouting() = runBlocking {
        val cases = listOf(
            Triple("FARMER", null, AuthDestination.FARMER), Triple("BUYER", null, AuthDestination.BUYER),
            Triple("SUPPLIER", null, AuthDestination.SUPPLIER),
            Triple("INSPECTOR", "VERIFIED", AuthDestination.INSPECTOR),
            Triple("INSPECTOR", "PENDING", AuthDestination.AWAITING_VERIFICATION),
            Triple("INSPECTOR", "UNVERIFIED", AuthDestination.AWAITING_VERIFICATION),
            Triple("INSPECTOR", null, AuthDestination.AWAITING_VERIFICATION),
            Triple("INSPECTOR", "UNKNOWN", AuthDestination.AWAITING_VERIFICATION),
            Triple("ADMIN", "VERIFIED", AuthDestination.UNSUPPORTED),
            Triple("DRIVER", "VERIFIED", AuthDestination.UNSUPPORTED),
            Triple("UNKNOWN", "VERIFIED", AuthDestination.UNSUPPORTED))
        cases.forEach { (role, status, destination) ->
            for (signup in listOf(true, false)) {
                val disk = MemoryPersistence()
                val store = SessionState().apply { initialize(disk) }
                val auth = testAuth(role = role).copy(verificationStatus = status)
                val service = object : StubAuthService() {
                    override suspend fun signup(request: SignupRequest) = Response.success(auth)
                    override suspend fun login(request: LoginRequest) = Response.success(auth)
                }
                val repository = AuthRepository(service, store)
                if (signup) repository.signup(signupInput()) else repository.login("person@example.test", "password")
                assertEquals(destination, authDestination(store.session.value.auth!!))
                val restored = SessionState().apply { initialize(MemoryPersistence(Gson().fromJson(Gson().toJson(disk.saved), AuthResponse::class.java))) }
                assertEquals(destination, authDestination(restored.session.value.auth!!))
            }
        }
    }

    @Test fun refreshUpdatesVerificationAndPersistedRouting() = runBlocking {
        val disk = MemoryPersistence(testAuth(role = "INSPECTOR").copy(verificationStatus = "PENDING"))
        val store = SessionState().apply { initialize(disk) }
        val service = object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest) = Response.success(
                testAuth("new-access", "new-refresh", "INSPECTOR").copy(verificationStatus = "VERIFIED"))
        }
        RefreshCoordinator(service, store).refresh(store.session.value)
        assertEquals("VERIFIED", disk.saved!!.verificationStatus)
        assertEquals(AuthDestination.INSPECTOR, authDestination(store.session.value.auth!!))
        val restored = SessionState().apply { initialize(disk) }
        assertEquals(AuthDestination.INSPECTOR, authDestination(restored.session.value.auth!!))
    }
}
