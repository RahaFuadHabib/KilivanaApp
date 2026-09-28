package com.example.farmapp.network

import com.example.farmapp.data.model.AuthResponse
import com.example.farmapp.data.model.LoginRequest
import com.example.farmapp.data.model.RefreshRequest
import com.example.farmapp.data.model.LogoutRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import java.time.Instant

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>
}

/** Explicit offline/demo service only. No production fallback, and no signup simulation. */
class MockAuthService : ApiService {
    private val accounts = listOf(
        Triple("farmer@kilivana.com", "FARMER", "David Karanja"),
        Triple("buyer@kilivana.com", "BUYER", "Sarah Ochieng"),
        Triple("supplier@kilivana.com", "SUPPLIER", "Joseph Kamau"),
        Triple("inspector@kilivana.com", "INSPECTOR", "Grace Wanjiku")
    )

    override suspend fun login(request: LoginRequest): Response<AuthResponse> {
        val index = accounts.indexOfFirst { it.first == request.email.trim().lowercase() }
        if (index < 0 || request.password.trim() != "1234") return rejected()
        val (email, role, name) = accounts[index]
        return Response.success(AuthResponse(
            userId = "00000000-0000-4000-8000-00000000000${index + 1}",
            email = email, fullName = name, role = role,
            accessToken = "mock-${role.lowercase()}-access",
            refreshToken = "mock-${role.lowercase()}-refresh",
            refreshTokenExpiresAt = Instant.now().plusSeconds(14 * 24 * 60 * 60).toString()
        ))
    }

    override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> {
        val account = accounts.firstOrNull { "mock-${it.second.lowercase()}-refresh" == request.refreshToken }
            ?: return rejected()
        return login(LoginRequest(account.first, "1234"))
    }

    override suspend fun logout(request: LogoutRequest): Response<Unit> = Response.success(Unit)

    private fun rejected(): Response<AuthResponse> = Response.error(
        401, """{"error":"Invalid credentials"}""".toResponseBody("application/json".toMediaType())
    )
}
