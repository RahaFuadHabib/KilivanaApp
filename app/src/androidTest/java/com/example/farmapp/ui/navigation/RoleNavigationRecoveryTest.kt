package com.example.farmapp.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.farmapp.data.model.AuthResponse
import com.example.farmapp.ui.screens.auth.AuthWelcomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RoleNavigationRecoveryTest {
    @get:Rule val compose = createComposeRule()
    @Test fun originalEntryOffersNormalAuthenticationWithoutOfflineDemo() {
        compose.setContent { MaterialTheme { AuthWelcomeScreen(false, false, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("Sign Up").assertExists()
        compose.onNodeWithText("Sign In").assertExists()
        compose.onNodeWithText("Continue with Demo Data").assertDoesNotExist()
    }
    @Test fun allFourAuthenticatedRolesRetainTheirDestinations() {
        val auth = AuthResponse("role-test", "role@example.test", "Role Test", "FARMER", "", "", "", verificationStatus = "VERIFIED")
        listOf("FARMER" to AuthDestination.FARMER, "BUYER" to AuthDestination.BUYER,
            "SUPPLIER" to AuthDestination.SUPPLIER, "INSPECTOR" to AuthDestination.INSPECTOR).forEach { (role, expected) ->
            assertEquals(expected, authDestination(auth.copy(role = role)))
        }
        assertEquals(AuthDestination.AWAITING_VERIFICATION, authDestination(auth.copy(role = "INSPECTOR", verificationStatus = "PENDING")))
    }
}
