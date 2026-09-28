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
import com.example.farmapp.ui.components.KilivanaErrorMessage

@Composable
internal fun PasswordRecoveryScreen(
    onBack: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var codeStep by remember { mutableStateOf(false) }
    var previewComplete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val back: () -> Unit = {
        focus.clearFocus()
        if (codeStep) { codeStep = false; previewComplete = false; error = null } else onBack()
    }
    BackHandler(onBack = back)
    val next: () -> Unit = {
        focus.clearFocus()
        error = null
        if (!codeStep) {
            if (phone.isNotBlank()) codeStep = true else error = "Enter a phone number to preview recovery."
        } else if (otp == "7392" && password.isNotBlank()) {
            previewComplete = true
        } else error = "Enter demo code 7392 and a new password to preview this step."
    }
    AuthPage("Reset Password", isDarkTheme, onToggleTheme, modifier, back) {
        AgriculturalVisual(password = true)
        Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
            AuthHeading(if (codeStep) "Choose a new password." else "Back to growing.",
                "Recovery preview only. No code is sent and no password is changed.")
            if (!codeStep) {
                AuthField(phone, { phone = it; error = null }, "Registered phone number", KeyboardType.Phone,
                    imeAction = ImeAction.Done, onDone = next)
            } else {
                AuthNotice("Use demo code 7392. This code is displayed locally, not delivered to your phone.")
                AuthField(otp, { otp = it; error = null; previewComplete = false }, "4-digit OTP", KeyboardType.Number)
                Spacer(Modifier.height(12.dp))
                AuthField(password, { password = it; error = null; previewComplete = false }, "New password",
                    isPassword = true, imeAction = ImeAction.Done, onDone = next)
            }
            error?.let { KilivanaErrorMessage(it) }
            if (previewComplete) AuthNotice("Preview complete. Password recovery is not connected. Your existing demo credentials are unchanged.")
        }
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AuthPrimaryButton(if (codeStep) "Preview Reset" else "Next", next)
            TextButton(onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Back to Sign In") }
        }
    }
}
