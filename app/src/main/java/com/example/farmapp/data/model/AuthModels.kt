package com.example.farmapp.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
) {
    override fun toString() = "LoginRequest(redacted)"
}

data class RefreshRequest(@SerializedName("refreshToken") val refreshToken: String) {
    override fun toString() = "RefreshRequest(redacted)"
}

data class LogoutRequest(@SerializedName("refreshToken") val refreshToken: String) {
    override fun toString() = "LogoutRequest(redacted)"
}

/** Exact login/refresh wire contract. UUID and ISO-8601 date-time remain lossless strings. */
data class AuthResponse(
    @SerializedName("userId") val userId: String,
    @SerializedName("email") val email: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("role") val role: String,
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("refreshTokenExpiresAt") val refreshTokenExpiresAt: String
) {
    override fun toString() = "AuthResponse(redacted)"
}
