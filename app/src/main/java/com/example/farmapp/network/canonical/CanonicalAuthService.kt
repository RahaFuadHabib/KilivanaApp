package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.*
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.*
import com.example.farmapp.repository.AuthFailure
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.Instant

internal fun <T, R> Response<CanonicalApiResponse<T>>.mapped(transform: (T) -> R): Response<R> {
    if (!isSuccessful) return Response.error(code(), errorBody() ?: "{}".toResponseBody())
    val data = body()?.requireData() ?: throw CanonicalContractException("Missing API envelope")
    return Response.success(code(), transform(data))
}

/** Canonical refresh expiry is unknown. Server decides validity; no JWT decoding or invented deadline. */
fun CanonicalAuthTokenDto.toAndroidAuth(origin: String, now: Instant = Instant.now()): AuthResponse {
    val account = (user ?: throw InvalidAuthResponse()).toAccount()
    val access = accessToken?.takeIf { it.isNotBlank() } ?: throw InvalidAuthResponse()
    val refresh = refreshToken?.takeIf { it.isNotBlank() } ?: throw InvalidAuthResponse()
    if (tokenType != "Bearer" || (expiresIn != null && expiresIn <= 0)) throw InvalidAuthResponse()
    return AuthResponse(account.userId, account.email, account.fullName, account.role, access, refresh,
        "", account.verificationStatus, account.username, account.region, account.phoneNumber,
        backendContract = "canonical", sessionOrigin = origin,
        accessTokenExpiresAt = expiresIn?.let { now.plusSeconds(it).toString() })
}

class CanonicalAuthService(private val api: CanonicalBackendApi, val origin: String) : ApiService {
    override suspend fun login(request: LoginRequest): Response<AuthResponse> {
        if (!request.identifier.contains("@")) throw AuthFailure("The shared backend requires your email address for sign-in.")
        return api.login(CanonicalLoginRequest(request.identifier.trim(), request.password)).mapped { it.toAndroidAuth(origin) }
    }
    override suspend fun signup(request: SignupRequest): Response<AuthResponse> {
        if (request.role == "SUPPLIER") return signupSupplier(request)
        if (request.role != "FARMER") throw AuthFailure("Self-registration is enabled for Farmers and Suppliers only. Inspector accounts are provided by the administrator.")
        val registration = api.register(CanonicalRegisterRequest(request.fullName, request.email, request.phoneNumber,
            request.password, request.role, request.username, request.region))
        if (!registration.isSuccessful) return Response.error(registration.code(), registration.errorBody() ?: "{}".toResponseBody())
        val registered = registration.body()?.requireData()?.toAccount() ?: throw InvalidAuthResponse()
        val login = login(LoginRequest(request.email, request.password))
        if (!login.isSuccessful) throw AuthFailure("Your account was created. Sign in again to continue; registration must not be repeated.")
        val auth = login.body() ?: throw InvalidAuthResponse()
        val profile = api.createFarmerProfile(registered.userId.toLong(), "Bearer ${auth.accessToken}",
            CanonicalFarmProfileRequest(requireNotNull(request.farmName), request.region))
        if (!profile.isSuccessful) {
            profile.errorBody()?.close()
            throw AuthFailure("Your account was created, but its farm profile was not completed. Sign in and contact the local administrator; do not register again.")
        }
        if (profile.body()?.requireData()?.userId != registered.userId.toLong()) throw InvalidAuthResponse()
        return login
    }
    suspend fun signupSupplier(request: SignupRequest, ensureCurrent: () -> Unit = {}): Response<AuthResponse> =
        SupplierSignup(api, origin).register(request, ensureCurrent)

    suspend fun recoverSupplierSignup(request: SignupRequest, ensureCurrent: () -> Unit = {}): Response<AuthResponse> =
        SupplierSignup(api, origin).recover(request, ensureCurrent)
    override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> =
        api.refresh(CanonicalRefreshRequest(request.refreshToken)).mapped { it.toAndroidAuth(origin) }

    // The canonical endpoint is a no-op. AuthRepository clears locally without claiming revocation.
    override suspend fun logout(request: LogoutRequest): Response<Unit> =
        Response.error(501, """{"message":"Server-side token revocation is unavailable. Sign out locally."}""".toResponseBody())
}
