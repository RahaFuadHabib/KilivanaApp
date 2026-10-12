package com.example.farmapp.data.model

/** Existing UUID backend /users/me contract. Email has no update field. */
data class FarmerProfileResponse(
    val userId: String,
    val email: String,
    val fullName: String,
    val phoneNumber: String?,
    val username: String?,
    val region: String?,
    val role: String,
    val verificationStatus: String?
)

data class UpdateFarmerPhoneRequest(val phoneNumber: String)
