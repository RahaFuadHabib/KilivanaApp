package com.example.farmapp.data.model.canonical

/** Separate from the active identifier-based LoginRequest. No endpoint uses this DTO yet. */
data class CanonicalLoginRequest(val email: String, val password: String) {
    override fun toString() = "CanonicalLoginRequest(redacted)"
}

/** Canonical register returns CanonicalUserDto, not tokens. Role profile creation is separate. */
data class CanonicalRegisterRequest(
    val name: String,
    val email: String,
    val phone: String,
    val password: String,
    val role: String,
    val username: String? = null,
    val region: String? = null
) {
    override fun toString() = "CanonicalRegisterRequest(redacted)"
}

data class CanonicalRefreshRequest(val refreshToken: String) {
    override fun toString() = "CanonicalRefreshRequest(redacted)"
}

/** Nullable transport fields let mapping reject absent fields instead of trusting Gson defaults. */
data class CanonicalUserDto(
    val id: Long? = null,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val username: String? = null,
    val referenceCode: String? = null,
    val region: String? = null,
    val role: String? = null,
    val status: String? = null,
    val verificationStatus: String? = null,
    val createdAt: String? = null,
    val emailVerified: Boolean? = null,
    val emailVerificationChallengeId: java.util.UUID? = null
)

data class CanonicalAuthTokenDto(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String? = null,
    /** Seconds; null is the canonical non-expiring-access contract, not a refresh deadline. */
    val expiresIn: Long? = null,
    val user: CanonicalUserDto? = null,
    val emailVerificationRequired: Boolean? = null,
    val emailVerificationChallengeId: java.util.UUID? = null
) {
    override fun toString() = "CanonicalAuthTokenDto(redacted)"
}
