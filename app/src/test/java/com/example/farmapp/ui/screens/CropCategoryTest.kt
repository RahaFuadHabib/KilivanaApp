package com.example.farmapp.ui.screens

import com.example.farmapp.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CropCategoryTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val secondId = "00000000-0000-0000-0000-000000000002"
    private val category = CategoryResponse(id, "Backend category", "FARM_PRODUCE", true)
    private fun draft() = CropDraft("Avocado", "5.25", "100.50", "Fresh", listOf("photo"), CropUnit.PIECES,
        categoryId = id, categoryName = category.name)
    private fun product() = FarmerProductResponse("p", "Avocado", "Fresh", "pieces", BigDecimal("100"), BigDecimal("5"),
        categoryId = id, lowStockThreshold = BigDecimal("2"), minimumOrderQuantity = BigDecimal.ONE, categoryName = category.name)

    @Test fun categoryIsRequiredWithNoDefaultSelection() {
        assertNull(CropDraft().categoryId)
        assertNull(CropDraft().categoryName)
        assertNotNull(draft().copy(categoryId = null).validationError())
        assertNotNull(draft().copy(categoryId = " ").validationError())
        assertNull(draft().validationError())
        try { draft().copy(categoryId = " ").toCreateProductRequest(); fail("Category required") } catch (_: IllegalArgumentException) { }
        assertFalse(CategoryCatalog(items = listOf(category), loaded = true).contains(null))
    }
    @Test fun createUsesExactIdAndNameIsPresentationOnly() {
        CropUnit.entries.forEach { unit ->
            val request = draft().copy(unit = unit).toCreateProductRequest()
            assertEquals(id, request.categoryId)
            assertEquals(unit.label, request.unit)
            assertEquals(BigDecimal("5.25"), request.availableQuantity)
            assertEquals(BigDecimal("100.50"), request.unitPrice)
            assertEquals(request, draft().copy(unit = unit, categoryName = "Renamed display").toCreateProductRequest())
            val json = Gson().toJson(request)
            assertTrue(json.contains(id)); assertFalse(json.contains(category.name)); assertFalse(json.contains("categoryName"))
        }
    }
    @Test fun checkpointKeepsCategoryAndLegacyDraftsDoNotInventOne() {
        val saved = draft().copy(productId = "p", uploadedPhotos = 1, creationUncertain = false)
        assertEquals(saved, CropDraft.fromSavedValues(saved.savedValues()))
        val uncertain = draft().copy(creationUncertain = true)
        assertEquals(uncertain, CropDraft.fromSavedValues(uncertain.savedValues()))
        val old = listOf("crop-draft-v3", "Avocado", "5", "100", "Fresh", "PIECES", "p", "1", "false", "photo")
        val restored = CropDraft.fromSavedValues(old)
        assertNull(restored.categoryId); assertNull(restored.categoryName)
        assertEquals("p", restored.productId); assertEquals(listOf("photo"), restored.photos)
        assertNull(restored.validationError()) // Only resumes an already-created listing's photos.
        assertNotNull(restored.copy(productId = null).validationError())
    }
    @Test fun failedCreateAndRetryRetainSelectedCategory() = runBlocking {
        var saved = draft()
        var reject = true
        val requests = mutableListOf<CreateProductRequest>()
        val publisher = CropPublisher({ requests += it; if (reject) throw com.example.farmapp.repository.ProductFailure("rejected", true); "p" },
            { _, _ -> }, { listOf(product()) })
        try { publisher.publish(saved, { saved = it }, {}); fail("Expected rejection") }
        catch (_: com.example.farmapp.repository.ProductFailure) { }
        saved = CropDraft.fromSavedValues(saved.savedValues())
        assertEquals(id, saved.categoryId); assertEquals(category.name, saved.categoryName)
        reject = false
        publisher.publish(saved, { saved = it }, {})
        assertEquals(listOf(id, id), requests.map { it.categoryId })
    }
    @Test fun editInitializesFromProductAndSendsChangedCategoryInCompleteRequest() {
        val product = product()
        val original = CropEdit.from(product)
        assertEquals(id, original.categoryId)
        assertEquals(id, original.request(product).categoryId)
        val changed = original.copy(categoryId = secondId).request(product)
        assertEquals(secondId, changed.categoryId)
        assertEquals(product.name, changed.title); assertEquals(product.description, changed.description)
        assertEquals(product.unit, changed.unit); assertEquals(product.unitPrice, changed.unitPrice)
        assertEquals(product.availableQuantity, changed.availableQuantity)
        assertEquals(product.lowStockThreshold, changed.lowStockThreshold)
        assertEquals(product.minimumOrderQuantity, changed.minimumOrderQuantity)
        try { original.copy(categoryId = null).request(product); fail("Category required") } catch (_: IllegalArgumentException) { }
    }
    @Test fun catalogLoadingFailureEmptyAndInactiveStatesNeverEnableSubmission() {
        val loaded = CategoryCatalog(items = listOf(category), loaded = true)
        assertTrue(loaded.contains(id)); assertEquals(category.name, loaded.name(id))
        assertNull(loaded.message)
        assertFalse(loaded.copy(loading = true).contains(id))
        assertNotNull(loaded.copy(loading = true).message)
        val failed = loaded.copy(error = "Unable to load categories")
        assertFalse(failed.contains(id)); assertEquals("Unable to load categories", failed.message)
        assertEquals(category.name, failed.name(id, category.name))
        assertFalse(CategoryCatalog().contains(id)); assertNotNull(CategoryCatalog().message)
        val empty = CategoryCatalog(loaded = true)
        assertFalse(empty.contains(id)); assertNotNull(empty.message)
        val excluded = loaded.copy(items = listOf(category.copy(active = false), category.copy(categoryId = secondId, sector = "AGRI_INPUTS")))
        assertTrue(excluded.selectable.isEmpty()); assertFalse(excluded.contains(id))
        assertEquals("", loaded.name(secondId)) // No UUID or first-category fallback.
    }
    @Test fun categoryAndProductDtosReadBackendNamesAndIds() {
        val response = Gson().fromJson("""{"categoryId":"$id","name":"Backend category","sector":"FARM_PRODUCE","active":true}""", CategoryResponse::class.java)
        assertEquals(category, response)
        val product = Gson().fromJson("""{"productId":"p","name":"Avocado","unit":"pieces","unitPrice":100,"availableQuantity":5,"categoryId":"$id","categoryName":"Backend category"}""", FarmerProductResponse::class.java)
        assertEquals(id, product.categoryId); assertEquals(category.name, product.categoryName)
    }
}
