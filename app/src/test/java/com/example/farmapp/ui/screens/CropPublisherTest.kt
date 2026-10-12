package com.example.farmapp.ui.screens

import com.example.farmapp.data.model.*
import com.example.farmapp.repository.ProductFailure
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CropPublisherTest {
    private fun draft() = CropDraft("Avocado", "5", "100", "Fresh and sweet", listOf("one", "two"), CropUnit.PIECES, categoryId = "00000000-0000-0000-0000-000000000001", categoryName = "Test category")
    private fun product() = FarmerProductResponse("real-id", "Avocado", "Fresh and sweet", "pieces", BigDecimal("100"), BigDecimal("5"))

    @Test fun mappingPreservesAllUnitsAndExactDecimals() {
        CropUnit.entries.forEach { unit ->
            val request = draft().copy(unit = unit, quantity = "5.123456789123456789").toCreateProductRequest()
            assertEquals(unit.label, request.unit)
            assertEquals(BigDecimal("5.123456789123456789"), request.availableQuantity)
            assertEquals(BigDecimal("100"), request.unitPrice)
            assertEquals("Avocado", request.title)
            assertEquals("Fresh and sweet", request.description)
            val json = Gson().toJson(request)
            assertFalse(json.contains("quantityKg")); assertFalse(json.contains("pricePerKg"))
            assertEquals(draft().categoryId, request.categoryId)
            assertFalse(json.contains("categoryName"))
        }
    }

    @Test fun partialFailureRetriesSameProductAndClearsOnlyAfterRefresh() = runBlocking {
        var creates = 0
        var saved = draft()
        var fail = true
        val calls = mutableListOf<String>()
        val publisher = CropPublisher(
            create = { creates++; calls += "create"; "real-id" },
            upload = { id, name ->
                assertEquals("real-id", id); calls += name
                if (name == "two" && fail) throw ProductFailure("Upload failed")
            },
            mine = { calls += "mine"; listOf(product()) })
        try { publisher.publish(saved, { saved = it }, {}) ; fail("Expected failure") }
        catch (_: ProductFailure) { }
        assertEquals("real-id", saved.productId)
        assertEquals(1, saved.uploadedPhotos)
        assertEquals(draft().photos, saved.photos)
        assertEquals(draft().categoryId, saved.categoryId)
        assertEquals(draft().categoryName, saved.categoryName)
        saved = CropDraft.fromSavedValues(saved.savedValues())
        fail = false
        val result = publisher.publish(saved, { saved = it }, {})
        assertEquals(1, creates)
        assertEquals(listOf("create", "one", "two", "two", "mine"), calls)
        assertEquals(listOf(product()), result)
        assertEquals(CropDraft(), saved)
    }

    @Test fun rejectedCreationKeepsEditableDraftAndDoesNotUpload() = runBlocking {
        var saved = draft()
        val publisher = CropPublisher({ throw ProductFailure("Rejected", true) }, { _, _ -> fail("Upload") }, { fail("Mine"); emptyList() })
        try { publisher.publish(saved, { saved = it }, {}); fail("Expected rejection") }
        catch (_: ProductFailure) { }
        assertEquals(draft(), saved)
    }

    @Test fun uncertainCreateCannotBlindlyCreateAgain() = runBlocking {
        var saved = draft()
        var creates = 0
        val publisher = CropPublisher({ creates++; throw java.io.IOException("offline") }, { _, _ -> }, { emptyList() })
        try { publisher.publish(saved, { saved = it }, {}) } catch (_: java.io.IOException) { }
        assertTrue(saved.creationUncertain)
        try { publisher.publish(saved, { saved = it }, {}); fail("Expected guard") } catch (_: IllegalStateException) { }
        assertEquals(1, creates)
    }

    @Test fun refreshFailureKeepsCompletedUploadCursor() = runBlocking {
        var saved = draft().copy(productId = "real-id", uploadedPhotos = 2)
        val publisher = CropPublisher({ fail("Create"); "" }, { _, _ -> fail("Upload") }, { throw ProductFailure("Offline") })
        try { publisher.publish(saved, { saved = it }, {}) } catch (_: ProductFailure) { }
        assertEquals(2, saved.uploadedPhotos)
        assertEquals("real-id", saved.productId)
    }

    @Test fun myProductsUsesBackendNameUuidAndDecimals() {
        val response = Gson().fromJson("""{"productId":"uuid","name":"Avocado","description":"Fresh","unit":"pieces","unitPrice":100.25,"availableQuantity":5,"images":[{"imageId":"image","fileName":"a.png","contentType":"image/png","sizeBytes":12,"url":"/api/v1/products/uuid/images/image/content","sortOrder":0}]}""", FarmerProductResponse::class.java)
        assertEquals("Avocado", response.name)
        assertEquals("pieces", response.unit)
        assertEquals(BigDecimal("100.25"), response.unitPrice)
        assertEquals("/api/v1/products/uuid/images/image/content", response.images.first().url)
    }
}
