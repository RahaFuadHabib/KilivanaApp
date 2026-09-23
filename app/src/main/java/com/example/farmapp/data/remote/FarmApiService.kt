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
