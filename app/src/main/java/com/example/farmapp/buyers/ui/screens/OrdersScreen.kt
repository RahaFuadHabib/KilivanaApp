// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.model.Order
import com.example.farmapp.buyers.model.OrderStatus
import com.example.farmapp.buyers.model.formatKes
import com.example.farmapp.buyers.ui.theme.*

@Composable
fun OrdersScreen(orders: List<Order>, open: (Order) -> Unit, shop: () -> Unit, loading: Boolean = false, error: String? = null, extraAction: @Composable (Order)->Unit = {}) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Active", "Delivered", "Cancelled")
    val shown = when (selectedFilter) {
        "Active" -> orders.filter { it.status in listOf(OrderStatus.Pending, OrderStatus.Confirmed, OrderStatus.OutForDelivery) }
        "Delivered" -> orders.filter { it.status == OrderStatus.Delivered }
        "Cancelled" -> orders.filter { it.status == OrderStatus.Cancelled }
        else -> orders
    }
    Column(Modifier.fillMaxSize().background(NeutralBackground).padding(top = 12.dp)) {
        Text("My orders", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.headlineMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                FilterChip(selected = selectedFilter == filter, onClick = { selectedFilter = filter }, label = { Text(filter) })
            }
        }
        if (loading && orders.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (error != null && orders.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, color = ErrorRed)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = shop) { Text("Browse products") }
                }
            }
        } else if (orders.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(22.dp), color = NeutralSurface, shadowElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(Modifier.size(68.dp), shape = RoundedCornerShape(22.dp), color = Green50) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Inventory2, null, tint = Green600, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Your orders will live here", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text("After placing an order, you’ll be able to follow deliveries and revisit past purchases.", color = TextSecondary)
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = shop) { Text("Browse produce") }
                    }
                }
            }
        } else if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No ${selectedFilter.lowercase()} orders")
                    TextButton(onClick = { selectedFilter = "All" }) { Text("Show all orders") }
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown, key = { it.id }) { order ->
                    Card(
                        Modifier.fillMaxWidth().clickable { open(order) },
                        border = BorderStroke(1.dp, Green100),
                        colors = CardDefaults.cardColors(containerColor = NeutralSurface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(order.code, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                OrderStatusBadge(order.status)
                            }
                            Text(order.date, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider(color = NeutralDivider)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${order.items.sumOf { it.quantity }} items", color = TextSecondary)
                                Text(formatKes(order.total), style = MaterialTheme.typography.titleSmall)
                            }
                            Text("View order  ›", color = Green600, style = MaterialTheme.typography.labelLarge)
                            extraAction(order)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderStatusBadge(status: OrderStatus) {
    val label = when (status) {
        OrderStatus.Pending -> "Pending"
        OrderStatus.Confirmed -> "Confirmed"
        OrderStatus.OutForDelivery -> "On the way"
        OrderStatus.Delivered -> "Delivered"
        OrderStatus.Cancelled -> "Cancelled"
        OrderStatus.Disputed -> "Disputed"
    }
    val tint = if (status == OrderStatus.Cancelled) ErrorRed else Green700
    val bg = if (status == OrderStatus.Cancelled) ErrorSurface else Green50
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = tint, style = MaterialTheme.typography.labelMedium)
    }
}


