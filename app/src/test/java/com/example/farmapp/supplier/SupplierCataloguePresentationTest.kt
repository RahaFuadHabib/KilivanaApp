package com.example.farmapp.supplier

import org.junit.Assert.*
import org.junit.Test

class SupplierCataloguePresentationTest {
    @Test fun initialStateDoesNotClaimEmptyInventory() {
        val state = SupplierUiState()
        assertFalse(state.catalogueVisible)
        assertTrue(state.catalogueWaiting)
    }
    @Test fun confirmedEmptyCatalogueCanShowZeroCounts() {
        val state = SupplierUiState(catalogueLoaded = true)
        assertTrue(state.catalogueVisible)
        assertFalse(state.catalogueWaiting)
        assertEquals(0, supplierMetrics(state.listings).total)
    }
    @Test fun initialFailureDoesNotBecomeAnEmptySuccess() {
        val state = SupplierUiState(error = "Unable to connect")
        assertFalse(state.catalogueVisible)
        assertFalse(state.catalogueWaiting)
    }
    @Test fun failedRefreshKeepsPreviouslyVerifiedCatalogueVisible() {
        val state = SupplierUiState(catalogueLoaded = true, error = "Unable to connect")
        assertTrue(state.catalogueVisible)
        assertFalse(state.catalogueWaiting)
    }
}
