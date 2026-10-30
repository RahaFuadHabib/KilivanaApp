package com.example.farmapp.network.canonical

import com.example.farmapp.network.InvalidAuthResponse
import com.example.farmapp.repository.AuthFailure
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.stream.MalformedJsonException
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException

/** Only fixed client strings escape this parser. Never return arbitrary server text or field values. */
internal object CanonicalAuthErrors {
    private const val MAX_ERROR_BYTES = 16 * 1024
    private val codes = setOf("BAD_REQUEST", "VALIDATION_ERROR", "UNAUTHORIZED", "FORBIDDEN", "CONFLICT",
        "DATA_INTEGRITY_VIOLATION", "TOO_MANY_REQUESTS", "UNSUPPORTED_MEDIA_TYPE", "MALFORMED_REQUEST_BODY",
        "INVALID_ARGUMENT", "SERVICE_UNAVAILABLE", "INTERNAL_SERVER_ERROR", "RESOURCE_NOT_FOUND", "METHOD_NOT_ALLOWED")
    private val fields = linkedMapOf("name" to "full name", "email" to "email", "phone" to "phone number",
        "password" to "password", "role" to "account type", "username" to "username", "region" to "county/region",
        "businessName" to "business name", "location" to "business location")
    private enum class Reason { CREDENTIALS, INACTIVE, SUSPENDED, EMAIL_TAKEN, PHONE_TAKEN, EMAIL_VERIFICATION }
    private data class Rejection(val code: String? = null, val reason: Reason? = null, val fields: List<String> = emptyList())

