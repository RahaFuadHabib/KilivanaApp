package com.example.farmapp.data.remote

import com.example.farmapp.data.model.Farmer
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface defining network endpoints for Farmer operations.
 */
interface FarmApiService {
    @GET("users/me")
    suspend fun farmerProfile(): Response<com.example.farmapp.data.model.FarmerProfileResponse>

    @PUT("users/me")
    suspend fun updateFarmerPhone(@Body request: com.example.farmapp.data.model.UpdateFarmerPhoneRequest): Response<com.example.farmapp.data.model.FarmerProfileResponse>

    @GET("categories")
    suspend fun categories(@Query("sector") sector: String): Response<List<com.example.farmapp.data.model.CategoryResponse>>
    @PUT("products/{productId}")
    suspend fun updateProduct(@Path("productId") id: String,
        @Body request: com.example.farmapp.data.model.UpdateProductRequest): Response<com.example.farmapp.data.model.FarmerProductResponse>
    @DELETE("products/{productId}")
    suspend fun unlistProduct(@Path("productId") id: String): Response<Void>
    @GET("orders/mine")
    suspend fun farmerOrders(): Response<List<com.example.farmapp.data.model.FarmerOrderResponse>>
    @GET("products/{productId}/images")
    suspend fun productImages(@Path("productId") productId: String): Response<List<com.example.farmapp.data.model.ProductImageResponse>>
    @retrofit2.http.Streaming
    @GET
    suspend fun productImageContent(@retrofit2.http.Url url: String): Response<okhttp3.ResponseBody>
    @POST("products")
    suspend fun createProduct(@Body request: com.example.farmapp.data.model.CreateProductRequest): Response<com.example.farmapp.data.model.FarmerProductResponse>

    @GET("products/mine")
    suspend fun myProducts(): Response<List<com.example.farmapp.data.model.FarmerProductResponse>>

    @retrofit2.http.Multipart
    @POST("products/{productId}/images/upload")
    suspend fun uploadProductImage(@Path("productId") productId: String,
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part): Response<com.example.farmapp.data.model.ProductImageResponse>

    @GET("farmers")
    suspend fun getFarmers(): List<Farmer>

    @GET("farmers/{id}")
    suspend fun getFarmerById(@Path("id") id: Long): Farmer

    @POST("farmers")
    suspend fun addFarmer(@Body farmer: Farmer): Farmer

    @PUT("farmers/{id}")
    suspend fun updateFarmer(
        @Path("id") id: Long,
        @Body farmer: Farmer
    ): Farmer

    @DELETE("farmers/{id}")
    suspend fun deleteFarmer(@Path("id") id: Long): Response<Unit>

    @GET("farmers/search")
    suspend fun searchFarmersByCounty(@Query("county") county: String): List<Farmer>
}
