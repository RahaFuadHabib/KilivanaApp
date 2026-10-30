// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.data.EmailVerificationChallenge
import com.example.farmapp.buyers.ui.theme.*

data class AuthForm(val name: String, val phone: String, val email: String, val password: String, val username: String, val region: String) { override fun toString() = "AuthForm(redacted)" }

@Composable
fun AuthScreen(
    signup: Boolean,
    submit: suspend (AuthForm) -> String?,
    forgotPassword: suspend (String) -> String?,
    switch: () -> Unit,
    back: () -> Unit,
    externalLoading: Boolean = false, externalError: String? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("Nairobi") }
    var showPassword by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val validEmail = email.contains("@") && email.substringAfter('@').contains('.')
    val validPhone = runCatching { com.example.farmapp.orders.buyerMpesaPhone(phone) }.isSuccess

    Column(
        Modifier.fillMaxSize().background(NeutralBackground).verticalScroll(rememberScrollState()).imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Kilivana", style = MaterialTheme.typography.titleLarge, color = Green700)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(if (signup) "Join Kilivana" else "Welcome back", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text(
                if (signup) "Create an account to manage orders and delivery details." else "Sign in to continue to your account.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            Spacer(Modifier.height(18.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = NeutralSurface, shadowElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (signup) {
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
                        OutlinedTextField(
                            phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true,
                            isError = phone.isNotEmpty() && !validPhone,
                            supportingText = { if (phone.isNotEmpty() && !validPhone) Text("Enter a valid Kenyan number") }
                        )
                        OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                        OutlinedTextField(region, { region = it }, Modifier.fillMaxWidth(), label = { Text("Region or county") }, singleLine = true)
                    }
                    OutlinedTextField(
                        email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true,
                        isError = email.isNotEmpty() && !validEmail,
                        supportingText = { if (email.isNotEmpty() && !validEmail) Text("Enter a valid email address") }
                    )
                    OutlinedTextField(
                        password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = password.isNotEmpty() && password.length < 8,
                        supportingText = { if (password.isNotEmpty() && password.length < 8) Text("Use at least 8 characters") },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (showPassword) "Hide password" else "Show password")
                            }
                        }
                    )
                    Button(
                        onClick = {
                            if (!submitting && !externalLoading) { submitting = true; scope.launch {
                                feedback = submit(AuthForm(name.trim(), phone.trim(), email.trim(), password, username.trim(), region.trim()))
                                submitting = false
                            } }
                        },
                        Modifier.fillMaxWidth(),
                        enabled = !submitting && !externalLoading && validEmail && password.length >= 8 && (!signup || (name.isNotBlank() && validPhone && username.isNotBlank() && region.isNotBlank()))
                    ) {
                        if (submitting || externalLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(if (signup) "Create account" else "Sign in")
                    }
                    if (!signup) TextButton(
                        onClick = {
                            if (!submitting && !externalLoading) { submitting = true; scope.launch {
                                feedback = forgotPassword(email.trim())
                                submitting = false
                            } }
                        }, enabled = validEmail && !submitting && !externalLoading, modifier = Modifier.fillMaxWidth()
                    ) { Text("Forgot password?") }
                    (externalError ?: feedback)?.let { Text(it, color = ErrorRed) }
                    TextButton(onClick = switch, Modifier.fillMaxWidth()) {
                        Text(if (signup) "Already have an account? Sign in" else "New to Kilivana? Create account")
                    }
                    Text(
                        "Your account details are sent securely to Kilivana.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun EmailVerificationScreen(
    email: String,
    initialChallenge: EmailVerificationChallenge,
    requestCode: suspend (String) -> EmailVerificationChallenge,
    verifyCode: suspend (String, String, String) -> Unit,
    continueToSignIn: () -> Unit,
    back: () -> Unit,
    isLoginFlow: Boolean = false,
    onLoginSuccess: suspend () -> String? = { null },
    externalLoading: Boolean = false, canVerify: Boolean = true, canResend: Boolean = true
) {
    var challengeId by remember(email) { mutableStateOf(initialChallenge.challengeId) }
    var resendAfterSeconds by remember(email) { mutableIntStateOf(initialChallenge.resendAfterSeconds) }
    var secondsRemaining by remember(challengeId) { mutableIntStateOf(resendAfterSeconds.coerceAtLeast(0)) }
    var code by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(challengeId) {
        while (secondsRemaining > 0) {
            delay(1_000)
            secondsRemaining -= 1
        }
    }

    Column(
        Modifier.fillMaxSize().background(NeutralBackground).verticalScroll(rememberScrollState()).imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Kilivana", style = MaterialTheme.typography.titleLarge, color = Green700)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(if (isLoginFlow) "Sign in verification" else "Verify your email", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text("Enter the six-digit code sent to $email.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            Spacer(Modifier.height(18.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = NeutralSurface, shadowElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Verification code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !verified,
                        isError = code.isNotEmpty() && code.length != 6
                    )
                    Button(
                        onClick = {
                            if (!submitting && !externalLoading) { submitting = true; scope.launch {
                                feedback = try {
                                    verifyCode(challengeId, email, code)
                                    if (isLoginFlow) {
                                        val failure=onLoginSuccess(); check(failure==null){failure.orEmpty()}; verified=true; null
                                    } else {
                                        verified=true; null
                                    }
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (error: Exception) {
                                    error.message ?: "Email verification failed."
                                }
                                submitting = false
                            } }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !submitting && !externalLoading && canVerify && !verified && code.length == 6
                    ) {
                        if (submitting || externalLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(if (isLoginFlow) "Verify and sign in" else "Verify email")
                    }
                    if (verified) {
                        Text(if (isLoginFlow) "Email verified and signed in successfully." else "Email verified. Sign in to continue.", color = Green700)
                        Button(onClick = continueToSignIn, modifier = Modifier.fillMaxWidth()) { Text(if (isLoginFlow) "Continue" else "Continue to sign in") }
                    } else {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    submitting = true
                                    try {
                                        val challenge = requestCode(email)
                                        challengeId = challenge.challengeId
                                        resendAfterSeconds = challenge.resendAfterSeconds
                                        secondsRemaining = challenge.resendAfterSeconds.coerceAtLeast(0)
                                        feedback = "A new code has been requested."
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (error: Exception) {
                                        feedback = error.message ?: "Could not request a new code."
                                    }
                                    submitting = false
                                }
                            },
                            enabled = !submitting && !externalLoading && canResend && secondsRemaining == 0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (secondsRemaining > 0) "Resend code in ${secondsRemaining}s" else "Resend code")
                        }
                    }
                    feedback?.let { Text(it, color = if (it.contains("failed", true) || it.contains("could not", true)) ErrorRed else TextSecondary) }
                }
            }
        }
    }
}




