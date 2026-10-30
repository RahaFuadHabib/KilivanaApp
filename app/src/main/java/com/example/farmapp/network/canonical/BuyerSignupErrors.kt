package com.example.farmapp.network.canonical

import com.example.farmapp.repository.AuthFailure
import retrofit2.Response
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration

/** Only a deadline/status and fixed messages are retained. Never echo response values. */
internal class BuyerSignupRejection(message: String, status: Int, val retryAt: Instant? = null) :
    AuthFailure(message, AuthDiagnostic(AuthStage.REGISTRATION_REQUEST, AuthFailureKind.HTTP, status))

internal fun signupRetryAfter(value: String?, now: Instant): Instant? {
    val text = value?.trim()?.takeIf { it.length in 1..128 } ?: return null
    return runCatching {
        if (text.all { it in '0'..'9' }) now.plusSeconds(text.toLong())
        else ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    }.getOrNull()?.takeIf { it.isAfter(now) }
}
internal fun signupWaitMessage(deadline: Instant, now: Instant): String {
    val duration = Duration.between(now, deadline)
    val seconds = duration.seconds + if (duration.nano > 0) 1 else 0
    return "Registration is temporarily rate limited (HTTP 429). Wait ${seconds.coerceAtLeast(1)} seconds before trying again."
}
internal fun buyerSignupRejection(response: Response<*>, now: Instant): BuyerSignupRejection {
    val code = response.code()
    val retryAt = if (code == 429) signupRetryAfter(response.headers()["Retry-After"], now) else null
    val mapped = CanonicalAuthErrors.registration(response)
    val message = when {
        code == 429 -> retryAt?.let { signupWaitMessage(it, now) }
            ?: "Registration is temporarily rate limited (HTTP 429). Please wait and try again later."
        code == 409 && mapped == "An account with these details may already exist." ->
            "The registration service reported conflicting details (HTTP 409). Check your email, phone number and username, or contact support."
        code >= 500 -> "Account creation could not be confirmed (HTTP $code). Try signing in before submitting signup again."
        else -> mapped
    }
    return BuyerSignupRejection(message, code, retryAt)
}
