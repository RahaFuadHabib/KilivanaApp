package com.example.farmapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.farmapp.data.model.*
import com.example.farmapp.demo.*
import com.example.farmapp.ui.navigation.FarmerDestination
import com.example.farmapp.ui.theme.ForestGreen
import kotlinx.coroutines.launch
import java.util.Locale

/** Separate from FarmerShell: construction and all actions are local, with no AuthResponse or API. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DemoFarmerShell(isDarkTheme: Boolean, onToggleTheme: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val repository = rememberSaveable(saver = Saver<DemoRepository, String>(
        save = { it.savedSnapshot() }, restore = { DemoRepository.restore(it) })) { DemoRepository() }
    val snapshot by repository.state.collectAsState()
    var destination by rememberSaveable { mutableStateOf(FarmerDestination.HOME) }
    var selectedCropId by rememberSaveable { mutableStateOf<String?>(null) }
    var cropOrdersOpen by rememberSaveable { mutableStateOf(false) }
    var selectedOrderId by rememberSaveable { mutableStateOf<String?>(null) }
    var removalId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var saveState by remember { mutableStateOf(CropSaveState()) }
    val tabs = rememberSaveableStateHolder()
    val categories = remember { CategoryCatalog(DemoData.categories, loaded = true) }
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun feedback(message: String) { scope.launch { snackbars.showSnackbar(message) } }
    val visibleCrops = snapshot.crops.filter { it.status != "HIDDEN" }
    val selectedCrop = snapshot.crops.firstOrNull { it.productId == selectedCropId }
    val matchingOrders = snapshot.orders.filter { order -> order.orderId.contains(query.trim(), true) ||
        order.items.any { it.productTitle.contains(query.trim(), true) } }
    fun select(tab: FarmerDestination) {
        destination = tab; selectedCropId = null; cropOrdersOpen = false; saveState = CropSaveState()
    }
    BackHandler(selectedCropId != null || destination != FarmerDestination.HOME) {
        if (cropOrdersOpen) cropOrdersOpen = false
        else if (selectedCropId != null) selectedCropId = null
        else select(FarmerDestination.HOME)
    }
    if (searchOpen) AlertDialog(onDismissRequest = { searchOpen = false }, title = { Text("Search your demo farm") },
        text = { OutlinedTextField(query, { query = it }, label = { Text("Crop name or order number") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { searchOpen = false }) { Text("Search") } },
        dismissButton = { TextButton(onClick = { query = ""; searchOpen = false }) { Text("Clear") } })
    removalId?.let { id -> AlertDialog(onDismissRequest = { removalId = null }, title = { Text("Unlist demo crop?") },
        text = { Text("Only this demo session will change. Its sample orders will be kept.") },
        confirmButton = { TextButton(onClick = {
            repository.unlist(id); removalId = null; selectedCropId = null; feedback("Demo crop unlisted locally.")
        }) { Text("Unlist locally") } }, dismissButton = { TextButton(onClick = { removalId = null }) { Text("Cancel") } }) }
    snapshot.orders.firstOrNull { it.orderId == selectedOrderId }?.let { order ->
        AlertDialog(onDismissRequest = { selectedOrderId = null }, title = { Text("Order ${order.orderId}") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sample order · ${order.status}")
                Text("Buyer: ${DemoData.buyers[order.orderId].orEmpty()}")
                Text("Placed: ${order.createdAt}")
                order.items.forEach { item ->
                    Text("${item.productTitle} · ${item.quantity.toPlainString()} ${snapshot.crops.firstOrNull { it.productId == item.productId }?.unit.orEmpty()}")
                    Text("KES ${item.unitPrice.toPlainString()} per unit · KES ${item.lineTotal.toPlainString()}")
                }
                Text("Total: KES ${order.totalAmount.toPlainString()}", style = MaterialTheme.typography.titleMedium)
                Text("Confirmation is simulated locally. No payment, delivery booking or stock reservation is performed.",
                    style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { if (order.status == "PLACED") TextButton(onClick = {
                repository.confirmOrder(order.orderId); feedback("Demo order confirmed locally.")
            }) { Text("Simulate confirmation") } },
            dismissButton = { TextButton(onClick = { selectedOrderId = null }) { Text("Close") } })
    }
    Scaffold(modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Column {
            Text("Kilivana", style = MaterialTheme.typography.titleLarge)
            Text("Demo Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        } }, actions = {
            IconButton(onClick = { searchOpen = true; select(FarmerDestination.HOME) }) { Icon(Icons.Default.Search, "Search demo data") }
            IconButton(onClick = onToggleTheme) { Icon(if (isDarkTheme) Icons.Default.WbSunny else Icons.Default.Bedtime, "Toggle theme") }
            IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Exit Demo Mode") }
        }) },
        bottomBar = { FarmerBottomBar(destination, ::select) },
        snackbarHost = { SnackbarHost(snackbars) }
    ) { padding ->
        tabs.SaveableStateProvider(destination.name) {
            val content = Modifier.fillMaxSize().padding(padding)
            when (destination) {
                FarmerDestination.HOME -> if (selectedCrop != null) {
                    if (cropOrdersOpen) FarmerOrdersScreen(snapshot.orders.filter { it.items.any { item -> item.productId == selectedCrop.productId } },
                        null, false, selectedCrop.name, content, { cropOrdersOpen = false }, { feedback("Local demo orders are up to date.") },
                        onOpenOrder = { selectedOrderId = it.orderId })
                    else CropDetailScreen(selectedCrop, content, false, "Demo edits and unlisting are local simulations.",
                        snapshot.orders.count { it.items.any { item -> item.productId == selectedCrop.productId } },
                        { selectedCropId = null }, { cropOrdersOpen = true },
                        onSave = { draft ->
                            try {
                                repository.edit(selectedCrop.productId, draft.request(selectedCrop))
                                saveState = CropSaveState(message = "Demo crop updated locally.", completedSaves = saveState.completedSaves + 1)
                            } catch (e: IllegalArgumentException) { saveState = saveState.copy(message = e.message, error = true) }
                        }, onRemove = { removalId = selectedCrop.productId }, categories = categories, saveState = saveState,
                        onRefresh = { feedback("Local demo crop is up to date.") }, demoMode = true)
                } else DemoHome(visibleCrops, matchingOrders, snapshot.activity, query, content,
                    onCrop = { selectedCropId = it.productId; saveState = CropSaveState() },
                    onOrder = { selectedOrderId = it.orderId }, onOrders = { select(FarmerDestination.ORDERS) },
                    onSupplies = { select(FarmerDestination.SUPPLIES) }, orderCount = snapshot.orders.size)
                FarmerDestination.ORDERS -> FarmerOrdersScreen(snapshot.orders, null, false, null, content, null,
                    { feedback("Local demo orders are up to date.") }, onOpenOrder = { selectedOrderId = it.orderId })
                FarmerDestination.ADD -> DemoAddCrop(content, categories) { request ->
                    repository.add(request); query = ""; select(FarmerDestination.HOME); feedback("Demo crop saved locally. No upload was made.")
                }
                FarmerDestination.MAP -> DemoFarmLocations(content)
                FarmerDestination.SUPPLIES -> FarmerFeatureShell("Supplies · Coming soon", Icons.Default.Agriculture,
                    "Supplier product browsing is not implemented yet. This preview contains no operational inventory or purchasing flow.", content)
                FarmerDestination.PROFILE -> FarmerAccount(FarmerAccountDetails(DemoData.profile.name, DemoData.profile.email,
                    phoneNumber = DemoData.profile.phone, region = DemoData.profile.county), null, content, isDarkTheme, onToggleTheme,
                    onExit, false, demoMode = true)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DemoHome(crops: List<FarmerProductResponse>, orders: List<FarmerOrderResponse>, activity: List<String>, query: String,
    modifier: Modifier, onCrop: (FarmerProductResponse) -> Unit, onOrder: (FarmerOrderResponse) -> Unit,
    onOrders: () -> Unit, onSupplies: () -> Unit, orderCount: Int) {
    val matchingCrops = crops.filter { it.name.contains(query.trim(), true) || it.description.orEmpty().contains(query.trim(), true) }
    LazyColumn(modifier.testTag("demo_home"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Good morning", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(DemoData.profile.name, style = MaterialTheme.typography.headlineSmall)
            Text("${DemoData.profile.farm}\n${DemoData.profile.county}", style = MaterialTheme.typography.bodyMedium)
        } }
        item { FarmerOverview(crops.size, orderCount, 0) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onOrders) { Text("Incoming Orders") }
            OutlinedButton(onClick = onSupplies) { Text("Supplies") }
        } }
        item { Text("Recent activity · Sample / local", style = MaterialTheme.typography.titleMedium) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            activity.take(3).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        } } }
        if (query.isNotBlank()) item { Text("Search results for “${query.trim()}”") }
        item { Text("My Crops", style = MaterialTheme.typography.titleLarge) }
        if (matchingCrops.isEmpty()) item { Text("No crops match this search.") }
        items(matchingCrops, key = { it.productId }) { crop ->
            Card(onClick = { onCrop(crop) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.Spa, null, Modifier.padding(12.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column { Text(crop.name, style = MaterialTheme.typography.titleLarge); Text(crop.categoryName.orEmpty()) }
                    }
                    Text("${crop.availableQuantity.toPlainString()} ${crop.unit} available")
                    Text("KES ${crop.unitPrice.toPlainString()} / ${crop.unit}", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium)
                    Text(crop.description.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Text("View / edit demo crop →", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item { Text("Recent orders", style = MaterialTheme.typography.titleLarge) }
        if (orders.isEmpty()) item { Text("No orders match this search.") }
        items(orders.take(4), key = { it.orderId }) { order ->
            Card(onClick = { onOrder(order) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(order.orderId, style = MaterialTheme.typography.titleMedium)
                    Text("${order.items.first().productTitle} · ${order.status}")
                    Text("KES ${order.totalAmount.toPlainString()} · View details")
                }
            }
        }
        item { Text("Sample data only. Demo changes reset after exiting Demo Mode.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun DemoAddCrop(modifier: Modifier, categories: CategoryCatalog, onSave: (CreateProductRequest) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var unit by rememberSaveable { mutableStateOf(CropUnit.KG) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Add Crop", style = MaterialTheme.typography.headlineSmall)
        Text("Save a sample listing locally. Photos and uploads are not required in this demonstration.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name, { name = it; error = null }, label = { Text("Crop name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        CropCategoryDropdown(categories, categoryId, null, true, {}) { categoryId = it.categoryId; error = null }
        OutlinedTextField(quantity, { quantity = it; error = null }, label = { Text("Quantity") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        CropUnitDropdown(unit) { unit = it }
        OutlinedTextField(price, { price = it; error = null }, label = { Text(unit.priceLabel) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it; error = null }, label = { Text("Description") }, minLines = 3,
            modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            try {
                val amount = quantity.toBigDecimalOrNull() ?: throw IllegalArgumentException("Enter a valid quantity.")
                val cost = price.toBigDecimalOrNull() ?: throw IllegalArgumentException("Enter a valid price in KES.")
                onSave(CreateProductRequest(name, description, unit.label, cost, amount, categoryId))
                name = ""; quantity = ""; price = ""; description = ""; categoryId = null; error = null
            } catch (e: IllegalArgumentException) { error = e.message }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save demo crop") }
    }
}

@Composable
private fun DemoFarmLocations(modifier: Modifier) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Farm & market locations", style = MaterialTheme.typography.headlineSmall)
        Text("Offline location preview · Illustrative layout, not a navigable map", color = MaterialTheme.colorScheme.primary)
        Text("Mapbox remains available in the real Farmer Map screen. Online tiles need connectivity; no offline tile pack is configured.")
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                val points = listOf(Offset(size.width * .18f, size.height * .73f), Offset(size.width * .82f, size.height * .25f), Offset(size.width * .42f, size.height * .63f))
                drawLine(ForestGreen.copy(alpha = .3f), points[0], points[2], 8f)
                drawLine(ForestGreen.copy(alpha = .3f), points[2], points[1], 8f)
                points.forEachIndexed { index, point -> drawCircle(ForestGreen, if (index == selected) 16f else 9f, point) }
            }
        }
        DemoData.locations.forEachIndexed { index, location ->
            OutlinedButton(onClick = { selected = index }, modifier = Modifier.fillMaxWidth()) { Text(location.name) }
        }
        val location = DemoData.locations[selected]
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(location.name, style = MaterialTheme.typography.titleMedium)
            Text(location.description)
            Text(String.format(Locale.ROOT, "Latitude %.3f · Longitude %.3f", location.latitude, location.longitude))
        } }
    }
}
