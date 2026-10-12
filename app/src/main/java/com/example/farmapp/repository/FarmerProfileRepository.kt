package com.example.farmapp.repository

import com.example.farmapp.data.model.*
import com.example.farmapp.data.remote.FarmApiService
import retrofit2.Response

class FarmerProfileRepository(private val api: FarmApiService) {
    suspend fun load(): FarmerProfileResponse = profile(api.farmerProfile())

    suspend fun savePhone(phone: String): FarmerProfileResponse {
        val value = phone.trim()
        require(Regex("\\+?[0-9]{7,15}").matches(value)) {
            "Enter a valid phone number, for example +254712345678."
        }
        return profile(api.updateFarmerPhone(UpdateFarmerPhoneRequest(value)))
    }

    private fun profile(response: Response<FarmerProfileResponse>): FarmerProfileResponse {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw IllegalStateException(when (response.code()) {
                409 -> "This phone number is already registered."
                400, 422 -> "The backend rejected this phone number. Check it and retry."
                401, 403 -> "Your session could not authorize this profile request. Sign in again."
                else -> "Profile request failed. Check your connection and retry."
            })
        }
        val result = response.body() ?: throw IllegalStateException("The backend returned no profile.")
        require(result.role == "FARMER" && result.userId.isNotBlank()) { "Unexpected farmer profile response." }
        return result
    }
}
