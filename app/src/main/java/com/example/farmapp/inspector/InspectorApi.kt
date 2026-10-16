package com.example.farmapp.inspector

import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.data.model.canonical.CanonicalUserDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Body
import retrofit2.http.Tag
import com.example.farmapp.network.SessionSnapshot

/** Assignment-scoped reads and explicit writes. Evidence uploads await private byte delivery. */
interface InspectorApi {
    @GET("auth/me") suspend fun me(): Response<CanonicalApiResponse<CanonicalUserDto>>
    @GET("profiles/inspectors/{userId}") suspend fun profile(@Path("userId") userId: Long): Response<CanonicalApiResponse<InspectorProfileDto>>
    @GET("inspections/inspector/{inspectorId}") suspend fun inspections(@Path("inspectorId") inspectorId: Long): Response<CanonicalApiResponse<List<InspectionDto>>>
    @GET("inspections/{id}") suspend fun inspection(@Path("id") id: Long): Response<CanonicalApiResponse<InspectionDto>>
    @GET("inspections/{id}/evidence/images") suspend fun evidence(@Path("id") id: Long): Response<CanonicalApiResponse<List<InspectorImageDto>>>
    @PUT("inspections/{id}/status") suspend fun updateStatus(@Path("id") id: Long, @Query("status") status: String,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<InspectionDto>>
    @PUT("inspections/{id}/result") suspend fun updateResult(@Path("id") id: Long, @Query("result") result: String,
        @Body request: InspectionFindingRequest, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<InspectionDto>>
}
