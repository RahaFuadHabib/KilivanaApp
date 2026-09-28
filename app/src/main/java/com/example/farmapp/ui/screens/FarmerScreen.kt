package com.example.farmapp.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.farmapp.data.model.Order
import com.example.farmapp.data.model.Product
import com.example.farmapp.data.model.SupplyItem
import com.example.farmapp.data.model.mockOrders
import com.example.farmapp.data.model.mockProducts
import com.example.farmapp.data.model.mockSupplyItems
import com.example.farmapp.ui.components.KilivanaEmptyState
import com.example.farmapp.ui.components.KilivanaStatusBadge
import com.example.farmapp.ui.theme.ForestDeep
import com.example.farmapp.ui.theme.FarmAppTheme
import com.example.farmapp.ui.theme.ForestGreen
import com.example.farmapp.ui.theme.HarvestAmber
import com.example.farmapp.ui.theme.KilivanaAccents
import com.example.farmapp.ui.theme.KilivanaFarmerShapes
import com.example.farmapp.ui.theme.KilivanaSpacing
import com.example.farmapp.ui.theme.LeafGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FarmerScreen(
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(1) } // Default to "My Crops" to showcase Behance listings
    val tabTitles = listOf("Incoming Orders", "My Crops", "Supplies Ordered")

    var farmerSearchQuery by remember { mutableStateOf("") }

    val orders = remember { mutableStateListOf<Order>().apply { addAll(mockOrders) } }
    val crops = remember {
        mutableStateListOf<Product>().apply {
            addAll(
                listOf(
                    mockProducts[0], // Hass Avocados - Approved
                    mockProducts[1], // French Beans - Pending
                    mockProducts[6], // Purple Tea Leaves - Flagged
                    mockProducts[3]  // Irish Potatoes - Approved
                )
            )
        }
    }
    val supplies = remember { mutableStateListOf<SupplyItem>().apply { addAll(mockSupplyItems) } }

    var showPostDialog by remember { mutableStateOf(false) }
    var activeTrackingOrder by remember { mutableStateOf<Order?>(null) }
    var selectedCropForView by remember { mutableStateOf<Product?>(null) }
    var showAuditsDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPostDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Create Crop", style = MaterialTheme.typography.labelLarge) },
                shape = KilivanaFarmerShapes.pill,
                containerColor = HarvestAmber,
                contentColor = ForestDeep
            )
        }
    ) { innerPadding ->
        // One scrolling surface keeps the overview from squeezing the list on small phones.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = KilivanaSpacing.content, end = KilivanaSpacing.content,
                top = innerPadding.calculateTopPadding() + KilivanaSpacing.small,
                bottom = innerPadding.calculateBottomPadding() + 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.section)
        ) {
            item(key = "greeting") {
                Column(verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.content)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Good morning", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("David Karanja", style = MaterialTheme.typography.headlineSmall)
                    }
                    OutlinedTextField(
                        value = farmerSearchQuery,
                        onValueChange = { farmerSearchQuery = it },
                        placeholder = { Text("Search your crops, orders, supplies…", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = KilivanaFarmerShapes.pill,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item(key = "overview") { FarmerOverview(crops.size, orders.size, supplies.size) }
            item(key = "quick_access") {
                Column(verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.medium)) {
                    Text("Your farm, at a glance", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(KilivanaSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.small),
                        maxItemsInEachRow = 4
                    ) {
                        FarmerShortcut("My Crops", Icons.Default.Spa) { selectedTabIndex = 1 }
                        FarmerShortcut("Orders", Icons.Default.LocalShipping) { selectedTabIndex = 0 }
                        FarmerShortcut("Supplies", Icons.Default.Agriculture) { selectedTabIndex = 2 }
                        FarmerShortcut("Audits", Icons.AutoMirrored.Filled.FactCheck) { showAuditsDialog = true }
                    }
                    Surface(shape = KilivanaFarmerShapes.inset,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                        FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.heightIn(min = KilivanaSpacing.touchTarget), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.AutoMirrored.Filled.FactCheck, null, tint = MaterialTheme.colorScheme.primary)
                                Text("${crops.count { it.inspectionStatus == "Pending" }} pending inspection",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            TextButton(onClick = { selectedTabIndex = 1; showAuditsDialog = true }) { Text("Review") }
                        }
                    }
                }
            }
            item(key = "section_selector") {
                Column(verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.medium)) {
                    Text(tabTitles[selectedTabIndex], style = MaterialTheme.typography.titleLarge)
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {}
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            val selected = selectedTabIndex == index
                            Tab(selected = selected, onClick = { selectedTabIndex = index },
                                modifier = Modifier.padding(end = 8.dp).clip(KilivanaFarmerShapes.pill)
                                    .background(if (selected) ForestGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                selectedContentColor = Color.White,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = { Text(title, style = MaterialTheme.typography.labelLarge) })
                        }
                    }
                }
            }
            when (selectedTabIndex) {
                0 -> {
                    if (orders.isEmpty()) item { KilivanaEmptyState("No incoming orders", "Your incoming orders will appear here.") }
                    items(orders, key = { "order_${it.id}" }) { order ->
                        FarmerOrderCard(order, onTrackOrder = { activeTrackingOrder = order })
                    }
                }
                1 -> {
                    if (crops.isEmpty()) item { KilivanaEmptyState("No crop listings", "Your crop listings will appear here.") }
                    items(crops, key = { "crop_${it.id}" }) { crop ->
                        FarmerCropCard(crop, onViewCrop = { selectedCropForView = crop })
                    }
                }
                2 -> {
                    if (supplies.isEmpty()) item { KilivanaEmptyState("No supplies ordered", "Your supply orders will appear here.") }
                    items(supplies, key = { "supply_${it.id}" }) { FarmerSupplyCard(it) }
                }
            }
        }
    }

    // Post Produce Modal Dialog
    if (showPostDialog) {
        var cropName by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Vegetables") }
        var price by remember { mutableStateOf("") }
        var quantity by remember { mutableStateOf("") }
        var county by remember { mutableStateOf("Nakuru") }

        AlertDialog(
            shape = KilivanaFarmerShapes.overview,
            onDismissRequest = { showPostDialog = false },
            title = { Text("Post New Produce") },
            text = {
                Column(modifier = Modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.medium)) {
                    OutlinedTextField(
                        value = cropName,
                        onValueChange = { cropName = it },
                        label = { Text("Crop / Produce Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.medium)
                    ) {
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("Price (KES/kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it },
                            label = { Text("Qty (kg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = county,
                        onValueChange = { county = it },
                        label = { Text("County / Farm Location") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (cropName.isNotBlank()) {
                            val newProduct = Product(
                                id = (crops.size + 100).toLong(),
                                name = cropName,
                                category = category,
                                pricePerKg = price.toDoubleOrNull() ?: 100.0,
                                quantityKg = quantity.toDoubleOrNull() ?: 500.0,
                                farmerName = "David Karanja",
                                county = county,
                                isVerified = true,
                                inspectionStatus = "Pending"
                            )
                            crops.add(0, newProduct)
                            showPostDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text("Publish to Market")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // View Crop Audit Details Modal
    selectedCropForView?.let { crop ->
        Dialog(onDismissRequest = { selectedCropForView = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = KilivanaFarmerShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.imePadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(LeafGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Spa, contentDescription = null, tint = LeafGreen, modifier = Modifier.size(20.dp))
                            }
                            Text(text = crop.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { selectedCropForView = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Origin: ${crop.county} • Grade: Certified A", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Harvest Batch: ${"%,.0f".format(crop.quantityKg)} kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = "Target Price: KES ${"%,.0f".format(crop.pricePerKg)} / kg", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when (crop.inspectionStatus) {
                                    "Approved" -> KilivanaAccents.success.container
                                    "Pending" -> KilivanaAccents.warning.container
                                    else -> KilivanaAccents.error.container
                                }
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Audit Status: ${crop.inspectionStatus}",
                                fontWeight = FontWeight.Bold,
                                color = when (crop.inspectionStatus) {
                                    "Approved" -> KilivanaAccents.success.content
                                    "Pending" -> KilivanaAccents.warning.content
                                    else -> KilivanaAccents.error.content
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (crop.inspectionStatus) {
                                    "Approved" -> "Verified compliant with statutory organic metrics. Green Badge awarded by KEPHIS field officer."
                                    "Pending" -> "Scheduled for pre-dispatch inspection by assigned field inspector Grace Wambui."
                                    else -> "Notice issued for pesticide buffer zone remediation. Re-audit requested."
                                },
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { selectedCropForView = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        shape = KilivanaFarmerShapes.inset
                    ) {
                        Text("Close Details")
                    }
                }
            }
        }
    }

    // Audits Governance Overview Dialog
    if (showAuditsDialog) {
        AlertDialog(
            shape = KilivanaFarmerShapes.overview,
            onDismissRequest = { showAuditsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FactCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Field Audit & Compliance Registry", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.medium)) {
                    Text(
                        text = "Assigned Inspector: Grace Wambui (Gov #802)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Plot Location: Parcel #42, Subukia Ridge, Nakuru County",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Statutory soil testing and buffer irrigation compliance passed. All approved produce carries the Kilivana Green Badge.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAuditsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text("Understood")
                }
            }
        )
    }

    // Order Tracking & Delivery Handshake Modal
    activeTrackingOrder?.let { order ->
        OrderTrackingDialog(
            order = order,
            isReceiver = false,
            onDismiss = { activeTrackingOrder = null },
            onOrderUpdated = { updatedOrder ->
                val index = orders.indexOfFirst { it.id == updatedOrder.id }
                if (index != -1) {
                    orders[index] = updatedOrder
                }
                activeTrackingOrder = updatedOrder
            }
        )
    }
}

// Farmer-only presentation. Domain models, status values, and callbacks are unchanged.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FarmerOverview(cropCount: Int, orderCount: Int, supplyCount: Int) {
    Surface(shape = KilivanaFarmerShapes.overview, color = ForestGreen, contentColor = Color.White,
        shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Box {
            // Intentional botanical motif, not a stand-in for a farm photograph or map.
            Icon(Icons.Default.Spa, contentDescription = null,
                tint = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp).size(168.dp))
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Agriculture, null, modifier = Modifier.size(20.dp))
                    Text("FARM OVERVIEW", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Rooted in growth.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("Your produce, orders and supplies.\nTogether in one place.",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FarmerOverviewMetric("Crop listings", cropCount, Modifier.weight(1f))
                    FarmerOverviewMetric("Incoming orders", orderCount, Modifier.weight(1f))
                    FarmerOverviewMetric("Supply entries", supplyCount, Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FarmerOverviewMetric(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = KilivanaFarmerShapes.inset,
        color = Color.White.copy(alpha = 0.12f), contentColor = Color.White) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FarmerShortcut(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = KilivanaFarmerShapes.inset,
        color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.widthIn(min = 72.dp).padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Icon(icon, null, modifier = Modifier.padding(12.dp).size(24.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FarmerCropCard(crop: Product, onViewCrop: () -> Unit) {
    val statusColors = when (crop.inspectionStatus) {
        "Pending" -> KilivanaAccents.warning
        "Flagged" -> KilivanaAccents.error
        else -> KilivanaAccents.success
    }
    Card(shape = KilivanaFarmerShapes.card, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(12.dp)) {
            // Honest agricultural placeholder: no invented image URL or crop-record changes.
            Box(Modifier.fillMaxWidth().clip(KilivanaFarmerShapes.inset)
                .background(MaterialTheme.colorScheme.primaryContainer)) {
                Icon(Icons.Default.Spa, null, tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp).size(104.dp))
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.Spa, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(crop.category, style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (crop.isVerified) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                                Text("Verified", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
            Column(Modifier.padding(8.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(crop.name, style = MaterialTheme.typography.titleLarge)
                    Text(crop.county, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("KES ${"%,.0f".format(crop.pricePerKg)} / kg", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text("${"%,.0f".format(crop.quantityKg)} kg available", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.heightIn(min = KilivanaSpacing.touchTarget), contentAlignment = Alignment.CenterStart) {
                        KilivanaStatusBadge(crop.inspectionStatus, statusColors.container, statusColors.content)
                    }
                    FilledTonalButton(onClick = onViewCrop, shape = KilivanaFarmerShapes.pill,
                        modifier = Modifier.heightIn(min = KilivanaSpacing.touchTarget)) {
                        Text("View crop")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FarmerOrderCard(order: Order, onTrackOrder: () -> Unit) {
    val statusColors = when (order.orderStatus) {
        "DELIVERED" -> KilivanaAccents.success
        "OUT_FOR_DELIVERY" -> KilivanaAccents.warning
        else -> KilivanaAccents.information
    }
    Card(shape = KilivanaFarmerShapes.card, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Order #${order.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KilivanaStatusBadge(order.orderStatus, statusColors.container, statusColors.content)
            }
            Text(order.productName, style = MaterialTheme.typography.titleLarge)
            Text("Buyer: ${order.buyerName}", style = MaterialTheme.typography.bodyMedium)
            Text("Logistics: ${order.driverName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${"%.0f".format(order.quantityKg)} kg", style = MaterialTheme.typography.bodyMedium)
                Text("KES ${"%,.0f".format(order.totalPrice)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            FilledTonalButton(onClick = onTrackOrder, shape = KilivanaFarmerShapes.pill,
                modifier = Modifier.heightIn(min = KilivanaSpacing.touchTarget)) {
                Icon(Icons.Default.LocalShipping, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Track & Delivery Handshake")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FarmerSupplyCard(item: SupplyItem) {
    val isPaid = item.paymentStatus.equals("PAID", ignoreCase = true)
    val statusColors = if (isPaid) KilivanaAccents.success else KilivanaAccents.warning
    Card(shape = KilivanaFarmerShapes.card, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Agriculture, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(item.itemName, style = MaterialTheme.typography.titleLarge)
            Text("Supplier: ${item.supplierName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            KilivanaStatusBadge(item.paymentStatus, statusColors.container, statusColors.content)
            Text("Stock in dispatch: ${item.stockAvailable}", style = MaterialTheme.typography.bodyMedium)
            Text("KES ${"%,.0f".format(item.unitPrice)} / unit", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

// Retain the existing standalone tab entry points, using the same Farmer cards.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyCropsTab(crops: List<Product>, onViewCrop: (Product) -> Unit = {}) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (crops.isEmpty()) item { KilivanaEmptyState("No crop listings", "Your crop listings will appear here.") }
        items(crops, key = { it.id }) { crop -> FarmerCropCard(crop) { onViewCrop(crop) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IncomingOrdersTab(orders: List<Order>, onTrackOrder: (Order) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (orders.isEmpty()) item { KilivanaEmptyState("No incoming orders", "Your incoming orders will appear here.") }
        items(orders, key = { it.id }) { order -> FarmerOrderCard(order) { onTrackOrder(order) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuppliesOrderedTab(supplies: List<SupplyItem>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (supplies.isEmpty()) item { KilivanaEmptyState("No supplies ordered", "Your supply orders will appear here.") }
        items(supplies, key = { it.id }) { FarmerSupplyCard(it) }
    }
}

@Preview(name = "Farmer · narrow", widthDp = 320, heightDp = 800, showBackground = true)
@Preview(name = "Farmer · dark", widthDp = 412, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Farmer · large text", widthDp = 360, heightDp = 900, fontScale = 1.5f, showBackground = true)
@Composable
private fun FarmerScreenPreview() {
    FarmAppTheme { FarmerScreen() }
}
