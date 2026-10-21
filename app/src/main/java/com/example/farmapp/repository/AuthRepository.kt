package com.example.farmapp.repository

import com.example.farmapp.data.model.SignupRequest
import com.example.farmapp.data.model.LoginRequest
import com.example.farmapp.data.model.LogoutRequest
import com.example.farmapp.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import com.example.farmapp.network.canonical.CanonicalAuthService
import com.example.farmapp.network.canonical.SupplierSignupRecoveryRequired

open class AuthFailure(message: String) : IOException(message)

class AuthRepository(
    private val service: ApiService,
    private val sessions: SessionStore = TokenManager,
    val demoMode: Boolean = false
) {
    val canonicalMode = service is com.example.farmapp.network.canonical.CanonicalAuthService
    val session = sessions.session
    private val signupGate = AtomicBoolean(false)
    private val supplierRecovery = AtomicBoolean(false)
    val supplierRegistrationNeedsRecovery get() = supplierRecovery.get()

    suspend fun restore(persistence: SessionPersistence) = withContext(Dispatchers.IO) {
        sessions.initialize(object : SessionPersistence {
            override fun read(): com.example.farmapp.data.model.AuthResponse? {
                val saved = persistence.read() ?: return null
                if (service is com.example.farmapp.network.canonical.CanonicalAuthService &&
                    (saved.backendContract != "canonical" || saved.sessionOrigin != service.origin)) {
                    persistence.clear(); return null
                }
                return saved
            }
            override fun write(auth: com.example.farmapp.data.model.AuthResponse) = persistence.write(auth)
            override fun clear() = persistence.clear()
        })
    }

    suspend fun login(identifier: String, password: String) = withContext(Dispatchers.IO) {
        if (identifier.isBlank() || password.isBlank()) throw AuthFailure("Please enter your email or phone number and password.")
        val before = sessions.session.value
        val response = service.login(LoginRequest(identifier.trim(), password))
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw AuthFailure(when (response.code()) {
                400, 401 -> "Invalid email or phone number, or password."
                403 -> "Sign-in is not permitted for this account."
                429 -> "Too many sign-in attempts. Please try again later."
                else -> "Sign-in is unavailable. Please try again."
            })
        }
        val auth = AuthValidation.validateBackend(response.body())
        if (!AuthValidation.canRefresh(auth)) throw AuthFailure("The server returned an expired session. Please sign in again.")
        currentCoroutineContext().ensureActive()
        if (!sessions.establish(before, auth, demoMode)) throw AuthFailure("The session changed. Please try signing in again.")
    }

    suspend fun signup(input: SignupRequest) = withContext(Dispatchers.IO) {
        if (!signupGate.compareAndSet(false, true)) throw AuthFailure("Account setup is already in progress. Please wait.")
        try {
            if (demoMode) throw AuthFailure("Account creation is unavailable in offline demo mode.")
            val request = SignupValidation.prepare(input)
            val before = sessions.session.value
            val supplier = request.role == "SUPPLIER" && service is CanonicalAuthService
            val response = if (supplier) {
                if (supplierRecovery.get()) throw SupplierSignupRecoveryRequired("The account may already exist. Use Sign in and finish Supplier setup; do not register again.")
                checkSignupSession(before)
                // Remains set on cancellation, lost responses and partial success. No automatic registration retry.
                supplierRecovery.set(true)
                (service as CanonicalAuthService).signupSupplier(request) { checkSignupSession(before) }
            } else service.signup(request)
            if (!response.isSuccessful) {
                if (supplier) supplierRecovery.set(false) // Supplier service returns only definite pre-creation rejections here.
                response.errorBody()?.close()
                throw AuthFailure(when (response.code()) {
                    400 -> "Please check your signup details and try again."
                    409 -> "An account with these details may already exist. Try signing in or use different details."
                    429 -> "Too many signup attempts. Please try again later."
                    503 -> "Account creation is temporarily unavailable. Please try again later."
                    else -> "Unable to create your account. Please try again."
                })
            }
            val auth = AuthValidation.validateBackend(response.body())
            if (!AuthValidation.canRefresh(auth)) throw AuthFailure("The server returned an expired session. Please sign in again.")
            currentCoroutineContext().ensureActive()
            if (!sessions.establish(before, auth, false)) throw AuthFailure("The session changed. Please try again.")
            if (supplier) supplierRecovery.set(false)
        } finally { signupGate.set(false) }
    }

    suspend fun recoverSupplierSignup(input: SignupRequest) = withContext(Dispatchers.IO) {
        if (!signupGate.compareAndSet(false, true)) throw AuthFailure("Account setup is already in progress. Please wait.")
        try {
            if (demoMode || service !is CanonicalAuthService) throw AuthFailure("Supplier setup recovery requires the shared backend.")
            val request = SignupValidation.prepareSupplierRecovery(input)
            val before = sessions.session.value
            checkSignupSession(before)
            supplierRecovery.set(true)
            val response = service.recoverSupplierSignup(request) { checkSignupSession(before) }
            val auth = AuthValidation.validateBackend(response.body())
            if (!response.isSuccessful || !AuthValidation.canRefresh(auth)) throw AuthFailure("Supplier setup could not be confirmed.")
            currentCoroutineContext().ensureActive()
            if (!sessions.establish(before, auth, false)) throw AuthFailure("The session changed. Sign in again to continue.")
            supplierRecovery.set(false)
        } finally { signupGate.set(false) }
    }

    private fun checkSignupSession(expected: SessionSnapshot) {
        if (expected.auth != null || expected.loggingOut || expected.isDemo || sessions.session.value !== expected)
            throw AuthFailure("The session changed. Sign in again to continue Supplier setup.")
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val current = sessions.beginLogout() ?: return@withContext
        try {
            current.auth?.refreshToken?.takeIf { it.isNotBlank() && current.auth?.backendContract != "canonical" }?.let {
                val response = service.logout(LogoutRequest(it))
                response.errorBody()?.close()
                // A rejected/expired server token still ends the local session.
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            // Offline or server failure must not trap the user in a local authenticated session.
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { sessions.clear(current) }
        }
    }
}
