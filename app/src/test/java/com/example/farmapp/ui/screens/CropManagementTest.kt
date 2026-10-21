package com.example.farmapp.ui.screens

import com.example.farmapp.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CropManagementTest {
    private val product = FarmerProductResponse("p", "Avocado", "Fresh", "pieces", BigDecimal("100"), BigDecimal("5"),
        listOf(ProductImageResponse("i", "a.png", "image/png", 10, "/api/v1/images/i", 0)), "ACTIVE", "category", BigDecimal("2"), BigDecimal("1"))
    @Test fun presentationUsesRealProductValues() {
        assertEquals(CropEdit("Avocado", "5", "pieces", "100", "Fresh", "category"), CropEdit.from(product))
        val detail = product.detailPresentation()
        assertEquals("Avocado", detail.name)
        assertEquals("5 pieces", detail.quantity)
        assertEquals("KES 100 / piece", detail.price)
        assertEquals("Fresh", detail.description)
        assertEquals("/api/v1/images/i", detail.imageUrl)
        assertEquals("ACTIVE", product.status)
        assertEquals("Active", detail.status)
    }
    @Test fun statusLabelsAreReadableWithoutChangingWireValues() {
        assertEquals("Pending Approval", productStatusLabel("PENDING_APPROVAL"))
        assertEquals("Archived", productStatusLabel("ARCHIVED"))
        assertEquals("Unlisted", productStatusLabel("unlisted"))
        assertEquals("Unknown", productStatusLabel(" "))
    }
    @Test fun completeRequestPreservesUnitsAndSettings() {
        CropUnit.entries.forEach {
            val request = CropEdit.from(product).copy(unit = it.label, quantity = "0", title = " New crop ").request(product)
            assertEquals(it.label, request.unit)
            assertEquals("New crop", request.title)
            assertEquals("Fresh", request.description)
            assertEquals(BigDecimal.ZERO, request.availableQuantity)
            assertEquals(product.unitPrice, request.unitPrice)
            assertEquals(product.categoryId, request.categoryId)
            assertEquals(product.lowStockThreshold, request.lowStockThreshold)
            assertEquals(product.minimumOrderQuantity, request.minimumOrderQuantity)
            val json = Gson().toJson(request)
            listOf("title", "description", "unit", "unitPrice", "availableQuantity", "categoryId", "lowStockThreshold", "minimumOrderQuantity").forEach { field -> assertTrue(json.contains("\"$field\"")) }
        }
    }
    @Test fun successfulEditRefreshesAndFailureKeepsData() = runBlocking {
        var visible = listOf(product)
        var reject = false
        val calls = mutableListOf<String>()
        val updated = product.copy(name = "Updated")
        val manager = CropManagement({ _, _ -> calls += "update"; if (reject) error("rejected") }, {}, { calls += "mine"; listOf(updated) })
        visible = manager.edit(product, CropEdit.from(product))
        assertEquals(listOf("update", "mine"), calls)
        assertEquals("Updated", visible.single().name)
        reject = true; calls.clear()
        try { visible = manager.edit(updated, CropEdit.from(updated)); fail("Expected rejection") } catch (_: IllegalStateException) { }
        assertEquals(listOf("update"), calls)
        assertEquals(listOf(updated), visible)
    }
    @Test fun deleteRequiresConfirmationAndRefreshesOnlyAfterSuccess() = runBlocking {
        var deleted = 0
        var refreshed = 0
        var reject = false
        var visible = listOf(product)
        val manager = CropManagement({ _, _ -> }, { if (reject) error("rejected"); deleted++ }, { refreshed++; listOf(product.copy(status = "HIDDEN")) })
        try { manager.confirmRemoval("p"); fail("Must confirm") } catch (_: IllegalStateException) { }
        assertEquals(0, deleted)
        manager.requestRemoval("p"); manager.cancelRemoval()
        try { manager.confirmRemoval("p"); fail("Cancelled") } catch (_: IllegalStateException) { }
        manager.requestRemoval("p"); reject = true
        try { visible = manager.confirmRemoval("p"); fail("Rejected") } catch (_: IllegalStateException) { }
        assertEquals(listOf(product), visible); assertEquals(0, refreshed)
        manager.requestRemoval("p"); reject = false
        visible = manager.confirmRemoval("p")
        assertEquals(1, deleted); assertEquals(1, refreshed)
        assertTrue(visible.filter { it.status == "ACTIVE" }.isEmpty())
    }
    @Test fun failedRefreshAfterAcceptedMutationRetainsVisibleData() = runBlocking {
        var visible = listOf(product)
        var updates = 0
        var removals = 0
        val manager = CropManagement({ _, _ -> updates++ }, { removals++ }, { error("refresh failed") })
        try { visible = manager.edit(product, CropEdit.from(product)); fail("Refresh should fail") } catch (_: IllegalStateException) { }
        assertEquals(1, updates); assertEquals(listOf(product), visible)
        manager.requestRemoval("p")
        try { visible = manager.confirmRemoval("p"); fail("Refresh should fail") } catch (_: IllegalStateException) { }
        assertEquals(1, removals); assertEquals(listOf(product), visible)
    }
    @Test fun orderCountIsDistinctSellerOrdersAcrossAllStatusesNotQuantity() {
        val item = FarmerOrderItem("p", "Avocado", BigDecimal("50"), BigDecimal.ONE, BigDecimal("50"))
        val order = FarmerOrderResponse("o", "farmer", "CANCELLED", BigDecimal("50"), "KES", "today", listOf(item, item))
        val orders = listOf(order, order, order.copy(orderId = "o2", status = "COMPLETED"),
            order.copy(orderId = "buyer-order", sellerId = "other"), order.copy(orderId = "other-product", items = listOf(item.copy(productId = "other"))))
        assertEquals(2, cropOrders(orders, "farmer", "p").size)
        assertEquals(0, cropOrders(emptyList(), "farmer", "p").size)
        val unavailable: List<FarmerOrderResponse>? = null
        assertNull(unavailable?.let { cropOrders(it, "farmer", "p").size })
    }
}
