package com.example.farmapp.supplier

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.runtime.mutableStateOf
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
    @Test fun createDraftSurvivesSavedStateRestorationAndWarnsBeforeLeaving() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FarmAppTheme {
            SupplierContent(SupplierUiState(), "Synthetic Supplier", {}, false, {}, false,
                {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
        } }
        compose.onNodeWithText("Add supply").performScrollTo().performClick()
        compose.onNodeWithText("Supply name").performTextInput("Unfinished supply")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Unfinished supply").assertExists()
        compose.onNodeWithContentDescription("Back to supplies").performClick()
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Unfinished supply").assertExists()
        compose.onNodeWithContentDescription("Back to supplies").performClick()
        compose.onNodeWithText("Discard changes").performClick()
        compose.onNodeWithText("New supply").assertDoesNotExist()
    }
    @Test fun missingCategoriesExplainWhySubmissionIsDisabled() {
        screen(SupplierUiState())
        compose.onNodeWithText("Add supply").performScrollTo().performClick()
        compose.onNodeWithText("No active Supplier categories are available. Ask your administrator to configure them, then refresh.").performScrollTo().assertExists()
        compose.onNodeWithText("Review supply").performScrollTo().assertIsNotEnabled()
    }
    @Test fun invalidFieldsHaveSpecificMessages() {
        screen(SupplierUiState(categories = listOf(CanonicalCategoryDto(2, "Synthetic seeds", "supplier", true))))
        compose.onNodeWithText("Add supply").performScrollTo().performClick()
        compose.onNodeWithText("Review supply").performScrollTo().performClick()
        compose.onNodeWithText("Enter a supply name with 2 to 200 characters.").performScrollTo().assertExists()
        compose.onNodeWithText("Enter a description with 1 to 4000 characters.").performScrollTo().assertExists()
        compose.onNodeWithText("Select an active Supplier category.").performScrollTo().assertExists()
        compose.onNodeWithText("Confirm supply submission").assertDoesNotExist()
    }
    @Test fun refreshingAndFailedRefreshKeepVisibleListings() {
        screen(SupplierUiState(listings = listOf(listing), catalogueLoaded = true, refreshing = true, error = "Synthetic refresh failed"))
        compose.onNodeWithText("Refreshing Supplier data. Showing previously loaded data.").assertExists()
        compose.onNodeWithText("Synthetic refresh failed").assertExists()
        compose.onNodeWithText("Total listings").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Synthetic seed listing"))
        compose.onNodeWithText("Synthetic seed listing").assertIsDisplayed()
        compose.onNodeWithContentDescription("Refresh Supplier data").assertIsNotEnabled()
    }
    @Test fun editDraftSurvivesDetailRefreshAndRestoration() {
        val restoration = StateRestorationTester(compose)
        val state = mutableStateOf(SupplierUiState(listings = listOf(listing), selectedId = listing.id, detail = listing,
            catalogueLoaded = true, categories = listOf(CanonicalCategoryDto(2, "Synthetic seeds", "supplier", true))))
        restoration.setContent { FarmAppTheme {
            SupplierContent(state.value, "Synthetic Supplier", {}, false, {}, false,
                {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
        } }
        compose.onNodeWithText("Edit pending listing").performScrollTo().performClick()
        compose.onNodeWithText("Supply name").performTextReplacement("My unfinished edit")
        compose.runOnIdle { state.value = state.value.copy(detail = listing.copy(name = "Changed on server"), refreshing = true) }
        compose.onNodeWithText("My unfinished edit").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("My unfinished edit").assertExists()
        compose.onNodeWithContentDescription("Back to supplies").performClick()
        compose.onNodeWithText("Discard unsaved changes?").assertExists()
    }
    @Test fun dirtyFormRequiresConfirmationBeforeLogout() {
        var logouts = 0
        compose.setContent { FarmAppTheme {
            SupplierContent(SupplierUiState(), "Synthetic Supplier", { logouts++ }, false, {}, false,
                {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
        } }
        compose.onNodeWithText("Add supply").performScrollTo().performClick()
        compose.onNodeWithText("Supply name").performTextInput("Unsaved")
        compose.onNodeWithContentDescription("Sign out").performClick()
        compose.runOnIdle { assertEquals(0, logouts) }
        compose.onNodeWithText("Discard changes").performClick()
        compose.runOnIdle { assertEquals(1, logouts) }
    }
    @Test fun confirmationDoubleTapSubmitsOnlyOnce() {
        var submissions = 0
        compose.setContent { FarmAppTheme {
            SupplyEditor(SupplyDraft("Synthetic supply", "Synthetic description", "bag", "100.00", "10", "1", 2), {},
                listOf(CanonicalCategoryDto(2, "Synthetic seeds", "supplier", true)), false, false, null) { submissions++ }
        } }
        compose.onNodeWithText("Review supply").performClick()
        compose.onNodeWithText("Submit supply").performTouchInput { doubleClick() }
        compose.runOnIdle { assertEquals(1, submissions) }
    }
}
