package com.example.farmapp.viewmodel

import android.content.Context
import com.example.farmapp.data.model.SignupRequest
import com.example.farmapp.network.canonical.AuthAttemptTrace
import com.example.farmapp.network.canonical.AuthAttemptEvent
import com.example.farmapp.network.canonical.AuthTraceOperation
import com.example.farmapp.network.canonical.AuthTraceStep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.farmapp.BuildConfig
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.EncryptedSessionPersistence
import com.example.farmapp.network.MockAuthService
import com.example.farmapp.network.SessionPersistence
import com.example.farmapp.network.canonical.SupplierProfileSetupRequired
import com.example.farmapp.network.canonical.EmailVerificationPending
import com.example.farmapp.network.canonical.EmailVerificationRequired
import com.example.farmapp.repository.AuthFailure
import com.example.farmapp.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isInitializing: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val supplierRegistrationRecovery: Boolean = false,
    val supplierProfileSetupRequired: Boolean = false,
    val emailVerification: EmailVerificationPending? = null,
    val diagnosticMessage: String? = null
)

class AuthViewModel(
    private val repository: AuthRepository,
    private val persistence: () -> SessionPersistence
) : ViewModel() {
    private val state = MutableStateFlow(AuthUiState())
    val uiState = state.asStateFlow()
    val session = repository.session
    val demoMode = repository.demoMode
    val canonicalMode = repository.canonicalMode
    private var storageReady = false
    // Continuation credentials exist only in this ViewModel's memory, never saved state or disk.
    private var resumeVerified: (suspend () -> Unit)? = null
    private var verificationFlow: EmailVerificationPending? = null
    private fun verificationPending(next: EmailVerificationPending): EmailVerificationPending {
        val previous = verificationFlow
        val pending = if (previous != null && previous.email.equals(next.email, true) &&
            (next.challengeId == null || next.challengeId == previous.challengeId))
            next.copy(flowId = previous.flowId) else next
        verificationFlow = pending
        return pending
    }

    init {
        viewModelScope.launch {
            try {
                restore()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(errorMessage = "Unable to restore the secure session. Please sign in again.", diagnosticMessage = (e as? AuthFailure)?.diagnostic?.summary) }
            } finally {
                state.update { it.copy(isInitializing = false) }
            }
        }
    }

    private suspend fun restore() {
        if (!storageReady) {
            try { repository.restore(persistence()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { throw AuthFailure("Secure session storage could not be initialized.",
                com.example.farmapp.network.canonical.AuthDiagnostic(com.example.farmapp.network.canonical.AuthStage.SESSION_RESTORE, com.example.farmapp.network.canonical.AuthFailureKind.STORAGE)) }
            storageReady = true
        }
    }

    fun clearError() { state.update { it.copy(errorMessage = null, diagnosticMessage = null) } }
    fun consumeSupplierSetupNavigation() { state.update { it.copy(supplierProfileSetupRequired = false) } }

    fun login(identifier: String, password: String) {
        if (state.value.isInitializing || state.value.isLoading) return
        resumeVerified = null
        state.update { it.copy(isLoading = true, errorMessage = null, diagnosticMessage = null, supplierProfileSetupRequired = false, emailVerification = null) }
        viewModelScope.launch {
            try {
                restore()
                repository.login(identifier, password)
                AuthAttemptTrace.record(AuthAttemptEvent(AuthTraceOperation.LOGIN, AuthTraceStep.FLOW_SUCCEEDED))
                verificationFlow = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: EmailVerificationRequired) {
                AuthAttemptTrace.record(AuthAttemptEvent(AuthTraceOperation.LOGIN, AuthTraceStep.VERIFICATION_REQUIRED))
                resumeVerified = { repository.login(identifier, password) }
                state.update { it.copy(emailVerification = verificationPending(e.pending), errorMessage = null) }
            } catch (e: SupplierProfileSetupRequired) {
                state.update { it.copy(errorMessage = e.message, supplierProfileSetupRequired = true) }
            } catch (e: AuthFailure) {
                AuthAttemptTrace.record(AuthAttemptEvent(AuthTraceOperation.LOGIN, AuthTraceStep.FLOW_FAILED, e.diagnostic?.httpStatus, stage = e.diagnostic?.stage, kind = e.diagnostic?.kind))
                state.update { it.copy(errorMessage = e.message, diagnosticMessage = e.diagnostic?.summary) }
            } catch (_: Exception) {
                // Never surface exception bodies or URLs which could contain sensitive server data.
                state.update { it.copy(errorMessage = "Unable to complete sign-in. Please try again or contact your administrator.") }
            } finally {
                state.update { it.copy(isLoading = false, supplierRegistrationRecovery = repository.supplierRegistrationNeedsRecovery) }
            }
        }
    }

    fun signup(request: SignupRequest) = submitSignup(request, false)
    fun recoverSupplierSignup(request: SignupRequest) = submitSignup(request, true)

    private fun submitSignup(request: SignupRequest, recover: Boolean) {
        if (state.value.isInitializing || state.value.isLoading) return
        state.update { it.copy(isLoading = true, errorMessage = null, diagnosticMessage = null) }
        viewModelScope.launch {
            try {
                restore()
                if (recover) repository.recoverSupplierSignup(request) else repository.signup(request)
                AuthAttemptTrace.record(AuthAttemptEvent(AuthTraceOperation.REGISTER, AuthTraceStep.FLOW_SUCCEEDED))
                verificationFlow = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: EmailVerificationRequired) {
                AuthAttemptTrace.record(AuthAttemptEvent(AuthTraceOperation.REGISTER, AuthTraceStep.VERIFICATION_REQUIRED))
                resumeVerified = {
                    if (recover) repository.recoverSupplierSignup(request)
                    else if (e.pending.registeredUserId != null) repository.completeVerifiedSignup(request, e.pending.registeredUserId)
                    else repository.login(request.email, request.password)
                }
                state.update { it.copy(emailVerification = verificationPending(e.pending), errorMessage = null) }
            } catch (e: AuthFailure) {
                AuthAttemptTrace.record(AuthAttemptEvent(AuthTraceOperation.REGISTER, AuthTraceStep.FLOW_FAILED, e.diagnostic?.httpStatus, stage = e.diagnostic?.stage, kind = e.diagnostic?.kind))
                state.update { it.copy(errorMessage = e.message, diagnosticMessage = e.diagnostic?.summary) }
            } catch (_: Exception) {
                // Never surface exception bodies or URLs which could contain sensitive server data.
                state.update { it.copy(errorMessage = if (request.role == "SUPPLIER")
                    "Account setup could not be confirmed. If you already submitted registration, use Sign in and finish Supplier setup; do not register again."
                    else "Unable to create your account. Check your connection and try again.") }
            } finally {
                state.update { it.copy(isLoading = false, supplierRegistrationRecovery = repository.supplierRegistrationNeedsRecovery) }
            }
        }
    }

    fun cancelEmailVerification() {
        resumeVerified = null
        state.update { it.copy(emailVerification = null, errorMessage = "Email verification is incomplete. Sign in when you are ready to continue.") }
    }

    /** Called only after the verification endpoint returned its successful envelope. */
    fun completeEmailVerification() {
        if (state.value.isLoading || state.value.emailVerification == null) return
        val action = resumeVerified
        resumeVerified = null
        verificationFlow = null
        state.update { it.copy(emailVerification = null, isLoading = true, errorMessage = null, diagnosticMessage = null) }
        viewModelScope.launch {
            try {
                restore()
                if (action == null) throw AuthFailure("Email verified. Sign in to continue.")
                action.invoke()
                AuthAttemptTrace.record(AuthAttemptEvent(AuthTraceOperation.LOGIN, AuthTraceStep.FLOW_SUCCEEDED))
            }
            catch (e: CancellationException) { throw e }
            catch (e: SupplierProfileSetupRequired) { state.update { it.copy(errorMessage = e.message, supplierProfileSetupRequired = true) } }
            catch (_: EmailVerificationRequired) { state.update { it.copy(errorMessage = "Email verification succeeded, but sign-in still requires verification. Try signing in again or contact your administrator.") } }
            catch (e: AuthFailure) { AuthAttemptTrace.record(AuthAttemptEvent(AuthTraceOperation.LOGIN, AuthTraceStep.FLOW_FAILED, e.diagnostic?.httpStatus, stage = e.diagnostic?.stage, kind = e.diagnostic?.kind))
                state.update { it.copy(errorMessage = e.message, diagnosticMessage = e.diagnostic?.summary) } }
            catch (_: Exception) { state.update { it.copy(errorMessage = "Email verified, but account setup could not be completed. Sign in to continue; do not register again.") } }
            finally { state.update { it.copy(isLoading = false, supplierRegistrationRecovery = repository.supplierRegistrationNeedsRecovery) } }
        }
    }

    override fun onCleared() { resumeVerified = null; verificationFlow = null }

    fun logout() {
        if (state.value.isLoading) return
        state.update { it.copy(isLoading = true, errorMessage = null, diagnosticMessage = null) }
        viewModelScope.launch {
            try {
                repository.logout()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.update { it.copy(errorMessage = "Signed out locally. Secure storage could not be fully cleared.") }
            } finally {
                state.update { it.copy(isLoading = false) }
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(AuthViewModel::class.java))
                    val demo = BuildConfig.DEBUG && BuildConfig.USE_MOCK_AUTH
                    val service = if (demo) MockAuthService() else ApiClient.authService
                    return AuthViewModel(AuthRepository(service, demoMode = demo)) {
                        EncryptedSessionPersistence(app)
                    } as T
                }
            }
        }
    }
}
