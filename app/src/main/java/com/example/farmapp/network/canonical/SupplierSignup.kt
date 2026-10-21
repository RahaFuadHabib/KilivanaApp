package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.AuthResponse
import com.example.farmapp.data.model.SignupRequest
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.SignupValidation
import com.example.farmapp.repository.AuthFailure
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class SupplierSignupRecoveryRequired(message: String) : AuthFailure(message)

/** Uses temporary, explicit credentials until account and business ownership have been verified.
 * No session is persisted here, and neither registration nor profile writes are retried.
 */
internal class SupplierSignup(private val api: CanonicalBackendApi, private val origin: String) {
    suspend fun register(input: SignupRequest, ensureCurrent: () -> Unit): Response<AuthResponse> {
        val request = SignupValidation.prepare(input)
        if (request.role != "SUPPLIER") throw AuthFailure("Select Supplier.")
        ensureCurrent()
        val response = try {
            api.register(CanonicalRegisterRequest(request.fullName, request.email, request.phoneNumber, request.password,
                "SUPPLIER", request.username.takeIf { it.isNotBlank() }, request.region.takeIf { it.isNotBlank() }))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { throw recovery("Registration outcome is uncertain.") }
        if (!response.isSuccessful) {
            val code = response.code()
            val body = response.errorBody() ?: "{}".toResponseBody()
            if (code !in setOf(400, 401, 403, 415, 422, 429)) {
                body.close()
                throw recovery(if (code == 409) "An account with these details may already exist." else "Registration outcome is uncertain.")
            }
            return Response.error(code, body)
        }
        return finish {
            ensureCurrent()
            val registered = response.body()?.requireData()?.toAccount() ?: throw AuthFailure("Missing registered identity")
            if (registered.role != "SUPPLIER" || !registered.email.equals(request.email, true)) throw AuthFailure("Registered identity mismatch")
            val auth = authenticate(request, ensureCurrent)
            if (auth.userId != registered.userId) throw AuthFailure("Authenticated identity mismatch")
            createProfile(request, auth, ensureCurrent)
            Response.success(auth)
        }
    }

    suspend fun recover(input: SignupRequest, ensureCurrent: () -> Unit): Response<AuthResponse> {
        val request = SignupValidation.prepareSupplierRecovery(input)
        // This explicit recovery path never calls auth/register. Read before any new profile write.
        return finish {
            val auth = authenticate(request, ensureCurrent)
            ensureCurrent()
            val profile = api.supplierProfile(auth.userId.toLong(), "Bearer ${auth.accessToken}")
            ensureCurrent()
            if (profile.code() == 404) {
                profile.errorBody()?.close()
                createProfile(request, auth, ensureCurrent)
            } else {
                if (!profile.isSuccessful) { profile.errorBody()?.close(); throw AuthFailure("Profile lookup unavailable") }
                verifyProfile(profile.body()?.requireData(), auth)
            }
            Response.success(auth)
        }
    }

    private suspend fun authenticate(request: SignupRequest, ensureCurrent: () -> Unit): AuthResponse {
        ensureCurrent()
        val login = api.login(CanonicalLoginRequest(request.email, request.password))
        ensureCurrent()
        if (!login.isSuccessful) { login.errorBody()?.close(); throw AuthFailure("Sign-in did not complete") }
        val auth = login.body()?.requireData()?.toAndroidAuth(origin) ?: throw AuthFailure("Missing authenticated identity")
        if (auth.role != "SUPPLIER" || !auth.email.equals(request.email, true)) throw AuthFailure("Authenticated Supplier identity mismatch")
        return auth
    }

    private suspend fun createProfile(request: SignupRequest, auth: AuthResponse, ensureCurrent: () -> Unit) {
        ensureCurrent()
        val profile = api.createSupplierProfile(auth.userId.toLong(), "Bearer ${auth.accessToken}",
            CanonicalSupplierProfileRequest(requireNotNull(request.businessName), requireNotNull(request.businessLocation)))
        ensureCurrent()
        if (!profile.isSuccessful) { profile.errorBody()?.close(); throw AuthFailure("Business setup did not complete") }
        verifyProfile(profile.body()?.requireData(), auth)
    }

    private fun verifyProfile(profile: CanonicalCreatedSupplierProfileDto?, auth: AuthResponse) {
        if (profile?.userId != auth.userId.toLong() || profile.id == null || profile.id <= 0)
            throw AuthFailure("Business profile ownership could not be verified")
    }

    private suspend fun <T> finish(action: suspend () -> T): T = try { action() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { throw recovery("The account may already exist, but sign-in or business setup could not be confirmed.") }

    private fun recovery(reason: String) = SupplierSignupRecoveryRequired(
        "$reason Do not register again. Use Sign in and finish Supplier setup with the same email, password and business details. " +
            "If sign-in still fails, contact your administrator; do not repeatedly create accounts.")
}
