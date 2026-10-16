package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.canonical.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*
import java.math.BigDecimal

/** Existing canonical contracts only. No general order feed or destructive product DELETE. */
interface CanonicalBackendApi {
    @POST("auth/login") suspend fun login(@Body request: CanonicalLoginRequest): Response<CanonicalApiResponse<CanonicalAuthTokenDto>>
    @POST("auth/register") suspend fun register(@Body request: CanonicalRegisterRequest): Response<CanonicalApiResponse<CanonicalUserDto>>
    @POST("auth/refresh") suspend fun refresh(@Body request: CanonicalRefreshRequest): Response<CanonicalApiResponse<CanonicalAuthTokenDto>>
    @POST("profiles/farmers/{id}") suspend fun createFarmerProfile(@Path("id") id: Long,
        @Header("Authorization") bearer: String, @Body request: CanonicalFarmProfileRequest): Response<CanonicalApiResponse<CanonicalCreatedProfileDto>>
    @GET("auth/me") suspend fun me(): Response<CanonicalApiResponse<CanonicalUserDto>>
    @PUT("users/me") suspend fun updateProfile(@Body request: CanonicalProfileUpdate): Response<CanonicalApiResponse<CanonicalUserDto>>
    @GET("categories/type/FARMER") suspend fun categories(): Response<CanonicalApiResponse<List<CanonicalCategoryDto>>>
    @GET("products/seller/{id}") suspend fun products(@Path("id") id: Long): Response<CanonicalApiResponse<List<CanonicalProductDto>>>
    @GET("products/{id}") suspend fun product(@Path("id") id: Long): Response<CanonicalApiResponse<CanonicalProductDto>>
    @POST("products") suspend fun create(@Body request: CanonicalProductRequest): Response<CanonicalApiResponse<CanonicalProductDto>>
    @PUT("products/{id}") suspend fun update(@Path("id") id: Long, @Body request: CanonicalProductRequest): Response<CanonicalApiResponse<CanonicalProductDto>>
    @GET("products/{id}/images") suspend fun images(@Path("id") id: Long): Response<CanonicalApiResponse<List<CanonicalProductImageDto>>>
    @Multipart @POST("products/{id}/images") suspend fun upload(@Path("id") id: Long,
        @Part image: MultipartBody.Part, @Query("sortOrder") sortOrder: Int,
        @Query("isPrimary") primary: Boolean): Response<CanonicalApiResponse<CanonicalProductImageDto>>
}
data class CanonicalFarmProfileRequest(val farmName: String, val location: String)
data class CanonicalProfileUpdate(val name: String, val email: String, val phone: String)
data class CanonicalProductRequest(val name: String, val description: String, val unit: String,
    val price: BigDecimal, val stockQty: Int, val minimumOrderQty: Int, val categoryId: Long)

data class CanonicalCreatedProfileDto(val userId: Long? = null, val farmName: String? = null)
