package com.example.farmapp.supplier

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.ui.theme.FarmAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/** Synthetic screen callbacks only; no sessions, ApiClient or remote writes. */
@RunWith(AndroidJUnit4::class)
class SupplierUiTest {
    @get:Rule val compose = createComposeRule()
    private val listing = SupplyListing(7, "Synthetic seed listing", "Synthetic description", "bag", BigDecimal("100.25"), 10, 2, 1, 2,
        CanonicalProductStatus.PENDING_APPROVAL, emptyList())
    private fun screen(state: SupplierUiState, create: (SupplyDraft) -> Unit = {}) {
        compose.setContent { FarmAppTheme {
            SupplierContent(state, "Synthetic Supplier", {}, false, {}, false, {}, {}, {}, create, { _, _ -> }, { _, _ -> }, {})
        } }
    }
    @Test fun dashboardUsesListingsAndOrdersExplainMissingBackend() {
        screen(SupplierUiState(listings = listOf(listing)))
        compose.onNodeWithText("Total listings").assertExists()
        compose.onNodeWithText("Orders").performClick()
        compose.onNodeWithText(SUPPLIER_ORDERS_BLOCKER).assertExists()
        compose.onNodeWithText("Delivery tracking").performScrollTo().assertExists()
        compose.onNodeWithText("Payment status unavailable").performScrollTo().assertExists()
        compose.onAllNodesWithText("Pay now").assertCountEquals(0)
    }
    @Test fun creatingSupplyRequiresValidFormAndExplicitConfirmation() {
        var submissions = 0
        screen(SupplierUiState(categories = listOf(CanonicalCategoryDto(2, "Synthetic seeds", "supplier", true)))) { submissions++ }
        compose.onNodeWithText("Add supply").performScrollTo().performClick()
        compose.onNodeWithText("Supply name").performTextInput("Synthetic supply")
        compose.onNodeWithText("Description").performTextInput("Synthetic description")
        compose.onNodeWithText("Choose Supplier category").performScrollTo().performClick()
        compose.onNodeWithText("Synthetic seeds").performClick()
        compose.onNodeWithText("Price per unit (KES)").performScrollTo().performTextInput("100.25")
        compose.onNodeWithText("Available stock (whole units)").performScrollTo().performTextInput("10")
        compose.onNodeWithText("Review supply").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, submissions) }
        compose.onNodeWithText("Submit supply").performClick()
        compose.runOnIdle { assertEquals(1, submissions) }
    }
    @Test fun profileIsReadOnlyAndShowsProvisionedBusiness() {
        val account = CanonicalAccount("42", "Synthetic Supplier", "supplier@example.test", "+254700000000", null, "Synthetic region", null,
            "SUPPLIER", "ACTIVE", "NOT_REQUIRED", null)
        screen(SupplierUiState(profile = SupplierProfile(account, SupplierProfileDto(1, 42, "Synthetic business", location = "Synthetic area"))))
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Synthetic business").performScrollTo().assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
    @Test fun loadingAndErrorsDoNotDisplayInventedZeroStatistics() {
        screen(SupplierUiState(error = "Synthetic service unavailable"))
        compose.onNodeWithText("Synthetic service unavailable").assertExists()
        compose.onNodeWithText("Total listings").assertDoesNotExist()
        compose.onNodeWithText("Try again").assertExists()
    }
}
