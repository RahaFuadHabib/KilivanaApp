package com.example.farmapp.buyer

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.ViewModelStore
import com.example.farmapp.data.model.*
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import com.example.farmapp.repository.AuthRepository
import com.example.farmapp.ui.screens.LoginScreen
import com.example.farmapp.ui.screens.auth.*
import com.example.farmapp.ui.theme.FarmAppTheme
import com.example.farmapp.viewmodel.AuthViewModel
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy
import java.util.UUID

/** Isolated session and fake OTP/token DTOs only. Does not touch the real Samsung Buyer session. */
class BuyerAuthenticationPhaseOnePresentationTest {
    @get:Rule val compose=createComposeRule()
    private val models=ViewModelStore()
    private val sessions=SessionState()
    private var saved: AuthResponse?=null
    private var verified=false
    private var registers=0;private var logins=0;private var requests=0;private var verifies=0
    private lateinit var vm: AuthViewModel
    private val challenge=UUID.fromString("00000000-0000-4000-8000-000000000042")
    private val otp=object: EmailVerificationApi {
        override suspend fun request(request: EmailOtpRequest): Response<CanonicalApiResponse<EmailOtpChallenge>> {
            requests++;return Response.success(CanonicalApiResponse(true,data=EmailOtpChallenge(challenge,600,60)))
        }
        override suspend fun verify(request: EmailOtpVerifyRequest): Response<CanonicalApiResponse<Unit>> {
            assertEquals(challenge,request.challengeId);assertEquals("123456",request.code)
            verifies++;verified=true;return Response.success(CanonicalApiResponse(true))
        }
    }
    private fun screen(afterLogout:Boolean=true) {
        val api=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(CanonicalBackendApi::class.java)) { _,m,_ ->
            when(m.name) {
                "register" -> {registers++;Response.success(CanonicalApiResponse(true,data=CanonicalUserDto(id=42,email="buyer@example.test",role="BUYER",emailVerified=false,emailVerificationChallengeId=challenge)))}
                "login" -> {logins++;Response.success(CanonicalApiResponse(true,data=if(!verified)
                    CanonicalAuthTokenDto(emailVerificationRequired=true,emailVerificationChallengeId=challenge)
                    else CanonicalAuthTokenDto("synthetic-access","synthetic-refresh","Bearer",3600,
                        CanonicalUserDto(42,"Synthetic Buyer","buyer@example.test","0712345678",role="BUYER",status="ACTIVE",verificationStatus="NOT_REQUIRED",emailVerified=true))))}
                else -> error("Unexpected profile or other request")
            }
        } as CanonicalBackendApi
        compose.runOnUiThread {
            vm=AuthViewModel(AuthRepository(CanonicalAuthService(api,"https://synthetic.example.test/api/v1/"),sessions)) {
                object:SessionPersistence {
                    override fun read()=saved
                    override fun write(auth:AuthResponse){saved=auth}
                    override fun clear(){saved=null}
                }
            };models.put("buyer-auth-phase1",vm)
        }
        compose.setContent { FarmAppTheme {
            if(sessions.session.collectAsState().value.auth==null) LoginScreen(vm,afterExplicitLogout=afterLogout,verificationApi=otp)
            else Text("Authenticated synthetic Buyer")
        } }
        compose.waitUntil(5000){!vm.uiState.value.isInitializing}
        assertNoExtraEntries()
    }
    private fun verify() {
        compose.waitUntil(15000){vm.uiState.value.emailVerification!=null}
        assertNoExtraEntries()
        assertNull(saved)
        compose.onNodeWithText("6-digit email code").performScrollTo().performTextInput("123456")
        // Exercise one accessibility activation after scrolling/enabled checks. Physical taps can miss
        // moving controls during Samsung IME animation; these tests cover the auth/OTP state boundary.
        compose.onNode(hasText("Verify email") and hasClickAction()).performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.waitUntil(15000){sessions.session.value.auth?.role=="BUYER" && !vm.uiState.value.isLoading}
        compose.onNodeWithText("Authenticated synthetic Buyer").assertIsDisplayed()
        assertEquals(1,verifies);assertEquals(0,requests);assertEquals("BUYER",saved!!.role)
    }
    private fun assertNoExtraEntries() {
        compose.onNodeWithText("Enable safe auth diagnostics (debug)").assertDoesNotExist()
        compose.onNodeWithText("Safe auth diagnostics enabled (10 min)").assertDoesNotExist()
        compose.onNodeWithText("Buyer sign in / Create account").assertDoesNotExist()
    }
    @Test fun cleanWelcomeSignInSignupAndRecoveryUseNormalDesign() {
        screen(afterLogout=false)
        compose.onNodeWithText("Get Started").performClick();assertNoExtraEntries()
        compose.onNode(hasText("Sign In") and hasClickAction()).performClick();assertNoExtraEntries()
        compose.onNodeWithText("Forgot password?").performScrollTo().performClick();assertNoExtraEntries()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("New to Kilivana? Sign Up").performScrollTo().performClick();assertNoExtraEntries()
        compose.onNodeWithText("Account type").performScrollTo().performClick()
        listOf("Farmer","Buyer","Supplier","Inspector").forEach{compose.onNodeWithText(it).assertExists()}
        compose.onNodeWithText("Inspector").performClick()
        compose.onNodeWithText("Create Account").performScrollTo().assertIsNotEnabled()
        assertEquals(0,registers);assertEquals(0,logins)
    }
    @After fun close(){compose.runOnUiThread {models.clear()}}
    @Test fun registrationUsesIssuedOtpAndOnlySignsInAfterVerification() {
        screen()
        compose.runOnUiThread {repeat(2){vm.signup(SignupRequest("buyer@example.test","synthetic-password","Synthetic Buyer","0712345678","BUYER","synthetic_buyer","Nairobi"))}}
        compose.waitUntil(5000){vm.uiState.value.emailVerification!=null}
        assertEquals(1,registers);assertEquals(0,logins)
        verify();assertEquals(1,registers);assertEquals(1,logins)
    }
    @Test fun loginVerificationGateContinuesWithoutRegisteringAnotherAccount() {
        screen()
        compose.runOnUiThread {vm.login("buyer@example.test","synthetic-password")}
        verify();assertEquals(0,registers);assertEquals(2,logins)
    }
    @Test fun sharedBuyerLoginUsesSameOtpAndSessionBoundary() {
        screen()
        compose.onNodeWithText("Email").performTextInput("buyer@example.test")
        compose.onNodeWithText("Password").performTextInput("synthetic-password")
        compose.onNode(hasText("Sign In") and hasClickAction()).performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick){it()}
        verify();assertEquals(0,registers);assertEquals(2,logins)
    }
    @Test fun sharedBuyerRoleSignupContinuesWithoutDuplicateRegistration() {
        screen()
        compose.onNodeWithText("New to Kilivana? Sign Up").performScrollTo().performClick()
        compose.onNodeWithText("Account type").performScrollTo().performClick()
        compose.onNodeWithText("Buyer").performClick()
        assertNoExtraEntries()
        compose.onNodeWithText("Join Kilivana").assertDoesNotExist()
        compose.onNodeWithText("Full name").performScrollTo().performTextInput("Synthetic Buyer")
        compose.onNodeWithText("Phone number").performScrollTo().performTextInput("0712345678")
        compose.onNodeWithText("Username").performScrollTo().performTextInput("synthetic_buyer")
        compose.onNodeWithText("Email").performScrollTo().performTextInput("buyer@example.test")
        compose.onNodeWithText("Region").performScrollTo().performTextInput("Nairobi")
        compose.onNodeWithText("Password").performScrollTo().performTextInput("synthetic-password")
        compose.onNodeWithText("Confirm password").performScrollTo().performTextInput("synthetic-password")
        compose.onNodeWithText("Create Account").performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick){it()}
        verify();assertEquals(1,registers);assertEquals(1,logins)
    }
}
