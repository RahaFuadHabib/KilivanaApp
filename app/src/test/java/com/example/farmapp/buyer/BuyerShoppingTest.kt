package com.example.farmapp.buyer

import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.example.farmapp.network.canonical.toFarmerProduct
import com.example.farmapp.supplier.supplierAuth
import com.example.farmapp.supplier.supplyDto
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal

class BuyerShoppingTest {
    private fun product(id: Long = 7) = supplyDto(id).copy(sellerType = "farmer", status = "active", price = BigDecimal("125.57"), minimumOrderQty = 2).toFarmerProduct()
    private class Memory(var value: BuyerSelection = BuyerSelection()) : BuyerSelectionStore {
        var fail = false
        override fun read() = value
        override fun write(value: BuyerSelection) { check(!fail); this.value = value }
    }
    private inner class Catalog : BuyerCatalog {
        var row = product()
        var queries = mutableListOf<Pair<String, Long?>>()
        var fail = false
        var last = true
        val pages = mutableListOf<Int>()
        var hold: CompletableDeferred<Unit>? = null
        var detailGate: CompletableDeferred<Unit>? = null
        var detailFailure = false
        override suspend fun categories(session: SessionSnapshot) = listOf(CanonicalCategoryDto(2, "Synthetic produce", "farmer", true))
        override suspend fun page(session: SessionSnapshot, query: String, category: Long?, page: Int): Pair<List<CanonicalFarmerProduct>, Boolean> {
            queries += query to category; pages += page; hold?.await(); check(!fail)
            return listOf(row.copy(productId = (7L + page).toString())) to last
        }
        override suspend fun detail(session: SessionSnapshot, id: Long): CanonicalFarmerProduct {
            detailGate?.await(); check(!detailFailure)
            return row.copy(productId = id.toString())
        }
    }
    private fun store() = signedInStore(supplierAuth().copy(role = "BUYER"))
    private fun rejects(action: () -> Unit) { try { action(); fail("Expected invalid quantity") } catch (_: IllegalStateException) { } }

