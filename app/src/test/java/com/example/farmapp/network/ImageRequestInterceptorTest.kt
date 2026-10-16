package com.example.farmapp.network

import com.example.farmapp.data.remote.allowedImageUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ImageRequestInterceptorTest {
    private val base = "https://api.example.test/api/v1/".toHttpUrl()

    private fun client(store: SessionStore, service: ApiService = StubAuthService(),
        respond: (Request) -> Int): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .addInterceptor(ImageRequestInterceptor(base, store, RefreshCoordinator(service, store)))
        .addInterceptor { chain ->
            val request = chain.request()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(respond(request)).message("Image").body("image".toResponseBody()).build()
        }.build()

    private fun get(client: OkHttpClient, url: String) =
        client.newCall(Request.Builder().url(url).build()).execute()

    @Test fun bothProtectedRoutesReceiveCurrentBearer() {
        val client = client(signedInStore()) { request ->
            assertEquals("Bearer access-old", request.header("Authorization")); 200
        }
        listOf("api/v1/images/1", "uploads/products/photo.jpg").forEach {
            get(client, "https://api.example.test/$it").use { response -> assertEquals(200, response.code) }
        }
        assertNull(client.cache)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test fun approvedImagesRemainPublicWithoutSession() {
        val store = SessionState().apply { initialize(MemoryPersistence()) }
        get(client(store) { assertNull(it.header("Authorization")); 200 },
            "https://api.example.test/api/v1/images/1").use { assertEquals(200, it.code) }
    }

    @Test fun credentialsExcludedFromOtherOriginsAndPaths() {
        val client = client(signedInStore()) { assertNull(it.header("Authorization")); 200 }
        listOf("https://res.cloudinary.com/demo/image/upload/a.jpg",
            "https://other.test/api/v1/images/1", "http://api.example.test/api/v1/images/1",
            "https://api.example.test:444/api/v1/images/1", "https://api.example.test.evil.test/api/v1/images/1",
            "https://api.example.test/api/v1/products/1", "https://api.example.test/api/v1/images-other/1",
            "https://api.example.test/api/v1/images/../auth/me").forEach {
            assertFalse(protectedImageUrl(base, it.toHttpUrl()))
            get(client, it).close()
        }
        assertFalse(allowedImageUrl(base, "https://other.test/image.jpg".toHttpUrl()))
        assertFalse(protectedImageUrl(base, "https://user:pass@api.example.test/api/v1/images/1".toHttpUrl()))
    }

    @Test fun unauthorizedImageRefreshesOnceUsingSharedMechanism() {
        var calls = 0
        val client = client(signedInStore()) {
            calls++
            if (calls == 1) { assertEquals("Bearer access-old", it.header("Authorization")); 401 }
            else { assertEquals("Bearer access-new", it.header("Authorization")); 200 }
        }
        get(client, "https://api.example.test/api/v1/images/1").use { assertEquals(200, it.code) }
        assertEquals(2, calls)
    }

    @Test fun logoutAndAccountSwitchInvalidateOldPreviewAndLateResponse() {
        val store = signedInStore()
        val original = store.session.value
        val client = client(store) {
            val logout = store.beginLogout()!!
            assertFalse(imageSessionCurrent(original, logout))
            store.clear(logout)
            store.establish(store.session.value, testAuth().copy(userId = "eb1f7b4e-8392-4bf4-8cdc-f96083411001"), false)
            200
        }
        try {
            get(client, "https://api.example.test/api/v1/images/1").close()
            fail("Old response must be rejected")
        } catch (_: IOException) { }
        assertFalse(imageSessionCurrent(original, store.session.value))
    }

    @Test fun tokenRotationKeepsPreviewInSameSession() {
        val store = signedInStore()
        val original = store.session.value
        store.rotate(original, testAuth("rotated", "rotated-refresh"))
        assertTrue(imageSessionCurrent(original, store.session.value))
    }

    @Test fun invisibleImageDoesNotRefreshOrClearSession() {
        val store = signedInStore()
        val original = store.session.value
        var calls = 0
        get(client(store) { calls++; 404 }, "https://api.example.test/api/v1/images/1")
            .use { assertEquals(404, it.code) }
        assertEquals(1, calls)
        assertSame(original, store.session.value)
    }

    @Test fun canonicalExpiryRefreshesBeforePrivateRouteCanReturnAnonymous404() {
        listOf(java.time.Instant.now().minusSeconds(60).toString(), null).forEach { expiry ->
            val expired = testAuth().copy(userId = "42", backendContract = "canonical",
                sessionOrigin = base.toString(), refreshTokenExpiresAt = "", accessTokenExpiresAt = expiry)
            val store = signedInStore(expired)
            var refreshes = 0
            val service = object : StubAuthService() {
                override suspend fun refresh(request: com.example.farmapp.data.model.RefreshRequest):
                    retrofit2.Response<com.example.farmapp.data.model.AuthResponse> {
                    refreshes++
                    return retrofit2.Response.success(expired.copy(accessToken = "fresh",
                        accessTokenExpiresAt = java.time.Instant.now().plusSeconds(3600).toString()))
                }
            }
            get(client(store, service) {
                assertEquals("Bearer fresh", it.header("Authorization")); 200
            }, "https://api.example.test/api/v1/images/1").use { assertEquals(200, it.code) }
            assertEquals(1, refreshes)
        }
    }
}
