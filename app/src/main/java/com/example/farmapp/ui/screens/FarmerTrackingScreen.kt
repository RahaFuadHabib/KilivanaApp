package com.example.farmapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.farmapp.ui.theme.ForestGreen
import com.example.farmapp.ui.theme.LeafGreen

private val TrackGreen = Color(0xFF00834A)
private val TrackGreenLight = Color(0xFFE8F5EE)
private val TrackAmber = Color(0xFFFFF8E1)
private val TrackAmberText = Color(0xFFE65100)
private val TrackInk = Color(0xFF101827)
private val TrackMuted = Color(0xFF526074)
private val TrackPage = Color(0xFFF0F3EE)
private val TrackSurface = Color(0xFFFFFFFF)
private val TrackBorder = Color(0xFFDCE3E0)

@Composable
internal fun FarmerTrackingScreen(modifier: Modifier, onBack: () -> Unit) {
    var mapOpen by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(TrackPage)) {
        // Header
        Box(Modifier.fillMaxWidth().background(ForestGreen)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Delivery Tracking", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Live shipment positions", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
                Box(
                    Modifier.padding(end = 12.dp).size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.LocalShipping, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status notice card
            Surface(shape = RoundedCornerShape(16.dp), color = TrackAmber, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(TrackAmberText.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Info, null, tint = TrackAmberText, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Live tracking unavailable", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TrackAmberText)
                        Text(
                            "Real-time shipment positions are not available in the current API version. " +
                            "You can open the map below to view your device location.",
                            style = MaterialTheme.typography.bodySmall, color = TrackInk
                        )
                    }
                }
            }

            // Tracking steps placeholder
            Surface(shape = RoundedCornerShape(16.dp), color = TrackSurface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text("Shipment timeline", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TrackInk)
                    Spacer(Modifier.height(14.dp))
                    listOf(
                        Triple(Icons.Default.CheckCircle, "Order placed", TrackGreen),
                        Triple(Icons.Default.CheckCircle, "Payment confirmed", TrackGreen),
                        Triple(Icons.Default.RadioButtonUnchecked, "Awaiting dispatch", TrackMuted),
                        Triple(Icons.Default.RadioButtonUnchecked, "Out for delivery", TrackMuted),
                        Triple(Icons.Default.RadioButtonUnchecked, "Delivered", TrackMuted)
                    ).forEachIndexed { index, (icon, label, tint) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                                if (index < 4) Box(Modifier.width(2.dp).height(28.dp).background(TrackBorder))
                            }
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (tint == TrackGreen) TrackInk else TrackMuted,
                                fontWeight = if (tint == TrackGreen) FontWeight.Medium else FontWeight.Normal)
                        }
                    }
                }
            }

            // Map toggle card
            Surface(shape = RoundedCornerShape(16.dp), color = TrackSurface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(TrackGreenLight), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Map, null, tint = TrackGreen, modifier = Modifier.size(20.dp))
                        }
                        Text("Farm location map", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TrackInk)
                    }
                    Text("View your device location on the map. Online tiles require connectivity.",
                        style = MaterialTheme.typography.bodySmall, color = TrackMuted)
                    if (!mapOpen) {
                        Button(
                            onClick = { mapOpen = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TrackGreen)
                        ) {
                            Icon(Icons.Default.Map, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open location map", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { mapOpen = false },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Close map")
                        }
                    }
                }
            }

            if (mapOpen) {
                Surface(shape = RoundedCornerShape(16.dp), color = TrackSurface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    FarmerMapScreen(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp)))
                }
            }
        }
    }
}
