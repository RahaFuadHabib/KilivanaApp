package com.example.farmapp.ui.screens.auth

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.canonical.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.http.*
import java.util.UUID

data class EmailOtpRequest(val email: String) { override fun toString() = "EmailOtpRequest(redacted)" }
data class EmailOtpVerifyRequest(val challengeId: UUID, val email: String, val code: String) {
    override fun toString() = "EmailOtpVerifyRequest(redacted)"
}
data class EmailOtpChallenge(val challengeId: UUID? = null, val expiresInSeconds: Int? = null,
    val resendAfterSeconds: Int? = null, val message: String? = null)
interface EmailVerificationApi {
    @POST("auth/email-verification/request") suspend fun request(@Body request: EmailOtpRequest): Response<CanonicalApiResponse<EmailOtpChallenge>>
    @POST("auth/email-verification/verify") suspend fun verify(@Body request: EmailOtpVerifyRequest): Response<CanonicalApiResponse<Unit>>
}
internal fun validEmailOtp(code: String) = Regex("[0-9]{6}").matches(code)
internal data class EmailVerificationState(val email: String = "", val challenge: EmailOtpChallenge? = null,
    val busy: Boolean = false, val error: String? = null, val verified: Boolean = false,
    val expiresAt: Long = 0, val resendAt: Long = 0, val retryAt: Long = 0)

