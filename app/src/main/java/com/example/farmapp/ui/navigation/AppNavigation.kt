package com.example.farmapp.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.farmapp.network.AuthValidation
import com.example.farmapp.viewmodel.AuthViewModel
import com.example.farmapp.ui.components.ProfileModal
import com.example.farmapp.ui.screens.BuyerScreen
import com.example.farmapp.ui.screens.FarmerScreen
import com.example.farmapp.ui.screens.InspectorScreen
import com.example.farmapp.ui.screens.LoginScreen
import com.example.farmapp.ui.screens.SupplierScreen
import com.example.farmapp.ui.theme.FarmAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(LocalContext.current))
) {
    // Theme toggle state (defaults to light)
    var isDarkTheme by rememberSaveable { mutableStateOf(false) }

    // Active session state
    val session by authViewModel.session.collectAsStateWithLifecycle()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val currentUserAuth = session.auth

    // Modal state for role profile governance
    var showProfileModal by rememberSaveable(session.generation) { mutableStateOf(false) }

    FarmAppTheme(darkTheme = isDarkTheme) {
        if (authState.isInitializing) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (currentUserAuth == null) {
            LoginScreen(
                authViewModel = authViewModel,
                isDarkTheme = isDarkTheme,
                onToggleTheme = { isDarkTheme = !isDarkTheme },
                modifier = modifier
            )
        } else {
            val currentRole = currentUserAuth.role

            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Kilivana",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            // Top Bar Theme Switcher
                            IconButton(onClick = { isDarkTheme = !isDarkTheme }) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.WbSunny else Icons.Default.Bedtime,
                                    contentDescription = if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            // Account Profile Modal
                            IconButton(
                                onClick = { showProfileModal = true },
                                enabled = currentRole in AuthValidation.supportedRoles && !authState.isLoading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Account Profile",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            // Logout
                            IconButton(onClick = authViewModel::logout, enabled = !authState.isLoading) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "Log Out",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            ) { innerPadding ->
                val screenModifier = Modifier.padding(innerPadding)
                when (currentRole) {
                    "FARMER" -> FarmerScreen(modifier = screenModifier)
                    "BUYER" -> BuyerScreen(modifier = screenModifier)
                    "SUPPLIER" -> SupplierScreen(modifier = screenModifier)
                    "INSPECTOR" -> InspectorScreen(modifier = screenModifier)
                    else -> Box(screenModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "This account type is not supported in the Android app yet. Please sign out to use another account.",
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }

            if (showProfileModal && currentRole in AuthValidation.supportedRoles) {
                ProfileModal(
                    userRole = currentUserAuth.role,
                    userName = currentUserAuth.fullName,
                    // The auth contract does not contain a phone number; do not invent one.
                    userPhone = "Not provided by sign-in",
                    onDismiss = { showProfileModal = false }
                )
            }
        }
    }
}
