// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.model.Order
import com.example.farmapp.buyers.model.OrderStatus
import com.example.farmapp.buyers.ui.theme.*

@Composable
fun AccountScreen(
    loggedIn: Boolean,
    name: String?,
    email: String?,
    phone: String?,
    orders: List<Order>,
    login: () -> Unit,
    logout: () -> Unit,
    createAccount: () -> Unit,
    openOrders: () -> Unit,
    openSavedItems: () -> Unit,
    openAddresses: () -> Unit,
    openProfile: ()->Unit = {}
) {
    LazyColumn(
        Modifier.fillMaxSize().background(NeutralBackground),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Account", style = MaterialTheme.typography.headlineMedium, color = TextPrimary) }
        if(loggedIn) item { TextButton(onClick=openProfile){Text("Edit profile")} }
        if (!loggedIn) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = Green600) {
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(Modifier.size(56.dp), shape = CircleShape, color = NeutralSurface.copy(alpha = .18f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = NeutralSurface, modifier = Modifier.size(30.dp))
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("Welcome to Kilivana", style = MaterialTheme.typography.titleLarge, color = NeutralSurface)
                            Text("Sign in to manage your shopping", style = MaterialTheme.typography.bodyMedium, color = Green50)
                        }
                    }
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = NeutralSurface,
                    shadowElevation = 1.dp
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Your account helps you", style = MaterialTheme.typography.titleMedium)
                        AccountBenefit(Icons.AutoMirrored.Filled.ReceiptLong, "Track orders", "See delivery progress and purchase history")
                        AccountBenefit(Icons.Default.LocationOn, "Save delivery details", "Make your next checkout quicker")
                        AccountBenefit(Icons.Default.FavoriteBorder, "Keep favourites", "Find the produce you love again")
                        Spacer(Modifier.height(2.dp))
                        Button(onClick = login, Modifier.fillMaxWidth()) { Text("Sign in") }
                        OutlinedButton(onClick = createAccount, Modifier.fillMaxWidth()) { Text("Create account") }
                        TextButton(onClick = openSavedItems, modifier = Modifier.fillMaxWidth()) { Text("View saved items") }
                        Text("You can continue browsing without an account.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
        } else {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = NeutralSurface, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(56.dp), shape = CircleShape, color = Green50) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(name?.trim()?.split(" ")?.mapNotNull(String::firstOrNull)?.take(2)?.joinToString("")?.uppercase().orEmpty().ifBlank { "?" }, style = MaterialTheme.typography.titleLarge, color = Green700)
                            }
                        }
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(name?.takeIf(String::isNotBlank) ?: "Your account", style = MaterialTheme.typography.titleLarge)
                            email?.let { Text(it, color = TextSecondary) }
                            phone?.let { Text(it, color = TextSecondary) }
                        }
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = NeutralSurface, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        AccountStat("Orders", orders.size.toString())
                        AccountStat("Pending", orders.count { it.status == OrderStatus.Pending }.toString())
                        AccountStat("Delivered", orders.count { it.status == OrderStatus.Delivered }.toString())
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = NeutralSurface, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        listOf("My orders", "Saved items", "Addresses", "Payment methods", "Notifications", "Help and support", "About").forEach { label ->
                            val actionable = label == "My orders" || label == "Saved items" || label == "Addresses"
                            TextButton(
                                onClick = { when (label) { "My orders" -> openOrders(); "Saved items" -> openSavedItems(); "Addresses" -> openAddresses() } },
                                enabled = actionable,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                            ) {
                                Text(label, Modifier.weight(1f), color = if (actionable) TextPrimary else TextSecondary)
                                if (actionable) Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                                else Text("Soon", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            item { OutlinedButton(onClick = logout, Modifier.fillMaxWidth()) { Text("Log out") } }
        }
    }
}

@Composable
private fun AccountBenefit(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = Green50) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = Green600) }
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun AccountStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Green700)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
