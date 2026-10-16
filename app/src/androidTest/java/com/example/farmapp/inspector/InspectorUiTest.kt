package com.example.farmapp.inspector

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.farmapp.data.model.canonical.CanonicalAccount
import com.example.farmapp.ui.theme.FarmAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic UI only. No ApiClient, real sessions, credentials or remote operations. */
@RunWith(AndroidJUnit4::class)
class InspectorUiTest {
    @get:Rule val compose = createComposeRule()
    private val job = Inspection(101, 42, "PRODUCT", 7, InspectionStatus.ASSIGNED, null, "Synthetic note", null, null, null)

    @Test fun dashboardOpensOwnInspectionAndBackReturnsToList() {
        compose.setContent {
            var state by remember { mutableStateOf(InspectorUiState(jobs = listOf(job))) }
            FarmAppTheme { InspectorContent(state, "Synthetic Inspector", {}, false, {}, false, {},
                { state = state.copy(selectedId = it, detail = job) },
                { state = state.copy(selectedId = null, detail = null) }) }
        }
        compose.onNodeWithText("Welcome, Synthetic Inspector").assertExists()
        compose.onNodeWithText("My inspections").performClick()
        compose.onNodeWithText("PRODUCT #7").performClick()
        compose.onNodeWithText("Synthetic note").assertExists()
        compose.onNodeWithContentDescription("Back to inspections").performClick()
        compose.onNodeWithText("Only inspections assigned to your account.").assertExists()
    }

    @Test fun profileShowsAdminAssignmentsWithoutEditingControls() {
        val account = CanonicalAccount("42", "Synthetic Inspector", "inspector@example.test", "+254700000000",
            null, null, null, "INSPECTOR", "ACTIVE", "NOT_REQUIRED", null)
        val profile = InspectorProfile(account, InspectorProfileDto(1, 42, specialization = "Synthetic specialization", assignedArea = "Synthetic area"))
        compose.setContent { FarmAppTheme {
            InspectorContent(InspectorUiState(profile = profile), "Synthetic Inspector", {}, false, {}, false, {}, {}, {})
        } }
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Synthetic specialization").performScrollTo().assertExists()
        compose.onNodeWithText("Synthetic area").performScrollTo().assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun errorRetryCanShowEmptyAssignmentsWithoutInventedMetrics() {
        compose.setContent {
            var state by remember { mutableStateOf(InspectorUiState(jobsError = "Synthetic connection failure")) }
            FarmAppTheme { InspectorContent(state, "Synthetic Inspector", {}, false, {}, false,
                { state = InspectorUiState() }, {}, {}) }
        }
        compose.onNodeWithText("Synthetic connection failure").assertExists()
        compose.onNodeWithText("Assigned to you").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("No inspections assigned").performScrollTo().assertExists()
    }

    @Test fun statusSubmissionRequiresConfirmationAndUploadStaysDisabled() {
        var submissions = 0
        compose.setContent { FarmAppTheme {
            InspectorContent(InspectorUiState(jobs = listOf(job), selectedId = job.id, detail = job),
                "Synthetic Inspector", {}, false, {}, false, {}, {}, {},
                onChangeStatus = { submissions++ })
        } }
        compose.onNodeWithText("Status: Assigned").performScrollTo().performClick()
        compose.onNodeWithText("In progress").performClick()
        compose.onNodeWithText("Review status change").performScrollTo().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(0, submissions) }
        compose.onNodeWithText("Confirm submission").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, submissions) }
        compose.onNodeWithText("Upload evidence unavailable").performScrollTo().assertIsNotEnabled()
    }
}
