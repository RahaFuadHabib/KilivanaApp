package com.example.farmapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.data.model.AuthResponse
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.repository.FarmerProfileRepository
import com.example.farmapp.viewmodel.FarmerProfileViewModel
import com.example.farmapp.viewmodel.FarmerProfileState

@Composable
internal fun FarmerProfileScreen(auth: AuthResponse, errorMessage: String?, modifier: Modifier,
    isDarkTheme: Boolean, onToggleTheme: () -> Unit, onLogout: () -> Unit, isLoading: Boolean) {
    val model: FarmerProfileViewModel = viewModel(key = "farmer-profile-${auth.userId}",
        factory = FarmerProfileViewModel.factory(FarmerProfileRepository(ApiClient.farmApiService)))
    val state by model.state.collectAsStateWithLifecycle()
    FarmerProfileContent(auth, state, model::savePhone, model::refresh, errorMessage, modifier,
        isDarkTheme, onToggleTheme, onLogout, isLoading)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FarmerProfileContent(auth: AuthResponse, state: FarmerProfileState,
    onSavePhone: (String) -> Unit, onRefresh: () -> Unit, errorMessage: String?, modifier: Modifier,
    isDarkTheme: Boolean, onToggleTheme: () -> Unit, onLogout: () -> Unit, isLoading: Boolean) {
    val profile = state.profile
    val savedPhone = profile?.phoneNumber ?: auth.phoneNumber
    var phone by rememberSaveable(auth.userId) { mutableStateOf(savedPhone.orEmpty()) }
    var editingPhone by rememberSaveable(auth.userId) { mutableStateOf(false) }
    var pendingPhone by rememberSaveable(auth.userId) { mutableStateOf<String?>(null) }
    LaunchedEffect(savedPhone) { if (!editingPhone) phone = savedPhone.orEmpty() }
    LaunchedEffect(state.busy, state.failed, profile?.phoneNumber) {
        if (!state.busy && pendingPhone != null) {
            if (!state.failed && profile?.phoneNumber == pendingPhone) editingPhone = false
            pendingPhone = null
        }
    }
    val snackbars = remember { SnackbarHostState() }
    var previousFeedback by rememberSaveable(auth.userId) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.message, errorMessage) {
        val message = errorMessage ?: state.message
        if (message == null) previousFeedback = null
        else if (message != previousFeedback) {
            previousFeedback = message
            snackbars.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Short)
        }
    }
    var editProfile by remember { mutableStateOf(false) }
    if (editProfile) com.example.farmapp.profile.ProfileEditor { editProfile = false; onRefresh() }
    val colors = MaterialTheme.colorScheme
    // The Farmer shell owns toolbar, navigation and system insets; only the editor handles the IME.
    PullToRefreshBox(isRefreshing = state.busy, onRefresh = { if (!state.busy && !editingPhone && !editProfile) onRefresh() }, modifier = modifier.imePadding()) {
        Column(Modifier.fillMaxSize().testTag("farmer-profile-scroll").verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Your profile", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { editProfile = true }, enabled = !state.busy) { Text("Edit profile") }
            }
            ProfileIdentityHeader(profile?.fullName ?: auth.fullName, profile?.username ?: auth.username,
                profile?.email ?: auth.email, profile?.region ?: auth.region,
                profile?.verificationStatus ?: auth.verificationStatus)
            ProfileSectionLabel("ACCOUNT")
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerLow, contentColor = colors.onSurface) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProfileSettingIcon(Icons.Default.Phone)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Phone Number", style = MaterialTheme.typography.titleMedium)
                            Text(savedPhone?.takeIf { it.isNotBlank() } ?: "Not provided",
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                        if (!editingPhone) TextButton(onClick = { phone = savedPhone.orEmpty(); editingPhone = true },
                            enabled = !state.busy && profile != null) { Text("Edit") }
                    }
                    if (editingPhone) Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(phone, { phone = it }, label = { Text("Phone number") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), enabled = !state.busy,
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { phone = savedPhone.orEmpty(); editingPhone = false },
                                enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Cancel") }
                            Button(onClick = { pendingPhone = phone.trim(); onSavePhone(phone) },
                                enabled = !state.busy && profile != null && phone.trim() != profile.phoneNumber.orEmpty(),
                                modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.secondaryContainer, contentColor = colors.onSecondaryContainer)) {
                                Text(if (state.busy) "Saving…" else "Save phone")
                            }
                        }
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.outlineVariant)
                    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ProfileSettingIcon(Icons.Default.Email)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Email address", style = MaterialTheme.typography.titleMedium)
                            Text(profile?.email ?: auth.email, style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant)
                            Text("Read-only · Email changes are unavailable", style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant)
                        }
                        Icon(Icons.Default.Lock, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            ProfileSectionLabel("PREFERENCES")
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerLow, contentColor = colors.onSurface) {
                Column {
                    Row(Modifier.fillMaxWidth().toggleable(value = isDarkTheme, role = Role.Switch,
                        onValueChange = { onToggleTheme() }).semantics {
                            stateDescription = if (isDarkTheme) "Dark mode on" else "Dark mode off"
                        }.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProfileSettingIcon(if (isDarkTheme) Icons.Default.Bedtime else Icons.Default.WbSunny)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Appearance", style = MaterialTheme.typography.titleMedium)
                            Text("Dark Mode", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                        Switch(checked = isDarkTheme, onCheckedChange = null)
                    }
                }
            }
            OutlinedButton(onClick = onLogout, enabled = !isLoading, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Logout")
            }
            com.example.farmapp.profile.AboutKilivana()
            Spacer(Modifier.height(8.dp))
        }
        SnackbarHost(snackbars, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileIdentityHeader(fullName: String, username: String?, email: String, region: String?, verification: String?) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = CircleShape, color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
                Icon(Icons.Default.Person, "Profile avatar placeholder", Modifier.padding(16.dp).size(36.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fullName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                username?.takeIf { it.isNotBlank() }?.let { Text("@$it", style = MaterialTheme.typography.titleSmall) }
                Text(email, style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                region?.takeIf { it.isNotBlank() }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                verification?.takeIf { it.isNotBlank() }?.let { status ->
                    Surface(shape = CircleShape, color = colors.surface, contentColor = colors.onSurfaceVariant) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(if (status == "VERIFIED") Icons.Default.Verified else Icons.Default.Info, null, Modifier.size(14.dp))
                            Text(when (status) {
                                "VERIFIED" -> "Verified"
                                "PENDING" -> "Verification pending"
                                "UNVERIFIED" -> "Unverified"
                                else -> status
                            }, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSectionLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun ProfileSettingIcon(icon: ImageVector) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Icon(icon, null, modifier = Modifier.padding(10.dp).size(22.dp))
    }
}
