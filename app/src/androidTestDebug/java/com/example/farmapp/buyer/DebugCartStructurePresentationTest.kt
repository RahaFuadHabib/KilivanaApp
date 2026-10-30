package com.example.farmapp.buyer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Only synthetic callbacks. This never constructs the live diagnostic service. */
class DebugCartStructurePresentationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun neverRunsOnCompositionAndRequiresExplicitClick() {
        var calls = 0
        compose.setContent { MaterialTheme {
            var report by remember { mutableStateOf<String?>(null) }
            DebugBuyerCartDiagnosticContent(false, report, { calls++; report = "HTTP status: 200" }, {})
        } }
        compose.waitForIdle(); assertEquals(0, calls)
        compose.onNodeWithText("Run read-only GET").performClick()
        assertEquals(1, calls)
        compose.onNodeWithText("Run read-only GET").assertIsNotEnabled()
        compose.onNodeWithText("HTTP status: 200").assertIsDisplayed()
    }
    @Test fun busyStateAndCloseNeverSubmit() {
        var closed = false
        compose.setContent { MaterialTheme { DebugBuyerCartDiagnosticContent(true, null, { error("No automatic run") }, { closed = true }) } }
        compose.onNodeWithText("Run read-only GET").assertIsNotEnabled()
        compose.onNodeWithText("Close").performClick(); assertTrue(closed)
    }
}
