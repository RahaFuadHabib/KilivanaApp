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
import com.example.farmapp.data.model.SignupRequest
import com.example.farmapp.ui.components.KilivanaErrorMessage

@Composable
internal fun SignUpScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSignup: (SignupRequest) -> Unit,
    onClearError: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    demoMode: Boolean,
    modifier: Modifier = Modifier
) {
    var account by remember { mutableStateOf<SignupRole?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var employeeCode by remember { mutableStateOf("") }
    var passwordStep by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val back: () -> Unit = {
        if (!isLoading) {
            focus.clearFocus()
            onClearError()
            if (passwordStep) passwordStep = false else onBack()
        }
    }
    BackHandler(onBack = back)
    val submit: () -> Unit = {
        if (!isLoading && !demoMode) {
            focus.clearFocus()
            onSignup(SignupRequest(email = email, password = password, fullName = name,
                phoneNumber = phone, role = account?.name.orEmpty(), username = username, region = region,
                farmName = signupFarmName(account, name), businessName = businessName, employeeCode = employeeCode))
        }
    }
    AuthPage("Sign Up", isDarkTheme, onToggleTheme, modifier, back) {
        AgriculturalVisual(password = true)
        Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
            AuthHeading(if (passwordStep) "Choose your password." else "Let's grow together.",
                if (passwordStep) "Step 2 of 2 ? Password" else "Step 1 of 2 ? Your details")
            if (!passwordStep) {
                SignupRoleDropdown(account, { account = it; onClearError() }, enabled = !isLoading)
                Spacer(Modifier.height(12.dp))
                AuthField(name, { name = it; onClearError() }, "Full name", enabled = !isLoading)
                Spacer(Modifier.height(12.dp))
                AuthField(email, { email = it; onClearError() }, "Email", KeyboardType.Email, enabled = !isLoading)
                Spacer(Modifier.height(12.dp))
                AuthField(phone, { phone = it; onClearError() }, "Phone number", KeyboardType.Phone, enabled = !isLoading)
                Spacer(Modifier.height(12.dp))
                AuthField(username, { username = it; onClearError() }, "Username", enabled = !isLoading)
                AuthNotice("Use 3 to 30 letters, digits, or underscores, starting with a letter.")
                AuthField(region, { region = it; onClearError() }, "Region", enabled = !isLoading)
                Spacer(Modifier.height(12.dp))
                when (account) {
                    SignupRole.SUPPLIER -> AuthField(businessName, { businessName = it; onClearError() }, "Business name", enabled = !isLoading)
                    SignupRole.INSPECTOR -> AuthField(employeeCode, { employeeCode = it; onClearError() }, "Employee code", enabled = !isLoading)
                    else -> Unit
                }
            } else {
                AuthField(password, { password = it; onClearError() }, "Password", isPassword = true,
                    enabled = !isLoading, imeAction = ImeAction.Done, onDone = submit)
                AuthNotice("Use 8 to 72 characters.")
            }
            if (errorMessage != null) KilivanaErrorMessage(errorMessage)
            if (demoMode) AuthNotice("Account creation is unavailable in offline demo mode.")
        }
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AuthPrimaryButton(if (passwordStep) "Create Account" else "Next",
                if (passwordStep) submit else ({ focus.clearFocus(); passwordStep = true }),
                enabled = !isLoading && !demoMode, loading = isLoading)
            TextButton(onSignIn, enabled = !isLoading, modifier = Modifier.padding(top = 8.dp)) {
                Text("Already have an account? Sign In")
            }
        }
    }
}
