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
import com.example.farmapp.network.canonical.SupplierProfileSetupRequired
import com.example.farmapp.network.canonical.CanonicalAuthErrors

open class AuthFailure(message: String, val diagnostic: com.example.farmapp.network.canonical.AuthDiagnostic? = null) : IOException(message)

class AuthRepository(
    private val service: ApiService,
    private val sessions: SessionStore = TokenManager,
    val demoMode: Boolean = false,
    private val signupClock: java.time.Clock = java.time.Clock.systemUTC()
) {
    val canonicalMode = service is com.example.farmapp.network.canonical.CanonicalAuthService
    val session = sessions.session
    private val signupGate = AtomicBoolean(false)
    private var buyerSignupRetryAt: java.time.Instant? = null
    private val supplierRecovery = AtomicBoolean(false)
    private var registrationAwaitingVerification: com.example.farmapp.network.canonical.EmailVerificationPending? = null
    private var pendingProfileSetup: SignupRequest? = null
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
        val response = try { service.login(LoginRequest(identifier.trim(), password)) }
            catch (e: CancellationException) { throw e }
            catch (e: AuthFailure) { throw e }
            catch (e: Exception) { throw CanonicalAuthErrors.exception(e) }
        if (!response.isSuccessful) {
            if (canonicalMode) throw CanonicalAuthErrors.login(response)
            response.errorBody()?.close()
            throw AuthFailure(when (response.code()) {
                400, 401 -> "Invalid email or phone number, or password."
                403 -> "Sign-in is not permitted for this account."
                429 -> "Too many sign-in attempts. Please try again later."
                else -> "Sign-in is unavailable. Please try again."
            })
        }
        val auth = try { AuthValidation.validateBackend(response.body()) }
            catch (e: InvalidAuthResponse) { throw CanonicalAuthErrors.exception(e) }
        if (!AuthValidation.canRefresh(auth)) throw AuthFailure("The server returned an expired session. Please sign in again.")
        val pending = pendingProfileSetup
        if (service is CanonicalAuthService && pending != null && registrationAwaitingVerification?.registeredUserId?.toString() == auth.userId) {
            service.finishRegisteredProfile(pending, auth) { checkSignupSession(before) }
        }
        if (service is CanonicalAuthService && auth.role == "SUPPLIER") {
            val ensureCurrent: () -> Unit = {
                if (sessions.session.value !== before || before.loggingOut)
                    throw AuthFailure("The session changed. Sign in again to continue Supplier setup.")
            }
            if (!service.supplierProfileExists(auth, ensureCurrent)) {
                currentCoroutineContext().ensureActive()
                ensureCurrent()
                // Authentication succeeded; this is setup guidance, not a new registration attempt.
                supplierRecovery.set(true)
                throw SupplierProfileSetupRequired()
            }
        }
        currentCoroutineContext().ensureActive()
        try {
            if (!sessions.establish(before, auth, demoMode)) throw AuthFailure("The session changed. Please try signing in again.", com.example.farmapp.network.canonical.AuthDiagnostic(com.example.farmapp.network.canonical.AuthStage.SESSION_CHANGED, com.example.farmapp.network.canonical.AuthFailureKind.CONTRACT))
        } catch (e: AuthFailure) { throw e }
        catch (_: Exception) { throw AuthFailure("Sign-in succeeded, but the secure session could not be saved. Please try again.", com.example.farmapp.network.canonical.AuthDiagnostic(com.example.farmapp.network.canonical.AuthStage.SESSION_SAVE, com.example.farmapp.network.canonical.AuthFailureKind.STORAGE)) }
        if (pending != null && registrationAwaitingVerification?.registeredUserId?.toString() == auth.userId) supplierRecovery.set(false)
        registrationAwaitingVerification = null
        pendingProfileSetup = null
    }

    suspend fun signup(input: SignupRequest) = withContext(Dispatchers.IO) {
        if (!signupGate.compareAndSet(false, true)) throw AuthFailure("Account setup is already in progress. Please wait.")
        try {
            if (demoMode) throw AuthFailure("Account creation is unavailable in offline demo mode.")
            val request = SignupValidation.prepare(input)
            val canonicalBuyer = request.role == "BUYER" && service is CanonicalAuthService
            if (canonicalBuyer) buyerSignupRetryAt?.takeIf { it.isAfter(signupClock.instant()) }?.let {
                throw com.example.farmapp.network.canonical.BuyerSignupRejection(
                    com.example.farmapp.network.canonical.signupWaitMessage(it, signupClock.instant()), 429, it)
            }
            if (registrationAwaitingVerification != null) throw AuthFailure("Your account was already created. Sign in to continue email verification; do not register again.")
            val before = sessions.session.value
            val supplier = request.role == "SUPPLIER" && service is CanonicalAuthService
            val response = if (supplier) {
                if (supplierRecovery.get()) throw SupplierSignupRecoveryRequired("The account may already exist. Use Sign in and finish Supplier setup; do not register again.")
                checkSignupSession(before)
                // Remains set on cancellation, lost responses and partial success. No automatic registration retry.
                supplierRecovery.set(true)
                (service as CanonicalAuthService).signupSupplier(request) { checkSignupSession(before) }
            } else try { service.signup(request) }
            catch (e: CancellationException) { throw e }
            catch (e: AuthFailure) { throw e }
            catch (e: Exception) {
                if (canonicalBuyer) throw AuthFailure("Registration could not be confirmed. It may have reached the service. Try signing in before submitting signup again.",
                    com.example.farmapp.network.canonical.AuthDiagnostic(
                        com.example.farmapp.network.canonical.AuthStage.REGISTRATION_REQUEST,
                        when (e) {
                            is com.google.gson.JsonParseException, is com.google.gson.stream.MalformedJsonException,
                            is com.example.farmapp.network.canonical.CanonicalContractException, is InvalidAuthResponse -> com.example.farmapp.network.canonical.AuthFailureKind.CONTRACT
                            is java.io.InterruptedIOException -> com.example.farmapp.network.canonical.AuthFailureKind.TIMEOUT
                            else -> com.example.farmapp.network.canonical.AuthFailureKind.NETWORK
                        }))
                else throw e
            }
            if (!response.isSuccessful) {
                if (supplier) supplierRecovery.set(false) // Supplier service returns only definite pre-creation rejections here.
                if (supplier) throw AuthFailure(CanonicalAuthErrors.registration(response))
                if (canonicalBuyer) {
                    val failure = com.example.farmapp.network.canonical.buyerSignupRejection(response, signupClock.instant())
                    if (failure.retryAt != null) buyerSignupRetryAt = failure.retryAt
                    throw failure
                }
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
        } catch (e: com.example.farmapp.network.canonical.EmailVerificationRequired) {
            if (e.pending.registeredUserId != null) {
                registrationAwaitingVerification = e.pending
                pendingProfileSetup = SignupValidation.prepare(input).copy(password = "")
            }
            throw e
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

    suspend fun completeVerifiedSignup(input: SignupRequest, registeredId: Long) = withContext(Dispatchers.IO) {
        if (!signupGate.compareAndSet(false, true)) throw AuthFailure("Account setup is already in progress.")
        try {
            if (service !is CanonicalAuthService) throw AuthFailure("Email verification requires the shared backend.")
            val before = sessions.session.value
            val response = service.finishVerifiedSignup(SignupValidation.prepare(input), registeredId) { checkSignupSession(before) }
            checkSignupSession(before)
            val auth = AuthValidation.validateBackend(response.body())
            currentCoroutineContext().ensureActive()
            if (!sessions.establish(before, auth, false)) throw AuthFailure("The session changed. Sign in to continue.")
            supplierRecovery.set(false)
            registrationAwaitingVerification = null
            pendingProfileSetup = null
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
