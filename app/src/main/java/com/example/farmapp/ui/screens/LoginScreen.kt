package com.example.farmapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.farmapp.ui.screens.auth.*
import com.example.farmapp.viewmodel.AuthViewModel

private enum class AuthStep { START, WELCOME, SIGN_IN, SIGN_UP, RECOVERY }

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var step by rememberSaveable { mutableStateOf(AuthStep.START) }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords remain local and are never put in saved instance state or session persistence.
    var password by remember { mutableStateOf("") }
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    fun navigate(destination: AuthStep) {
        focus.clearFocus()
        authViewModel.clearError()
        step = destination
    }
    val back: () -> Unit = {
        if (!state.isLoading) navigate(when (step) {
            AuthStep.RECOVERY -> AuthStep.SIGN_IN
            AuthStep.SIGN_IN, AuthStep.SIGN_UP -> AuthStep.WELCOME
            else -> AuthStep.START
        })
    }
    BackHandler(enabled = step != AuthStep.START, onBack = back)
    AuthTheme(isDarkTheme) {
        when (step) {
            AuthStep.START, AuthStep.WELCOME -> AuthWelcomeScreen(
                isEntry = step == AuthStep.START,
                isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme, onBack = back,
                onGetStarted = { navigate(AuthStep.WELCOME) },
                onSignUp = { navigate(AuthStep.SIGN_UP) },
                onSignIn = { navigate(AuthStep.SIGN_IN) }, modifier = modifier
            )
            AuthStep.SIGN_IN -> SignInScreen(
                email = email, password = password, errorMessage = state.errorMessage,
                isLoading = state.isLoading, demoMode = authViewModel.demoMode,
                onEmailChange = { email = it; authViewModel.clearError() },
                onPasswordChange = { password = it; authViewModel.clearError() },
                onAccountSelected = {
                    email = it.email
                    password = it.password
                    authViewModel.clearError()
                },
                onSignIn = { focus.clearFocus(); authViewModel.login(email, password) },
                onBack = back, onSignUp = { navigate(AuthStep.SIGN_UP) },
                onForgotPassword = { navigate(AuthStep.RECOVERY) },
                isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme, modifier = modifier
            )
            AuthStep.SIGN_UP -> SignUpScreen(
                onBack = back, onSignIn = { navigate(AuthStep.SIGN_IN) },
                isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme, modifier = modifier
            )
            AuthStep.RECOVERY -> PasswordRecoveryScreen(
                onBack = back, isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme, modifier = modifier
            )
        }
    }
}
