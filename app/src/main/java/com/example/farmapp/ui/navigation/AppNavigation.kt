package com.example.farmapp.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import com.example.farmapp.ui.screens.BuyerScreen
import com.example.farmapp.ui.screens.FarmerScreen
import com.example.farmapp.ui.screens.InspectorScreen
import com.example.farmapp.ui.screens.LoginScreen
import com.example.farmapp.ui.screens.SupplierScreen
import com.example.farmapp.ui.theme.FarmAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier
) {
    // Hosted Theme toggle state (defaults to false for LightColorScheme)
    var isDarkTheme by rememberSaveable { mutableStateOf(false) }

    // Active authentication state: null when logged out, or ("Farmer", "Buyer", "Supplier", "Inspector")
    var currentUserRole by rememberSaveable { mutableStateOf<String?>(null) }

    FarmAppTheme(darkTheme = isDarkTheme) {
        if (currentUserRole == null) {
            LoginScreen(
                onLoginSuccess = { role ->
                    currentUserRole = role
                },
                modifier = modifier
            )
        } else {
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
                            // Theme toggle: Sun icon when dark mode, Moon icon when light mode
                            IconButton(onClick = { isDarkTheme = !isDarkTheme }) {
                                if (isDarkTheme) {
                                    Icon(
                                        imageVector = Icons.Default.WbSunny,
                                        contentDescription = "Switch to Light Mode",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.NightlightRound,
                                        contentDescription = "Switch to Dark Mode",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            // Logout action icon back to LoginScreen
                            IconButton(onClick = { currentUserRole = null }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "Log Out",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            ) { innerPadding ->
                val screenModifier = Modifier.padding(innerPadding)
                when (currentUserRole) {
                    "Farmer" -> FarmerScreen(modifier = screenModifier)
                    "Buyer" -> BuyerScreen(modifier = screenModifier)
                    "Supplier" -> SupplierScreen(modifier = screenModifier)
                    "Inspector" -> InspectorScreen(modifier = screenModifier)
                    else -> FarmerScreen(modifier = screenModifier)
                }
            }
        }
    }
}
