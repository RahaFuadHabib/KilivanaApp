package com.example.farmapp.supplier

import android.graphics.Bitmap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.*
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.farmapp.data.model.AuthResponse
import com.example.farmapp.data.model.SignupRequest
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import com.example.farmapp.profile.*
import com.example.farmapp.repository.AuthRepository
import com.example.farmapp.ui.screens.CropPhotoStorage
import com.example.farmapp.ui.screens.LoginScreen
import com.example.farmapp.ui.screens.auth.*
import com.example.farmapp.ui.theme.FarmAppTheme
import com.example.farmapp.viewmodel.AuthViewModel
import com.google.gson.JsonSyntaxException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy
import java.util.UUID

/** Only synthetic APIs and a private SessionState. Never signs into a live account. */
class AuthAndProfileRefinementUiTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    private val sessions = SessionState()
    private var saved: AuthResponse? = null
    private var verified = false
    private var malformed = false
    private var logins = 0
    private var requests = 0
    private var verifications = 0
    private var registrations = 0
    private var profiles = 0
    private var advertiseChallenge = true
    private lateinit var vm: AuthViewModel
    private val challenge = UUID.fromString("00000000-0000-4000-8000-000000000042")
    private fun account() = CanonicalUserDto(42, "Synthetic Farmer", "farmer@example.test", "+254700000000",
        role = "FARMER", status = "ACTIVE", verificationStatus = "NOT_REQUIRED", emailVerified = verified)
    private val otp = object : EmailVerificationApi {
        override suspend fun request(request: EmailOtpRequest): Response<CanonicalApiResponse<EmailOtpChallenge>> {
            requests++; return Response.success(CanonicalApiResponse(true, data = EmailOtpChallenge(challenge, 600, 60)))
        }
        override suspend fun verify(request: EmailOtpVerifyRequest): Response<CanonicalApiResponse<Unit>> {
            assertEquals(challenge, request.challengeId); assertEquals("123456", request.code)
            verifications++; verified = true; return Response.success(CanonicalApiResponse(true))
        }
    }
    private fun authScreen(submitLogin: Boolean = true) {
        val api = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CanonicalBackendApi::class.java)) { _, method, _ ->
            when (method.name) {
            "register" -> { registrations++; Response.success(CanonicalApiResponse(true, data = account().copy(emailVerificationChallengeId = challenge))) }
            "farmerProfile" -> Response.error<CanonicalApiResponse<CanonicalCreatedProfileDto>>(404, "{}".toResponseBody())
            "createFarmerProfile" -> { profiles++; Response.success(CanonicalApiResponse(true, data = CanonicalCreatedProfileDto(42))) }
            "login" -> {
            logins++
            if (malformed) throw JsonSyntaxException("private-response-token")
            Response.success(CanonicalApiResponse(true, data = if (verified)
                CanonicalAuthTokenDto("synthetic-access", "synthetic-refresh", "Bearer", 3600, account())
                else CanonicalAuthTokenDto(user = account(), emailVerificationRequired = true, emailVerificationChallengeId = challenge.takeIf { advertiseChallenge })))
            }
            else -> error("Unexpected synthetic endpoint")
            }
        } as CanonicalBackendApi
        compose.runOnUiThread {
            vm = AuthViewModel(AuthRepository(CanonicalAuthService(api, "https://fixture.example.test/api/v1/"), sessions)) {
                object : SessionPersistence {
                    override fun read(): AuthResponse? = null
                    override fun write(auth: AuthResponse) { saved = auth }
                    override fun clear() { saved = null }
                }
            }; models.put("auth-refinement", vm)
        }
        compose.setContent { FarmAppTheme {
            if (sessions.session.collectAsState().value.auth != null) Text("Authenticated synthetic Farmer")
            else LoginScreen(vm, afterExplicitLogout = true, verificationApi = otp)
        } }
        compose.waitUntil(5_000) { !vm.uiState.value.isInitializing }
        compose.onNodeWithText("Verify email / resend code").assertDoesNotExist()
        if (!submitLogin) return
        compose.onNodeWithText("Email").performScrollTo().performTextInput("farmer@example.test")
        compose.onNodeWithText("Password").performScrollTo().performTextInput("synthetic-password")
        compose.onNode(hasText("Sign In") and hasClickAction()).performScrollTo().performClick()
    }
    @After fun close() { compose.runOnUiThread { models.clear() } }
    @Test fun unverifiedFarmerAutomaticallyEntersOtpThenResumesOnlyAfterVerifiedResponse() {
        authScreen()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("6-digit email code").fetchSemanticsNodes().isNotEmpty() }
        assertNull(saved); assertEquals(1, logins); assertEquals(0, requests)
        compose.onNodeWithText("6-digit email code").performScrollTo().performTextInput("123456")
        compose.onNode(hasText("Verify email") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Authenticated synthetic Farmer").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, verifications); assertEquals(0, requests); assertEquals(2, logins); assertEquals("FARMER", saved!!.role)
    }
    @Test fun verifiedFarmerSignsInWithoutOtpOrResend() {
        verified = true; authScreen()
        compose.waitUntil(5_000) { saved != null }
        compose.onNodeWithText("6-digit email code").assertDoesNotExist()
        assertEquals(1, logins); assertEquals(0, requests); assertEquals(0, verifications)
    }
    @Test fun registrationAutomaticallyOpensOtpAndCreatesOnlyOneAccountAndProfile() {
        authScreen(submitLogin = false)
        compose.runOnUiThread { vm.signup(SignupRequest("farmer@example.test", "synthetic-password", "Synthetic Farmer", "+254700000000",
            "FARMER", "synthetic_farmer", "Kiambu", farmName = "Synthetic farm")) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("6-digit email code").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, registrations); assertEquals(0, logins); assertEquals(0, requests); assertNull(saved)
        compose.onNodeWithText("6-digit email code").performScrollTo().performTextInput("123456")
        compose.onNode(hasText("Verify email") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5_000) { saved != null }
        assertEquals(1, registrations); assertEquals(1, logins); assertEquals(1, profiles); assertEquals(1, verifications)
    }
    @Test fun returningToSignInAndRetryingReusesActiveChallengeWithoutAutomaticResend() {
        advertiseChallenge = false; authScreen()
        compose.waitUntil(5_000) { requests == 1 && compose.onAllNodesWithText("6-digit email code").fetchSemanticsNodes().isNotEmpty() }
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Password").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Password").performScrollTo().performTextInput("synthetic-password")
        compose.onNode(hasText("Sign In") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5_000) { logins == 2 && compose.onAllNodesWithText("6-digit email code").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals(1, requests); assertEquals(0, verifications); assertNull(saved) }
    }
    @Test fun malformedLoginDisplaysDecodeStageWithoutPrivateContent() {
        malformed = true; authScreen()
        compose.waitUntil(5_000) { vm.uiState.value.errorMessage != null && !vm.uiState.value.isLoading }
        compose.onNodeWithText("AUTH_JSON_DECODE_MALFORMED").performScrollTo().assertIsDisplayed()
        assertFalse(vm.uiState.value.errorMessage!!.contains("private-response-token")); assertNull(saved)
    }
    @Test fun imagePlusLaunchesPhotoPickerShowsThumbnailAndAllowsReplacementBeforeSave() {
        val storage = CropPhotoStorage(InstrumentationRegistry.getInstrumentation().targetContext)
        val name = storage.create()
        val uri = storage.uri(name)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try { InstrumentationRegistry.getInstrumentation().targetContext.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
        finally { bitmap.recycle() }
        var launches = 0
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                assertTrue(contract is ActivityResultContracts.PickVisualMedia); launches++
                dispatchResult(requestCode, uri)
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        try {
            compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { FarmAppTheme {
                SupplierContent(SupplierUiState(catalogueLoaded = true, categories = listOf(CanonicalCategoryDto(2, "Seeds", "supplier", true))),
                    "Synthetic Supplier", {}, false, {}, false, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
            } } }
            compose.onNodeWithText("Add supply").performScrollTo().performClick()
            compose.onNodeWithTag("supplier-list").performScrollToNode(hasContentDescription("Add product photo"))
            compose.onNodeWithText("Choose product photo").assertDoesNotExist()
            compose.onNodeWithContentDescription("Add product photo").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Selected supply photo preview").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Selected supply photo preview").assertExists()
            compose.onNodeWithContentDescription("Add product photo").performClick()
            compose.runOnIdle { assertEquals(2, launches) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Remove selected photo").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
            compose.onNodeWithText("Remove selected photo").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Selected supply photo preview").assertDoesNotExist()
            compose.onNodeWithText("Upload photo").assertDoesNotExist()
        } finally { storage.delete(name) }
    }
    @Test fun mainSupplierProfileHasStandaloneAboutAtBottom() {
        compose.setContent { FarmAppTheme {
            SupplierContent(SupplierUiState(profile = SupplierProfile(account().copy(role = "SUPPLIER").toAccount(),
                SupplierProfileDto(5, 42, "Synthetic business", "Synthetic details", "Synthetic location"))),
                "Synthetic Supplier", {}, false, {}, false, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {})
        } }
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithTag("supplier-list").performScrollToNode(hasText("About"))
        compose.onNodeWithTag("profile-about").assertHasClickAction().performClick()
        compose.onNodeWithText("About Kilivana").assertIsDisplayed()
        compose.onNodeWithText("Version", substring = true).assertIsDisplayed()
    }
    @Test fun editProfileContainsOnlyEditingActionsAndNoAbout() {
        compose.setContent { FarmAppTheme {
            ProfileEditorContent(ProfileEditState(account = account().toAccount()), {}, {}, { _, _, _ -> }, { _, _, _ -> })
        } }
        compose.onNodeWithText("Edit profile").assertIsDisplayed()
        compose.onNodeWithText("About").assertDoesNotExist()
        compose.onNodeWithText("About Kilivana").assertDoesNotExist()
        compose.onNodeWithText("Save").assertExists(); compose.onNodeWithText("Cancel").assertExists()
    }
}
