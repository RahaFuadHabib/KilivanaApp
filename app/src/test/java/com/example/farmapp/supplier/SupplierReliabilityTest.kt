package com.example.farmapp.supplier

import com.example.farmapp.data.model.AuthResponse
import com.example.farmapp.data.model.RefreshRequest
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.time.Instant

class SupplierReliabilityTest {
    private fun transport(store: SessionStore, auth: ApiService, respond: (Request) -> okhttp3.Response): SupplierApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthTokenInterceptor(SUPPLIER_TEST_ORIGIN.toHttpUrl(), store, RefreshCoordinator(auth, store)))
            .addInterceptor { respond(it.request()) }.build()
        return Retrofit.Builder().baseUrl(SUPPLIER_TEST_ORIGIN).client(client)
            .addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson)).build().create(SupplierApi::class.java)
    }
    private fun response(request: Request, data: Any? = null, code: Int = 200) = okhttp3.Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("Synthetic")
        .body(CanonicalJson.gson.toJson(CanonicalApiResponse(true, data = data)).toResponseBody()).build()
    private suspend fun fails(action: suspend () -> Unit) {
        try { action(); fail("Expected rejected request") } catch (_: IOException) { }
    }
    @Test fun expiredTokenRefreshesBeforePublicCatalogueAndRetainsPendingProducts() = runBlocking {
        val store = signedInStore(supplierAuth().copy(accessTokenExpiresAt = Instant.EPOCH.toString()))
        val expected = store.session.value
        var refreshes = 0
        val auth = object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> {
                refreshes++
                return Response.success(supplierAuth().copy(accessToken = "fresh-supplier"))
            }
        }
        val paths = mutableListOf<String>()
        val api = transport(store, auth) { request ->
            paths += request.url.encodedPath
            assertEquals("Bearer fresh-supplier", request.header("Authorization"))
            response(request, if (request.url.encodedPath.endsWith("auth/me")) supplierUser() else listOf(supplyDto()))
        }
        val listings = SupplierRepository(api, store, SUPPLIER_TEST_ORIGIN).listings(expected)
        assertEquals(1, refreshes)
        assertEquals(listOf("/api/v1/auth/me", "/api/v1/products/seller/42"), paths)
        assertEquals(com.example.farmapp.data.model.canonical.CanonicalProductStatus.PENDING_APPROVAL, listings.single().status)
        assertEquals(expected.generation, store.session.value.generation)
    }
    @Test fun unknownExpiryAlsoRequiresRefreshBeforePublicRead() = runBlocking {
        val store = signedInStore(supplierAuth().copy(accessTokenExpiresAt = null))
        var refreshes = 0
        val auth = object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> {
                refreshes++; return Response.success(supplierAuth().copy(accessToken = "fresh"))
            }
        }
        val api = transport(store, auth) { request ->
            assertEquals("Bearer fresh", request.header("Authorization")); response(request, emptyList<Any>())
        }
        api.listings(42, store.session.value)
        assertEquals(1, refreshes)
    }
    @Test fun refreshFailureNeverFallsBackToAnonymousCatalogue() = runBlocking {
        val store = signedInStore(supplierAuth().copy(accessTokenExpiresAt = Instant.EPOCH.toString()))
        var calls = 0
        val auth = object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> = Response.error(503, "Synthetic".toResponseBody())
        }
        val api = transport(store, auth) { calls++; response(it) }
        fails { api.listings(42, store.session.value) }
        assertEquals(0, calls)
    }
    @Test fun accountSwitchDuringRefreshNeverUsesReplacementToken() = runBlocking {
        val store = signedInStore(supplierAuth().copy(accessTokenExpiresAt = Instant.EPOCH.toString()))
        val expected = store.session.value
        var calls = 0
        val auth = object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> {
                store.clear(store.beginLogout()!!)
                store.establish(store.session.value, supplierAuth("99"), false)
                return Response.success(supplierAuth())
            }
        }
        val api = transport(store, auth) { calls++; response(it) }
        fails { api.listings(42, expected) }
        assertEquals(0, calls)
        assertEquals("99", store.session.value.auth!!.userId)
    }
    @Test fun rejectedRefreshExpiresSessionWithoutCallingCatalogue() = runBlocking {
        val store = signedInStore(supplierAuth().copy(accessTokenExpiresAt = Instant.EPOCH.toString()))
        val expected = store.session.value
        var calls = 0
        val auth = object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> = Response.error(401, "Synthetic".toResponseBody())
        }
        val api = transport(store, auth) { calls++; response(it) }
        fails { api.listings(42, expected) }
        assertEquals(0, calls); assertNull(store.session.value.auth)
        assertTrue(store.session.value.generation > expected.generation)
    }
    @Test fun changedRoleAfter401IsNotRetriedAsFarmer() = runBlocking {
        val store = supplierStore()
        var calls = 0
        val auth = object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> = Response.success(supplierAuth().copy(role = "FARMER"))
        }
        val api = transport(store, auth) { calls++; response(it, code = 401) }
        fails { api.listings(42, store.session.value) }
        assertEquals(1, calls)
    }
    @Test fun expiredWriteIsNeverRefreshedOrReplayed() = runBlocking {
        val store = signedInStore(supplierAuth().copy(accessTokenExpiresAt = Instant.EPOCH.toString()))
        var calls = 0
        val auth = object : StubAuthService() {
            override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> = error("Must not replay writes")
        }
        val api = transport(store, auth) { calls++; response(it, code = 401) }
        assertEquals(401, api.create(supplyDraft().request(StubSupplierApi().categoryValues), store.session.value).code())
        assertEquals(1, calls)
    }
    @Test fun failedProtectedIdentityReadPreventsPublicCatalogueRequest() = runBlocking {
        val store = supplierStore()
        val api = object : StubSupplierApi() {
            override suspend fun me(session: SessionSnapshot): Response<CanonicalApiResponse<com.example.farmapp.data.model.canonical.CanonicalUserDto>> =
                Response.error(401, "Synthetic".toResponseBody())
        }
        fails { SupplierRepository(api, store, SUPPLIER_TEST_ORIGIN).listings(store.session.value) }
        assertNull(api.ownerRequested)
    }
    @Test fun refreshPreservesDetailsAndDataAndCoalescesRepeatedActions() = runBlocking {
        val store = supplierStore(); val release = CompletableDeferred<Unit>(); var reads = 0
        val api = object : StubSupplierApi() {
            override suspend fun listings(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<List<com.example.farmapp.data.model.canonical.CanonicalProductDto>>> {
                reads++
                if (reads > 1) release.await()
                return super.listings(id, session)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = SupplierStateHolder(SupplierRepository(api, store, SUPPLIER_TEST_ORIGIN), store, MemorySupplierOperations(), scope)
        try {
            holder.open(7)
            withTimeout(5000) { holder.uiState.first { it.detail != null } }
            holder.refresh(); holder.refresh(); holder.refresh()
            val during = holder.uiState.value
            assertTrue(during.refreshing); assertFalse(during.loading)
            assertEquals(7L, during.selectedId); assertNotNull(during.detail); assertEquals(1, during.listings.size)
            holder.create(supplyDraft()); assertEquals(0, api.writes)
            release.complete(Unit)
            withTimeout(5000) { holder.uiState.first { !it.refreshing } }
            assertEquals(2, reads); assertEquals(7L, holder.uiState.value.selectedId)
        } finally { release.complete(Unit); holder.close(); scope.cancel() }
    }
    @Test fun failedRefreshKeepsLastSuccessfulDataCategoriesProfileAndError() = runBlocking {
        val store = supplierStore(); var reads = 0
        val api = object : StubSupplierApi() {
            override suspend fun listings(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<List<com.example.farmapp.data.model.canonical.CanonicalProductDto>>> {
                if (++reads > 1) throw IOException("Synthetic failure")
                return super.listings(id, session)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = SupplierStateHolder(SupplierRepository(api, store, SUPPLIER_TEST_ORIGIN), store, MemorySupplierOperations(), scope)
        try {
            holder.refresh()
            val result = withTimeout(5000) { holder.uiState.first { it.error != null && !it.refreshing } }
            assertTrue(result.catalogueLoaded); assertEquals(1, result.listings.size)
            assertEquals(1, result.categories.size); assertNotNull(result.profile)
        } finally { holder.close(); scope.cancel() }
    }
    @Test fun accountSwitchDuringCatalogueReadDiscardsLatePrivateResponse() = runBlocking {
        val store = supplierStore(); val expected = store.session.value
        val api = object : StubSupplierApi() {
            override suspend fun listings(id: Long, session: SessionSnapshot): Response<CanonicalApiResponse<List<com.example.farmapp.data.model.canonical.CanonicalProductDto>>> {
                store.clear(store.beginLogout()!!); store.establish(store.session.value, supplierAuth("99"), false)
                return super.listings(id, session)
            }
        }
        fails { SupplierRepository(api, store, SUPPLIER_TEST_ORIGIN).listings(expected) }
    }
    @Test fun validationIdentifiesEachInvalidFieldAndMissingCategory() {
        val errors = SupplyDraft(name = "x", description = " ", unit = "", price = "1.001", available = "-1", minimum = "0").validationErrors(emptyList())
        assertEquals(SupplyField.entries.toSet(), errors.keys)
        assertTrue(supplyDraft().validationErrors(StubSupplierApi().categoryValues).isEmpty())
        assertEquals(setOf(SupplyField.CATEGORY), supplyDraft().validationErrors(emptyList()).keys)
    }
}
