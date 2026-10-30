package com.example.farmapp.supplier

import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal

class SupplierOrdersTest {
    private fun line(id: Long = 1, seller: Long? = 42) = SupplierOrderLineDto(id, 7, seller,
        "Synthetic input", "bag", 3, BigDecimal("0.10"), BigDecimal("0.30"))
    private fun row(id: Long = 8, lines: List<SupplierOrderLineDto> = listOf(line())) =
        SupplierOrderDto(id, "SYNTHETIC", "confirmed", "2026-10-30T09:00:00Z", lines)
    private fun page(rows: List<SupplierOrderDto> = listOf(row()), number: Int = 0, last: Boolean = true) =
        SupplierOrderPageDto(rows, number, 20, last, if (last) number + 1 else number + 2, 40)
    private fun repository(api: SupplierApi, store: SessionStore) = SupplierRepository(api, store, SUPPLIER_TEST_ORIGIN)
    private fun rejects(block: () -> Unit) { try { block(); fail("Expected verification failure") } catch (_: SupplierFailure) { } }

    @Test fun mixedSellerOrderOnlyRetainsOwnedLinesAndExactSubtotal() {
        val result = page(listOf(row(lines = listOf(line(), line(2, 99).copy(productName = "Other seller private item")))))
            .verified(42, 0, 20).orders.single()
        assertEquals(listOf(1L), result.lines.map { it.id })
        assertEquals(BigDecimal("0.30"), result.supplierSubtotal)
        assertFalse(result.toString().contains("Other seller"))
    }
    @Test fun missingOwnershipOrForeignOnlyOrdersFailClosed() {
        for (lines in listOf(listOf(line(seller = null)), listOf(line(seller = 99)), emptyList())) {
            rejects { page(listOf(row(lines = lines))).verified(42, 0, 20) }
        }
    }
    @Test fun malformedOwnedPricesQuantitiesAndDuplicatesAreRejected() {
        for (lines in listOf(listOf(line().copy(quantity = 0)), listOf(line().copy(subtotal = BigDecimal("100"))),
            listOf(line().copy(unitPrice = null)), listOf(line(), line()))) {
            rejects { page(listOf(row(lines = lines))).verified(42, 0, 20) }
        }
        rejects { page(listOf(row(), row())).verified(42, 0, 20) }
    }
    @Test fun unknownStatusDoesNotBecomeACompletedOrder() {
        assertNull(page(listOf(row().copy(status = "future_status"))).verified(42, 0, 20).orders.single().status)
        assertEquals("confirmed", page(listOf(row().copy(status = "CONFIRMED"))).verified(42, 0, 20).orders.single().status)
    }
    @Test fun invalidPaginationCannotLoopOrSkipPages() {
        rejects { page(number = 1).verified(42, 0, 20) }
        rejects { page().copy(last = false).verified(42, 0, 20) }
        rejects { page().copy(size = null).verified(42, 0, 20) }
        rejects { page(emptyList(), last = false).verified(42, 0, 20) }
        assertTrue(SupplierOrderPageDto(emptyList(), 0, 20, true, 0, 0).verified(42, 0, 20).orders.isEmpty())
    }
    @Test fun requestUsesVerifiedUserIdentityAndExplicitPageParameters() = runBlocking {
        val store = supplierStore()
        val paths = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); paths += request.url.encodedPath
            val data: Any = if (request.url.encodedPath.endsWith("auth/me")) supplierUser() else {
                assertEquals("1", request.url.queryParameter("page"))
                assertEquals("20", request.url.queryParameter("size"))
                page(number = 1)
            }
            okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic")
                .body(CanonicalJson.gson.toJson(CanonicalApiResponse(true, data = data)).toResponseBody()).build()
        }.build()
        val api = Retrofit.Builder().baseUrl(SUPPLIER_TEST_ORIGIN).client(client)
            .addConverterFactory(GsonConverterFactory.create(CanonicalJson.gson)).build().create(SupplierApi::class.java)
        assertEquals(1, repository(api, store).orders(store.session.value, 1).number)
        assertEquals(listOf("/api/v1/auth/me", "/api/v1/suppliers/42/orders"), paths)
    }
    @Test fun wrongRoleOrAccountCannotRequestSupplierOrders() = runBlocking {
        val store = supplierStore()
        var calls = 0
        val api = object : StubSupplierApi() {
            override suspend fun orders(supplierId: Long, page: Int, size: Int, session: SessionSnapshot): Response<CanonicalApiResponse<SupplierOrderPageDto>> {
                calls++; return super.orders(supplierId, page, size, session)
            }
        }
        api.account = supplierUser(99)
        try { repository(api, store).orders(store.session.value, 0); fail() } catch (_: SupplierFailure) { }
        assertEquals(0, calls)
        store.clear(store.beginLogout()!!)
        store.establish(store.session.value, supplierAuth().copy(role = "BUYER"), false)
        try { repository(api, store).orders(store.session.value, 0); fail() } catch (_: SupplierFailure) { }
        assertEquals(0, calls)
    }
    @Test fun forbiddenOrdersStopWithoutAlternateEndpoint() = runBlocking {
        val store = supplierStore()
        var calls = 0
        val api = object : StubSupplierApi() {
            override suspend fun orders(supplierId: Long, page: Int, size: Int, session: SessionSnapshot): Response<CanonicalApiResponse<SupplierOrderPageDto>> {
                calls++; return Response.error(403, "Synthetic private server error".toResponseBody())
            }
        }
        try { repository(api, store).orders(store.session.value, 0); fail() }
        catch (e: SupplierFailure) { assertTrue(e.rejected); assertTrue(e.message!!.contains("cannot access")); assertFalse(e.message!!.contains("private")) }
        assertEquals(1, calls)
    }
    @Test fun duplicateLoadsPaginationAndRefreshAreGuarded() = runBlocking {
        val store = supplierStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var calls = 0
        val release = CompletableDeferred<Unit>()
        val api = object : StubSupplierApi() {
            override suspend fun orders(supplierId: Long, page: Int, size: Int, session: SessionSnapshot): Response<CanonicalApiResponse<SupplierOrderPageDto>> {
                calls++; if (calls == 1) release.await()
                return Response.success(CanonicalApiResponse(true, data = this@SupplierOrdersTest.page(listOf(row(if (page == 0) 8 else 9)), page, page == 1)))
            }
        }
        val holder = SupplierStateHolder(repository(api, store), store, MemorySupplierOperations(), scope)
        try {
            withTimeout(2000) { holder.uiState.first { it.catalogueLoaded } }
            holder.loadOrders(true); holder.loadOrders(true); assertEquals(1, calls)
            release.complete(Unit)
            withTimeout(2000) { holder.uiState.first { it.ordersLoaded } }
            holder.loadOrders(false)
            withTimeout(2000) { holder.uiState.first { it.ordersLast } }
            assertEquals(listOf(8L, 9L), holder.uiState.value.orders.map { it.id })
            holder.loadOrders(false); assertEquals(2, calls)
            holder.loadOrders(true)
            assertEquals(listOf(8L), holder.uiState.value.orders.map { it.id })
        } finally { holder.close(); scope.cancel() }
    }
    @Test fun lateResponseCannotLeakOrdersAcrossAccounts() = runBlocking {
        val store = supplierStore()
        val expected = store.session.value
        val api = object : StubSupplierApi() {
            override suspend fun orders(supplierId: Long, page: Int, size: Int, session: SessionSnapshot): Response<CanonicalApiResponse<SupplierOrderPageDto>> {
                store.clear(store.beginLogout()!!)
                store.establish(store.session.value, supplierAuth("99"), false)
                return Response.success(CanonicalApiResponse(true, data = this@SupplierOrdersTest.page()))
            }
        }
        try { repository(api, store).orders(expected, 0); fail() } catch (_: SupplierFailure) { }
    }
    @Test fun offlineRefreshRetainsRecordsButAccessDenialClearsThem() = runBlocking {
        val store = supplierStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var mode = 0
        val api = object : StubSupplierApi() {
            override suspend fun orders(supplierId: Long, page: Int, size: Int, session: SessionSnapshot): Response<CanonicalApiResponse<SupplierOrderPageDto>> = when (mode) {
                1 -> throw java.io.IOException("Synthetic connection failure")
                2 -> Response.error(403, "Synthetic".toResponseBody())
                else -> Response.success(CanonicalApiResponse(true, data = this@SupplierOrdersTest.page()))
            }
        }
        val holder = SupplierStateHolder(repository(api, store), store, MemorySupplierOperations(), scope)
        try {
            withTimeout(2000) { holder.uiState.first { it.catalogueLoaded } }
            holder.loadOrders(true)
            assertEquals(1, holder.uiState.value.orders.size)
            mode = 1; holder.loadOrders(true)
            assertEquals(1, holder.uiState.value.orders.size)
            assertNotNull(holder.uiState.value.ordersError)
            mode = 0; holder.loadOrders(true)
            assertNull(holder.uiState.value.ordersError)
            mode = 2; holder.loadOrders(true)
            assertTrue(holder.uiState.value.orders.isEmpty())
            assertFalse(holder.uiState.value.ordersLoaded)
            assertTrue(holder.uiState.value.ordersError!!.contains("cannot access"))
        } finally { holder.close(); scope.cancel() }
    }
}
