package com.example.farmapp.buyer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.farmapp.ui.screens.auth.SignUpScreen
import com.example.farmapp.ui.theme.FarmAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Pure signup form only; no authenticated session, API or real account is touched. */
class BuyerSignupPresentationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun sameFrameDoubleSubmitAndRecompositionInvokeOneIntentionalCallback() {
        var calls = 0
        val error = mutableStateOf<String?>(null)
        compose.setContent { FarmAppTheme { SignUpScreen({}, {}, false, {}, { calls++ }, {}, false, error.value, false) } }
        compose.onNodeWithText("Account type").performScrollTo().performClick()
        compose.onNodeWithText("Buyer").performClick()
        fun enter(label: String, value: String) { compose.onNodeWithText(label).performScrollTo().performTextInput(value) }
        enter("Full name", "Synthetic Buyer"); enter("Email", "synthetic@example.test")
        enter("Phone number", "0712345678"); enter("Username", "synthetic_buyer"); enter("Region", "Nairobi")
        enter("Password", "synthetic-password"); enter("Confirm password", "synthetic-password")
        compose.onNodeWithText("Create Account").performScrollTo()
        val click = compose.onNodeWithText("Create Account").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle { click(); click() }
        compose.onNodeWithText("Create Account").assertIsNotEnabled()
        assertEquals(1,calls)
        compose.runOnIdle { error.value = "Registration is temporarily rate limited (HTTP 429). Please wait and try again later." }
        compose.waitForIdle()
        compose.onNodeWithText("rate limited", substring = true).performScrollTo().assertIsDisplayed()
        assertEquals(1,calls)
    }
    @Test fun pendingSignupDisablesSubmitAndFieldsWithoutAnotherRequest() {
        compose.setContent { FarmAppTheme { SignUpScreen({}, {}, false, {}, { error("No submission") }, {}, true, null, false) } }
        compose.onNodeWithText("Account type").performScrollTo().assertIsNotEnabled()
        // Loading button is intentionally disabled even when no role is yet chosen.
        compose.onNode(hasClickAction() and hasAnyDescendant(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)), useUnmergedTree = true).performScrollTo().assertIsNotEnabled()
    }
}
