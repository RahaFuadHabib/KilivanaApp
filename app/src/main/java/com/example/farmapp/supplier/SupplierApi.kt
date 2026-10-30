package com.example.farmapp.supplier

import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.network.canonical.CanonicalProductRequest
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/** No administrator, buyer-order, driver or payment initiation routes. */
interface SupplierApi {
    @GET("suppliers/{supplierId}/orders") suspend fun orders(@Path("supplierId") supplierId: Long,
        @Query("page") page: Int, @Query("size") size: Int,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<SupplierOrderPageDto>>
    @GET("auth/me") suspend fun me(@Tag session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalUserDto>>
    @GET("profiles/suppliers/{id}") suspend fun profile(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<SupplierProfileDto>>
    @GET("categories/type/SUPPLIER") suspend fun categories(@Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<CanonicalCategoryDto>>>
    @GET("products/seller/{id}") suspend fun listings(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<CanonicalProductDto>>>
    @GET("products/{id}") suspend fun listing(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalProductDto>>
    @POST("products") suspend fun create(@Body request: CanonicalProductRequest, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalProductDto>>
    @PUT("products/{id}") suspend fun update(@Path("id") id: Long, @Body request: CanonicalProductRequest, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalProductDto>>
    @GET("products/{id}/images") suspend fun images(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<List<CanonicalProductImageDto>>>
    @Multipart @POST("products/{id}/images") suspend fun upload(@Path("id") id: Long, @Part image: MultipartBody.Part,
        @Query("sortOrder") sortOrder: Int, @Query("isPrimary") primary: Boolean,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalProductImageDto>>
}