internal class EmailVerificationController(private val api: EmailVerificationApi, private val scope: CoroutineScope,
    private val clock: () -> Long) {
    private var wallClock: java.time.Clock = java.time.Clock.systemUTC()
    internal constructor(api: EmailVerificationApi, scope: CoroutineScope, clock: () -> Long, wallClock: java.time.Clock) : this(api, scope, clock) {
        this.wallClock = wallClock
    }
    private val mutable = MutableStateFlow(EmailVerificationState())
    val state = mutable.asStateFlow()
    private var started = false
    fun begin(pending: EmailVerificationPending) {
        if (started) return
        started = true
        mutable.value = EmailVerificationState(email = pending.email)
        if (pending.challengeId != null) {
            // Registration/login already issued this challenge. Its expiry is not advertised here.
            mutable.value = mutable.value.copy(challenge = EmailOtpChallenge(challengeId = pending.challengeId),
                resendAt = clock() + 60_000)
        } else request(pending.email)
    }
    private class OtpFailure(message: String, val status: Int) : Exception(message)
    private fun <T> envelope(response: Response<CanonicalApiResponse<T>>): CanonicalApiResponse<T> {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            if (response.code() == 429) {
                val now = wallClock.instant()
                val retry = signupRetryAfter(response.headers()["Retry-After"], now)
                val waitMillis = retry?.let { runCatching { java.time.Duration.between(now, it).toMillis() }.getOrNull() }
                if (waitMillis != null && waitMillis > 0 && waitMillis < Long.MAX_VALUE - clock()) mutable.update {
                    it.copy(resendAt = maxOf(it.resendAt, clock() + waitMillis), retryAt = maxOf(it.retryAt, clock() + waitMillis))
                }
            }
            throw OtpFailure(when (response.code()) {
                429 -> "Too many attempts. Wait before requesting or submitting another code."
                400, 401, 410, 422 -> "This code is invalid or expired. Check the latest email or resend the code."
                409 -> "This challenge cannot be used again. Try signing in or request another code."
                else -> "Email verification is unavailable (HTTP ${response.code()}). Try again later."
            }, response.code())
        }
        return (response.body() ?: error("Incomplete verification response.")).also { it.requireSuccess() }
    }
    private fun launch(operation: AuthTraceOperation, action: suspend () -> Unit) {
        if (mutable.value.busy || mutable.value.verified || clock() < mutable.value.retryAt) return
        mutable.update { it.copy(busy = true, error = null) }
        scope.launch {
            try { action(); AuthAttemptTrace.record(AuthAttemptEvent(operation, AuthTraceStep.FLOW_SUCCEEDED)) }
            catch (e: CancellationException) { throw e }
            catch (e: OtpFailure) {
                AuthAttemptTrace.record(AuthAttemptEvent(operation, AuthTraceStep.FLOW_FAILED, e.status,
                    stage = if (operation == AuthTraceOperation.OTP_REQUEST) AuthStage.OTP_REQUEST else AuthStage.OTP_VERIFY, kind = AuthFailureKind.HTTP))
                mutable.update { it.copy(error = e.message) }
            }
            catch (_: Exception) { AuthAttemptTrace.record(AuthAttemptEvent(operation, AuthTraceStep.FLOW_FAILED)); mutable.update { it.copy(error = "Verification was not confirmed. Check the code and connection, or resend when available.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun request(email: String = mutable.value.email) {
        if (clock() < mutable.value.resendAt) return
        launch(AuthTraceOperation.OTP_REQUEST) {
            val value = email.trim()
            check(value.length <= 254 && Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(value))
            mutable.update { it.copy(email = value, resendAt = clock() + 60_000) }
            val challenge = envelope(api.request(EmailOtpRequest(value))).requireData()
            check(challenge.challengeId != null && challenge.expiresInSeconds != null && challenge.expiresInSeconds > 0 &&
                challenge.resendAfterSeconds != null && challenge.resendAfterSeconds >= 0)
            val now = clock()
            mutable.value = EmailVerificationState(value, challenge, busy = true,
                expiresAt = now + challenge.expiresInSeconds.toLong() * 1000,
                resendAt = now + challenge.resendAfterSeconds.toLong() * 1000)
        }
    }
    fun verify(code: String) = launch(AuthTraceOperation.OTP_VERIFY) {
        val challenge = mutable.value.challenge ?: error("Request a verification code first.")
        check(mutable.value.expiresAt == 0L || clock() < mutable.value.expiresAt)
        check(validEmailOtp(code))
        envelope(api.verify(EmailOtpVerifyRequest(challenge.challengeId!!, mutable.value.email, code)))
        mutable.update { it.copy(verified = true, challenge = null) }
    }
}

internal class EmailVerificationModel(api: EmailVerificationApi) : ViewModel() {
    private val controller = EmailVerificationController(api, viewModelScope, SystemClock::elapsedRealtime)
    val state = controller.state
    fun begin(pending: EmailVerificationPending) = controller.begin(pending)
    fun request() = controller.request()
    fun verify(code: String) = controller.verify(code)
    companion object {
        fun factory(api: EmailVerificationApi) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return EmailVerificationModel(api) as T
            }
        }
    }
}

@Composable internal fun EmailVerificationScreen(pending: EmailVerificationPending, onBack: () -> Unit, isDarkTheme: Boolean,
    onToggleTheme: () -> Unit, modifier: Modifier = Modifier, onVerified: () -> Unit,
    api: EmailVerificationApi = ApiClient.emailVerificationApi) {
    val model: EmailVerificationModel = viewModel(key = "email-verification-${pending.flowId}", factory = EmailVerificationModel.factory(api))
    val state by model.state.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(pending.flowId) { model.begin(pending) }
    LaunchedEffect(state.verified) { if (state.verified) { code = ""; onVerified() } }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { now = SystemClock.elapsedRealtime(); delay(1000) } } }
    LaunchedEffect(state.challenge?.challengeId) { code = "" }
    AuthPage("Verify email", isDarkTheme, onToggleTheme, modifier, onBack) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AuthHeading("Verify your email.", "Enter the six-digit code from your email to continue.")
            Text(pending.email)
            AuthField(code, { code = it.filter { char -> char in '0'..'9' }.take(6) }, "6-digit email code", KeyboardType.Number,
                enabled = !state.busy && state.challenge != null)
            if (state.expiresAt > 0) Text(if (now >= state.expiresAt) "Code expired. Resend to get a new code."
                else "Code expires in ${((state.expiresAt - now) / 1000).coerceAtLeast(0)} seconds.")
            AuthPrimaryButton("Verify email", { model.verify(code) }, enabled = !state.busy && state.challenge != null &&
                validEmailOtp(code) && now >= state.retryAt && (state.expiresAt == 0L || now < state.expiresAt), loading = state.busy)
            TextButton(onClick = model::request, enabled = !state.busy && now >= state.resendAt) {
                Text(if (now < state.resendAt) "Resend available in ${((state.resendAt - now) / 1000 + 1).coerceAtLeast(0)} seconds" else "Resend code")
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
