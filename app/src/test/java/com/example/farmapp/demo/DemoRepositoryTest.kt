package com.example.farmapp.demo

import com.example.farmapp.data.model.CreateProductRequest
import com.example.farmapp.data.model.UpdateProductRequest
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class DemoRepositoryTest {
    private fun request() = CreateProductRequest("Sorghum", "Clean dry sorghum from Nakuru.", "kg", BigDecimal("70"), BigDecimal("450"), "demo-cereals")

    @Test fun seedIsDeterministicAndOrderTotalsAreConsistent() {
        assertEquals(DemoData.initial(), DemoData.initial())
        assertEquals("Mary Wanjiku", DemoData.profile.name)
        assertEquals(listOf("Maize", "Tomatoes", "Potatoes", "Beans"), DemoData.initial().crops.map { it.name })
        assertEquals(setOf("PLACED", "CONFIRMED", "IN_TRANSIT", "DELIVERED"), DemoData.initial().orders.map { it.status }.toSet())
        DemoData.initial().orders.forEach { order ->
            assertEquals("KES", order.currency)
            assertEquals(order.totalAmount, order.items.fold(BigDecimal.ZERO) { total, item -> total + item.lineTotal })
            order.items.forEach { assertEquals(it.quantity * it.unitPrice, it.lineTotal) }
        }
    }

    @Test fun addingIsLocalAndUsesUniqueIds() {
        val repository = DemoRepository()
        assertEquals("demo-crop-5", repository.add(request()))
        assertEquals("demo-crop-6", repository.add(request()))
        assertEquals(6, repository.state.value.crops.size)
        assertEquals("Sorghum", repository.state.value.crops.first().name)
        assertTrue(repository.state.value.activity.first().startsWith("Added locally"))
        assertEquals(4, DemoRepository().state.value.crops.size)
    }

    @Test fun invalidAddsNeverMutateLocalData() {
        val repository = DemoRepository()
        val original = repository.state.value
        listOf(request().copy(title = ""), request().copy(unitPrice = BigDecimal.ZERO),
            request().copy(availableQuantity = BigDecimal.ZERO), request().copy(categoryId = "real-category"),
            request().copy(unit = "unsupported"), request().copy(description = "")).forEach {
            assertThrows(IllegalArgumentException::class.java) { repository.add(it) }
            assertEquals(original, repository.state.value)
        }
    }

    @Test fun savedSnapshotRetainsChangesAndNextIdWithoutAuthenticationFields() {
        val repository = DemoRepository()
        repository.add(request())
        repository.confirmOrder("DEMO-1001")
        val encoded = repository.savedSnapshot()
        assertFalse(encoded.contains("accessToken"))
        assertFalse(encoded.contains("refreshToken"))
        val restored = DemoRepository.restore(encoded)
        assertEquals(repository.state.value, restored.state.value)
        assertEquals("demo-crop-6", restored.add(request()))
    }

    @Test fun editingAndUnlistingKeepSampleOrders() {
        val repository = DemoRepository()
        val originalOrders = repository.state.value.orders
        repository.edit("demo-crop-1", UpdateProductRequest("White Maize", "Updated demo description", "kg", BigDecimal("60"), BigDecimal.ZERO,
            "demo-cereals", null, null))
        assertEquals("INACTIVE", repository.state.value.crops.first().status)
        assertEquals("White Maize", repository.state.value.crops.first().name)
        repository.unlist("demo-crop-1")
        assertEquals("HIDDEN", repository.state.value.crops.first().status)
        assertEquals(originalOrders, repository.state.value.orders)
    }

    @Test fun confirmationOnlyChangesPlacedOrderAndNeverDeductsStock() {
        val repository = DemoRepository()
        val crops = repository.state.value.crops
        repository.confirmOrder("DEMO-1001")
        assertEquals("CONFIRMED", repository.state.value.orders.first().status)
        assertEquals(crops, repository.state.value.crops)
        assertThrows(IllegalArgumentException::class.java) { repository.confirmOrder("DEMO-1001") }
        assertThrows(IllegalArgumentException::class.java) { repository.confirmOrder("DEMO-1004") }
    }
}
