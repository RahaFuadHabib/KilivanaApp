package com.example.farmapp.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun SignUpScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Local, disposable presentation state. No DTO, persistence, session or service calls.
    var account by remember { mutableStateOf<DemoAccount?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordStep by remember { mutableStateOf(false) }
    var showNotice by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val back: () -> Unit = {
        focus.clearFocus()
        if (passwordStep) { passwordStep = false; showNotice = false } else onBack()
    }
    BackHandler(onBack = back)
    val preview: () -> Unit = { focus.clearFocus(); showNotice = true }
    AuthPage("Sign Up", isDarkTheme, onToggleTheme, modifier, back) {
        AgriculturalVisual(password = true)
        Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
            AuthHeading(if (passwordStep) "Choose your password." else "Let's grow together.",
                if (passwordStep) "Step 2 of 2 · Password" else "Step 1 of 2 · Your details")
            if (!passwordStep) {
                AccountTypeDropdown(account, { account = it })
                Spacer(Modifier.height(12.dp))
                // Existing ProfileModal fields and login UI only; signup still has no backend contract.
                AuthField(name, { name = it }, "Full name / Enterprise")
                Spacer(Modifier.height(12.dp))
                AuthField(phone, { phone = it }, "Phone number", KeyboardType.Phone)
                Spacer(Modifier.height(12.dp))
                AuthField(email, { email = it }, "Email", KeyboardType.Email, imeAction = ImeAction.Done,
                    onDone = { focus.clearFocus(); passwordStep = true })
            } else {
                AuthField(password, { password = it; showNotice = false }, "Password", isPassword = true,
                    imeAction = ImeAction.Done, onDone = preview)
                if (showNotice) AuthNotice("Account creation will be connected when backend registration is available. No account has been created.")
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Sign-up preview · Details are not saved.",
                Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AuthPrimaryButton(if (passwordStep) "Create Account" else "Next",
                if (passwordStep) preview else ({ focus.clearFocus(); passwordStep = true }))
            TextButton(onSignIn, modifier = Modifier.padding(top = 8.dp)) {
                Text("Already have an account? Sign In")
            }
        }
    }
}
