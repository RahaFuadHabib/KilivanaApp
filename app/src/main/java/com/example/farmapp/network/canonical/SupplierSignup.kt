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
        catch (e: Exception) { throw recovery("${CanonicalAuthErrors.exception(e, "Registration").message} Account creation could not be confirmed.") }
        if (!response.isSuccessful) {
            val code = response.code()
            if (code !in setOf(400, 401, 403, 415, 422, 429)) {
                throw recovery(CanonicalAuthErrors.registration(response))
            }
            return Response.error(code, response.errorBody() ?: "{}".toResponseBody())
        }
        ensureCurrent()
        val registered = try {
            val account = response.body()?.requireData()?.toAccount() ?: throw AuthFailure("Missing registered identity")
            if (account.role != "SUPPLIER" || !account.email.equals(request.email, true)) throw AuthFailure("Registered identity mismatch")
            account
        } catch (_: Exception) { throw recovery("Registration returned an incomplete or unexpected response format. Account creation could not be confirmed.") }
        val registeredUser = response.body()!!.requireData()
        requireEmailVerification(registeredUser, request.email, registered = true)
        return finish("Your Supplier account was created, but setup is incomplete.") {
            val auth = authenticate(request, ensureCurrent)
            if (auth.userId != registered.userId) throw AuthFailure("Authenticated identity mismatch")
            createProfile(request, auth, ensureCurrent)
            Response.success(auth)
        }
    }

    suspend fun recover(input: SignupRequest, ensureCurrent: () -> Unit): Response<AuthResponse> {
        val request = SignupValidation.prepareSupplierRecovery(input)
        // This explicit recovery path never calls auth/register. Read before any new profile write.
        return finish("Supplier setup could not be completed.") {
            val auth = authenticate(request, ensureCurrent)
            completeProfile(request, auth, ensureCurrent)
        }
    }

    suspend fun completeProfile(request: SignupRequest, auth: AuthResponse, ensureCurrent: () -> Unit): Response<AuthResponse> {
            ensureCurrent()
            val profile = api.supplierProfile(auth.userId.toLong(), "Bearer ${auth.accessToken}")
            ensureCurrent()
            if (profile.code() == 404) {
                profile.errorBody()?.close()
                createProfile(request, auth, ensureCurrent)
            } else {
                if (!profile.isSuccessful) { profile.errorBody()?.close(); throw AuthFailure("Supplier profile lookup was unavailable (HTTP ${profile.code()}). No profile was created.") }
                verifyProfile(profile.body()?.requireData(), auth)
            }
            return Response.success(auth)
    }

    private suspend fun authenticate(request: SignupRequest, ensureCurrent: () -> Unit): AuthResponse {
        ensureCurrent()
        val login = try { api.login(CanonicalLoginRequest(request.email, request.password)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { throw CanonicalAuthErrors.exception(e) }
        ensureCurrent()
        if (!login.isSuccessful) throw CanonicalAuthErrors.login(login, request.email)
        val data = login.body()?.requireData() ?: throw AuthFailure("Missing authenticated identity")
        if (data.emailVerificationRequired == true || data.user?.emailVerified == false)
            throw EmailVerificationRequired(EmailVerificationPending(request.email, data.emailVerificationChallengeId ?: data.user?.emailVerificationChallengeId))
        val auth = data.toAndroidAuth(origin)
        if (auth.role != "SUPPLIER" || !auth.email.equals(request.email, true)) throw AuthFailure("Authenticated Supplier identity mismatch")
        return auth
    }

    private suspend fun createProfile(request: SignupRequest, auth: AuthResponse, ensureCurrent: () -> Unit) {
        ensureCurrent()
        val profile = api.createSupplierProfile(auth.userId.toLong(), "Bearer ${auth.accessToken}",
            CanonicalSupplierProfileRequest(requireNotNull(request.businessName), requireNotNull(request.businessLocation)))
        ensureCurrent()
        if (!profile.isSuccessful) { profile.errorBody()?.close(); throw AuthFailure("Supplier business setup was not confirmed (HTTP ${profile.code()}). Check the profile before submitting again.") }
        verifyProfile(profile.body()?.requireData(), auth)
    }

    private fun verifyProfile(profile: CanonicalCreatedSupplierProfileDto?, auth: AuthResponse) {
        if (profile?.userId != auth.userId.toLong() || profile.id == null || profile.id <= 0)
            throw AuthFailure("Business profile ownership could not be verified")
    }

    private suspend fun <T> finish(context: String, action: suspend () -> T): T = try { action() }
        catch (e: CancellationException) { throw e }
        catch (e: EmailVerificationRequired) { throw e }
        catch (e: Exception) {
            val detail = if (e is AuthFailure) e.message else CanonicalAuthErrors.exception(e, "Supplier setup").message
            throw recovery("$context $detail")
        }

    private fun recovery(reason: String) = SupplierSignupRecoveryRequired(
        "$reason Do not register again. Use Sign in and finish Supplier setup with the same email, password and business details. " +
            "If sign-in still fails, contact your administrator; do not repeatedly create accounts.")
}
