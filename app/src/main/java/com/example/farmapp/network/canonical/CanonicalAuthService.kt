package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.*
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.*
import com.example.farmapp.repository.AuthFailure
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.Instant

class SupplierProfileSetupRequired : AuthFailure(
    "Your Supplier account was authenticated, but its business profile is missing. Finish Supplier setup without registering again.")

internal fun <T, R> Response<CanonicalApiResponse<T>>.mapped(transform: (T) -> R): Response<R> {
    if (!isSuccessful) return Response.error(code(), errorBody() ?: "{}".toResponseBody())
    val data = body()?.requireData() ?: throw CanonicalContractException("Missing API envelope")
    return Response.success(code(), transform(data))
}

/** Canonical refresh expiry is unknown. Server decides validity; no JWT decoding or invented deadline. */
fun CanonicalAuthTokenDto.toAndroidAuth(origin: String, now: Instant = Instant.now()): AuthResponse {
    if (emailVerificationRequired == true || user?.emailVerified == false) throw InvalidAuthResponse()
    val account = (user ?: throw InvalidAuthResponse()).toAccount()
    val access = accessToken?.takeIf { it.isNotBlank() } ?: throw InvalidAuthResponse()
    val refresh = refreshToken?.takeIf { it.isNotBlank() } ?: throw InvalidAuthResponse()
    if (tokenType != "Bearer" || (expiresIn != null && expiresIn <= 0)) throw InvalidAuthResponse()
    return AuthResponse(account.userId, account.email, account.fullName, account.role, access, refresh,
        "", account.verificationStatus, account.username, account.region, account.phoneNumber,
        backendContract = "canonical", sessionOrigin = origin,
        accessTokenExpiresAt = expiresIn?.let { now.plusSeconds(it).toString() })
}

