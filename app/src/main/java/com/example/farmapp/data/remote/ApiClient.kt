package com.example.farmapp.data.remote

import com.example.farmapp.BuildConfig
import com.example.farmapp.network.ApiService
import com.example.farmapp.network.AuthTokenInterceptor
import com.example.farmapp.network.RefreshCoordinator
import com.example.farmapp.network.TokenManager
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // No HTTP logging on either client: credentials, tokens, headers and response bodies stay private.
    // Redirects are not API successes and must not forward credentials to an unexpected endpoint.
    private fun clientBuilder() = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)

    private fun retrofit(client: OkHttpClient) = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Deliberately independent: login/refresh/logout never invoke the refresh interceptor.
    val authService: ApiService by lazy {
        retrofit(clientBuilder().build()).create(ApiService::class.java)
    }

    private val refreshCoordinator by lazy { RefreshCoordinator(authService, TokenManager) }

    private val authenticatedClient by lazy {
        clientBuilder()
            .addInterceptor(AuthTokenInterceptor(BuildConfig.API_BASE_URL.toHttpUrl(), TokenManager, refreshCoordinator))
            .build()
    }

    val apiService: FarmApiService by lazy {
        retrofit(authenticatedClient).create(FarmApiService::class.java)
    }
    val farmApiService: FarmApiService get() = apiService
}
