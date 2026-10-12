package com.example.farmapp.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("identifier") val identifier: String,
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

/** Exact signup/login/refresh wire contract. UUID and ISO-8601 date-time remain lossless strings. */
data class AuthResponse(
    @SerializedName("userId") val userId: String,
    @SerializedName("email") val email: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("role") val role: String,
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("refreshTokenExpiresAt") val refreshTokenExpiresAt: String,
    @SerializedName("verificationStatus") val verificationStatus: String? = null,
    // Nullable only for encrypted sessions written before the account contract update.
    // Fresh transport responses are checked by AuthValidation.validateBackend.
    @SerializedName("username") val username: String? = null,
    @SerializedName("region") val region: String? = null,
    @SerializedName("phoneNumber") val phoneNumber: String? = null
) {
    override fun toString() = "AuthResponse(redacted)"
}

/** Nullable role fields are omitted by the existing Gson converter. */
data class SignupRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("phoneNumber") val phoneNumber: String,
    @SerializedName("role") val role: String,
    @SerializedName("username") val username: String,
    @SerializedName("region") val region: String,
    @SerializedName("farmName") val farmName: String? = null,
    @SerializedName("businessName") val businessName: String? = null,
    @SerializedName("employeeCode") val employeeCode: String? = null
) {
    override fun toString() = "SignupRequest(redacted)"
}
