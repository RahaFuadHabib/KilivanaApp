package com.example.farmapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun FarmerSuppliesScreen(modifier: Modifier, onTracking: () -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Supplies", style = MaterialTheme.typography.headlineSmall)
        Text("Supplier product browsing is coming soon. There are no supplier products available to browse in this app yet.")
        Text("Supply delivery tracking is unavailable: the current API provides no supplier shipment locations.")
        OutlinedButton(onClick = onTracking) { Text("Tracking & map availability") }
    }
}
