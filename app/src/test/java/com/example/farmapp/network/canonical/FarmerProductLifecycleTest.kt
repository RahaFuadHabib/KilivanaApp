package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.*
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.*
import com.example.farmapp.repository.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal
import java.io.IOException

/** Production Retrofit + auth interceptor, synthetic terminal transport; no live backend writes. */
class FarmerProductLifecycleTest {
    private val origin = "https://farmer.example.test/api/v1/"
    private fun product(status: String = "active", reserved: Int = 0) = CanonicalProductDto(
        7, 42, "farmer", 1, "Synthetic crop", "Description", "kg", BigDecimal.TEN,
        10, reserved, 2, 1, status, images = emptyList())
    private fun auth(role: String = "FARMER") = testAuth(role = role).copy(userId = "42",
        backendContract = "canonical", sessionOrigin = origin, refreshTokenExpiresAt = "")

    private inner class Fixture(var value: CanonicalProductDto = product()) {
        val store = signedInStore(auth())
        val calls = mutableListOf<Request>()
        var mutationCode = 200
        var readCode = 200
        var confirmed = true
        var responseOwner = 42L
        var hook: (Request) -> Unit = {}
        val api = Retrofit.Builder().baseUrl(origin).client(OkHttpClient.Builder()
            .addInterceptor(AuthTokenInterceptor(origin.toHttpUrl(), store, RefreshCoordinator(object : StubAuthService() {
                override suspend fun refresh(request: RefreshRequest): retrofit2.Response<AuthResponse> =
                    throw IOException("No unexpected refresh")
            }, store)))
            .addInterceptor { chain ->
                val request = chain.request()
                calls += request
                assertEquals("Bearer access-old", request.header("Authorization"))
                assertNotNull(request.tag(SessionSnapshot::class.java))
                hook(request)
                val write = request.method in setOf("PUT", "DELETE")
                val code = if (write) mutationCode else readCode
                val data: Any? = when {
                    request.url.encodedPath.endsWith("auth/me") -> CanonicalUserDto(42, "Synthetic Farmer", "farmer@example.test", "0700000000",
                        role = "FARMER", status = "ACTIVE", verificationStatus = "VERIFIED")
                    request.method == "DELETE" -> null
                    request.method == "PUT" -> value.copy(status = when (request.url.queryParameter("status")) {
                        "UNLISTED" -> "unlisted"
                        "ARCHIVED" -> "archived"
                        "PENDING_APPROVAL" -> "pending"
                        else -> error("Unsafe status")
                    }, sellerId = responseOwner)
                    else -> value
                }
                val body = if (code == 200) CanonicalJson.gson.toJson(CanonicalApiResponse(confirmed, data = data))
                    else """{"success":false,"message":"Synthetic lifecycle rejection","data":null}"""
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Synthetic")
                    .body(body.toResponseBody()).build()
            }.build()).addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson)).build()
            .create(CanonicalBackendApi::class.java)
        val repository = ProductRepository(CanonicalFarmService(api, store) { error("No images") })
        fun writes() = calls.filter { it.method in setOf("PUT", "DELETE") }
    }

    private suspend fun rejected(status: Int? = null, action: suspend () -> Unit) {
        try { action(); fail("Expected rejected action") }
        catch (e: ProductFailure) { if (status != null) assertEquals(status, e.httpStatus) }
    }

    @Test fun lifecycleMatrixOffersOnlyOwnedEligibleStates() {
        val expected = mapOf("active" to setOf(FarmerProductAction.UNLIST, FarmerProductAction.ARCHIVE),
            "pending" to setOf(FarmerProductAction.ARCHIVE), "rejected" to setOf(FarmerProductAction.ARCHIVE),
            "unlisted" to setOf(FarmerProductAction.ARCHIVE, FarmerProductAction.RESUBMIT),
            "archived" to setOf(FarmerProductAction.RESUBMIT, FarmerProductAction.DELETE),
            "suspended" to emptySet(), "deleted" to emptySet())
        expected.forEach { (state, actions) ->
            val view = product(state).toAndroidProduct()
            assertEquals(actions, view.lifecycleActions("42", "FARMER").toSet())
            assertTrue(view.lifecycleActions("99", "FARMER").isEmpty())
            assertTrue(view.lifecycleActions("42", "BUYER").isEmpty())
        }
        assertFalse(product("archived", 1).toAndroidProduct().lifecycleActions("42", "FARMER").contains(FarmerProductAction.DELETE))
        assertFalse(product("archived").toAndroidProduct().copy(reservedQuantity = null).lifecycleActions("42", "FARMER").contains(FarmerProductAction.DELETE))
    }
    @Test fun statusWritesUseExistingRouteUppercaseQueryAndNoBody() = runBlocking {
        for ((state, action) in listOf("active" to FarmerProductAction.UNLIST, "pending" to FarmerProductAction.ARCHIVE,
            "rejected" to FarmerProductAction.ARCHIVE, "unlisted" to FarmerProductAction.RESUBMIT, "archived" to FarmerProductAction.RESUBMIT)) {
            val f = Fixture(product(state))
            f.repository.lifecycle("7", action)
            val write = f.writes().single()
            assertEquals("PUT", write.method)
            assertEquals("/api/v1/products/7/status", write.url.encodedPath)
            assertEquals(action.targetStatus, write.url.queryParameter("status"))
            assertEquals(0L, write.body?.contentLength() ?: 0L)
            assertEquals(listOf("/api/v1/auth/me", "/api/v1/products/7", "/api/v1/products/7/status"), f.calls.map { it.url.encodedPath })
        }
    }
    @Test fun archivedZeroReservedUsesServerDeleteAndAcceptsNullEnvelopeData() = runBlocking {
        val f = Fixture(product("archived"))
        f.repository.lifecycle("7", FarmerProductAction.DELETE)
        assertEquals("DELETE", f.writes().single().method)
        assertEquals("/api/v1/products/7", f.writes().single().url.encodedPath)
    }
    @Test fun staleOrReservedOrSuspendedStateNeverWrites() = runBlocking {
        for (value in listOf(product("active"), product("archived", 1), product("suspended"))) {
            val f = Fixture(value)
            rejected(400) { f.repository.lifecycle("7", FarmerProductAction.DELETE) }
            assertTrue(f.writes().isEmpty())
        }
    }
    @Test fun wrongOwnerAndDeletedProductsNeverWrite() = runBlocking {
        val f = Fixture(product().copy(sellerId = 99))
        rejected(403) { f.repository.lifecycle("7", FarmerProductAction.UNLIST) }
        assertTrue(f.writes().isEmpty())
        val deleted = Fixture(product("deleted"))
        rejected(404) { deleted.repository.lifecycle("7", FarmerProductAction.UNLIST) }
        assertTrue(deleted.writes().isEmpty())
    }
    @Test fun backendRejectionsPreserveHttpCodesAndNeverReplayWrites() = runBlocking {
        for (code in listOf(400, 401, 403, 404, 500)) {
            val f = Fixture().apply { mutationCode = code }
            rejected(code) { f.repository.lifecycle("7", FarmerProductAction.UNLIST) }
            assertEquals(1, f.writes().size)
        }
    }
    @Test fun failedProtectedReadPreventsMutation() = runBlocking {
        val f = Fixture().apply { readCode = 403 }
        rejected(403) { f.repository.lifecycle("7", FarmerProductAction.UNLIST) }
        assertEquals(1, f.calls.size)
        assertTrue(f.writes().isEmpty())
    }
    @Test fun sessionSwitchAfterPreflightPreventsMutation() = runBlocking {
        val f = Fixture()
        f.hook = { if (it.url.encodedPath.endsWith("products/7")) {
            f.store.clear(f.store.beginLogout()!!)
            f.store.establish(f.store.session.value, auth().copy(userId = "99"), false)
        } }
        rejected(401) { f.repository.lifecycle("7", FarmerProductAction.UNLIST) }
        assertTrue(f.writes().isEmpty())
    }
    @Test fun nonFarmerSessionCannotMakeAnyRequest() = runBlocking {
        val f = Fixture()
        f.store.clear(f.store.beginLogout()!!)
        f.store.establish(f.store.session.value, auth("SUPPLIER"), false)
        rejected(401) { f.repository.lifecycle("7", FarmerProductAction.UNLIST) }
        assertTrue(f.calls.isEmpty())
    }
    @Test fun detailRetainsImageIdentityAndReservationMetadata() = runBlocking {
        val f = Fixture(product("unlisted", 3).copy(images = listOf(CanonicalProductImageDto(8, "/api/v1/images/100", "100", sortOrder = 0))))
        val detail = f.repository.detail("7")
        assertEquals("UNLISTED", detail.status)
        assertEquals(BigDecimal(3), detail.reservedQuantity)
        assertEquals("42", detail.sellerId)
        assertEquals("8", detail.images.single().imageId)
        assertEquals(BigDecimal(7), detail.availableQuantity)
    }
    @Test fun responseWithWrongOwnershipCannotConfirmMutation() = runBlocking {
        val f = Fixture().apply { responseOwner = 99 }
        rejected(403) { f.repository.lifecycle("7", FarmerProductAction.UNLIST) }
        assertEquals(1, f.writes().size)
    }
}
