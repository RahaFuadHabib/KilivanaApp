package com.example.farmapp.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.ViewModelStore
import com.example.farmapp.BuildConfig
import com.example.farmapp.data.model.*
import com.example.farmapp.network.*
import com.example.farmapp.repository.AuthRepository
import com.example.farmapp.ui.navigation.AppNavigation
import com.example.farmapp.viewmodel.AuthViewModel
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** The transport is unavailable; demo entry/actions must make zero calls and zero session writes. */
class DemoModePresentationTest {
    @get:Rule val compose = createComposeRule()
    private val viewModels = ViewModelStore()
    private val sessions = SessionState()
    private lateinit var vm: AuthViewModel
    private lateinit var restoration: StateRestorationTester
    private var apiCalls = 0
    private var writes = 0
    private var clears = 0
    private val persistence = object : SessionPersistence {
        override fun read(): AuthResponse? = null
        override fun write(auth: AuthResponse) { writes++ }
        override fun clear() { clears++ }
    }
    private val service = object : ApiService {
        override suspend fun login(request: LoginRequest): Response<AuthResponse> { apiCalls++; return Response.error(401, "{}".toResponseBody()) }
        override suspend fun signup(request: SignupRequest): Response<AuthResponse> { apiCalls++; return Response.error(503, "{}".toResponseBody()) }
        override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> { apiCalls++; return Response.error(503, "{}".toResponseBody()) }
        override suspend fun logout(request: LogoutRequest): Response<Unit> { apiCalls++; return Response.error(503, "{}".toResponseBody()) }
    }

    @Before fun setup() {
        compose.runOnUiThread {
            vm = AuthViewModel(AuthRepository(service, sessions)) { persistence }
            viewModels.put("demo-test", vm)
        }
        restoration = StateRestorationTester(compose)
        restoration.setContent { AppNavigation(authViewModel = vm) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Continue with Demo Data").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { compose.runOnUiThread { viewModels.clear() } }
    private fun enterDemo() = compose.onNodeWithText("Continue with Demo Data").performScrollTo().performClick()
    private fun captureReview(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(directory, "demo-review-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun search(query: String) {
        compose.onNodeWithContentDescription("Search demo data").performClick()
        compose.onNodeWithText("Crop name or order number").performTextReplacement(query)
        compose.onNode(hasText("Search") and hasClickAction()).performClick()
    }

    @Test fun offlineNavigationOrderDetailsAndProfileActionsLeaveAuthUntouched() {
        enterDemo()
        compose.onNodeWithText("Demo Mode").assertIsDisplayed()
        compose.onNodeWithText("Mary Wanjiku").assertIsDisplayed()
        captureReview("home")
        listOf("Home", "Orders", "Add crop", "Map", "Supplies", "Profile").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Orders").performClick()
        compose.onNodeWithText("Order DEMO-1001").assertIsDisplayed()
        compose.onAllNodesWithText("View details")[0].performScrollTo().performClick()
        compose.onNodeWithText("Buyer: Nakuru Fresh Grocers").assertIsDisplayed()
        compose.onNodeWithText("Simulate confirmation").performClick()
        compose.onNodeWithText("Sample order · CONFIRMED").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Map").performClick()
        compose.onNodeWithText("Offline location preview", substring = true).assertIsDisplayed()
        captureReview("map")
        compose.onNode(hasText("Nakuru market") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Representative produce market in Nakuru.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Supplies").performClick()
        compose.onNodeWithText("Supplies · Coming soon").assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("About").performScrollTo().performClick()
        compose.onNodeWithText("About Kilivana").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Theme: Light · Switch to dark").performScrollTo().performClick()
        compose.onNodeWithText("Theme: Dark · Switch to light").assertIsDisplayed()
        compose.onNodeWithText("Exit Demo Mode / Logout").performScrollTo().performClick()
        compose.onNodeWithText("Email or phone number").assertExists()
        compose.runOnIdle { assertEquals(0, apiCalls); assertEquals(0, writes); assertEquals(0, clears); assertNull(sessions.session.value.auth) }
        compose.onNodeWithText("Email or phone number").performScrollTo().performTextInput("real@example.test")
        compose.onNodeWithText("Password").performScrollTo().performTextInput("test-password")
        compose.onNode(hasText("Sign In") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5000) { apiCalls == 1 && !vm.uiState.value.isLoading }
        compose.onNodeWithText("Invalid email or phone number, or password.").assertExists()
        assertFalse(BuildConfig.USE_MOCK_AUTH)
        // Backend host is intentionally configurable; demo must not switch the real login transport.
    }

    @Test fun addValidationSearchAndRotationKeepDemoDataAndExitResetsIt() {
        enterDemo()
        compose.onNodeWithContentDescription("Add crop").performClick()
        captureReview("add")
        compose.onNodeWithText("Save demo crop").performScrollTo().performClick()
        compose.onNodeWithText("Enter a valid quantity.").assertIsDisplayed()
        compose.onNodeWithText("Crop name").performScrollTo().performTextInput("Sorghum")
        compose.onNodeWithText("Category").performScrollTo().performClick()
        compose.onNodeWithText("Cereals").performClick()
        compose.onNodeWithText("Quantity").performScrollTo().performTextInput("450")
        compose.onNodeWithText("Price (KES/kg)").performScrollTo().performTextInput("70")
        compose.onNodeWithText("Description").performScrollTo().performTextInput("Clean dry sorghum from Nakuru.")
        compose.onNodeWithText("Save demo crop").performScrollTo().performClick()
        search("Sorghum")
        compose.onNodeWithTag("demo_home").performScrollToNode(hasText("Sorghum"))
        compose.onNodeWithText("Sorghum").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Sorghum").assertIsDisplayed()
        compose.onNodeWithText("Sorghum").performClick()
        compose.onNodeWithText("450 kg").assertIsDisplayed()
        compose.onNodeWithContentDescription("Exit Demo Mode").performClick()
        enterDemo()
        search("Sorghum")
        compose.onNodeWithTag("demo_home").performScrollToNode(hasText("No crops match this search."))
        compose.onNodeWithText("No crops match this search.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, apiCalls); assertEquals(0, writes); assertNull(sessions.session.value.auth) }
    }
}
