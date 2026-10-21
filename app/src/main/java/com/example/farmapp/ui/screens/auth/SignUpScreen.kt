package com.example.farmapp.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.farmapp.data.model.SignupRequest
import com.example.farmapp.network.KenyanCounties
import com.example.farmapp.network.SignupValidation
import com.example.farmapp.repository.AuthFailure
import com.example.farmapp.ui.components.KilivanaErrorMessage

@Composable
internal fun SignUpScreen(
    onBack: () -> Unit, onSignIn: () -> Unit, isDarkTheme: Boolean, onToggleTheme: () -> Unit,
    onSignup: (SignupRequest) -> Unit, onClearError: () -> Unit, isLoading: Boolean,
    errorMessage: String?, demoMode: Boolean, modifier: Modifier = Modifier,
    supplierRecoveryRequired: Boolean = false, onRecoverSupplierSignup: ((SignupRequest) -> Unit)? = null
) {
    var account by rememberSaveable { mutableStateOf<SignupRole?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Neither password is saved into instance state, a model, persistence, or logs.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var businessName by rememberSaveable { mutableStateOf("") }
    var town by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var recoveryMode by rememberSaveable { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var reviewed by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val supplier = account == SignupRole.SUPPLIER
    val recovering = supplier && (recoveryMode || supplierRecoveryRequired)
    val busy = isLoading || sent
    val focus = LocalFocusManager.current
    val request = SignupRequest(email, password, name, phone, account?.name.orEmpty(), username, region,
        farmName = signupFarmName(account, name), businessName = businessName.takeIf { supplier },
        businessLocation = if (supplier) supplierProfileLocation(town, address) else null)
    val errors = if (reviewed) signupFormErrors(request, town, address, confirmation, recovering) else emptyMap()
    LaunchedEffect(isLoading, errorMessage) { if (!isLoading && errorMessage != null) sent = false }
    val back: () -> Unit = { if (!isLoading) { focus.clearFocus(); onClearError(); onBack() } }
    BackHandler(onBack = back)
    val submit: () -> Unit = {
        if (!busy && !demoMode && account != SignupRole.INSPECTOR) {
            focus.clearFocus()
            reviewed = true
            localError = null
            if (signupFormErrors(request, town, address, confirmation, recovering).isEmpty()) {
                try {
                    val prepared = if (recovering) SignupValidation.prepareSupplierRecovery(request) else SignupValidation.prepare(request)
                    if (!recovering || onRecoverSupplierSignup != null) {
                        sent = true
                        if (recovering) onRecoverSupplierSignup?.invoke(prepared) else onSignup(prepared)
                    }
                } catch (e: AuthFailure) { localError = e.message }
            }
        }
    }
    AuthPage("Sign Up", isDarkTheme, onToggleTheme, modifier, back) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AuthHeading(if (recovering) "Finish Supplier setup" else "Create your account", "Your details, business location and account security in one form.")
            SignupRoleDropdown(account, { account = it; sent = false; reviewed = false; localError = null; onClearError() }, enabled = !busy)
            errors[SignupField.ROLE]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (account == SignupRole.INSPECTOR) {
                AuthNotice("Inspector accounts are provided by the administrator. Sign in with your assigned credentials.")
            } else {
                SignupSection("Personal information")
                if (!recovering) AuthField(name, { name = it; onClearError() }, "Full name", enabled = !busy, errorMessage = errors[SignupField.NAME])
                AuthField(email, { email = it; onClearError() }, "Email", KeyboardType.Email, enabled = !busy, errorMessage = errors[SignupField.EMAIL])
                if (!recovering) {
                    AuthField(phone, { phone = it; onClearError() }, "Phone number", KeyboardType.Phone, enabled = !busy, errorMessage = errors[SignupField.PHONE])
                    AuthField(username, { username = it; onClearError() }, if (supplier) "Username (optional)" else "Username", enabled = !busy,
                        errorMessage = errors[SignupField.USERNAME], supportingText = "3 to 30 letters, digits or underscores, starting with a letter.")
                }
                if (supplier) {
                    SignupSection("Business and location")
                    AuthField(businessName, { businessName = it; onClearError() }, "Business name", enabled = !busy, errorMessage = errors[SignupField.BUSINESS_NAME])
                    SignupCountyDropdown(region, { region = it; onClearError() }, !busy, errors[SignupField.COUNTY])
                    AuthField(town, { town = it; onClearError() }, "Town or sub-county", enabled = !busy,
                        errorMessage = errors[SignupField.TOWN], supportingText = "For example Ruiru, Naivasha or Westlands.")
                    AuthField(address, { address = it; onClearError() }, "Physical business location", enabled = !busy,
                        errorMessage = errors[SignupField.ADDRESS], supportingText = "For example Shop 4, Market Road, near the bus stage. Include a building, road, market or landmark.")
                } else {
                    SignupSection("Location")
                    AuthField(region, { region = it; onClearError() }, "Region", enabled = !busy, errorMessage = errors[SignupField.COUNTY])
                }
                SignupSection("Account security")
                AuthField(password, { password = it; onClearError() }, "Password", isPassword = true, enabled = !busy,
                    errorMessage = errors[SignupField.PASSWORD], supportingText = if (recovering) "Use your existing account password." else "Use 8 to 72 characters.")
                AuthField(confirmation, { confirmation = it; onClearError() }, "Confirm password", isPassword = true, enabled = !busy,
                    imeAction = ImeAction.Done, onDone = submit, errorMessage = errors[SignupField.CONFIRM_PASSWORD], visibilityLabel = "confirm password")
            }
            if (recovering) AuthNotice("Sign in to finish your existing Supplier business profile. No new account will be registered.")
            localError?.let { KilivanaErrorMessage(it) }
            errorMessage?.let { KilivanaErrorMessage(it) }
            if (demoMode) AuthNotice("Account creation is unavailable in offline demo mode.")
        }
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AuthPrimaryButton(if (recovering) "Sign in and finish setup" else "Create Account", submit,
                enabled = !busy && !demoMode && account != null && account != SignupRole.INSPECTOR && (!recovering || onRecoverSupplierSignup != null), loading = isLoading)
            if (supplier && !recovering) TextButton(onClick = { recoveryMode = true; localError = null; reviewed = false }, enabled = !busy) {
                Text("Already registered? Finish Supplier setup")
            }
            TextButton(onSignIn, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) { Text("Already have an account? Sign In") }
        }
    }
}

@Composable private fun SignupSection(title: String) {
    Text(title, Modifier.padding(top = 12.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SignupCountyDropdown(value: String, select: (String) -> Unit, enabled: Boolean, error: String?) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded && enabled, { if (enabled) expanded = it }) {
        OutlinedTextField(value = KenyanCounties.normalize(value).orEmpty(), onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text("County/region") }, placeholder = { Text("Select a Kenyan county") }, isError = error != null,
            supportingText = error?.let { message -> ({ Text(message) }) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth())
        ExposedDropdownMenu(expanded && enabled, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            KenyanCounties.all.sorted().forEach { county -> DropdownMenuItem(text = { Text(county) }, onClick = { select(county); expanded = false }) }
        }
    }
}
