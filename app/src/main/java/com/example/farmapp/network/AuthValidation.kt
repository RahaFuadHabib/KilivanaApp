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
        if (auth == null || auth.userId.isNullOrBlank() || auth.email.isNullOrBlank() ||
            auth.fullName.isNullOrBlank() || auth.role.isNullOrBlank() ||
            auth.accessToken.isNullOrBlank() || auth.refreshToken.isNullOrBlank()) throw InvalidAuthResponse()
        try {
            if (auth.backendContract == "canonical") {
                val id = auth.userId.toLongOrNull()
                if (id == null || id <= 0 || id.toString() != auth.userId ||
                    auth.sessionOrigin.isNullOrBlank() || !auth.refreshTokenExpiresAt.isNullOrEmpty()) throw InvalidAuthResponse()
                auth.accessTokenExpiresAt?.let { Instant.parse(it) }
            } else {
                if (auth.refreshTokenExpiresAt.isNullOrBlank() ||
                    !UUID.fromString(auth.userId).toString().equals(auth.userId, ignoreCase = true)) throw InvalidAuthResponse()
                expiry(auth)
            }
        } catch (_: IllegalArgumentException) { throw InvalidAuthResponse() }
        catch (_: java.time.DateTimeException) { throw InvalidAuthResponse() }
        return auth
    }
    fun validateBackend(auth: AuthResponse?): AuthResponse {
        val valid = validate(auth)
        if (valid.backendContract != "canonical" &&
            (valid.username.isNullOrBlank() || valid.region.isNullOrBlank() || valid.phoneNumber.isNullOrBlank())) throw InvalidAuthResponse()
        return valid
    }
    fun expiry(auth: AuthResponse): Instant {
        if (auth.backendContract == "canonical") throw InvalidAuthResponse()
        return OffsetDateTime.parse(auth.refreshTokenExpiresAt).toInstant()
    }
    /** Canonical true means refresh may be attempted, not that an unknown server expiry is valid. */
    fun canRefresh(auth: AuthResponse, now: Instant = Instant.now()) =
        if (auth.backendContract == "canonical") !auth.refreshToken.isNullOrBlank() else expiry(auth).isAfter(now)
}