class CanonicalAuthService(private val api: CanonicalBackendApi, val origin: String,
    private val loginTransport: CanonicalLoginTransport? = null) : ApiService {
    override suspend fun login(request: LoginRequest): Response<AuthResponse> {
        if (!request.identifier.contains("@")) throw AuthFailure("The shared backend requires your email address for sign-in.")
        val email = request.identifier.trim()
        val response = try {
            val payload = CanonicalLoginRequest(email, request.password)
            loginTransport?.let { readCanonicalLogin(it, payload) } ?: api.login(payload)
        } catch (e: CancellationException) { throw e }
        catch (e: AuthFailure) { throw e }
        catch (e: Exception) { throw CanonicalAuthErrors.exception(e) }
        if (!response.isSuccessful) throw CanonicalAuthErrors.login(response, email)
        val dto = try { response.body()?.requireData() ?: throw InvalidAuthResponse() }
            catch (_: Exception) { throw AuthFailure("Sign-in returned an incomplete or unexpected response format.", AuthDiagnostic(AuthStage.TOKEN_VALIDATION, AuthFailureKind.CONTRACT, response.code())) }
        if (dto.emailVerificationRequired == true || dto.user?.emailVerified == false)
            throw EmailVerificationRequired(EmailVerificationPending(email, dto.emailVerificationChallengeId ?: dto.user?.emailVerificationChallengeId))
        if (dto.user?.role !in setOf("FARMER", "SUPPLIER", "BUYER", "INSPECTOR", "DRIVER", "ADMIN"))
            throw AuthFailure("Sign-in returned an unexpected account role in its response format.", AuthDiagnostic(AuthStage.ROLE_VALIDATION, AuthFailureKind.CONTRACT, response.code()))
        val auth = try { dto.toAndroidAuth(origin) }
            catch (_: Exception) { throw AuthFailure("Sign-in returned an incomplete or unexpected response format.", AuthDiagnostic(AuthStage.TOKEN_VALIDATION, AuthFailureKind.CONTRACT, response.code())) }
        return Response.success(response.code(), auth)
    }

    /** Read only, with the newly authenticated credentials. Do not persist a partial Supplier session. */
    suspend fun supplierProfileExists(auth: AuthResponse, ensureCurrent: () -> Unit): Boolean {
        ensureCurrent()
        if (auth.role != "SUPPLIER" || auth.backendContract != "canonical" || auth.sessionOrigin != origin)
            throw AuthFailure("Supplier identity could not be verified.")
        val id = auth.userId.toLongOrNull()?.takeIf { it > 0 && it.toString() == auth.userId }
            ?: throw AuthFailure("Supplier identity could not be verified.")
        val response = try { api.supplierProfile(id, "Bearer ${auth.accessToken}") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { throw CanonicalAuthErrors.exception(e, "Supplier profile lookup") }
        ensureCurrent()
        if (response.code() == 404) { response.errorBody()?.close(); return false }
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw AuthFailure("Your Supplier business profile could not be checked (HTTP ${response.code()}). Sign in again later or contact your administrator.", AuthDiagnostic(AuthStage.SUPPLIER_PROFILE, AuthFailureKind.HTTP, response.code()))
        }
        val profile = try { response.body()?.requireData() ?: throw InvalidAuthResponse() }
            catch (e: Exception) { throw CanonicalAuthErrors.exception(e, "Supplier profile lookup") }
        if (profile.userId != id || profile.id == null || profile.id <= 0)
            throw AuthFailure("Supplier business profile ownership could not be verified.")
        ensureCurrent()
        return true
    }
    override suspend fun signup(request: SignupRequest): Response<AuthResponse> {
        if (request.role == "SUPPLIER") return signupSupplier(request)
        if (request.role !in setOf("FARMER", "BUYER")) throw AuthFailure("Self-registration is enabled for Farmers, Buyers and Suppliers only. Staff accounts are provided by the administrator.")
        val registration = api.register(CanonicalRegisterRequest(request.fullName, request.email, request.phoneNumber,
            request.password, request.role, request.username, request.region))
        if (!registration.isSuccessful) return Response.error(registration.errorBody() ?: "{}".toResponseBody(), registration.raw())
        val user = registration.body()?.requireData() ?: throw InvalidAuthResponse()
        // Buyer registration is not a session. An issued challenge need not contain a full login profile.
        val buyer = request.role == "BUYER"
        if (buyer && (user.id == null || user.id <= 0 || user.role != "BUYER" || !user.email.equals(request.email, true)))
            throw AuthFailure("Registration returned an unexpected Buyer identity. Try signing in; do not register again.",
                AuthDiagnostic(AuthStage.REGISTRATION_REQUEST, AuthFailureKind.CONTRACT, registration.code()))
        val registered = if (buyer) null else user.toAccount()
        requireEmailVerification(user, request.email, registered = true)
        val login = try { login(LoginRequest(request.email, request.password)) }
            catch (e: EmailVerificationRequired) { throw EmailVerificationRequired(e.pending.copy(registeredUserId = user.id)) }
            catch (e: AuthFailure) { throw AuthFailure("Your account was created, but sign-in was not completed. ${e.message} Sign in to continue; do not register again.", e.diagnostic) }
        if (!login.isSuccessful) throw AuthFailure("Your account was created. Sign in again to continue; registration must not be repeated.")
        val auth = login.body() ?: throw InvalidAuthResponse()
        if (buyer) {
            if (auth.userId != user.id.toString() || auth.role != "BUYER" || !auth.email.equals(request.email, true))
                throw AuthFailure("Your account was created, but sign-in identity could not be matched. Sign in to continue; do not register again.",
                    AuthDiagnostic(AuthStage.ROLE_VALIDATION, AuthFailureKind.CONTRACT))
            return login
        }
        val registeredFarmer = requireNotNull(registered)
        val profile = api.createFarmerProfile(registeredFarmer.userId.toLong(), "Bearer ${auth.accessToken}",
            CanonicalFarmProfileRequest(requireNotNull(request.farmName), request.region))
        if (!profile.isSuccessful) {
            profile.errorBody()?.close()
            throw AuthFailure("Your account was created, but its farm profile was not completed. Sign in and contact the local administrator; do not register again.")
        }
        if (profile.body()?.requireData()?.userId != registeredFarmer.userId.toLong()) throw InvalidAuthResponse()
        return login
    }
    suspend fun signupSupplier(request: SignupRequest, ensureCurrent: () -> Unit = {}): Response<AuthResponse> =
        SupplierSignup(api, origin).register(request, ensureCurrent)

    suspend fun recoverSupplierSignup(request: SignupRequest, ensureCurrent: () -> Unit = {}): Response<AuthResponse> =
        SupplierSignup(api, origin).recover(request, ensureCurrent)

    /** Resume a confirmed registration after OTP; never calls auth/register again. */
    suspend fun finishVerifiedSignup(request: SignupRequest, registeredId: Long, ensureCurrent: () -> Unit): Response<AuthResponse> {
        ensureCurrent()
        val response = login(LoginRequest(request.email, request.password))
        val auth = response.body() ?: throw InvalidAuthResponse()
        if (auth.userId != registeredId.toString() || auth.role != request.role || !auth.email.equals(request.email, true))
            throw AuthFailure("Verified account identity could not be matched to this registration.")
        finishRegisteredProfile(request, auth, ensureCurrent)
        return response
    }

    suspend fun finishRegisteredProfile(request: SignupRequest, auth: AuthResponse, ensureCurrent: () -> Unit) {
        val stage = if (auth.role == "SUPPLIER") AuthStage.SUPPLIER_PROFILE else AuthStage.FARMER_PROFILE
        try { finishRegisteredProfileChecked(request, auth, ensureCurrent) }
        catch (e: CancellationException) { throw e }
        catch (e: AuthFailure) { throw if (e.diagnostic != null) e else AuthFailure(e.message ?: "Profile setup was not confirmed.", AuthDiagnostic(stage, AuthFailureKind.CONTRACT)) }
        catch (e: Exception) { throw CanonicalAuthErrors.exception(e, if (auth.role == "SUPPLIER") "Supplier profile lookup" else "Farmer profile lookup") }
    }

    private suspend fun finishRegisteredProfileChecked(request: SignupRequest, auth: AuthResponse, ensureCurrent: () -> Unit) {
        if (auth.role != request.role || !auth.email.equals(request.email, true)) throw AuthFailure("Registered account identity did not match sign-in.")
        val registeredId = auth.userId.toLong()
        ensureCurrent()
        if (auth.role == "SUPPLIER") { SupplierSignup(api, origin).completeProfile(request, auth, ensureCurrent); return }
        if (auth.role == "FARMER") {
            val existing = api.farmerProfile(registeredId, "Bearer ${auth.accessToken}")
            ensureCurrent()
            if (existing.code() == 404) {
                existing.errorBody()?.close()
                val created = api.createFarmerProfile(registeredId, "Bearer ${auth.accessToken}", CanonicalFarmProfileRequest(requireNotNull(request.farmName), request.region))
                ensureCurrent()
                if (!created.isSuccessful) { created.errorBody()?.close(); throw AuthFailure("Email verified, but Farmer profile setup was not confirmed (HTTP ${created.code()}). Sign in or contact your administrator; do not register again.", AuthDiagnostic(AuthStage.FARMER_PROFILE, AuthFailureKind.HTTP, created.code())) }
                if (created.body()?.requireData()?.userId != registeredId) throw InvalidAuthResponse()
            } else {
                if (!existing.isSuccessful) { existing.errorBody()?.close(); throw AuthFailure("Email verified, but Farmer profile lookup failed (HTTP ${existing.code()}). No profile was created.", AuthDiagnostic(AuthStage.FARMER_PROFILE, AuthFailureKind.HTTP, existing.code())) }
                if (existing.body()?.requireData()?.userId != registeredId) throw InvalidAuthResponse()
            }
        }
    }
    override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> =
        api.refresh(CanonicalRefreshRequest(request.refreshToken)).mapped { it.toAndroidAuth(origin) }

    // The canonical endpoint is a no-op. AuthRepository clears locally without claiming revocation.
    override suspend fun logout(request: LogoutRequest): Response<Unit> =
        Response.error(501, """{"message":"Server-side token revocation is unavailable. Sign out locally."}""".toResponseBody())
}
