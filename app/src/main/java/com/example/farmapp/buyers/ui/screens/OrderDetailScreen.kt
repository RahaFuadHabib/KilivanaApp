// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.model.*
import com.example.farmapp.buyers.ui.theme.*

@Composable fun OrderDetailScreen(order: Order, reorder: () -> Unit, payment: @Composable ()->Unit = {}, back: ()->Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScrollCompat().padding(16.dp)) {
        IconButton(onClick=back){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}
        Text(order.code, style = MaterialTheme.typography.headlineMedium)
        Text(order.date, color = TextSecondary)
        order.timeline.forEach { event ->
            Text(event.status.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            if (!event.note.isNullOrBlank()) Text(event.note, color = TextSecondary)
            if (event.createdAt.isNotBlank()) Text(event.createdAt, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        if (order.timeline.isEmpty()) Text("Status: ${order.status}", modifier = Modifier.padding(vertical = 12.dp))
        order.items.forEach { Text("${it.quantity}  x  ${it.product.name}   ${formatKes(it.lineSubtotal ?: it.product.pricePerUnit.multiply(it.quantity.toBigDecimal()))}") }
        Text("Items subtotal: ${formatKes(order.subtotal)}", modifier = Modifier.padding(top = 12.dp))
        Text("Delivery: ${formatKes(order.deliveryFee)}")
        Text("Total: ${formatKes(order.total)}", style = MaterialTheme.typography.titleMedium)
        Text("Delivery address: ${order.deliveryAddress}", modifier = Modifier.padding(vertical = 8.dp))
        Button(reorder) { Text("Reorder") }
        payment()
    }
}

