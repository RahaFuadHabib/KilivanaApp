package com.example.farmapp.supplier

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.ui.theme.FarmAppTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow

/** Synthetic visual fixtures only: no login, credentials, ApiClient or remote mutations. */
class SupplierVisualReviewTest {
    @get:Rule val compose = createComposeRule()
    private val supply = SupplyListing(17, "Certified maize seeds", "Quality planting seeds. Store in a cool, dry place.",
        "bag", BigDecimal("2450.00"), 32, 4, 1, 5, CanonicalProductStatus.PENDING_APPROVAL, emptyList())
    private val categories = listOf(CanonicalCategoryDto(5, "Seeds & seedlings", "SUPPLIER", true))
    private val account = CanonicalAccount("42", "Amina", "supplier@example.test", "+254700000000", null,
        "Nakuru", null, "SUPPLIER", "ACTIVE", "NOT_REQUIRED", null)
    private val base = SupplierUiState(listings = listOf(supply), categories = categories, catalogueLoaded = true, ordersLoaded = true, ordersLast = true,
        profile = SupplierProfile(account, SupplierProfileDto(1, 42, "Greenfield Supplies", location = "Nakuru, Market Road")))

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "supplier-redesign")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun captureSupplierScreensAndDarkSmallLargeTypeStates() {
        val state = MutableStateFlow(base)
        var dark by mutableStateOf(false)
        var large by mutableStateOf(false)
        compose.setContent {
            val observed by state.collectAsState()
            FarmAppTheme(darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.5f else 1f)) {
                    Box(Modifier.widthIn(max = if (large) 320.dp else 600.dp).fillMaxHeight()) {
                        SupplierContent(observed, "Amina", {}, dark, { dark = !dark }, false, {},
                            { state.value = state.value.copy(selectedId = it, detail = supply) },
                            { state.value = state.value.copy(selectedId = null, detail = null) }, {}, { _, _ -> }, { _, _ -> }, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Grow Your\nAgribusiness").assertIsDisplayed()
        capture("01-dashboard")
        compose.onNodeWithText("Supplies").performClick()
        capture("02-supplies")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(supply.name))
        compose.onNodeWithText(supply.name).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5_000) { state.value.selectedId == supply.id }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Back to supplies").fetchSemanticsNodes().isNotEmpty() }
        capture("03-details")
        compose.onNodeWithText("Edit pending listing").performScrollTo().performClick()
        capture("04-edit")
        compose.onNodeWithContentDescription("Back to supplies").performClick()
        compose.onNodeWithContentDescription("Back to supplies").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Supplies").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add supply").performScrollTo().performClick()
        capture("05-create")
        compose.onNodeWithText("Review supply").performScrollTo()
        capture("06-create-stock")
        compose.onNodeWithContentDescription("Back to supplies").performClick()
        compose.onNodeWithText("Orders").performClick()
        capture("07-orders")
        compose.onNodeWithText("No orders yet").assertIsDisplayed()
        capture("08-orders-empty")
        compose.onNodeWithText("Profile").performClick()
        capture("09-profile")
        compose.onNodeWithContentDescription("Toggle color theme").performClick()
        capture("10-profile-dark")
        compose.onNodeWithText("Dashboard").performClick()
        capture("11-dashboard-dark")
        compose.runOnIdle { large = true }
        capture("12-small-large-type")
        compose.onNodeWithText("Add supply").performScrollTo().performClick()
        compose.onNodeWithText("Supply name").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Refresh Supplier data").assertDoesNotExist()
        compose.onNodeWithContentDescription("Toggle color theme").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sign out").assertIsDisplayed()
        capture("13-small-large-type-create")
        compose.onNodeWithText("Review supply").performScrollTo().assertIsDisplayed()
    }
}
