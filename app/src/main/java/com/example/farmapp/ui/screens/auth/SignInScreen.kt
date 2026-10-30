package com.example.farmapp.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.farmapp.ui.components.KilivanaErrorMessage

@Composable
internal fun SignInScreen(
    identifier: String,
    password: String,
    errorMessage: String?,
    isLoading: Boolean,
    demoMode: Boolean,
    canonicalMode: Boolean = false,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAccountSelected: (DemoAccount) -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    diagnosticMessage: String? = null
) {
    AuthPage("Sign In", isDarkTheme, onToggleTheme, modifier, onBack) {
        AgriculturalVisual(password = true)
        Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
            AuthHeading("Welcome back.", "Sign in to your Kilivana account.")
            if (demoMode) {
                AccountTypeDropdown(DemoAccount.forEmail(identifier), onAccountSelected, enabled = !isLoading)
                Text("Offline demo: choose an account type to fill demo credentials.",
                    Modifier.padding(top = 8.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AuthField(identifier, onIdentifierChange, if (canonicalMode && !demoMode) "Email" else "Email or phone number", KeyboardType.Text, enabled = !isLoading)
            Spacer(Modifier.height(12.dp))
            AuthField(password, onPasswordChange, "Password", isPassword = true, enabled = !isLoading,
                imeAction = ImeAction.Done, onDone = onSignIn)
            TextButton(onForgotPassword, enabled = !isLoading, modifier = Modifier.align(Alignment.End)) {
                Text("Forgot password?")
            }
            if (errorMessage != null) KilivanaErrorMessage(errorMessage)
            diagnosticMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AuthPrimaryButton("Sign In", onSignIn, enabled = !isLoading, loading = isLoading)
            TextButton(onSignUp, enabled = !isLoading, modifier = Modifier.padding(top = 8.dp)) {
                Text("New to Kilivana? Sign Up")
            }
        }
    }
}
