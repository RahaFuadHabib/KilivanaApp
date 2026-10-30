package com.example.farmapp.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.BuildConfig
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.canonical.CanonicalApiResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.http.*

interface PasswordRecoveryApi {
    @POST("auth/forgot-password") suspend fun request(@Query("email") email: String): Response<CanonicalApiResponse<String>>
}
internal data class RecoveryState(val busy: Boolean = false, val sent: Boolean = false, val error: String? = null)
internal class PasswordRecoveryModel : ViewModel() {
    private val mutable = MutableStateFlow(RecoveryState())
    val state = mutable.asStateFlow()
    fun resetForm() { if (!mutable.value.busy) mutable.value = RecoveryState() }
    fun request(email: String) {
        if (mutable.value.busy || mutable.value.sent) return
        val value = email.trim()
        if (!Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(value) || value.length > 254) {
            mutable.value = RecoveryState(error = "Enter the email address used for your account."); return
        }
        mutable.value = RecoveryState(busy = true)
        viewModelScope.launch {
            try {
                val response = ApiClient.passwordRecoveryApi.request(value)
                if (!response.isSuccessful) { response.errorBody()?.close(); error("Recovery request failed.") }
                response.body()?.requireSuccess() ?: error("Incomplete response.")
                mutable.value = RecoveryState(sent = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = RecoveryState(error = "Recovery request was not confirmed. Check your email before requesting another link.") }
        }
    }
}

@Composable internal fun PasswordRecoveryScreen(onBack: () -> Unit, isDarkTheme: Boolean, onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier) {
    val model: PasswordRecoveryModel = viewModel(key = "password-recovery")
    val state by model.state.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    AuthPage("Reset Password", isDarkTheme, onToggleTheme, modifier, onBack) {
        AgriculturalVisual(password = true)
        Column(Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AuthHeading("Recover your account.", "Request password reset instructions using your registered email address.")
            if (!BuildConfig.USE_CANONICAL_BACKEND || BuildConfig.USE_MOCK_AUTH) {
                Text("Password recovery requires the online Kilivana service.")
            } else if (state.sent) {
                Text("If this email belongs to an eligible account, reset instructions have been sent. Open the link in your email, reset your password, then return here to sign in.")
                Text("This request does not verify your email or change your password by itself.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { model.resetForm(); email = "" }) { Text("Use another email") }
            } else {
                AuthField(email, { email = it }, "Registered email", KeyboardType.Email, enabled = !state.busy)
                AuthPrimaryButton("Send reset instructions", { model.request(email) }, enabled = !state.busy, loading = state.busy)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            TextButton(onClick = onBack, enabled = !state.busy) { Text("Back to Sign In") }
        }
    }
}
