package com.example.farmapp.supplier

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.farmapp.data.model.SignupRequest
import com.example.farmapp.ui.screens.auth.SignUpScreen
import com.example.farmapp.ui.theme.FarmAppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Pure synthetic callbacks: no ApiClient, real account registration, product upload or payment. */
class SupplierSignupUiTest {
    @get:Rule val compose = createComposeRule()
    private fun screen(signup: (SignupRequest) -> Unit = {}, recover: (SignupRequest) -> Unit = {}, recovery: Boolean = false) {
        compose.setContent { FarmAppTheme {
            SignUpScreen({}, {}, false, {}, signup, {}, false, null, false,
                supplierRecoveryRequired = recovery, onRecoverSupplierSignup = recover)
        } }
    }
    private fun supplier() {
        compose.onNodeWithText("Account type").performClick()
        compose.onNodeWithText("Supplier").performClick()
    }
    private fun business(location: Boolean = true, county: Boolean = true, town: Boolean = true) {
        compose.onNodeWithText("Email").performScrollTo().performTextInput("supplier@example.test")
        compose.onNodeWithText("Business name").performScrollTo().performTextInput("Synthetic shop")
        if (county) {
            compose.onNodeWithText("County/region").performScrollTo().performClick()
            compose.onNodeWithText("Kiambu").performScrollTo().performClick()
        }
        if (town) compose.onNodeWithText("Town or sub-county").performScrollTo().performTextInput("Ruiru")
        if (location) compose.onNodeWithText("Physical business location").performScrollTo().performTextInput("Shop 4, Synthetic Road")
    }
    private fun password(confirmation: String? = "synthetic-password") {
        compose.onNodeWithText("Password").performScrollTo().performTextInput("synthetic-password")
        if (confirmation != null) compose.onNodeWithText("Confirm password").performScrollTo().performTextInput(confirmation)
    }
    private fun personal() {
        compose.onNodeWithText("Full name").performScrollTo().performTextInput("Synthetic Supplier")
        compose.onNodeWithText("Phone number").performScrollTo().performTextInput("+254700000000")
    }
    @Test fun allSupplierFieldsAreOnOneScrollableForm() {
        screen(); supplier()
        for (label in listOf("Full name", "Email", "Phone number", "Business name", "County/region", "Town or sub-county", "Physical business location", "Password", "Confirm password", "Create Account")) {
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Next").assertDoesNotExist()
    }
    @Test fun missingCountyBlocksCallback() {
        var calls = 0
        screen(signup = { calls++ }); supplier(); personal(); business(county = false); password()
        compose.onNodeWithText("Create Account").performScrollTo().performClick()
        compose.onNodeWithText("Select a supported Kenyan county/region.").performScrollTo().assertExists()
        compose.runOnIdle { assertEquals(0, calls) }
    }
    @Test fun missingTownBlocksCallback() {
        var calls = 0
        screen(signup = { calls++ }); supplier(); personal(); business(town = false); password()
        compose.onNodeWithText("Create Account").performScrollTo().performClick()
        compose.onNodeWithText("Enter your town or sub-county, for example Ruiru.").performScrollTo().assertExists()
        compose.runOnIdle { assertEquals(0, calls) }
    }
    @Test fun passwordMismatchPreservesFieldsAndCanBeCorrected() {
        var calls = 0
        screen(signup = { calls++ }); supplier(); personal(); business(); password("different-password")
        compose.onNodeWithText("Create Account").performScrollTo().performClick()
        compose.onNodeWithText("Passwords do not match.").performScrollTo().assertExists()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText("Synthetic shop").performScrollTo().assertExists()
        compose.onNodeWithText("Confirm password").performScrollTo().performTextReplacement("synthetic-password")
        compose.onNodeWithText("Create Account").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }
    @Test fun missingConfirmationBlocksCallback() {
        var calls = 0
        screen(signup = { calls++ }); supplier(); personal(); business(); password(null)
        compose.onNodeWithText("Create Account").performScrollTo().performClick()
        compose.onNodeWithText("Confirm your password.").performScrollTo().assertExists()
        compose.runOnIdle { assertEquals(0, calls) }
    }
    @Test fun passwordVisibilityTogglesAreIndependent() {
        screen(); supplier(); password()
        compose.onNodeWithContentDescription("Show password").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Hide password").assertExists()
        compose.onNodeWithContentDescription("Show confirm password").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Hide confirm password").assertExists()
        compose.onNodeWithContentDescription("Hide password").assertExists()
    }
    @Test fun countyAloneIsNotAcceptedAsDetailedAddress() {
        var calls = 0
        screen(signup = { calls++ }); supplier(); personal(); business(); password()
        compose.onNodeWithText("Physical business location").performScrollTo().performTextReplacement("Kiambu")
        compose.onNodeWithText("Create Account").performScrollTo().performClick()
        compose.onNodeWithText("County or town alone is not enough.", substring = true).performScrollTo().assertExists()
        compose.runOnIdle { assertEquals(0, calls) }
    }
    @Test fun farmerUsesSinglePageAndKeepsOriginalRegistrationPayload() {
        var submitted: SignupRequest? = null
        screen(signup = { submitted = it })
        compose.onNodeWithText("Account type").performClick()
        compose.onNodeWithText("Farmer").performClick()
        compose.onNodeWithText("Full name").performScrollTo().performTextInput("Synthetic Farmer")
        compose.onNodeWithText("Email").performScrollTo().performTextInput("farmer@example.test")
        compose.onNodeWithText("Phone number").performScrollTo().performTextInput("+254700000000")
        compose.onNodeWithText("Username").performScrollTo().performTextInput("synthetic_farmer")
        compose.onNodeWithText("Region").performScrollTo().performTextInput("Nairobi")
        password()
        compose.onNodeWithText("Create Account").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("FARMER", submitted!!.role)
            assertEquals("Synthetic Farmer's farm", submitted!!.farmName)
            assertNull(submitted!!.businessLocation)
        }
    }
    @Test fun supplierFieldsRemainSeparateAndDoubleTapSubmitsOnce() {
        var submitted: SignupRequest? = null; var calls = 0
        screen(signup = { submitted = it; calls++ })
        supplier()
        compose.onNodeWithText("Full name").performScrollTo().performTextInput("Synthetic Supplier")
        compose.onNodeWithText("Phone number").performScrollTo().performTextInput("+254700000000")
        business(); password()
        compose.onNodeWithText("Create Account").performScrollTo().performTouchInput { doubleClick() }
        compose.runOnIdle {
            assertEquals(1, calls); assertEquals("SUPPLIER", submitted!!.role)
            assertEquals("Kiambu", submitted!!.region)
            assertEquals("Ruiru, Shop 4, Synthetic Road", submitted!!.businessLocation)
        }
        compose.onNodeWithText("Create Account").assertIsNotEnabled()
    }
    @Test fun inspectorSignupIsDisabled() {
        screen()
        compose.onNodeWithText("Account type").performClick()
        compose.onNodeWithText("Inspector").performClick()
        compose.onNodeWithText("Inspector accounts are provided by the administrator. Sign in with your assigned credentials.").performScrollTo().assertExists()
        compose.onNodeWithText("Create Account").performScrollTo().assertIsNotEnabled()
    }
    @Test fun missingPhysicalLocationIsRejectedBeforeSubmission() {
        var calls = 0
        screen(signup = { calls++ }); supplier()
        compose.onNodeWithText("Full name").performScrollTo().performTextInput("Synthetic Supplier")
        compose.onNodeWithText("Phone number").performScrollTo().performTextInput("+254700000000")
        business(location = false); password()
        compose.onNodeWithText("Create Account").performScrollTo().performClick()
        compose.onNodeWithText("Add a detailed address", substring = true).performScrollTo().assertExists()
        compose.runOnIdle { assertEquals(0, calls) }
    }
    @Test fun recoveryUsesExistingCredentialsAndNeverSubmitsRegistration() {
        var creates = 0; var recoveries = 0
        screen(signup = { creates++ }, recover = { recoveries++ }, recovery = true)
        supplier(); business(); password()
        compose.onNodeWithText("Sign in and finish setup").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, creates); assertEquals(1, recoveries) }
    }
    @Test fun existingSupplierCanExplicitlyChooseRecoveryAfterRestart() {
        var creates = 0; var recoveries = 0
        screen(signup = { creates++ }, recover = { recoveries++ })
        supplier(); business()
        compose.onNodeWithText("Already registered? Finish Supplier setup").performScrollTo().performClick()
        password()
        compose.onNodeWithText("Sign in and finish setup").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, creates); assertEquals(1, recoveries) }
    }
    @Test fun partialFailureSwitchesActionToRecovery() {
        val recovery = mutableStateOf(false); val error = mutableStateOf<String?>(null)
        var creates = 0; var recoveries = 0
        compose.setContent { FarmAppTheme {
            SignUpScreen({}, {}, false, {}, { creates++; recovery.value = true; error.value = "Account may exist. Finish setup." }, {}, false, error.value, false,
                supplierRecoveryRequired = recovery.value, onRecoverSupplierSignup = { recoveries++ })
        } }
        supplier()
        compose.onNodeWithText("Full name").performScrollTo().performTextInput("Synthetic Supplier")
        compose.onNodeWithText("Phone number").performScrollTo().performTextInput("+254700000000")
        business(); password()
        compose.onNodeWithText("Create Account").performScrollTo().performClick()
        compose.onNodeWithText("Sign in and finish setup").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, creates); assertEquals(1, recoveries) }
    }
}
