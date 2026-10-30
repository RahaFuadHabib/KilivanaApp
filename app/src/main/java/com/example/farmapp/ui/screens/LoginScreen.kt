package com.example.farmapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.farmapp.ui.screens.auth.*
import com.example.farmapp.viewmodel.AuthViewModel

internal enum class AuthStep { START, WELCOME, SIGN_IN, SIGN_UP, RECOVERY, EMAIL_VERIFICATION }

internal fun initialAuthStep(afterExplicitLogout: Boolean): AuthStep =
    if (afterExplicitLogout) AuthStep.SIGN_IN else AuthStep.START

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    modifier: Modifier = Modifier,
    afterExplicitLogout: Boolean = false,
    verificationApi: EmailVerificationApi? = null
) {
    var step by rememberSaveable { mutableStateOf(initialAuthStep(afterExplicitLogout)) }
    var identifier by rememberSaveable { mutableStateOf("") }
    var supplierSetupEmail by rememberSaveable { mutableStateOf<String?>(null) }
    // Passwords remain local and are never put in saved instance state or session persistence.
    var password by remember { mutableStateOf("") }
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.emailVerification?.flowId) {
        if (state.emailVerification != null) { password = ""; step = AuthStep.EMAIL_VERIFICATION }
        else if (step == AuthStep.EMAIL_VERIFICATION) step = AuthStep.SIGN_IN
    }
    LaunchedEffect(state.supplierProfileSetupRequired) {
        if (state.supplierProfileSetupRequired) {
            supplierSetupEmail = identifier.trim()
            password = ""
            step = AuthStep.SIGN_UP
            authViewModel.consumeSupplierSetupNavigation()
        }
    }
    val focus = LocalFocusManager.current
    fun navigate(destination: AuthStep) {
        focus.clearFocus()
        authViewModel.clearError()
        step = destination
    }
    val back: () -> Unit = {
        if (!state.isLoading) {
        if (step == AuthStep.EMAIL_VERIFICATION) authViewModel.cancelEmailVerification()
        navigate(when (step) {
            AuthStep.RECOVERY, AuthStep.EMAIL_VERIFICATION -> AuthStep.SIGN_IN
            AuthStep.SIGN_IN, AuthStep.SIGN_UP -> AuthStep.WELCOME
            else -> AuthStep.START
        })
        }
    }
    BackHandler(enabled = step != AuthStep.START, onBack = back)
    AuthTheme(isDarkTheme) {
        Box(modifier.fillMaxSize()) {
        when (step) {
            AuthStep.START, AuthStep.WELCOME -> AuthWelcomeScreen(
                isEntry = step == AuthStep.START,
                isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme, onBack = back,
                onGetStarted = { navigate(AuthStep.WELCOME) },
                onSignUp = { supplierSetupEmail = null; navigate(AuthStep.SIGN_UP) },
                onSignIn = { navigate(AuthStep.SIGN_IN) }, modifier = Modifier.fillMaxSize()
            )
            AuthStep.SIGN_IN -> SignInScreen(
                identifier = identifier, password = password, errorMessage = state.errorMessage,
                isLoading = state.isLoading, demoMode = authViewModel.demoMode, canonicalMode = authViewModel.canonicalMode,
                onIdentifierChange = { identifier = it; authViewModel.clearError() },
                onPasswordChange = { password = it; authViewModel.clearError() },
                onAccountSelected = {
                    identifier = it.email
                    password = it.password
                    authViewModel.clearError()
                },
                onSignIn = { focus.clearFocus(); authViewModel.login(identifier, password) },
                onBack = back, onSignUp = { supplierSetupEmail = null; navigate(AuthStep.SIGN_UP) },
                onForgotPassword = { navigate(AuthStep.RECOVERY) },
                isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme, modifier = Modifier.fillMaxSize(),
                diagnosticMessage = state.diagnosticMessage
            )
            AuthStep.SIGN_UP -> SignUpScreen(
                onSignup = authViewModel::signup, onClearError = authViewModel::clearError,
                isLoading = state.isLoading, errorMessage = state.errorMessage, demoMode = authViewModel.demoMode,
                onBack = back, onSignIn = { navigate(AuthStep.SIGN_IN) },
                isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme, modifier = Modifier.fillMaxSize(),
                supplierRecoveryRequired = state.supplierRegistrationRecovery,
                onRecoverSupplierSignup = authViewModel::recoverSupplierSignup,
                initialSupplierEmail = supplierSetupEmail
            )
            AuthStep.RECOVERY -> PasswordRecoveryScreen(
                onBack = back, isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme, modifier = Modifier.fillMaxSize()
            )
            AuthStep.EMAIL_VERIFICATION -> state.emailVerification?.let { pending ->
                EmailVerificationScreen(pending, back, isDarkTheme, onToggleTheme, Modifier.fillMaxSize(),
                    onVerified = authViewModel::completeEmailVerification,
                    api = verificationApi ?: com.example.farmapp.data.remote.ApiClient.emailVerificationApi)
            }
        }
        }
    }
}
