package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.*
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.data.remote.allowedImageUrl
import com.example.farmapp.network.*
import com.example.farmapp.repository.ProductFailure
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.Instant

class CanonicalIntegrationAdapterTest {
    private val origin = "http://127.0.0.1:8081/api/v1/"
    private fun user() = CanonicalUserDto(9007199254740993L, "Farmer", "farmer@example.test", "+254700000001",
        role = "FARMER", status = "ACTIVE", verificationStatus = "NOT_REQUIRED")
    private fun tokens() = CanonicalAuthTokenDto("fixture-access", "fixture-refresh", "Bearer", 900, user())
    private fun product() = CanonicalProductDto(7, user().id, "FARMER", 1, "Tomatoes", "Fresh", "kg",
        BigDecimal("100.25"), 20, 3, 2, 1, "pending", images = emptyList())
    private fun api(handler: (String, Array<out Any?>) -> Any?): CanonicalBackendApi =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CanonicalBackendApi::class.java)) { _, method, args ->
            handler(method.name, args ?: emptyArray())
        } as CanonicalBackendApi
    private fun store(): SessionState {
        val persistence = object : SessionPersistence {
            override fun read(): AuthResponse? = null
            override fun write(auth: AuthResponse) {}
            override fun clear() {}
        }
        return SessionState().apply {
            initialize(persistence)
            establish(session.value, tokens().toAndroidAuth(origin), false)
        }
    }
    @Test fun lowercaseSellerTypesMatchTheLiveCanonicalWireContract() { assertEquals("FARM_PRODUCE", canonicalSector("farmer")); assertEquals("AGRI_INPUTS", canonicalSector("supplier")) }
    @Test fun canonicalSessionKeepsNumericIdentityAndUnknownRefreshExpiry() {
        val auth = tokens().toAndroidAuth(origin, Instant.parse("2026-10-14T00:00:00Z"))
        assertEquals("9007199254740993", AuthValidation.validateBackend(auth).userId)
        assertEquals("", auth.refreshTokenExpiresAt)
        assertEquals("2026-10-14T00:15:00Z", auth.accessTokenExpiresAt)
        assertTrue(AuthValidation.canRefresh(auth))
        try { AuthValidation.expiry(auth); fail() } catch (_: InvalidAuthResponse) {}
        assertNull(auth.username)
        assertNull(auth.region)
    }
    @Test fun emailLoginAndRegistrationUseCanonicalContracts() = runBlocking {
        val calls = mutableListOf<String>()
        val transport = api { method, args ->
            calls += method
            when (method) {
                "register" -> { assertEquals("Fixture Farm", (args[0] as CanonicalRegisterRequest).name); Response.success(CanonicalApiResponse(true, data = user())) }
                "login" -> { assertEquals("farmer@example.test", (args[0] as CanonicalLoginRequest).email); Response.success(CanonicalApiResponse(true, data = tokens())) }
                "createFarmerProfile" -> { assertEquals(user().id, args[0]); assertEquals("Kiambu", (args[2] as CanonicalFarmProfileRequest).location); Response.success(CanonicalApiResponse(true, data = CanonicalCreatedProfileDto(user().id, "Farm"))) }
                else -> error("Unexpected endpoint")
            }
        }
        val service = CanonicalAuthService(transport, origin)
        val result = service.signup(SignupRequest("farmer@example.test", "test-only-password", "Fixture Farm",
            "+254700000001", "FARMER", "fixture_farmer", "Kiambu", farmName = "Farm"))
        assertTrue(result.isSuccessful)
        assertEquals(listOf("register", "login", "createFarmerProfile"), calls)
    }
    @Test fun stockEditPreservesReservationsAndPendingModeration() = runBlocking {
        var captured: CanonicalProductRequest? = null
        val transport = api { method, args ->
            when (method) {
                "product" -> Response.success(CanonicalApiResponse(true, data = product()))
                "update" -> {
                    captured = args[1] as CanonicalProductRequest
                    Response.success(CanonicalApiResponse(true, data = product().copy(stockQty = captured!!.stockQty)))
                }
                else -> error("Unexpected endpoint")
            }
        }
        val service = CanonicalFarmService(transport, store()) { error("No image download") }
        val result = service.updateProduct("7", UpdateProductRequest("Tomatoes", "Fresh", "kg",
            BigDecimal("100.25"), BigDecimal("5"), "1", null, BigDecimal.ONE))
        assertEquals(8, captured!!.stockQty)
        assertEquals(BigDecimal("5"), result.body()!!.availableQuantity)
        assertEquals("PENDING_APPROVAL", result.body()!!.status)
    }
    @Test fun unsupportedFractionAndOrderFeedNeverMakeRequests() = runBlocking {
        var calls = 0
        val service = CanonicalFarmService(api { _, _ -> calls++; error("No requests allowed") }, store()) { error("No downloads") }
        suspend fun rejects(block: suspend () -> Unit) {
            try { block(); fail() } catch (_: ProductFailure) {}
        }
        rejects { service.createProduct(CreateProductRequest("Tomatoes", "Fresh", "kg", BigDecimal.ONE, BigDecimal("1.5"), "1")) }
        rejects { service.farmerOrders() }
        assertEquals(0, calls)
    }
    @Test fun imageProjectionDoesNotInventUploadMetadataAndHostPolicyIsBounded() {
        val image = CanonicalProductImageDto(1, "/api/v1/images/1", "1", sortOrder = 0).toAndroidImage()
        assertNull(image.fileName); assertNull(image.contentType); assertNull(image.sizeBytes)
        val base = origin.toHttpUrl()
        assertTrue(allowedImageUrl(base, base.resolve("/api/v1/images/1")!!))
        assertTrue(allowedImageUrl(base, base.resolve("/uploads/image.png")!!))
        assertTrue(allowedImageUrl(base, "https://res.cloudinary.com/example/image/upload/photo.png".toHttpUrl()))
        assertFalse(allowedImageUrl(base, "http://res.cloudinary.com/image.png".toHttpUrl()))
        assertFalse(allowedImageUrl(base, "https://evil.example/image.png".toHttpUrl()))
        assertFalse(allowedImageUrl(base, "https://res.cloudinary.com.evil.example/image.png".toHttpUrl()))
        assertFalse(allowedImageUrl(base, base.resolve("/actuator/env")!!))
    }
}
