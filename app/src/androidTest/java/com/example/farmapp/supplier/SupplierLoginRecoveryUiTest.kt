package com.example.farmapp.supplier

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModelStore
import com.example.farmapp.data.model.AuthResponse
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.SessionPersistence
import com.example.farmapp.network.SessionState
import com.example.farmapp.network.canonical.*
import com.example.farmapp.repository.AuthRepository
import com.example.farmapp.ui.screens.LoginScreen
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
import java.util.Collections

/** Synthetic canonical API and private in-memory session only. Never constructs ApiClient/SupplierShell. */
class SupplierLoginRecoveryUiTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    private val sessions = SessionState()
    private var saved: AuthResponse? = null
    private val calls = Collections.synchronizedList(mutableListOf<String>())
    private var profileCode = 404
    private var invalidLoginFormat = false
    private lateinit var vm: AuthViewModel
    private fun screen() {
        val api = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CanonicalBackendApi::class.java)) { _, method, args ->
            calls += method.name
            when (method.name) {
                "login" -> {
                    if (invalidLoginFormat) throw JsonSyntaxException("private-response-token")
                    val credentials = args[0] as CanonicalLoginRequest
                    assertEquals("supplier@example.test", credentials.email)
                    assertEquals("synthetic-password", credentials.password)
                    Response.success(CanonicalApiResponse(true, data = CanonicalAuthTokenDto("synthetic-access", "synthetic-refresh", "Bearer", 3600,
                        CanonicalUserDto(42, "Synthetic Supplier", "supplier@example.test", "+254700000000",
                            role = "SUPPLIER", status = "ACTIVE", verificationStatus = "NOT_REQUIRED"))))
                }
                "supplierProfile" -> if (profileCode == 200) Response.success(CanonicalApiResponse(true, data = CanonicalCreatedSupplierProfileDto(5, 42)))
                    else Response.error<CanonicalApiResponse<CanonicalCreatedSupplierProfileDto>>(profileCode, "{}".toResponseBody())
                "createSupplierProfile" -> Response.success(CanonicalApiResponse(true, data = CanonicalCreatedSupplierProfileDto(5, 42)))
                else -> error("Unexpected synthetic route")
            }
        } as CanonicalBackendApi
        compose.runOnUiThread {
            vm = AuthViewModel(AuthRepository(CanonicalAuthService(api, "https://auth.example.test/api/v1/"), sessions)) {
                object : SessionPersistence {
                    override fun read() = saved
                    override fun write(auth: AuthResponse) { saved = auth }
                    override fun clear() { saved = null }
                }
            }
            models.put("supplier-auth-test", vm)
        }
        compose.setContent { FarmAppTheme {
            if (sessions.session.collectAsState().value.auth == null) LoginScreen(vm, afterExplicitLogout = true)
            else Text("Authenticated synthetic Supplier")
        } }
        compose.waitUntil(5_000) { !vm.uiState.value.isInitializing }
        compose.onNodeWithText("Email").performScrollTo().performTextInput("supplier@example.test")
        compose.onNodeWithText("Password").performScrollTo().performTextInput("synthetic-password")
        compose.onNode(hasText("Sign In") and hasClickAction()).performScrollTo().performClick()
    }
    @After fun close() { compose.runOnUiThread { models.clear() } }

    @Test fun missingProfileAutomaticallyOpensExistingRecoveryFormAndOnlyExplicitSubmitCreatesProfile() {
        screen()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Finish Supplier setup").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("supplier@example.test").assertExists()
        compose.onNodeWithText("Create Account").assertDoesNotExist()
        compose.onNodeWithText("Full name").assertDoesNotExist()
        compose.onNodeWithText("Password").performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertNull(saved); assertNull(sessions.session.value.auth)
        assertEquals(listOf("login", "supplierProfile"), calls.toList())
        compose.onNodeWithText("Business name").performScrollTo().performTextInput("Synthetic shop")
        compose.onNodeWithText("County/region").performScrollTo().performClick()
        compose.onNodeWithText("Kiambu").performScrollTo().performClick()
        compose.onNodeWithText("Town or sub-county").performScrollTo().performTextInput("Ruiru")
        compose.onNodeWithText("Physical business location").performScrollTo().performTextInput("Shop 4, Synthetic Road")
        compose.onNodeWithText("Password").performScrollTo().performTextInput("synthetic-password")
        compose.onNodeWithText("Confirm password").performScrollTo().performTextInput("synthetic-password")
        compose.onNodeWithText("Sign in and finish setup").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Authenticated synthetic Supplier").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("SUPPLIER", saved!!.role)
        assertEquals(listOf("login", "supplierProfile", "login", "supplierProfile", "createSupplierProfile"), calls.toList())
    }

    @Test fun deniedProfileReadDoesNotOpenSetupOrSaveAPartialSession() {
        profileCode = 403
        screen()
        compose.waitUntil(5_000) { vm.uiState.value.errorMessage != null && !vm.uiState.value.isLoading }
        compose.onNodeWithText("Finish Supplier setup").assertDoesNotExist()
        assertTrue(vm.uiState.value.errorMessage!!.contains("profile could not be checked"))
        assertNull(saved); assertNull(sessions.session.value.auth)
        assertEquals(listOf("login", "supplierProfile"), calls.toList())
    }

    @Test fun malformedLoginResponseIsNotPresentedAsAConnectionFailure() {
        invalidLoginFormat = true
        screen()
        compose.waitUntil(5_000) { vm.uiState.value.errorMessage != null && !vm.uiState.value.isLoading }
        val message = vm.uiState.value.errorMessage!!
        assertTrue(message.contains("response format")); assertFalse(message.contains("connection"))
        assertFalse(message.contains("private-response-token"))
        assertNull(saved); assertEquals(listOf("login"), calls.toList())
    }
}
