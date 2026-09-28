package com.example.farmapp.network

import com.example.farmapp.data.model.AuthResponse
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

class InvalidAuthResponse : IOException("The server returned an invalid authentication response.")

object AuthValidation {
    val supportedRoles = setOf("FARMER", "BUYER", "SUPPLIER", "INSPECTOR")

    fun validate(auth: AuthResponse?): AuthResponse {
        // Gson can assign null to a Kotlin non-null field when a required field is absent.
        if (auth == null || auth.userId.isNullOrBlank() || auth.email.isNullOrBlank() ||
            auth.fullName.isNullOrBlank() || auth.role.isNullOrBlank() ||
            auth.accessToken.isNullOrBlank() || auth.refreshToken.isNullOrBlank() ||
            auth.refreshTokenExpiresAt.isNullOrBlank()) throw InvalidAuthResponse()
        try {
            if (!UUID.fromString(auth.userId).toString().equals(auth.userId, ignoreCase = true)) {
                throw InvalidAuthResponse()
            }
            expiry(auth)
        } catch (_: IllegalArgumentException) {
            throw InvalidAuthResponse()
        } catch (_: java.time.DateTimeException) {
            throw InvalidAuthResponse()
        }
        return auth
    }

    fun expiry(auth: AuthResponse): Instant = OffsetDateTime.parse(auth.refreshTokenExpiresAt).toInstant()
    fun canRefresh(auth: AuthResponse, now: Instant = Instant.now()) = expiry(auth).isAfter(now)
}
