package com.example.farmapp.supplier

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
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
    @Test fun dashboardUsesListingsAndOrdersRemainReadOnly() {
        screen(SupplierUiState(listings = listOf(listing), ordersLoaded = true, ordersLast = true))
        compose.onNodeWithText("Total listings").assertExists()
        compose.onNodeWithText("Orders").performClick()
        compose.onNodeWithText("No orders yet").assertExists()
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
    @Test fun initialCatalogueWaitsInsteadOfClaimingNoSupplies() {
        screen(SupplierUiState())
        compose.onNodeWithText("Loading your supplies").assertExists()
        compose.onNodeWithText("Total listings").assertDoesNotExist()
        compose.onNodeWithText("No supplies listed").assertDoesNotExist()
    }
    @Test fun confirmedEmptyInventoryHasHelpfulEmptyState() {
        screen(SupplierUiState(catalogueLoaded = true))
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("No supplies listed"))
        compose.onNodeWithText("No supplies listed").assertIsDisplayed()
        compose.onNodeWithText("Currently unavailable").assertDoesNotExist()
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
        compose.onNodeWithText("Synthetic refresh failed").assertExists()
        compose.onNodeWithText("Showing previously loaded listings. Stock and prices may have changed.").assertExists()
        compose.onNodeWithText("Total listings").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Synthetic seed listing"))
        compose.onNodeWithText("Synthetic seed listing").assertIsDisplayed()
        compose.onNodeWithContentDescription("Refresh Supplier data").assertDoesNotExist()
    }
    @Test fun pullGestureRefreshesWithoutToolbarReload() {
        var refreshes = 0
        compose.setContent { FarmAppTheme {
            SupplierContent(SupplierUiState(catalogueLoaded = true), "Synthetic Supplier", {}, false, {}, false,
                { refreshes++ }, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
        } }
        compose.onNodeWithContentDescription("Refresh Supplier data").assertDoesNotExist()
        compose.onNodeWithTag("supplier-list").performTouchInput { swipeDown(durationMillis = 600) }
        compose.runOnIdle { assertEquals(1, refreshes) }
    }
    @Test fun creationAndEditingExposeProductPhotoSelection() {
        val state = mutableStateOf(SupplierUiState(catalogueLoaded = true, categories = listOf(CanonicalCategoryDto(2, "Synthetic seeds", "supplier", true))))
        compose.setContent { FarmAppTheme {
            SupplierContent(state.value, "Synthetic Supplier", {}, false, {}, false, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
        } }
        compose.onNodeWithText("Add supply").performScrollTo().performClick()
        compose.onNodeWithTag("supplier-list").performScrollToNode(hasContentDescription("Add product photo"))
        compose.onNodeWithContentDescription("Add product photo").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Choose product photo").assertDoesNotExist()
    }
    @Test fun photoUploadCompletionKeepsUnsavedEditDraft() {
        val state = mutableStateOf(SupplierUiState(listings = listOf(listing), selectedId = listing.id, detail = listing,
            catalogueLoaded = true, categories = listOf(CanonicalCategoryDto(2, "Synthetic seeds", "supplier", true))))
        compose.setContent { FarmAppTheme {
            SupplierContent(state.value, "Synthetic Supplier", {}, false, {}, false, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
        } }
        compose.onNodeWithText("Edit pending listing").performScrollTo().performClick()
        compose.onNodeWithText("Supply name").performScrollTo().performTextReplacement("Unsaved supply name")
        compose.runOnIdle { state.value = state.value.copy(notice = "Photo saved", completedOperation = SupplyOperation.UPLOAD) }
        compose.onNodeWithText("Supply name").performScrollTo().assertTextContains("Unsaved supply name")
        compose.onNodeWithTag("supplier-list").performScrollToNode(hasContentDescription("Add product photo"))
        compose.onNodeWithContentDescription("Add product photo").assertIsDisplayed()
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
            Column(Modifier.verticalScroll(rememberScrollState())) {
            SupplyEditor(SupplyDraft("Synthetic supply", "Synthetic description", "bag", "100.00", "10", "1", 2), {},
                listOf(CanonicalCategoryDto(2, "Synthetic seeds", "supplier", true)), false, false, null) { submissions++ }
            }
        } }
        compose.onNodeWithText("Review supply").performScrollTo().performClick()
        compose.onNodeWithText("Submit supply").performTouchInput { doubleClick() }
        compose.runOnIdle { assertEquals(1, submissions) }
    }

    @Test fun localSearchAndCategoryStatusFiltersOnlyShowMatchingOwnedRecords() {
        screen(SupplierUiState(listings = listOf(listing, listing.copy(id = 8, name = "Irrigation kit", categoryId = 3,
            status = CanonicalProductStatus.ACTIVE)), categories = listOf(
            CanonicalCategoryDto(2, "Seeds", "SUPPLIER", true), CanonicalCategoryDto(3, "Irrigation", "SUPPLIER", true))))
        compose.onNodeWithText("Supplies").performClick()
        compose.onNodeWithText("Search supplies").performTextInput("not found")
        compose.onNodeWithText("No matching supplies").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Clear filters").performClick()
        compose.onNodeWithText("Seeds").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(listing.name))
        compose.onNodeWithText(listing.name).assertIsDisplayed()
        compose.onNodeWithText("Irrigation kit").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("All categories"))
        compose.onNodeWithText("All categories").performClick()
        compose.onNodeWithText("active", substring = false).performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Irrigation kit"))
        compose.onNodeWithText("Irrigation kit").assertIsDisplayed()
        compose.onNodeWithText(listing.name).assertDoesNotExist()
    }

    @Test fun publishedSupplyKeepsEditingDisabledAndDisplaysActualQuantities() {
        val active = listing.copy(status = CanonicalProductStatus.ACTIVE)
        screen(SupplierUiState(listings = listOf(active), selectedId = active.id, detail = active))
        compose.onNodeWithText("Edit pending listing").performScrollTo().assertIsNotEnabled()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Add product photo"))
        compose.onNodeWithContentDescription("Add product photo").assertIsNotEnabled()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Reserved"))
        compose.onNodeWithText("Reserved").assertExists()
        compose.onNodeWithText("2 bag").assertExists()
        compose.onAllNodesWithText("Delete").assertCountEquals(0)
        compose.onAllNodesWithText("Archive").assertCountEquals(0)
    }

    @Test fun selectingSupplyRecomposesIntoDetailWithoutResettingSessionState() {
        val state = MutableStateFlow(SupplierUiState(listings = listOf(listing), catalogueLoaded = true))
        compose.setContent { FarmAppTheme {
            SupplierContent(state.collectAsState().value, "Synthetic Supplier", {}, false, {}, false, {},
                { state.value = state.value.copy(selectedId = it, detail = listing) },
                { state.value = state.value.copy(selectedId = null, detail = null) }, {}, { _, _ -> }, { _, _ -> }, {})
        } }
        compose.onNodeWithText("Supplies").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(listing.name))
        compose.onNodeWithText(listing.name).performClick()
        compose.runOnIdle { assertEquals(listing.id, state.value.selectedId) }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Back to supplies").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back to supplies").assertExists()
        compose.onNodeWithText("Edit pending listing").performScrollTo().assertIsEnabled()
    }

    @Test fun imageGallerySupportsSelectionAndSafeUnavailableImageFallback() {
        val images = listOf(
            CanonicalProductImageDto(71, "https://untrusted.invalid/photo-one.jpg", "71", sortOrder = 0, isPrimary = true),
            CanonicalProductImageDto(72, "https://untrusted.invalid/photo-two.jpg", "72", sortOrder = 1, isPrimary = false))
        compose.setContent { FarmAppTheme { SupplierTheme(false) { SupplierGallery(listing.copy(images = images)) } } }
        compose.onNodeWithText("Photo unavailable").assertExists()
        compose.onNodeWithContentDescription("View ${listing.name} photo 1").assertIsSelected()
        compose.onNodeWithContentDescription("View ${listing.name} photo 2").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("View ${listing.name} photo 1").assertIsNotSelected()
    }
}
