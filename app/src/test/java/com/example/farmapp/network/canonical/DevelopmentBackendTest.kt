package com.example.farmapp.network.canonical

import com.example.farmapp.BuildConfig
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.data.remote.allowedImageUrl
import com.example.farmapp.network.*
import com.example.farmapp.repository.AuthRepository
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal

/** Exercises real Retrofit serialization against an in-memory transport; never contacts a server. */
class DevelopmentBackendTest {
    private val base = BuildConfig.API_BASE_URL.toHttpUrl()
    @Test fun selectedBackendHasOneApiPrefixAndIsolatedDebugPackage() {
        assertEquals("/api/v1/", base.encodedPath)
        assertTrue(base.isHttps || base.host in setOf("127.0.0.1", "localhost", "10.0.2.2"))
        assertEquals("/api/v1/auth/login", base.resolve("auth/login")!!.encodedPath)
        assertEquals("/api/v1/products/search", base.resolve("products/search")!!.encodedPath)
        assertTrue(BuildConfig.APPLICATION_ID == "com.example.farmapp.debug" ||
            Regex("com\\.example\\.farmapp\\.dev[0-9a-f]{16}").matches(BuildConfig.APPLICATION_ID))
        assertTrue(BuildConfig.USE_CANONICAL_BACKEND)
        assertFalse(BuildConfig.USE_MOCK_AUTH)
    }
    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/canonical/$name.json"))
        .bufferedReader().use { it.readText() }
    private fun api(body: String, inspect: (Request) -> Unit): CanonicalBackendApi {
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .addInterceptor { chain ->
                inspect(chain.request())
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                    .message("Fixture").body(body.toResponseBody("application/json".toMediaType())).build()
            }.build()
        return Retrofit.Builder().baseUrl(base).client(client)
            .addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson)).build()
            .create(CanonicalBackendApi::class.java)
    }
    private fun json(request: Request) = JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).asJsonObject

    @Test fun emailLoginAndRefreshUseSelectedBackendAndCanonicalBodies() = runBlocking {
        api(fixture("login")) {
            assertEquals(base.resolve("auth/login"), it.url)
            assertEquals("POST", it.method)
            assertEquals(setOf("email", "password"), json(it).keySet())
            assertNull(it.header("Authorization"))
        }.login(CanonicalLoginRequest("farmer@example.test", "fixture-only"))
        api(fixture("login")) {
            assertEquals(base.resolve("auth/refresh"), it.url)
            assertEquals(setOf("refreshToken"), json(it).keySet())
        }.refresh(CanonicalRefreshRequest("fixture-refresh"))
        Unit
    }

    @Test fun creationSendsNumericCategoryAndStockWithoutApprovalOrSellerOverrides() = runBlocking {
        api("""{"success":true,"data":null}""") {
            assertEquals(base.resolve("products"), it.url)
            assertEquals("POST", it.method)
            val body = json(it)
            assertEquals(setOf("name", "description", "unit", "price", "stockQty", "minimumOrderQty", "categoryId"), body.keySet())
            assertEquals(BigDecimal("100.25"), body["price"].asBigDecimal)
            assertTrue(body["categoryId"].asJsonPrimitive.isNumber)
            assertEquals(12, body["stockQty"].asInt)
        }.create(CanonicalProductRequest("Disposable test", "Development check", "kg", BigDecimal("100.25"), 12, 1, 20))
        Unit
    }

    @Test fun uploadUsesImagePartAndExplicitGalleryParameters() = runBlocking {
        api("""{"success":true,"data":null}""") {
            assertEquals("/api/v1/products/7/images", it.url.encodedPath)
            assertEquals("0", it.url.queryParameter("sortOrder"))
            assertEquals("true", it.url.queryParameter("isPrimary"))
            val part = (it.body as MultipartBody).parts.single()
            assertEquals("form-data; name=\"image\"; filename=\"fixture.png\"", part.headers!!["Content-Disposition"])
            assertEquals("image/png", part.body.contentType().toString())
        }.upload(7, MultipartBody.Part.createFormData("image", "fixture.png",
            byteArrayOf(1, 2, 3).toRequestBody("image/png".toMediaType())), 0, true)
        Unit
    }

    @Test fun selectedBackendTrustDoesNotExtendToAnotherEnvironment() {
        val other = "https://different-backend.example.test/api/v1/images/1".toHttpUrl()
        assertFalse(protectedImageUrl(base, other))
        assertFalse(allowedImageUrl(base, other))
        assertTrue(protectedImageUrl(base, base.resolve("images/1")!!))
        assertFalse(protectedImageUrl(base, "https://res.cloudinary.com/demo/image/upload/1".toHttpUrl()))
    }

    @Test fun restoredTokensFromAnotherBackendAreRemovedBeforeAnyRequest() = runBlocking {
        val auth = CanonicalJson.gson.fromJson(fixture("login"), com.google.gson.JsonObject::class.java)["data"]
        val dto = CanonicalJson.gson.fromJson(auth, CanonicalAuthTokenDto::class.java)
        val disk = MemoryPersistence(dto.toAndroidAuth("https://previous-backend.example.test/api/v1/"))
        val store = SessionState()
        val service = CanonicalAuthService(api("{}") { error("Restore must not send requests") }, base.toString())
        AuthRepository(service, store).restore(disk)
        assertNull(store.session.value.auth)
        assertNull(disk.saved)
        assertEquals(1, disk.clears)
    }

    @Test fun savedSessionForSelectedBackendCanBeRestoredWithoutRequests() = runBlocking {
        val auth = CanonicalJson.gson.fromJson(fixture("login"), com.google.gson.JsonObject::class.java)["data"]
        val dto = CanonicalJson.gson.fromJson(auth, CanonicalAuthTokenDto::class.java)
        val disk = MemoryPersistence(dto.toAndroidAuth(base.toString()))
        val store = SessionState()
        AuthRepository(CanonicalAuthService(api("{}") { error("Restore must not send requests") }, base.toString()), store).restore(disk)
        assertEquals(dto.user!!.id.toString(), store.session.value.auth!!.userId)
        assertEquals(0, disk.clears)
    }
}
