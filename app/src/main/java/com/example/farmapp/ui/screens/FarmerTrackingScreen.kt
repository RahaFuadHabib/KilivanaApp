package com.example.farmapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun FarmerTrackingScreen(modifier: Modifier, onBack: () -> Unit) {
    var mapOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Tracking", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
        Text("Live order and supply tracking is unavailable in the current API. No shipment positions are shown.",
            modifier = Modifier.padding(24.dp))
        if (mapOpen) {
            Text("Map shows your device location only. Online tiles require connectivity; offline maps are not configured.",
                modifier = Modifier.padding(horizontal = 24.dp))
            FarmerMapScreen(Modifier.weight(1f).fillMaxWidth())
        } else OutlinedButton(onClick = { mapOpen = true }, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Open location map")
        }
    }
}
