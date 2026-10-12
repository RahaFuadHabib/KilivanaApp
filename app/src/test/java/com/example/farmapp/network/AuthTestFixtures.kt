package com.example.farmapp.network

import com.example.farmapp.data.model.*
import retrofit2.Response
import java.time.Instant

internal fun testAuth(access: String = "access-old", refresh: String = "refresh-old", role: String = "FARMER") =
    AuthResponse("eb1f7b4e-8392-4bf4-8cdc-f96083411000", "person@example.test", "Test Person", role,
        access, refresh, Instant.now().plusSeconds(86400).toString(), verificationStatus = "VERIFIED",
        username = "test_person", region = "Nairobi", phoneNumber = "+254700000000")

internal class MemoryPersistence(var saved: AuthResponse? = null) : SessionPersistence {
    var writes = 0
    var clears = 0
    var failWrite = false
    override fun read() = saved
    override fun write(auth: AuthResponse) {
        if (failWrite) throw java.io.IOException("Disk unavailable")
        saved = auth
        writes++
    }
    override fun clear() { saved = null; clears++ }
}

internal fun signedInStore(auth: AuthResponse = testAuth()): SessionState =
    SessionState().apply { initialize(MemoryPersistence(auth)) }

internal open class StubAuthService : ApiService {
    override suspend fun signup(request: SignupRequest): Response<AuthResponse> = Response.success(testAuth())
    override suspend fun login(request: LoginRequest): Response<AuthResponse> = Response.success(testAuth())
    override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> =
        Response.success(testAuth("access-new", "refresh-new"))
    override suspend fun logout(request: LogoutRequest): Response<Unit> = Response.success(Unit)
}
