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
        override suspend fun login(request: LoginRequest): Response<AuthResponse> {
            receivedLogin = request
            if (request.password != "valid-test-password") return Response.error(401, "{}".toResponseBody())
            return Response.success(AuthResponse(
                "eb1f7b4e-8392-4bf4-8cdc-f96083411000", request.email, "Test Person", "ADMIN",
                "test-access", "test-refresh", Instant.now().plusSeconds(86400).toString()
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
        lateinit var vm: AuthViewModel
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
    }

    @Test fun realModeLoginUsesServerRoleAndUnsupportedRoleCanLogout() {
        openSignIn()
        compose.onNodeWithText("Email").performTextInput("test@example.test")
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
        compose.onNodeWithText("Get Started").assertIsDisplayed()
        assertEquals("test-refresh", receivedLogout!!.refreshToken)
        assertNull(stored)
    }

    @Test fun invalidCredentialsKeepTheFormAndDisplayError() {
        openSignIn()
        compose.onNodeWithText("Email").performTextInput("test@example.test")
        compose.onNodeWithText("Password").performTextInput("wrong")
        compose.onNode(hasText("Sign In") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Invalid email or password.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Invalid email or password.").performScrollTo().assertIsDisplayed()
        assertNull(sessions.session.value.auth)
        assertNull(stored)
    }
}
