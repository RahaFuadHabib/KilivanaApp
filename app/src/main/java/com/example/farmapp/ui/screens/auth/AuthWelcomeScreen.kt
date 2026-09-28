package com.example.farmapp.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.farmapp.R

@Composable
internal fun AuthWelcomeScreen(
    isEntry: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onGetStarted: () -> Unit,
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    AuthPage("Kilivana", isDarkTheme, onToggleTheme, modifier,
        onBack = if (isEntry) null else onBack) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isEntry) {
                Image(painterResource(if (isDarkTheme) R.drawable.kilivana_logo_darkmode else R.drawable.kilivana_logo),
                    "Kilivana: Farm, Buy, Sell, Grow", contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(vertical = 20.dp).size(280.dp))
            } else AgriculturalVisual(Modifier.padding(top = 16.dp, bottom = 20.dp))
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isEntry) "Rooted in agriculture." else "Grow with Kilivana.",
                style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(if (isEntry) "Farm. Buy. Sell. Grow.\nYour agricultural community, connected."
                else "Connect with the people who grow,\nsupply and support every harvest.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
        Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isEntry) AuthPrimaryButton("Get Started", onGetStarted)
            else {
                AuthPrimaryButton("Sign Up", onSignUp)
                OutlinedButton(onSignIn, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Text("Sign In", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
