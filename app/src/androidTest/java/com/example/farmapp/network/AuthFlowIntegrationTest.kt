package com.example.farmapp.network

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.example.farmapp.data.model.*
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
import java.time.Instant

/** Exercises the production ViewModel/repository path with an isolated test transport/store. */
class AuthFlowIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val viewModels = ViewModelStore()
    private lateinit var vm: AuthViewModel
    private var signupCalls = 0
    private var receivedSignup: SignupRequest? = null
    private var signupGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private val sessions = SessionState()
    private var stored: AuthResponse? = null
    private var receivedLogin: LoginRequest? = null
    private var receivedLogout: LogoutRequest? = null
    private val persistence = object : SessionPersistence {
        override fun read() = stored
        override fun write(auth: AuthResponse) { stored = auth }
        override fun clear() { stored = null }
    }
    private val service = object : ApiService {
        override suspend fun signup(request: SignupRequest): Response<AuthResponse> {
            signupCalls++
            receivedSignup = request
            signupGate?.await()
            return Response.success(AuthResponse(
            "eb1f7b4e-8392-4bf4-8cdc-f96083411000", request.email, request.fullName, request.role,
            "access", "refresh", Instant.now().plusSeconds(86400).toString(), "PENDING", request.username, request.region, "+254700000000"))
        }
        override suspend fun login(request: LoginRequest): Response<AuthResponse> {
            receivedLogin = request
            if (request.password != "valid-test-password") return Response.error(401, "{}".toResponseBody())
            return Response.success(AuthResponse(
                "eb1f7b4e-8392-4bf4-8cdc-f96083411000", "test@example.test", "Test Person", "ADMIN",
                "test-access", "test-refresh", Instant.now().plusSeconds(86400).toString(), "VERIFIED", "test_person", "Nairobi", "+254700000000"
            ))
        }
        override suspend fun refresh(request: RefreshRequest): Response<AuthResponse> =
            Response.error(401, "{}".toResponseBody())
        override suspend fun logout(request: LogoutRequest): Response<Unit> {
            receivedLogout = request
            return Response.success(Unit)
        }
    }

    @Before fun setup() {
        compose.runOnUiThread {
            vm = AuthViewModel(AuthRepository(service, sessions)) { persistence }
            viewModels.put("auth-test", vm)
        }
        compose.setContent { AppNavigation(authViewModel = vm) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Get Started").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun cleanup() { compose.runOnUiThread { viewModels.clear() } }

    private fun openSignIn() {
        compose.onNodeWithText("Get Started").performClick()
        compose.onNode(hasText("Sign In") and hasClickAction()).performClick()
        compose.onNodeWithText("Account type").assertDoesNotExist()
        compose.onNodeWithText("Username").assertDoesNotExist()
    }

    @Test fun signupIgnoresDuplicateSubmissionsWhileLoading() {
        signupGate = kotlinx.coroutines.CompletableDeferred()
        val request = SignupRequest("buyer@example.test", "password", "Buyer", "123", "BUYER", username = "buyer", region = "Nairobi")
        compose.runOnUiThread {
            vm.signup(request)
            vm.signup(request)
        }
        compose.waitUntil(5000) { vm.uiState.value.isLoading }
        compose.runOnIdle { signupGate!!.complete(Unit) }
        compose.waitUntil(5000) { sessions.session.value.auth != null && !vm.uiState.value.isLoading }
        assertEquals(1, signupCalls)
    }

    @Test fun inspectorSelfRegistrationRemainsDisabled() {
        compose.onNodeWithText("Get Started").performClick()
        compose.onNode(hasText("Sign Up") and hasClickAction()).performClick()
        compose.onNodeWithText("Account type").performClick()
        compose.onNodeWithText("Inspector").performClick()
        compose.onNodeWithText("Inspector accounts are provided by the administrator. Sign in with your assigned credentials.").performScrollTo().assertExists()
        compose.onNodeWithText("Create Account").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, signupCalls); assertNull(sessions.session.value.auth) }
    }

    @Test fun realModeLoginUsesServerRoleAndUnsupportedRoleCanLogout() {
        openSignIn()
        compose.onNodeWithText("Email or phone number").performTextInput("test@example.test")
        compose.onNodeWithText("Password").performTextInput("valid-test-password")
        compose.onNodeWithContentDescription("Show password").performClick()
        compose.onNodeWithContentDescription("Hide password").assertExists()
        compose.onNode(hasText("Sign In") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5000) { sessions.session.value.auth != null }
        compose.onNodeWithText("This account type is not supported in the Android app yet. Please sign out to use another account.")
            .assertIsDisplayed()
        assertEquals(LoginRequest("test@example.test", "valid-test-password"), receivedLogin)
        assertEquals("ADMIN", stored!!.role)
        compose.onNodeWithContentDescription("Log Out").performClick()
        compose.waitUntil(5000) { sessions.session.value.auth == null }
        compose.onNodeWithText("Get Started").assertDoesNotExist()
        compose.onNodeWithText("Email or phone number").assertIsDisplayed()
        compose.onNodeWithText("Password").assertIsDisplayed()
        compose.onNodeWithContentDescription("Log Out").assertDoesNotExist()
        assertEquals("test-refresh", receivedLogout!!.refreshToken)
        assertNull(stored)
    }

    @Test fun phoneLoginUsesIdentifierAndPreservesPasswordWhitespace() {
        openSignIn()
        compose.onNodeWithText("Email or phone number").performTextInput(" 0712345678 ")
        compose.onNodeWithText("Password").performTextInput(" wrong ")
        compose.onNode(hasText("Sign In") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5000) { receivedLogin != null && !vm.uiState.value.isLoading }
        assertEquals(LoginRequest("0712345678", " wrong "), receivedLogin)
        assertNull(stored)
    }

    @Test fun invalidCredentialsKeepTheFormAndDisplayError() {
        openSignIn()
        compose.onNodeWithText("Email or phone number").performTextInput("test@example.test")
        compose.onNodeWithText("Password").performTextInput("wrong")
        compose.onNode(hasText("Sign In") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Invalid email or phone number, or password.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Invalid email or phone number, or password.").performScrollTo().assertIsDisplayed()
        assertNull(sessions.session.value.auth)
        assertNull(stored)
    }
}
