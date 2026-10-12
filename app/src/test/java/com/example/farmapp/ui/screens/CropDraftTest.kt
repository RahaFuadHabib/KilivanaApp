package com.example.farmapp.ui.screens

import org.junit.Assert.*
import org.junit.Test

class CropDraftTest {
    private fun valid() = CropDraft("Avocados", "20.5", "150", "Ready for harvest", listOf("photo"), categoryId = "00000000-0000-0000-0000-000000000001", categoryName = "Test category")
    @Test fun validLocalDraftUsesExistingKilogramTerminology() { assertNull(valid().validationError()) }
    @Test fun defaultUnitIsKg() { assertEquals(CropUnit.KG, CropDraft().unit) }
    @Test fun changingUnitPreservesNumericValuesAndPhotos() {
        val changed = valid().copy(unit = CropUnit.BAGS)
        assertEquals(CropUnit.BAGS, changed.unit)
        assertEquals(valid().quantity, changed.quantity)
        assertEquals(valid().pricePerUnit, changed.pricePerUnit)
        assertEquals(valid().photos, changed.photos)
        assertEquals(CropUnit.BAGS, changed.addPhotos(listOf("second")).removePhoto("photo").unit)
    }
    @Test fun everyUnitHasTheCorrectPriceLabel() {
        assertEquals(listOf("kg", "bags", "crates", "pieces", "bunches"), CropUnit.entries.map { it.label })
        assertEquals(listOf("Price (KES/kg)", "Price (KES/bag)", "Price (KES/crate)", "Price (KES/piece)", "Price (KES/bunch)"),
            CropUnit.entries.map { it.priceLabel })
    }
    @Test fun savedDraftRetainsUnitAndFormForEveryUnit() {
        CropUnit.entries.forEach {
            val draft = valid().copy(unit = it)
            assertEquals(draft, CropDraft.fromSavedValues(draft.savedValues()))
        }
    }
    @Test fun legacySavedDraftDefaultsToKgWithoutLosingPhotos() {
        assertEquals(valid().copy(categoryId = null, categoryName = null), CropDraft.fromSavedValues(listOf("Avocados", "20.5", "150", "Ready for harvest", "photo")))
    }
    @Test fun invalidNumbersNeverPassReview() {
        CropUnit.entries.forEach { unit ->
            assertNull(valid().copy(unit = unit).validationError())
            listOf("", "abc", "0", "-1", "NaN", "Infinity").forEach {
                assertNotNull(valid().copy(unit = unit, quantity = it).validationError())
                assertNotNull(valid().copy(unit = unit, pricePerUnit = it).validationError())
            }
        }
    }
    @Test fun cropNameAndPhotoRequiredForReview() {
        assertNotNull(valid().copy(name = " ").validationError())
        assertNotNull(valid().copy(photos = emptyList()).validationError())
    }
    @Test fun addingAndRemovingPhotosPreservesFormAndOtherPhotos() {
        val draft = valid().addPhotos(listOf("second", "second", "third"))
        assertEquals(listOf("photo", "second", "third"), draft.photos)
        assertEquals(valid().name, draft.removePhoto("second").name)
        assertEquals(listOf("photo", "third"), draft.removePhoto("second").photos)
        assertEquals(draft, draft.removePhoto("missing"))
    }
    @Test fun photoLimitBoundsDraftResources() {
        assertEquals(MAX_CROP_PHOTOS, valid().addPhotos((1..30).map { "photo$it" }).photos.size)
    }
}
