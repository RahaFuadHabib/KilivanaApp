package com.example.farmapp.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AwaitingVerificationScreen(onLogout: () -> Unit, loading: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            AgriculturalVisual(password = true)
            AuthHeading("Inspector verification pending", "Your Inspector account has been created successfully. An administrator must verify your account before you can access inspection tools.")
            Text("You can sign in again after your account has been verified.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            AuthPrimaryButton("Logout", onLogout, enabled = !loading, loading = loading)
        }
    }
}
