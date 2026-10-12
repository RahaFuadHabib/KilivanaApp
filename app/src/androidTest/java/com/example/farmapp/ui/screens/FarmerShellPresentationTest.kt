package com.example.farmapp.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.farmapp.data.model.AuthResponse
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FarmerShellPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val auth = AuthResponse("farmer-ui-test", "farmer@example.test", "Test Farmer", "FARMER", "", "", "")

    @Test fun headerThemeIconHasAccessibleActionsWithoutDayNightLabels() {
        compose.setContent {
            var dark by remember { mutableStateOf(false) }
            com.example.farmapp.ui.theme.FarmAppTheme(darkTheme = dark) {
                com.example.farmapp.ui.components.FarmerThemeToggle(dark, { dark = !dark }, iconOnly = true)
            }
        }
        compose.onNodeWithText("DAY").assertDoesNotExist()
        compose.onNodeWithText("NIGHT").assertDoesNotExist()
        compose.onNodeWithContentDescription("Switch to dark mode").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Switch to light mode").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Switch to dark mode").assertIsDisplayed()
    }

    private fun showShell(fontScale: Float = 1f, logout: () -> Unit = {}) {
        compose.setContent {
            val density = LocalDensity.current.density
            var dark by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MaterialTheme {
                    FarmerShell(auth, false, null, logout, dark, { dark = !dark },
                        Modifier.width(320.dp).fillMaxHeight())
                }
            }
        }
    }

    @Test fun fiveDestinationsFitNarrowPhoneAndProfileReusesActions() {
        var loggedOut = false
        showShell(logout = { loggedOut = true })
        val destinations = listOf("Home", "Orders", "Add crop", "Supplies", "Profile")
        val bounds = destinations.map { label ->
            compose.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        bounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
        val metrics = listOf("Crop listings", "Incoming orders", "Supply entries")
            .map { compose.onNodeWithTag("metric_$it").fetchSemanticsNode().boundsInRoot }
        metrics.drop(1).forEach {
            assertEquals(metrics.first().width, it.width, 1f)
            assertEquals(metrics.first().height, it.height, 1f)
            assertEquals(metrics.first().top, it.top, 1f)
        }
        compose.onNodeWithContentDescription("Supplies").performClick()
        compose.onNodeWithText("Supplier product browsing is coming soon.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Tracking & map availability").performScrollTo().performClick()
        compose.onNodeWithText("Live order and supply tracking is unavailable", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Back", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Orders").performClick()
        compose.onNodeWithText("Tracking & map availability").performScrollTo().performClick()
        compose.onNodeWithText("No shipment positions are shown.", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("About", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("About Kilivana").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Dark Mode").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Logout").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(loggedOut) }
    }

    @Test fun largeTextKeepsLastDestinationReachableAndToolbarSearchWorks() {
        showShell(fontScale = 1.5f)
        compose.onNodeWithContentDescription("Profile").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Home").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Search your farm").performClick()
        compose.onNodeWithText("Crops, orders, supplies").performTextInput("Avocado")
        compose.onNodeWithText("Search", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Search results for “Avocado”").assertIsDisplayed()
    }
}
