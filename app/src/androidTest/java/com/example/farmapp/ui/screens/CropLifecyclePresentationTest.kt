package com.example.farmapp.ui.screens

import com.example.farmapp.ui.theme.FarmAppTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.farmapp.data.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/** Synthetic callbacks only; does not mutate any backend product. */
class CropLifecyclePresentationTest {
    @get:Rule val compose = createComposeRule()
    private fun crop(status: String, reserved: BigDecimal? = BigDecimal.ZERO) = FarmerProductResponse(
        "7", "Synthetic crop", "Description", "kg", BigDecimal.TEN, BigDecimal.TEN,
        status = status, sellerId = "42", reservedQuantity = reserved)

    @Test fun activeOffersUnlistAndArchiveWithConfirmationAndCancel() {
        var calls = 0
        compose.setContent { FarmAppTheme(darkTheme = false) { CropLifecycleActions(crop("ACTIVE"), "42", "FARMER", true) { calls++ } } }
        compose.onNodeWithText("Unlist").performClick()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Archive").assertExists()
        compose.onNodeWithText("Archive").performClick()
        compose.onNodeWithText("Manage Synthetic crop").assertExists()
        compose.onNodeWithText("Archive product").performClick()
        compose.onNodeWithText("Archive Synthetic crop?").assertExists()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Submit for Admin approval").assertDoesNotExist()
    }
    @Test fun confirmDoubleTapEmitsOneAction() {
        var calls = 0
        compose.setContent { FarmAppTheme(darkTheme = false) { CropLifecycleActions(crop("ACTIVE"), "42", "FARMER", true) {
            assertEquals(FarmerProductAction.ARCHIVE, it); calls++
        } } }
        compose.onNodeWithText("Archive").performClick()
        compose.onNodeWithText("Archive product").performClick()
        compose.onNodeWithText("Archive product").performTouchInput { doubleClick() }
        compose.runOnIdle { assertEquals(1, calls) }
    }
    @Test fun archivedWithReservationsOffersReviewButNoDeletion() {
        compose.setContent { FarmAppTheme(darkTheme = false) { CropLifecycleActions(crop("ARCHIVED", BigDecimal.ONE), "42", "FARMER", true) {} } }
        compose.onNodeWithText("Submit for Admin approval").assertExists()
        compose.onNodeWithText("Archive").performClick()
        compose.onNodeWithText("Permanently delete").assertIsNotEnabled()
        compose.onNodeWithText("Deletion requires confirmed zero reserved stock.", substring = true).assertExists()
    }
    @Test fun wrongOwnerHasNoLifecycleActions() {
        compose.setContent { FarmAppTheme(darkTheme = false) { CropLifecycleActions(crop("ACTIVE"), "99", "FARMER", true) {} } }
        compose.onNodeWithText("Unlist").assertDoesNotExist()
        compose.onNodeWithText("Archive").assertIsNotEnabled()
    }
    @Test fun changedStateInvalidatesOpenDeletionConfirmation() {
        val product = mutableStateOf(crop("ARCHIVED"))
        compose.setContent { FarmAppTheme(darkTheme = false) { CropLifecycleActions(product.value, "42", "FARMER", true) { fail() } } }
        compose.onNodeWithText("Archive").performClick()
        compose.onNodeWithText("Permanently delete").performClick()
        compose.runOnIdle { product.value = crop("ARCHIVED", BigDecimal.ONE) }
        compose.onNodeWithText("Permanently delete").assertIsNotEnabled()
    }
    @Test fun busyDisablesAllLifecycleActions() {
        compose.setContent { FarmAppTheme(darkTheme = false) { CropLifecycleActions(crop("ACTIVE"), "42", "FARMER", false) { fail() } } }
        compose.onNodeWithText("Unlist").assertIsNotEnabled()
        compose.onNodeWithText("Archive").assertIsNotEnabled()
    }
    @Test fun primaryTilesStayInOneEvenRowOnNarrowScreensAndKeepCallbacks() {
        var edits = 0; var orders = 0
        compose.setContent { FarmAppTheme(darkTheme = false) { Box(Modifier.width(280.dp)) {
            CropLifecycleActions(crop("ACTIVE"), "42", "FARMER", true,
                onEdit = { edits++ }, onOrders = { orders++ }) {}
        } } }
        val bounds = listOf("Edit", "Orders", "Archive").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
        assertEquals(bounds[0].top, bounds[1].top, 1f)
        assertEquals(bounds[1].top, bounds[2].top, 1f)
        assertEquals(bounds[0].width, bounds[1].width, 1f)
        assertEquals(bounds[1].width, bounds[2].width, 1f)
        assertTrue(bounds[0].right <= bounds[1].left && bounds[1].right <= bounds[2].left)
        compose.onAllNodesWithText("Archive").assertCountEquals(1)
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Orders").performClick()
        compose.runOnIdle { assertEquals(1, edits); assertEquals(1, orders) }
    }
    @Test fun activeProductCannotDeleteBeforeArchiving() {
        compose.setContent { FarmAppTheme(darkTheme = false) { CropLifecycleActions(crop("ACTIVE"), "42", "FARMER", true) { fail() } } }
        compose.onNodeWithText("Archive").performClick()
        compose.onNodeWithText("Permanently delete").assertIsNotEnabled()
        compose.onNodeWithText("Archive this product before deleting it.").assertExists()
    }
    @Test fun detailsShowReadableStatusAndSingleActionRowAndManagementDialog() {
        compose.setContent { FarmAppTheme(darkTheme = false) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                CropDetailScreen(crop("PENDING_APPROVAL"), Modifier.fillMaxSize(), false, null, 3, {}, {}, {}, {},
                    canonicalLifecycle = true, authenticatedUserId = "42", authenticatedRole = "FARMER")
            }
        } }
        compose.onNodeWithText("Status: Pending Approval").assertExists()
        compose.onAllNodesWithText("Archive").assertCountEquals(1)
        compose.onNodeWithText("Refresh crop").assertExists()
        screenshot("crop-details-redesign.png")
        compose.onNodeWithText("Archive").performClick()
        compose.onNodeWithText("Manage Synthetic crop").assertExists()
        screenshot("crop-manage-redesign.png", dialog = true)
    }

    private fun screenshot(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Capture only the synthetic Compose window, excluding notifications and other system UI.
        val bitmap = (if (dialog) compose.onNode(isDialog()) else compose.onRoot()).captureToImage().asAndroidBitmap()
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
