package com.example.farmapp.viewmodel

import android.content.Context
import com.example.farmapp.data.model.SignupRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.farmapp.BuildConfig
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.EncryptedSessionPersistence
import com.example.farmapp.network.MockAuthService
import com.example.farmapp.network.SessionPersistence
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
    val errorMessage: String? = null
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

    init {
        viewModelScope.launch {
            try {
                restore()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.update { it.copy(errorMessage = "Unable to restore the secure session. Please sign in again.") }
            } finally {
                state.update { it.copy(isInitializing = false) }
            }
        }
    }

    private suspend fun restore() {
        if (!storageReady) {
            repository.restore(persistence())
            storageReady = true
        }
    }

    fun clearError() { state.update { it.copy(errorMessage = null) } }

    fun login(identifier: String, password: String) {
        if (state.value.isInitializing || state.value.isLoading) return
        state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                restore()
                repository.login(identifier, password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthFailure) {
                state.update { it.copy(errorMessage = e.message) }
            } catch (_: Exception) {
                // Never surface exception bodies or URLs which could contain sensitive server data.
                state.update { it.copy(errorMessage = "Unable to sign in. Check your connection and try again.") }
            } finally {
                state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun signup(request: SignupRequest) {
        if (state.value.isInitializing || state.value.isLoading) return
        state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                restore()
                repository.signup(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthFailure) {
                state.update { it.copy(errorMessage = e.message) }
            } catch (_: Exception) {
                // Never surface exception bodies or URLs which could contain sensitive server data.
                state.update { it.copy(errorMessage = "Unable to create your account. Check your connection and try again.") }
            } finally {
                state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun logout() {
        if (state.value.isLoading) return
        state.update { it.copy(isLoading = true, errorMessage = null) }
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
