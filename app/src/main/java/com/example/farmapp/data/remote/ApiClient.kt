package com.example.farmapp.data.remote

import com.example.farmapp.BuildConfig
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Configured backend transport. No HTTP logging, redirects or automatic connection retries. */
object ApiClient {
    private fun clientBuilder() = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)

    private fun retrofit(client: OkHttpClient) = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL).client(client)
        .addConverterFactory(if (BuildConfig.USE_CANONICAL_BACKEND)
            GsonConverterFactory.create(CanonicalJson.gson) else GsonConverterFactory.create()).build()

    private val plainClient by lazy { clientBuilder().addInterceptor(AuthGatewayHeadersInterceptor(BuildConfig.API_BASE_URL.toHttpUrl()))
        .addInterceptor(AuthLoginDiagnosticInterceptor()).addInterceptor(SafeAuthTraceInterceptor()).build() }
    private val canonicalAuthApi by lazy { retrofit(plainClient).create(CanonicalBackendApi::class.java) }
    val emailVerificationApi: com.example.farmapp.ui.screens.auth.EmailVerificationApi by lazy {
        retrofit(plainClient).create(com.example.farmapp.ui.screens.auth.EmailVerificationApi::class.java)
    }
    val passwordRecoveryApi: com.example.farmapp.ui.screens.auth.PasswordRecoveryApi by lazy {
        retrofit(plainClient).create(com.example.farmapp.ui.screens.auth.PasswordRecoveryApi::class.java)
    }
    val authService: ApiService by lazy {
        if (BuildConfig.USE_CANONICAL_BACKEND) CanonicalAuthService(canonicalAuthApi, BuildConfig.API_BASE_URL,
            retrofit(plainClient).create(CanonicalLoginTransport::class.java))
        else retrofit(plainClient).create(ApiService::class.java)
    }
    private val refreshCoordinator by lazy { RefreshCoordinator(authService, TokenManager) }
    private val authenticatedClient by lazy {
        clientBuilder().addInterceptor(AuthTokenInterceptor(BuildConfig.API_BASE_URL.toHttpUrl(),
            TokenManager, refreshCoordinator)).build()
    }
    /** Reuses normal authentication; callers can attach PreserveSessionOnReadFailure to prevent refresh writes. */
    internal fun <T> authenticatedService(service: Class<T>): T = retrofit(authenticatedClient).create(service)
    private val imageApi by lazy {
        retrofit(clientBuilder().addInterceptor(ImageRequestInterceptor(
            BuildConfig.API_BASE_URL.toHttpUrl(), TokenManager, refreshCoordinator)).build())
            .create(FarmApiService::class.java)
    }
    val apiService: FarmApiService by lazy {
        if (BuildConfig.USE_CANONICAL_BACKEND) CanonicalFarmService(
            retrofit(authenticatedClient).create(CanonicalBackendApi::class.java), TokenManager
        ) { imageContent(it) }
        else retrofit(authenticatedClient).create(FarmApiService::class.java)
    }
    val farmApiService: FarmApiService get() = apiService
    val inspectorApi: com.example.farmapp.inspector.InspectorApi by lazy {
        retrofit(authenticatedClient).create(com.example.farmapp.inspector.InspectorApi::class.java)
    }
    val supplierApi: com.example.farmapp.supplier.SupplierApi by lazy {
        retrofit(authenticatedClient).create(com.example.farmapp.supplier.SupplierApi::class.java)
    }
    val supplierGalleryApi: com.example.farmapp.supplier.SupplierGalleryApi by lazy {
        retrofit(authenticatedClient).create(com.example.farmapp.supplier.SupplierGalleryApi::class.java)
    }
    val profileEditorApi: com.example.farmapp.profile.ProfileEditorApi by lazy {
        retrofit(authenticatedClient).create(com.example.farmapp.profile.ProfileEditorApi::class.java)
    }
    val supplierProfilePhotosApi: com.example.farmapp.profile.SupplierProfilePhotosApi by lazy {
        retrofit(authenticatedClient).create(com.example.farmapp.profile.SupplierProfilePhotosApi::class.java)
    }
    val marketplaceApi: com.example.farmapp.marketplace.MarketplaceApi by lazy {
        retrofit(authenticatedClient).create(com.example.farmapp.marketplace.MarketplaceApi::class.java)
    }
    internal val buyerPickupApi: com.example.farmapp.buyer.BuyerPickupApi by lazy {
        retrofit(authenticatedClient).create(com.example.farmapp.buyer.BuyerPickupApi::class.java)
    }
    internal val buyerCheckoutApi: com.example.farmapp.buyer.BuyerCheckoutApi by lazy {
        retrofit(authenticatedClient).create(com.example.farmapp.buyer.BuyerCheckoutApi::class.java)
    }
    internal val buyerAddressApi: com.example.farmapp.buyer.BuyerAddressApi by lazy {
        retrofit(authenticatedClient).create(com.example.farmapp.buyer.BuyerAddressApi::class.java)
    }
    val buyerOrdersApi: com.example.farmapp.orders.BuyerOrdersApi by lazy {
        retrofit(authenticatedClient).create(com.example.farmapp.orders.BuyerOrdersApi::class.java)
    }
    val deliveryTrackingApi: com.example.farmapp.tracking.DeliveryTrackingApi by lazy {
        retrofit(authenticatedClient).create(com.example.farmapp.tracking.DeliveryTrackingApi::class.java)
    }
    val driverApi: com.example.farmapp.tracking.DriverApi by lazy {
        retrofit(authenticatedClient).create(com.example.farmapp.tracking.DriverApi::class.java)
    }

    /** Image transport attaches credentials only to same-origin image delivery routes. */
    fun imageUrl(url: String): HttpUrl {
        val base = BuildConfig.API_BASE_URL.toHttpUrl()
        val target = base.resolve(url) ?: throw IllegalArgumentException("Invalid image URL")
        require(allowedImageUrl(base, target)) { "Image origin/path is not allowed" }
        return target
    }
    suspend fun imageContent(url: String) = imageApi.productImageContent(imageUrl(url).toString())
}
fun allowedImageUrl(base: HttpUrl, target: HttpUrl): Boolean {
    if (target.username.isNotEmpty() || target.password.isNotEmpty()) return false
    val sameOrigin = target.scheme == base.scheme && target.host == base.host && target.port == base.port
    if (sameOrigin) return target.encodedPath.startsWith(base.encodedPath) || target.encodedPath.startsWith("/uploads/")
    return target.scheme == "https" && target.port == 443 && target.host == "res.cloudinary.com"
}
