package com.example.farmapp.ui.screens

import com.example.farmapp.ui.components.KilivanaHeaderMetric
import com.example.farmapp.ui.components.KilivanaEmptyState
import com.example.farmapp.ui.components.KilivanaStatusBadge
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.farmapp.ui.theme.KilivanaAccents
import com.example.farmapp.ui.theme.KilivanaSpacing
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmapp.data.model.SupplyItem
import com.example.farmapp.data.model.mockSupplyItems
import com.example.farmapp.ui.theme.EarthBrown
import com.example.farmapp.ui.theme.ForestGreen
import com.example.farmapp.ui.theme.HarvestAmber
import com.example.farmapp.ui.theme.LeafGreen
import com.example.farmapp.ui.components.FoodcourtHeaderBlock
import com.example.farmapp.ui.theme.SupplierHeroGradient

data class RequisitionOrder(
    val id: String,
    val farmerName: String,
    val farmParcel: String,
    val itemName: String,
    val quantity: Int,
    val totalCost: Double,
    val paymentStatus: String
)

@Composable
@Deprecated("Legacy mock screen. Production Supplier navigation must use SupplierShell.", level = DeprecationLevel.ERROR)
fun SupplierScreen(
    modifier: Modifier = Modifier
) {
    val inventory = remember { mutableStateListOf<SupplyItem>().apply { addAll(mockSupplyItems) } }
    var selectedTab by remember { mutableIntStateOf(0) }

    val requisitionLedger = remember {
        mutableStateListOf(
            RequisitionOrder("REQ-801", "David Karanja", "Parcel #42, Nakuru", "Certified DAP Planting Fertilizer", 4, 14000.0, "PAID"),
            RequisitionOrder("REQ-802", "Sarah Ochieng", "Parcel #19, Kiambu", "Hybrid F1 Tomato Seedlings", 15, 18750.0, "PENDING"),
            RequisitionOrder("REQ-803", "Kiprono Biwott", "Parcel #88, Uasin Gishu", "Organic Compost Bulk 50kg", 10, 8500.0, "PAID"),
            RequisitionOrder("REQ-804", "Jane Muthoni", "Parcel #05, Murang'a", "Copper Hydroxide Bio-Fungicide", 3, 5850.0, "PENDING")
        )
    }

    var itemToEdit by remember { mutableStateOf<SupplyItem?>(null) }
    var itemToDelete by remember { mutableStateOf<SupplyItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var supplierSearchQuery by remember { mutableStateOf("") }

    val filteredInventory = remember(inventory.toList(), supplierSearchQuery) {
        if (supplierSearchQuery.isBlank()) inventory
        else inventory.filter {
            it.itemName.contains(supplierSearchQuery, ignoreCase = true) ||
            it.supplierName.contains(supplierSearchQuery, ignoreCase = true)
        }
    }

    val filteredRequisitions = remember(requisitionLedger.toList(), supplierSearchQuery) {
        if (supplierSearchQuery.isBlank()) requisitionLedger
        else requisitionLedger.filter {
            it.farmerName.contains(supplierSearchQuery, ignoreCase = true) ||
            it.itemName.contains(supplierSearchQuery, ignoreCase = true) ||
            it.farmParcel.contains(supplierSearchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Item") },
                text = { Text("Add Supply Item", fontWeight = FontWeight.Bold) },
                containerColor = EarthBrown,
                contentColor = Color.White
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Foodcourt Shared Header Block: Greeting, Rounded Search Bar, Promo Card
            FoodcourtHeaderBlock(
                greetingName = "Joseph",
                searchPlaceholder = "Search agro-inputs, seeds, fertilizer...",
                searchQuery = supplierSearchQuery,
                onSearchQueryChange = { supplierSearchQuery = it },
                promoHighlight = "2 orders awaiting fulfillment",
                promoSubtitle = "Urgent: Dispatch required for Nakuru agro-hub",
                promoActionText = "Fulfill",
                onPromoAction = { selectedTab = 1 }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // -------------------------------------------------------------------
            // 1. Rich Brown Gradient Header with KEPHIS Verification Badge
            // -------------------------------------------------------------------
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EarthBrown)
                        .padding(KilivanaSpacing.content)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Avatar Circle
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .border(2.5.dp, HarvestAmber, CircleShape)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Inventory,
                                        contentDescription = "Supplier Hub",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Kilivana Agro-Hub",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Nairobi Depot • Industrial Area Bay 4",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                                    )
                                }
                            }

                            // KEPHIS Verification Badge Pill
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .background(HarvestAmber.copy(alpha = 0.22f))
                                    .border(1.dp, HarvestAmber, MaterialTheme.shapes.large)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified Dealer",
                                        tint = HarvestAmber,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "KEPHIS Verified",
                                        color = Color.White,
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // KPI Metrics Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SupplierMetricPill(
                                title = "Catalog Items",
                                value = "${inventory.size}",
                                modifier = Modifier.weight(1f)
                            )
                            SupplierMetricPill(
                                title = "Stock Units",
                                value = "${inventory.sumOf { it.stockAvailable }}",
                                modifier = Modifier.weight(1f)
                            )
                            val pendingCount = requisitionLedger.count { it.paymentStatus == "PENDING" }
                            SupplierMetricPill(
                                title = "Pending Orders",
                                value = "$pendingCount",
                                valueColor = if (pendingCount > 0) HarvestAmber else Color.White,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // -------------------------------------------------------------------
            // 2. TabRow Switcher (Catalog vs Requisition Ledger)
            // -------------------------------------------------------------------
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
                contentColor = EarthBrown,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = KilivanaAccents.supplierContent
                        )
                    }
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    selectedContentColor = EarthBrown,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = { Text("Agro-Inputs Catalog (${inventory.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    selectedContentColor = EarthBrown,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = { Text("Requisitions Ledger (${requisitionLedger.size})", fontWeight = FontWeight.Bold) }
                )
            }

            // Tab Content
            if (selectedTab == 0) {
                // Agro-Inputs Catalog Cards with Stock Counts
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (filteredInventory.isEmpty()) {
                        item {
                            KilivanaEmptyState(title = "No supplies found", message = "No agro-inputs matching \"$supplierSearchQuery\"")
                        }
                    } else {
                        items(filteredInventory, key = { it.id }) { item ->
                            AgroInputCard(
                                item = item,
                                onEdit = { itemToEdit = item },
                                onDelete = { itemToDelete = item },
                                onIncrement = {
                                    val index = inventory.indexOfFirst { it.id == item.id }
                                    if (index != -1) {
                                        val current = inventory[index]
                                        inventory[index] = current.copy(stockAvailable = current.stockAvailable + 1)
                                    }
                                },
                                onDecrement = {
                                    val index = inventory.indexOfFirst { it.id == item.id }
                                    if (index != -1) {
                                        val current = inventory[index]
                                        if (current.stockAvailable > 0) {
                                            inventory[index] = current.copy(stockAvailable = current.stockAvailable - 1)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // Requisition Orders Ledger
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (filteredRequisitions.isEmpty()) {
                        item {
                            KilivanaEmptyState(title = "No requisitions found", message = "No requisition orders matching \"$supplierSearchQuery\"")
                        }
                    } else {
                        items(filteredRequisitions, key = { it.id }) { order ->
                            RequisitionOrderCard(
                                order = order,
                                onToggleStatus = {
                                    val index = requisitionLedger.indexOfFirst { it.id == order.id }
                                    if (index != -1) {
                                        val current = requisitionLedger[index]
                                        val nextStatus = if (current.paymentStatus == "PAID") "PENDING" else "PAID"
                                        requisitionLedger[index] = current.copy(paymentStatus = nextStatus)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Item Dialog
    itemToEdit?.let { item ->
        var editName by remember { mutableStateOf(item.itemName) }
        var editPrice by remember { mutableStateOf(item.unitPrice.toString()) }
        var editStock by remember { mutableStateOf(item.stockAvailable.toString()) }

        AlertDialog(
            onDismissRequest = { itemToEdit = null },
            title = { Text("Edit Agro-Input Details") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.medium)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Item Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPrice,
                        onValueChange = { editPrice = it },
                        label = { Text("Unit Price (KES)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editStock,
                        onValueChange = { editStock = it },
                        label = { Text("Stock Quantity") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val index = inventory.indexOfFirst { it.id == item.id }
                        if (index != -1) {
                            inventory[index] = item.copy(
                                itemName = editName,
                                unitPrice = editPrice.toDoubleOrNull() ?: item.unitPrice,
                                stockAvailable = editStock.toIntOrNull() ?: item.stockAvailable
                            )
                        }
                        itemToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EarthBrown),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Remove Input from Catalog") },
            text = { Text("Are you sure you want to delete '${item.itemName}'? This input will no longer appear for farmers.") },
            confirmButton = {
                Button(
                    onClick = {
                        inventory.removeAll { it.id == item.id }
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Supply Dialog
    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var newSupplier by remember { mutableStateOf("Kilivana Supplies Hub") }
        var newPrice by remember { mutableStateOf("") }
        var newStock by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Agro-Input to Inventory") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.medium)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Input / Product Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSupplier,
                        onValueChange = { newSupplier = it },
                        label = { Text("Brand / Supplier Depot") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPrice,
                        onValueChange = { newPrice = it },
                        label = { Text("Price per Bag / Unit (KES)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newStock,
                        onValueChange = { newStock = it },
                        label = { Text("Initial Stock Quantity") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            val newItem = SupplyItem(
                                id = (inventory.size + 100).toLong(),
                                itemName = newName,
                                supplierName = newSupplier,
                                unitPrice = newPrice.toDoubleOrNull() ?: 1500.0,
                                stockAvailable = newStock.toIntOrNull() ?: 50,
                                paymentStatus = "PAID"
                            )
                            inventory.add(0, newItem)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EarthBrown),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Add Input")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Supplier Metric Pill Composable
// ---------------------------------------------------------------------------
@Composable
private fun SupplierMetricPill(
    title: String,
    value: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    KilivanaHeaderMetric(title = title, value = value, valueColor = valueColor, modifier = modifier)
}

// ---------------------------------------------------------------------------
// Agro-Inputs Catalog Card (Behance Rounded 18.dp with Stock Count Controls)
// ---------------------------------------------------------------------------
@Composable
fun AgroInputCard(
    item: SupplyItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.itemName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Source: ${item.supplierName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(KilivanaSpacing.touchTarget)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = KilivanaAccents.supplierContent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(KilivanaSpacing.touchTarget)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = KilivanaAccents.error.content,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stock Counts & Quantity Adjustment Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock Availability Tag
                val isLowStock = item.stockAvailable < 15
                KilivanaStatusBadge(
                    label = if (isLowStock) "Low Stock: ${item.stockAvailable} units" else "In Stock: ${item.stockAvailable} units",
                    containerColor = if (isLowStock) KilivanaAccents.error.container else KilivanaAccents.success.container,
                    contentColor = if (isLowStock) KilivanaAccents.error.content else KilivanaAccents.success.content,
                    modifier = Modifier.weight(1f).padding(end = KilivanaSpacing.small)
                )

                // Increment / Decrement Stock Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(KilivanaSpacing.touchTarget)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onDecrement() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrement", modifier = Modifier.size(16.dp))
                    }

                    Text(
                        text = "${item.stockAvailable}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(KilivanaSpacing.touchTarget)
                            .clip(CircleShape)
                            .background(EarthBrown)
                            .clickable { onIncrement() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increment", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Price Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Price per Unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "KES ${"%,.0f".format(item.unitPrice)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = KilivanaAccents.supplierContent
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Requisition Order Card (Ledger Item)
// ---------------------------------------------------------------------------
@Composable
fun RequisitionOrderCard(
    order: RequisitionOrder,
    onToggleStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.id,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val isPaid = order.paymentStatus == "PAID"
                KilivanaStatusBadge(
                    label = order.paymentStatus,
                    containerColor = if (isPaid) KilivanaAccents.success.container else KilivanaAccents.warning.container,
                    contentColor = if (isPaid) KilivanaAccents.success.content else KilivanaAccents.warning.content,
                    modifier = Modifier.heightIn(min = KilivanaSpacing.touchTarget).clickable { onToggleStatus() }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = order.itemName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Requested by: ${order.farmerName} (${order.farmParcel})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quantity: ${order.quantity} units",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Total: KES ${"%,.0f".format(order.totalCost)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = KilivanaAccents.supplierContent
                )
            }
        }
    }
}
