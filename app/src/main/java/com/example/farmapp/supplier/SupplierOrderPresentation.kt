package com.example.farmapp.supplier

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyer.buyerDate

internal fun LazyListScope.supplierOrders(state: SupplierUiState, load: (Boolean) -> Unit) {
    item {
        Text("Your orders", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Products supplied by your business", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state.ordersLoading && !state.ordersLoaded || !state.ordersLoaded && state.ordersError == null) item {
        SupplierSection("Loading your orders") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
    state.ordersError?.let { message -> item {
        SupplierSection("Couldn't load orders", message) {
            if (state.orders.isNotEmpty()) Text("Showing previously loaded orders. Refresh to check for changes.")
            TextButton(onClick = { load(true) }, enabled = !state.ordersLoading) { Text("Retry orders") }
        }
    } }
    if (state.ordersLoaded && state.orders.isEmpty() && state.ordersError == null) item {
        SupplierSection("No orders yet", "Orders containing your supplies will appear here.") {
            OutlinedButton(onClick = { load(true) }, enabled = !state.ordersLoading) { Text("Refresh orders") }
        }
    }
    items(state.orders, key = { "order-${it.id}" }) { order ->
        SupplierSection(order.code ?: "Order", buyerDate(order.createdAt)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(order.status?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Status unavailable",
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
            }
            order.lines.forEach { line ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(line.name ?: "Product", style = MaterialTheme.typography.titleMedium)
                    Text("${line.quantity}${line.unit?.let { " $it" }.orEmpty()} × KES ${line.unitPrice.toPlainString()}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("KES ${line.subtotal.toPlainString()}", fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider()
            }
            Text("Your items subtotal", style = MaterialTheme.typography.labelLarge)
            Text("KES ${order.supplierSubtotal.toPlainString()}", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Items only. Delivery charges and other sellers' items are excluded.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (state.ordersLoaded && !state.ordersLast && state.ordersError == null) item {
        OutlinedButton(onClick = { load(false) }, enabled = !state.ordersLoading, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.ordersLoading) "Loading more orders…" else "Load more orders")
        }
    }
}
