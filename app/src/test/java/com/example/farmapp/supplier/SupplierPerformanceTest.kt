package com.example.farmapp.supplier

import com.example.farmapp.network.SessionSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.data.model.canonical.CanonicalUserDto
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

/** Synthetic API invocation counts, never live traffic or credentials. */
class SupplierPerformanceTest {
    @Test fun catalogueRefreshRequestBudget() = runBlocking {
        var identityReads = 0
        var resourceReads = 0
        val api = object : StubSupplierApi() {
            override suspend fun me(session: SessionSnapshot) = super.me(session).also { identityReads++ }
            override suspend fun listings(id: Long, session: SessionSnapshot) = super.listings(id, session).also { resourceReads++ }
            override suspend fun profile(id: Long, session: SessionSnapshot) = super.profile(id, session).also { resourceReads++ }
            override suspend fun categories(session: SessionSnapshot) = super.categories(session).also { resourceReads++ }
            override suspend fun listing(id: Long, session: SessionSnapshot) = super.listing(id, session).also { resourceReads++ }
        }
        val store = supplierStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val holder = SupplierStateHolder(SupplierRepository(api, store, SUPPLIER_TEST_ORIGIN), store, MemorySupplierOperations(), scope)
        suspend fun settled() { withTimeout(2000) { holder.uiState.first { it.catalogueLoaded && !it.profileLoading && !it.categoriesLoading && !it.refreshing } } }
        try {
            settled()
            println("SUPPLIER_OPTIMIZED initial auth/me=$identityReads resource=$resourceReads total=${identityReads + resourceReads}")
            assertEquals(1, identityReads); assertEquals(3, resourceReads)
            identityReads = 0; resourceReads = 0
            holder.refresh(); settled()
            println("SUPPLIER_OPTIMIZED refresh auth/me=$identityReads resource=$resourceReads total=${identityReads + resourceReads}")
            assertEquals(1, identityReads); assertEquals(3, resourceReads)
            holder.open(7)
            withTimeout(2000) { holder.uiState.first { it.detail != null && !it.detailLoading } }
            identityReads = 0; resourceReads = 0
            holder.refresh(); settled()
            println("SUPPLIER_OPTIMIZED detail-refresh auth/me=$identityReads resource=$resourceReads total=${identityReads + resourceReads}")
            assertEquals(1, identityReads); assertEquals(4, resourceReads)
        } finally { holder.close(); scope.cancel() }
    }
    @Test fun sharedIdentityFailureCannotFetchAnonymousResourcesOrStrandLoading() = runBlocking {
        var reads = 0
        val api = object : StubSupplierApi() {
            override suspend fun me(session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalUserDto>> {
                reads++; return Response.error(401, "Synthetic".toResponseBody())
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = supplierStore()
        val holder = SupplierStateHolder(SupplierRepository(api, store, SUPPLIER_TEST_ORIGIN), store, MemorySupplierOperations(), scope)
        try {
            val state = withTimeout(2000) { holder.uiState.first { it.error != null && !it.categoriesLoading && !it.profileLoading } }
            assertEquals(1, reads); assertNull(api.ownerRequested)
            assertFalse(state.loading); assertNotNull(state.profileError)
            assertEquals(1, state.categories.size)
        } finally { holder.close(); scope.cancel() }
    }
    @Test fun refreshBatchCannotReuseIdentityAfterAccountSwitch() = runBlocking {
        val store = supplierStore()
        val api = StubSupplierApi()
        val repository = SupplierRepository(api, store, SUPPLIER_TEST_ORIGIN)
        coroutineScope {
            val batch = repository.readBatch(store.session.value, this)
            batch.profile()
            store.clear(store.beginLogout()!!)
            store.establish(store.session.value, supplierAuth("99"), false)
            try { batch.listings(); fail("Old refresh identity must be rejected") } catch (_: SupplierFailure) { }
            assertNull(api.ownerRequested)
        }
    }
}
