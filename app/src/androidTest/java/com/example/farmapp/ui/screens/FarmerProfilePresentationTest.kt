package com.example.farmapp.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.farmapp.data.model.*
import com.example.farmapp.viewmodel.FarmerProfileState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FarmerProfilePresentationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun phoneEditUsesSaveActionAndEmailHasNoSaveAction() {
        var saved: String? = null
        val auth = AuthResponse("profile-test", "mary@example.test", "Mary Wanjiku", "FARMER", "", "", "")
        val profile = FarmerProfileResponse(auth.userId, auth.email, auth.fullName, "+254712345678", null, "Nakuru", "FARMER", null)
        compose.setContent {
            var dark by remember { mutableStateOf(false) }
            var state by remember { mutableStateOf(FarmerProfileState(profile)) }
            com.example.farmapp.ui.theme.FarmAppTheme(darkTheme = dark) {
                FarmerProfileContent(auth, state, {
                    saved = it
                    state = FarmerProfileState(profile.copy(phoneNumber = it), message = "Phone number saved to your account.")
                }, {}, null, Modifier.width(320.dp), dark, { dark = !dark }, {}, false)
            }
        }
        compose.onNodeWithText("Save email", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Account details are read-only here.").assertDoesNotExist()
        compose.onNodeWithText("ACCOUNT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save phone").assertDoesNotExist()
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNodeWithText("Save phone").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Phone number").performScrollTo().performTextReplacement("+254722222222")
        compose.onNodeWithText("Save phone").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("+254722222222", saved) }
        compose.onNodeWithText("Save phone").assertDoesNotExist()
        compose.onNodeWithText("Phone number saved to your account.").assertIsDisplayed()
        compose.onNodeWithText("Dark Mode").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("DAY").assertDoesNotExist()
        compose.onNodeWithText("NIGHT").assertDoesNotExist()
    }

    @Test fun cancelDoesNotSaveAndAboutAndLogoutRemainReachableInDarkMode() {
        var saves = 0
        var refreshes = 0
        var logouts = 0
        val auth = AuthResponse("profile-test", "mary@example.test", "Mary Wanjiku", "FARMER", "", "", "")
        val profile = FarmerProfileResponse(auth.userId, auth.email, auth.fullName, "+254712345678", "mary_wanjiku", "Nakuru", "FARMER", "PENDING")
        compose.setContent {
            com.example.farmapp.ui.theme.FarmAppTheme(darkTheme = true) {
                FarmerProfileContent(auth, FarmerProfileState(profile), { saves++ }, { refreshes++ }, null,
                    Modifier.width(320.dp), true, {}, { logouts++ }, false)
            }
        }
        compose.onNodeWithText("@mary_wanjiku").assertIsDisplayed()
        compose.onNodeWithText("Verification pending").assertIsDisplayed()
        compose.onNodeWithText("Verified").assertDoesNotExist()
        compose.onNodeWithContentDescription("Refresh profile").assertDoesNotExist()
        compose.onNodeWithTag("farmer-profile-scroll").performTouchInput { swipeDown(durationMillis = 600) }
        compose.runOnIdle { assertEquals(1, refreshes) }
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNodeWithText("Phone number").performScrollTo().performTextReplacement("+254722222222")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithText("Save phone").assertDoesNotExist()
        compose.onNodeWithText("About").performScrollTo().performClick()
        compose.onNodeWithText("About Kilivana").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Logout").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, logouts) }
    }
}
