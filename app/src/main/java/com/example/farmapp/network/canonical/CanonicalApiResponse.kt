package com.example.farmapp.network.canonical

/** Canonical ApiResponse<T>. LocalDateTime timestamps stay offset-free wire strings. */
data class CanonicalApiResponse<T>(
    val success: Boolean? = null,
    val message: String? = null,
    val data: T? = null,
    val timestamp: String? = null,
    val error: CanonicalErrorDetail? = null
) {
    fun requireSuccess() {
        if (success == null) throw CanonicalContractException("Missing response success flag")
        if (success != true) throw CanonicalResponseFailure(error?.code)
    }

    /** Use requireSuccess() for success-message/void responses which legitimately have no data. */
    fun requireData(): T {
        requireSuccess()
        return data ?: throw CanonicalContractException("Missing response data")
    }

    override fun toString() = "CanonicalApiResponse(redacted)"
}

data class CanonicalErrorDetail(val code: String? = null, val details: String? = null)

class CanonicalContractException(message: String) : IllegalArgumentException(message)

/** Never include raw response bodies, credentials or server details in exception messages. */
class CanonicalResponseFailure(val errorCode: String?) :
    IllegalStateException("Canonical API returned an unsuccessful response")