    private fun read(body: ResponseBody?): Rejection {
        if (body == null) return Rejection()
        return try {
            body.use {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                it.byteStream().use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_ERROR_BYTES) return Rejection()
                        output.write(buffer, 0, count)
                    }
                }
                val root = JsonParser.parseString(output.toString(Charsets.UTF_8.name()))
                if (!root.isJsonObject) return Rejection()
                val json = root.asJsonObject
                if (json.get("success")?.let { value -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean && !value.asBoolean } != true)
                    return Rejection()
                val error = json.get("error")?.takeIf { value -> value.isJsonObject }?.asJsonObject
                fun string(key: String, from: com.google.gson.JsonObject? = json): String? = from?.get(key)
                    ?.takeIf { value -> value.isJsonPrimitive && value.asJsonPrimitive.isString }?.asString?.takeIf { value -> value.length <= 256 }
                val code = string("code", error)?.takeIf { value -> value in codes }
                val text = listOfNotNull(string("message"), string("details", error))
                val reason = when {
                    "This account is suspended" in text -> Reason.SUSPENDED
                    "This account is inactive" in text -> Reason.INACTIVE
                    "Invalid email or password" in text -> Reason.CREDENTIALS
                    "Email already exists, please log in instead" in text -> Reason.EMAIL_TAKEN
                    "Phone number already exists, please log in instead" in text -> Reason.PHONE_TAKEN
                    "Email verification is required before login" in text -> Reason.EMAIL_VERIFICATION
                    else -> null
                }
                val data = json.get("data")?.takeIf { value -> value.isJsonObject }?.asJsonObject
                Rejection(code, reason, if (code == "VALIDATION_ERROR") fields.filterKeys { key -> data?.has(key) == true }.values.toList() else emptyList())
            }
        } catch (_: Exception) { Rejection() }
    }

    fun login(response: Response<*>, email: String? = null): AuthFailure {
        val rejection = read(response.errorBody())
        if (response.code() == 403 && rejection.code == "FORBIDDEN" && rejection.reason == Reason.EMAIL_VERIFICATION && !email.isNullOrBlank())
            return EmailVerificationRequired(EmailVerificationPending(email))
        return AuthFailure(when (response.code()) {
            400, 422 -> if (rejection.code == "UNAUTHORIZED" && rejection.reason == Reason.CREDENTIALS)
                "Invalid email or password." else "Please check your sign-in details and try again."
            401 -> when {
                rejection.code == "UNAUTHORIZED" && rejection.reason == Reason.SUSPENDED -> "This account is suspended. Contact your administrator."
                rejection.code == "UNAUTHORIZED" && rejection.reason == Reason.INACTIVE -> "This account is inactive. Contact your administrator."
                else -> "Invalid email or password."
            }
            403 -> if (rejection.code == "FORBIDDEN")
                "Sign-in is not permitted for this account. Contact your administrator."
                else "The sign-in service is unavailable (HTTP 403). Please try again later."
            429 -> "Too many sign-in attempts. Please try again later."
            else -> "Sign-in is unavailable (HTTP ${response.code()}). Please try again later."
        }, AuthDiagnostic(AuthStage.LOGIN_REQUEST, AuthFailureKind.HTTP, response.code()))
    }

    fun registration(response: Response<*>): String {
        val rejection = read(response.errorBody())
        return when (response.code()) {
            400, 422 -> if (rejection.fields.isNotEmpty()) "Please check these signup fields: ${rejection.fields.joinToString(", ")}."
                else if (rejection.code == "DATA_INTEGRITY_VIOLATION") "These signup details conflict with an existing record. Check your details or contact your administrator."
                else "Please check your signup details and try again."
            401, 403 -> "Account registration was not permitted. Contact your administrator."
            415 -> "The registration service rejected the request format. Contact your administrator."
            429 -> "Too many signup attempts. Please try again later."
            409 -> when (rejection.reason.takeIf { rejection.code == "CONFLICT" }) {
                Reason.EMAIL_TAKEN -> "An account with this email already exists. Sign in instead."
                Reason.PHONE_TAKEN -> "This phone number is already in use. Use the existing account details or contact your administrator."
                else -> "An account with these details may already exist."
            }
            else -> "The registration service returned HTTP ${response.code()}. Account creation could not be confirmed."
        }
    }

    /** Parsing an otherwise successful response is different from failing to reach/read the service. */
    fun exception(error: Exception, operation: String = "Sign-in"): AuthFailure {
        if (error is AuthFailure) return error
        val diagnostic = when (error) {
            is AuthTransportFailure -> error.diagnostic
            is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> AuthDiagnostic(AuthStage.LOGIN_REQUEST, AuthFailureKind.TIMEOUT)
            is JsonParseException, is MalformedJsonException, is EOFException -> AuthDiagnostic(AuthStage.JSON_DECODE, AuthFailureKind.MALFORMED)
            is InvalidAuthResponse, is CanonicalContractException, is CanonicalResponseFailure, is IllegalArgumentException -> AuthDiagnostic(AuthStage.TOKEN_VALIDATION, AuthFailureKind.CONTRACT)
            else -> AuthDiagnostic(AuthStage.LOGIN_REQUEST, AuthFailureKind.NETWORK)
        }
        return AuthFailure(when (error) {
        is AuthTransportFailure -> if (diagnostic.stage == AuthStage.RESPONSE_READ)
            if (diagnostic.kind == AuthFailureKind.TIMEOUT) "$operation response timed out while being read. Please try again."
            else "$operation response could not be read completely. Check your connection and try again."
            else if (diagnostic.kind == AuthFailureKind.TIMEOUT) "$operation request timed out before response headers arrived. Check your connection and try again."
            else "$operation could not connect to the service. Check your connection and try again."
        is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "$operation request timed out. Check your connection and try again."
        is InvalidAuthResponse, is CanonicalContractException, is CanonicalResponseFailure,
        is JsonParseException, is MalformedJsonException, is EOFException, is IllegalArgumentException ->
            "$operation returned an incomplete or unexpected response format. Please contact your administrator if this continues."
        is IOException -> "$operation could not reach the service. Check your connection and try again."
        else -> "$operation could not be completed because of an unexpected error. Please try again later."
    }, when (operation) {
        "Supplier profile lookup" -> diagnostic.copy(stage = AuthStage.SUPPLIER_PROFILE)
        "Farmer profile lookup" -> diagnostic.copy(stage = AuthStage.FARMER_PROFILE)
        else -> diagnostic
    })
    }
}
