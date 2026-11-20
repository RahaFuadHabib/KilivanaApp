package com.example.farmapp.repository

import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.SessionStore
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.network.canonical.CanonicalFarmDto
import com.example.farmapp.network.canonical.CanonicalFarmRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Tag

internal data class FarmerFarm(
    val id: Long,
    val farmerId: Long,
    val name: String,
    val county: String,
    val subCounty: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val sizeAcres: Double,
    val ownershipType: String,
    val status: String,
    val description: String?
) {
    val canDispatchFrom: Boolean get() = status == "ACTIVE" && !address.isNullOrBlank() &&
        latitude?.let { it.isFinite() && it in -90.0..90.0 } == true &&
        longitude?.let { it.isFinite() && it in -180.0..180.0 } == true
}

internal interface FarmerFarmsApi {
    @GET("farmers/me/farms") suspend fun mine(@Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<CanonicalFarmDto>>>
    @GET("profiles/farmers/{id}") suspend fun profile(@Path("id") id: Long,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalFarmerProfileDto>>
    @POST("profiles/farmers/{id}") suspend fun createProfile(@Path("id") id: Long,
        @Body request: CanonicalFarmerProfileRequest, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalFarmerProfileDto>>
    @PUT("profiles/farmers/{id}") suspend fun updateProfile(@Path("id") id: Long,
        @Body request: CanonicalFarmerProfileRequest, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalFarmerProfileDto>>
    @PUT("farmers/me/farms/{id}") suspend fun updateFarm(@Path("id") id: Long,
        @Body request: CanonicalFarmRequest, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalFarmDto>>
}

internal data class CanonicalFarmerProfileRequest(
    val farmName: String,
    val location: String,
    val farmDetails: String? = null,
    val sizeAcres: Double,
    val ownershipType: String
)

internal data class CanonicalFarmerProfileDto(
    val id: Long? = null,
    val userId: Long? = null,
    val farmName: String? = null,
    val location: String? = null
)

/** Farm setup follows the web client: save/sync the Farmer profile, then update its linked Farm record. */
internal class FarmerFarmsRepository(private val api: FarmerFarmsApi, private val sessions: SessionStore,
    private val expected: SessionSnapshot) {
    private fun current(): SessionSnapshot {
        val now = sessions.session.value
        val auth = now.auth
        check(now.generation == expected.generation && !now.loggingOut && !now.isDemo && auth != null &&
            auth.role == "FARMER" && auth.backendContract == "canonical" && auth.userId == expected.auth?.userId &&
            auth.sessionOrigin == expected.auth?.sessionOrigin) { "Sign in with the same shared-backend Farmer account." }
        return now
    }

    suspend fun mine(): List<FarmerFarm> {
        val response = api.mine(current())
        val rows = read(response).map(::farmerFarm)
        val owner = current().auth!!.userId.toLong()
        check(rows.all { it.farmerId == owner } && rows.map { it.id }.distinct().size == rows.size) {
            "Farm ownership could not be verified."
        }
        return rows.sortedByDescending { it.id }
    }

    suspend fun create(request: CanonicalFarmRequest): FarmerFarm {
        val snapshot = current()
        val owner = snapshot.auth!!.userId.toLongOrNull()?.takeIf { it > 0 }
            ?: error("Farmer identity could not be verified.")
        val profileRequest = CanonicalFarmerProfileRequest(request.name,
            listOfNotNull(request.subCounty, request.county).joinToString(", "), request.description,
            request.sizeAcres, request.ownershipType)
        val existingProfile = api.profile(owner, snapshot)
        val profileResponse = when {
            existingProfile.isSuccessful -> api.updateProfile(owner, profileRequest, current())
            existingProfile.code() == 404 -> {
                existingProfile.errorBody()?.close()
                api.createProfile(owner, profileRequest, current())
            }
            else -> {
                existingProfile.errorBody()?.close()
                throw IllegalStateException("The Farmer profile could not be loaded (HTTP ${existingProfile.code()}).")
            }
        }
        val profile = read(profileResponse)
        check(profile.userId == owner && profile.farmName?.equals(request.name, ignoreCase = true) == true) {
            "The saved farm profile did not match this Farmer."
        }

        // Profile create/update creates or synchronizes the linked ACTIVE farm as in the web flow.
        val linkedFarm = mine().firstOrNull { it.name.equals(request.name, ignoreCase = true) }
            ?: error("The linked farm was not returned after saving the Farmer profile.")
        val updatedResponse = api.updateFarm(linkedFarm.id, request, current())
        val updated = farmerFarm(read(updatedResponse))
        check(updated.id == linkedFarm.id && updated.farmerId == owner &&
            updated.name.equals(request.name, ignoreCase = true)) {
            "The updated farm response did not match this Farmer's farm."
        }
        return updated
    }

    private fun farmerFarm(row: CanonicalFarmDto): FarmerFarm {
        val id = row.id?.takeIf { it > 0 } ?: error("Farm ID is missing or invalid.")
        val owner = row.farmerId?.takeIf { it > 0 } ?: error("Farm owner is missing or invalid.")
        val name = row.name?.trim()?.takeIf { it.isNotEmpty() } ?: error("Farm name is missing.")
        val county = row.county?.trim()?.takeIf { it.isNotEmpty() } ?: error("Farm county is missing.")
        val size = row.sizeAcres?.takeIf { it.isFinite() && it > 0 } ?: error("Farm size is missing or invalid.")
        val ownership = row.ownershipType?.takeIf { it in setOf("OWNED", "LEASED", "COMMUNAL") }
            ?: error("Farm ownership type is invalid.")
        val status = row.status?.takeIf { it in setOf("PENDING", "ACTIVE", "FALLOW", "INACTIVE") }
            ?: error("Farm status is invalid.")
        val latitude = row.latitude
        val longitude = row.longitude
        check((latitude == null) == (longitude == null)) { "Farm coordinates are incomplete." }
        if (latitude != null && longitude != null) {
            check(latitude.isFinite() && latitude in -90.0..90.0 &&
                longitude.isFinite() && longitude in -180.0..180.0) { "Farm coordinates are invalid." }
        }
        return FarmerFarm(id, owner, name, county, row.subCounty, row.address, latitude, longitude,
            size, ownership, status, row.description)
    }

    private fun <T> read(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            error(when (response.code()) {
                400, 422 -> "The backend rejected the farm details. Check the name, county, size and ownership type."
                401 -> "Your session expired. Sign in again."
                403 -> "Only the farm's signed-in Farmer can manage this record."
                else -> "The farm service could not complete this request (HTTP ${response.code()})."
            })
        }
        return response.body()?.requireData() ?: error("The farm service returned an incomplete response.")
    }
}
