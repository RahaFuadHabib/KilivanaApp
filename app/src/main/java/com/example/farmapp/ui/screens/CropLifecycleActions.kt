package com.example.farmapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.farmapp.data.model.*

@Composable
internal fun CropLifecycleActions(product: FarmerProductResponse, userId: String?, role: String?,
    enabled: Boolean,
    onEdit: () -> Unit = {}, onOrders: () -> Unit = {}, orderCount: Int? = null,
    editEnabled: Boolean = enabled, ordersEnabled: Boolean = enabled,
    onAction: (FarmerProductAction) -> Unit) {
    var managing by remember(product.productId) { mutableStateOf(false) }
    var pending by remember(product.productId) { mutableStateOf<FarmerProductAction?>(null) }
    val actions = product.lifecycleActions(userId, role)
    val owner = role == "FARMER" && userId != null && product.sellerId == userId && product.status != "DELETED"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CropPrimaryActions(onEdit, onOrders, { managing = true }, editEnabled, ordersEnabled, enabled && owner)
        orderCount?.let { Text("$it linked orders", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            actions.filter { it == FarmerProductAction.UNLIST || it == FarmerProductAction.RESUBMIT }.forEach { action ->
                TextButton(onClick = { if (enabled) pending = action }, enabled = enabled) { Text(action.label) }
            }
        }
    }
    if (managing) AlertDialog(onDismissRequest = { managing = false }, containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Manage ${product.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ManagementOption("Archive product", Icons.Default.Archive,
                    "Move this product to Archived. It will no longer be actively listed, but its history will be preserved.",
                    enabled && FarmerProductAction.ARCHIVE in actions, false,
                    if (product.status == "ARCHIVED") "This product is already archived."
                    else if (FarmerProductAction.ARCHIVE !in actions) "Archiving is unavailable in the current product state." else null) {
                    managing = false; pending = FarmerProductAction.ARCHIVE
                }
                ManagementOption("Permanently delete", Icons.Default.DeleteForever,
                    "Remove this product from your management list. Existing order and transaction history will be preserved.",
                    enabled && FarmerProductAction.DELETE in actions, true,
                    when {
                        product.status != "ARCHIVED" -> "Archive this product before deleting it."
                        product.reservedQuantity?.signum() != 0 -> "Deletion requires confirmed zero reserved stock. Refresh after reservations are resolved."
                        else -> null
                    }) {
                    managing = false; pending = FarmerProductAction.DELETE
                }
            }
        },
        confirmButton = { TextButton(onClick = { managing = false }) { Text("Close") } })
    pending?.let { action ->
        val destructive = action == FarmerProductAction.DELETE
        val confirmLabel = when (action) {
            FarmerProductAction.ARCHIVE -> "Archive product"
            FarmerProductAction.DELETE -> "Permanently delete"
            else -> "Confirm ${action.label.lowercase()}"
        }
        val title = when (action) {
            FarmerProductAction.ARCHIVE -> "Archive ${product.name}?"
            FarmerProductAction.DELETE -> "Permanently delete ${product.name}?"
            else -> "${action.label}: ${product.name}?"
        }
        AlertDialog(onDismissRequest = { pending = null }, containerColor = MaterialTheme.colorScheme.surface,
            icon = { Icon(if (destructive) Icons.Default.DeleteForever else Icons.Default.Archive, null,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
            title = { Text(title) },
            text = { Text(when (action) {
                FarmerProductAction.UNLIST -> "Hide this crop from the marketplace? Product details, photos and existing orders are kept."
                FarmerProductAction.ARCHIVE -> "Archive this crop? It will remain in your management list and existing orders are kept."
                FarmerProductAction.RESUBMIT -> "Submit this crop for Admin review? It stays private until an Admin approves it."
                FarmerProductAction.DELETE -> "Delete this archived crop? The server removes its listing and photos while retaining historical orders and payments. This action cannot be undone here."
            }) },
            confirmButton = { TextButton(onClick = {
                if (pending == action && enabled && action in actions) {
                    pending = null
                    onAction(action)
                }
            }, enabled = enabled && action in actions,
                colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
                Text(confirmLabel)
            } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } })
    }
}

@Composable
internal fun CropPrimaryActions(onEdit: () -> Unit, onOrders: () -> Unit, onManage: () -> Unit,
    editEnabled: Boolean, ordersEnabled: Boolean, manageEnabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CropActionTile("Edit", Icons.Default.Edit, editEnabled, onEdit, Modifier.weight(1f))
        CropActionTile("Orders", Icons.Default.ShoppingBag, ordersEnabled, onOrders, Modifier.weight(1f))
        CropActionTile("Archive", Icons.Default.Archive, manageEnabled, onManage, Modifier.weight(1f))
    }
}

@Composable
private fun CropActionTile(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 80.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(contentColor = MaterialTheme.colorScheme.primary)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ManagementOption(title: String, icon: ImageVector, description: String, enabled: Boolean,
    destructive: Boolean, reason: String?, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, color.copy(alpha = if (enabled) 0.35f else 0.15f)),
        colors = CardDefaults.outlinedCardColors(containerColor = if (destructive)
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = color)
                Text(title, color = color, style = MaterialTheme.typography.titleSmall)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
