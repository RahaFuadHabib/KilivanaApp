// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.ui.theme.*

@Composable
fun AuthenticationRequiredScreen(
    feature: String,
    signIn: () -> Unit,
    createAccount: () -> Unit,
    goBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(NeutralBackground).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(Modifier.size(76.dp), shape = CircleShape, color = Green50) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LockOpen, contentDescription = null, tint = Green600, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (feature == "order history") "Your orders, all in one place" else "You're almost there",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (feature == "checkout") {
                "Sign in or create an account to complete your purchase and keep track of delivery updates."
            } else {
                "Sign in or create an account to access $feature, follow deliveries, and review past purchases. You can keep browsing as a guest."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = NeutralSurface,
            shadowElevation = 1.dp
        ) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = signIn, Modifier.fillMaxWidth()) { Text("Sign in") }
                OutlinedButton(onClick = createAccount, Modifier.fillMaxWidth()) { Text("Create account") }
                TextButton(onClick = goBack) {
                    Text(if (feature == "checkout") "Return to cart" else "Continue shopping", color = Green600)
                }
            }
        }
    }
}
