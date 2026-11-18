package com.example.farmapp.repository

import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.SessionStore
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.network.canonical.CanonicalBackendApi
import com.example.farmapp.network.canonical.CanonicalFarmDto
import com.example.farmapp.network.canonical.CanonicalFarmRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Tag

internal data class FarmerFarm(
    val id: Long,
    val farmerId: Long,
    val name: String,
    val county: String,
    val subCounty: String?,
    val address: String?,
    val sizeAcres: Double,
    val ownershipType: String,
    val status: String,
    val description: String?
) {
    val canDispatchFrom: Boolean get() = status == "ACTIVE" && !address.isNullOrBlank()
}

internal interface FarmerFarmsApi {
    @GET("farmers/me/farms") suspend fun mine(@Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<CanonicalFarmDto>>>
    @POST("farmers/me/farms") suspend fun create(@Body request: CanonicalFarmRequest,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalFarmDto>>
}

/** Own-farm endpoints: status is backend controlled; farmers cannot self-approve farms. */
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
        val response = api.create(request, current())
        val row = farmerFarm(read(response))
        val owner = current().auth!!.userId.toLong()
        check(row.farmerId == owner && row.name == request.name && row.status in setOf("PENDING", "ACTIVE")) {
            "The new farm response did not match this Farmer's registration."
        }
        return row
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
        return FarmerFarm(id, owner, name, county, row.subCounty, row.address, size, ownership, status, row.description)
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