    @Test fun exactMoneyMinimumAvailableStockAndLoadedSorting() {
        val row = product()
        assertEquals(BigDecimal("251.14"), buyerLineTotal(row, 2))
        rejects { buyerQuantity(row, 1) }; rejects { buyerQuantity(row, 11) }; rejects { buyerQuantity(row, 0) }
        rejects { buyerQuantity(row.copy(sector = "AGRI_INPUTS"), 2) }
        rejects { buyerQuantity(row.copy(status = CanonicalProductStatus.UNLISTED), 2) }
        val lower = row.copy(productId = "8", unitPrice = BigDecimal("125.56"))
        assertEquals(listOf(lower, row), buyerSortedProducts(listOf(row, lower), 1))
        assertEquals(listOf(row, lower), buyerSortedProducts(listOf(lower, row), 2))
    }
    @Test fun cartAndSavedPersistOnlySelectionsAndRecoverWithFreshPrices() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Catalog(); val sessions = store(); val disk = Memory()
            var clock = 0L
            val first = BuyerShoppingController(api, sessions, sessions.session.value, disk, scope) { clock }
            first.start(); first.toggleSaved(7); first.add(api.row, 2)
            assertEquals(mapOf(7L to 2), disk.value.cart); assertEquals(setOf(7L), disk.value.saved)
            repeat(5) { first.add(api.row, 2) }; assertEquals(2, disk.value.cart[7])
            clock += 600_000_000L
            first.add(api.row, 2); assertEquals(4, disk.value.cart[7])
            first.add(api.row, Int.MAX_VALUE); assertEquals(4, disk.value.cart[7])
            first.setQuantity(7, 1); assertEquals(4, disk.value.cart[7])
            api.row = api.row.copy(unitPrice = BigDecimal("126.03"))
            val restored = BuyerShoppingController(api, sessions, sessions.session.value, disk, scope)
            restored.start()
            assertEquals(BigDecimal("504.12"), buyerCartSubtotal(restored.state.value))
            restored.remove(7); assertTrue(disk.value.cart.isEmpty()); assertEquals(setOf(7L), disk.value.saved)
        } finally { scope.cancel() }
    }
    @Test fun missingProductNeverFabricatesSubtotalAndCanBeRemoved() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Catalog().apply { detailFailure = true }; val sessions = store(); val disk = Memory(BuyerSelection(setOf(7), mapOf(7L to 2)))
            val controller = BuyerShoppingController(api, sessions, sessions.session.value, disk, scope)
            controller.start()
            assertTrue(7L in controller.state.value.unavailable)
            assertNull(buyerCartSubtotal(controller.state.value))
            assertEquals(2, controller.state.value.selection.cart[7])
            controller.remove(7); assertEquals(BigDecimal.ZERO, buyerCartSubtotal(controller.state.value))
        } finally { scope.cancel() }
    }
    @Test fun failedSearchRetriesAttemptedFiltersAndRetainsPreviousRows() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Catalog(); val sessions = store(); val controller = BuyerShoppingController(api, sessions, sessions.session.value, Memory(), scope)
            controller.start(); api.fail = true; controller.refresh("beans", 2)
            assertEquals(1, controller.state.value.products.size); assertNotNull(controller.state.value.error)
            api.fail = false; controller.refresh()
            assertEquals("beans" to 2L, api.queries.last()); assertEquals("beans", controller.state.value.query)
        } finally { scope.cancel() }
    }
    @Test fun concurrentPageLoadIsSingleFlightAndLateLogoutCannotPublish() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Catalog().apply { hold = CompletableDeferred() }; val sessions = store()
            val controller = BuyerShoppingController(api, sessions, sessions.session.value, Memory(), scope)
            controller.start(); repeat(3) { controller.refresh() }
            assertEquals(1, api.queries.size); assertTrue(controller.state.value.busy)
            sessions.beginLogout(); api.hold!!.complete(Unit)
            assertTrue(controller.state.value.products.isEmpty())
        } finally { scope.cancel() }
    }
    @Test fun closedDetailCannotOverwriteNewSelectionAndStorageFailureIsNotPublished() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Catalog().apply { detailGate = CompletableDeferred() }; val sessions = store(); val disk = Memory()
            val controller = BuyerShoppingController(api, sessions, sessions.session.value, disk, scope)
            controller.open(7); controller.closeDetail(); controller.open(8); api.detailGate!!.complete(Unit)
            assertEquals("8", controller.state.value.detail!!.productId); assertFalse(controller.state.value.detailBusy)
            disk.fail = true; controller.toggleSaved(8)
            assertTrue(controller.state.value.selection.saved.isEmpty()); assertNotNull(controller.state.value.notice)
        } finally { scope.cancel() }
    }
    @Test fun loadMoreAfterFailedFilterKeepsDisplayedQueryAndPage() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Catalog().apply { last = false }; val sessions = store()
            val controller = BuyerShoppingController(api, sessions, sessions.session.value, Memory(), scope)
            controller.refresh("beans", 2); api.fail = true; controller.refresh("eggs", 3)
            api.fail = false; controller.refresh(more = true)
            assertEquals("beans" to 2L, api.queries.last()); assertEquals(1, api.pages.last())
            assertEquals(listOf("7", "8"), controller.state.value.products.map { it.productId })
            controller.refresh(); assertEquals("eggs" to 3L, api.queries.last()); assertEquals(0, api.pages.last())
            assertEquals(listOf("7"), controller.state.value.products.map { it.productId })
        } finally { scope.cancel() }
    }
    @Test fun oneUnitAddPreservesCentsAndRejectsStockAndStorageFailures() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Catalog(); val sessions = store(); val disk = Memory(); var clock = 0L
            val controller = BuyerShoppingController(api, sessions, sessions.session.value, disk, scope) { clock }
            val row = api.row.copy(minimumOrderQuantity = BigDecimal.ONE, availableQuantity = BigDecimal.ONE)
            controller.add(row, 1)
            repeat(10) { controller.add(row, 1) }
            assertEquals(mapOf(7L to 1), disk.value.cart)
            assertEquals(BigDecimal("125.57"), buyerCartSubtotal(controller.state.value))
            clock += 600_000_000L; controller.dismissNotice(); controller.add(row, 1)
            assertEquals(1, disk.value.cart[7]); assertFalse(controller.state.value.notice!!.startsWith("Added"))
            controller.remove(7); disk.fail = true; controller.dismissNotice(); controller.add(row, 1)
            assertTrue(controller.state.value.selection.cart.isEmpty())
            assertFalse(controller.state.value.notice!!.startsWith("Added"))
        } finally { scope.cancel() }
    }
    @Test fun pullRefreshResetsPagePreservesFiltersAndIgnoresOverlap() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Catalog().apply { last = false }; val sessions = store()
            val controller = BuyerShoppingController(api, sessions, sessions.session.value, Memory(), scope)
            controller.refresh("beans", 2); controller.refresh(more = true)
            assertEquals(1, controller.state.value.page)
            api.hold = CompletableDeferred(); controller.refresh(); controller.refresh()
            assertEquals(listOf(0, 1, 0), api.pages)
            api.hold!!.complete(Unit)
            assertEquals(0, controller.state.value.page); assertEquals("beans" to 2L, api.queries.last())
            assertEquals(listOf("7"), controller.state.value.products.map { it.productId })
            api.fail = true; controller.refresh()
            assertEquals(listOf("7"), controller.state.value.products.map { it.productId }); assertNotNull(controller.state.value.error)
        } finally { scope.cancel() }
    }
    @Test fun quantityChangesPreserveExactTotalsAndNeverRemoveAtZero() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Catalog(); val sessions = store(); val disk = Memory()
            val controller = BuyerShoppingController(api, sessions, sessions.session.value, disk, scope)
            val row = api.row.copy(minimumOrderQuantity = BigDecimal.ONE, availableQuantity = BigDecimal("2"))
            controller.add(row, 1); controller.setQuantity(7, 2)
            assertEquals(BigDecimal("251.14"), buyerCartSubtotal(controller.state.value))
            controller.setQuantity(7, 3); assertEquals(2, disk.value.cart[7])
            controller.setQuantity(7, 1); controller.setQuantity(7, 0)
            assertEquals(1, disk.value.cart[7]); assertEquals(BigDecimal("125.57"), buyerCartSubtotal(controller.state.value))
        } finally { scope.cancel() }
    }
    @Test fun allCategoryResetsPaginationAndSavedTogglesDoNotChangeCart() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val api = Catalog().apply { last = false }; val sessions = store(); val disk = Memory()
            val c = BuyerShoppingController(api, sessions, sessions.session.value, disk, scope)
            c.refresh("tea", 2); c.refresh(more = true); assertEquals(1, c.state.value.page)
            c.refresh(category = null); assertEquals(0, c.state.value.page); assertEquals("tea" to null, api.queries.last())
            c.toggleSaved(7); assertEquals(setOf(7L), disk.value.saved); assertTrue(disk.value.cart.isEmpty())
            val restored = BuyerShoppingController(api, sessions, sessions.session.value, disk, scope)
            restored.start(); assertEquals(setOf(7L), restored.state.value.selection.saved)
            restored.toggleSaved(7); assertTrue(disk.value.saved.isEmpty()); assertTrue(disk.value.cart.isEmpty())
        } finally { scope.cancel() }
    }
    @Test fun selectionKeysSeparateAccountsAndOrigins() {
        assertNotEquals(buyerScope("https://one/api/v1/", "42"), buyerScope("https://two/api/v1/", "42"))
        assertNotEquals(buyerScope("https://one/api/v1/", "42"), buyerScope("https://one/api/v1/", "43"))
        assertFalse(buyerScope("https://one/api/v1/", "42").contains("https"))
    }
    @Test fun addressReadsRejectForeignOwnershipAndInvalidCoordinates() {
        val own = BuyerAddressDto(1, 42, "Home", "Synthetic address", -1.2, 36.8)
        assertEquals(listOf(own), validateBuyerAddresses(listOf(own), 42))
        rejects { validateBuyerAddresses(listOf(own.copy(userId = 99)), 42) }
        rejects { validateBuyerAddresses(listOf(own.copy(latitude = Double.NaN)), 42) }
        rejects { validateBuyerAddresses(listOf(own, own), 42) }
        rejects { validateBuyerAddresses(listOf(own.copy(addressText = "")), 42) }
    }
    @Test fun failedAddressReadRetainsOnlyPreviouslyValidatedAddresses() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = store(); var wrong = false
            val api = object : BuyerAddressApi {
                override suspend fun create(request: BuyerAddressRequest, session: SessionSnapshot): Response<CanonicalApiResponse<BuyerAddressDto>> = error("No write expected")
                override suspend fun list(session: SessionSnapshot) = Response.success(CanonicalApiResponse(true, data =
                    listOf(BuyerAddressDto(1, if (wrong) 99 else 42, "Home", "Synthetic address"))))
            }
            val controller = BuyerAddressController(api, sessions, sessions.session.value, scope)
            controller.refresh(); assertEquals(42L, controller.state.value.rows.single().userId)
            wrong = true; controller.refresh(); assertNotNull(controller.state.value.error)
            assertEquals(42L, controller.state.value.rows.single().userId)
        } finally { scope.cancel() }
    }
}
