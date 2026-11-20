// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.model.*
import com.example.farmapp.buyers.ui.theme.*

private val OdGreen = Color(0xFF00834A)
private val OdGreenLight = Color(0xFFE8F5EE)
private val OdInk = Color(0xFF101827)
private val OdMuted = Color(0xFF526074)
private val OdPage = Color(0xFFF0F3EE)
private val OdSurface = Color(0xFFFFFFFF)
private val OdBorder = Color(0xFFDCE3E0)
private val OdHeaderGreen = Color(0xFF00644B)

@Composable
fun OrderDetailScreen(
    order: Order,
    reorder: () -> Unit,
    payment: @Composable () -> Unit = {},
    back: () -> Unit = {}
) {
    Column(Modifier.fillMaxSize().background(OdPage)) {
        // Green header
        Box(Modifier.fillMaxWidth().background(OdHeaderGreen)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Order Details", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(order.code, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Order summary header card
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = OdSurface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(order.code, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OdInk)
                                Text(order.date, style = MaterialTheme.typography.bodySmall, color = OdMuted)
                            }
                            OrderStatusChip(order.status)
                        }
                        HorizontalDivider(color = OdBorder)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            OdMetric("Items", "${order.items.sumOf { it.quantity }}")
                            OdMetric("Subtotal", formatKes(order.subtotal))
                            OdMetric("Delivery", formatKes(order.deliveryFee))
                        }
                    }
                }
            }

            // Items section
            item {
                OdSection(title = "Order Items", icon = Icons.Default.Inventory2) {
                    order.items.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = OdBorder, modifier = Modifier.padding(vertical = 4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(OdGreenLight),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Spa, null, tint = OdGreen, modifier = Modifier.size(22.dp)) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.product.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = OdInk)
                                Text("${item.quantity} × ${formatKes(item.product.pricePerUnit)} / ${item.product.unit}",
                                    style = MaterialTheme.typography.bodySmall, color = OdMuted)
                            }
                            Text(
                                formatKes(item.lineSubtotal ?: item.product.pricePerUnit.multiply(item.quantity.toBigDecimal())),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = OdInk
                            )
                        }
                    }
                }
            }

            // Delivery address
            item {
                OdSection(title = "Delivery Address", icon = Icons.Default.LocationOn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Home, null, tint = OdMuted, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                        Text(order.deliveryAddress.ifBlank { "Delivery details not available" },
                            style = MaterialTheme.typography.bodyMedium, color = OdMuted)
                    }
                }
            }

            // Order total
            item {
                OdSection(title = "Order Summary", icon = Icons.Default.Receipt) {
                    OdSummaryRow("Items subtotal", formatKes(order.subtotal))
                    OdSummaryRow("Delivery fee", formatKes(order.deliveryFee))
                    HorizontalDivider(color = OdBorder, modifier = Modifier.padding(vertical = 4.dp))
                    OdSummaryRow("Total", formatKes(order.total), isTotal = true)
                }
            }

            // Timeline
            if (order.timeline.isNotEmpty()) item {
                OdSection(title = "Order Timeline", icon = Icons.Default.Timeline) {
                    order.timeline.forEachIndexed { index, event ->
                        val isLast = index == order.timeline.lastIndex
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(OdGreenLight), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CheckCircle, null, tint = OdGreen, modifier = Modifier.size(14.dp))
                                }
                                if (!isLast) Box(Modifier.width(2.dp).height(28.dp).background(OdBorder))
                            }
                            Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    event.status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = OdInk
                                )
                                if (event.createdAt.isNotBlank()) Text(event.createdAt, style = MaterialTheme.typography.labelSmall, color = OdMuted)
                                event.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = OdMuted) }
                            }
                        }
                    }
                }
            }

            // Payment slot
            item { payment() }

            // Reorder button
            item {
                Button(
                    onClick = reorder,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OdGreen)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reorder", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun OrderStatusChip(status: OrderStatus) {
    val (bg, fg) = when (status) {
        OrderStatus.Delivered -> Color(0xFFE8F5E9) to Color(0xFF1B5E20)
        OrderStatus.Cancelled -> Color(0xFFFFEBEE) to Color(0xFFB71C1C)
        OrderStatus.OutForDelivery -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
        OrderStatus.Confirmed -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
        OrderStatus.Disputed -> Color(0xFFFFF3E0) to Color(0xFFBF360C)
        OrderStatus.Pending -> Color(0xFFFFF8E1) to Color(0xFFE65100)
    }
    val label = when (status) {
        OrderStatus.Pending -> "Pending"
        OrderStatus.Confirmed -> "Confirmed"
        OrderStatus.OutForDelivery -> "On the way"
        OrderStatus.Delivered -> "Delivered"
        OrderStatus.Cancelled -> "Cancelled"
        OrderStatus.Disputed -> "Disputed"
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OdSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = OdSurface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(4.dp, 18.dp).background(OdGreen, RoundedCornerShape(2.dp)))
                Icon(icon, null, tint = OdGreen, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OdInk)
            }
            content()
        }
    }
}

@Composable
private fun OdMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OdInk)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OdMuted)
    }
}

@Composable
private fun OdSummaryRow(label: String, value: String, isTotal: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label,
            style = if (isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal, color = OdInk)
        Text(value,
            style = if (isTotal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
            color = if (isTotal) OdGreen else OdInk)
    }
}
