package com.example.farmapp.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton object providing Retrofit and FarmApiService instances.
 */
object ApiClient {

    /**
     * Base URL for the backend API.
     * Note: Use 10.0.2.2 when running Android Emulator to refer to localhost on the host machine.
     * For physical devices or remote servers, set this to the corresponding host IP or domain.
     */
    private const val BASE_URL = "http://10.0.2.2:8080/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: FarmApiService by lazy {
        retrofit.create(FarmApiService::class.java)
    }

    val farmApiService: FarmApiService
        get() = apiService
}
