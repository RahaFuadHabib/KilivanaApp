package com.example.farmapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.farmapp.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CropDetailScreen(product: FarmerProductResponse, modifier: Modifier, busy: Boolean,
    message: String?, orderCount: Int?, onBack: () -> Unit, onOrders: () -> Unit,
    onSave: (CropEdit) -> Unit, onRemove: () -> Unit,
    categories: CategoryCatalog = CategoryCatalog(), onRetryCategories: () -> Unit = {}, saveState: CropSaveState = CropSaveState(), refreshing: Boolean = false,
    refreshError: String? = null, onRefresh: () -> Unit = {}, demoMode: Boolean = false,
    canonicalLifecycle: Boolean = false, authenticatedUserId: String? = null, authenticatedRole: String? = null,
    lifecycleBlocked: Boolean = false, onLifecycle: (FarmerProductAction) -> Unit = {}) {
    val detail = product.detailPresentation()
    val scroll = rememberScrollState()
    val pullState = rememberPullToRefreshState()
    var editing by rememberSaveable(product.productId) { mutableStateOf(false) }
    var title by rememberSaveable(product.productId, product.name) { mutableStateOf(product.name) }
    var quantity by rememberSaveable(product.productId, product.availableQuantity.toPlainString()) { mutableStateOf(product.availableQuantity.toPlainString()) }
    var price by rememberSaveable(product.productId, product.unitPrice.toPlainString()) { mutableStateOf(product.unitPrice.toPlainString()) }
    var unit by rememberSaveable(product.productId, product.unit) { mutableStateOf(product.unit) }
    var description by rememberSaveable(product.productId, product.description) { mutableStateOf(product.description.orEmpty()) }
    var categoryId by rememberSaveable(product.productId, product.categoryId) { mutableStateOf(product.categoryId) }
    var categoryName by rememberSaveable(product.productId, product.categoryId, product.categoryName) { mutableStateOf(product.categoryName) }
    var validation by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(saveState.completedSaves) {
        if (saveState.completedSaves > 0) { editing = false; validation = null; scroll.scrollTo(0) }
    }
    Box(modifier.imePadding().pullToRefresh(state = pullState, isRefreshing = refreshing,
        enabled = !editing && !busy && !refreshing, onRefresh = onRefresh)) {
        Column(Modifier.fillMaxSize()) {
            if (!editing) saveState.message?.let {
                Text(it, color = if (saveState.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to My Crops"); Text("My Crops") }
                detail.imageUrl?.let { ProductImage(it, detail.name) }
                Text(detail.name, style = MaterialTheme.typography.headlineMedium)
                Text("Category: ${categories.name(product.categoryId, product.categoryName).ifBlank { if (product.categoryId == null) "Not assigned" else "Name unavailable" }}")
                Text(detail.quantity)
                Text(detail.price)
                Text(detail.description)
                Text("Status: ${detail.status}")
                if (canonicalLifecycle) CropLifecycleActions(product, authenticatedUserId, authenticatedRole,
                    enabled = !busy && !refreshing && !lifecycleBlocked && !editing,
                    onAction = onLifecycle, onEdit = { editing = !editing }, onOrders = onOrders, orderCount = orderCount,
                    editEnabled = !busy && !refreshing, ordersEnabled = !busy)
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { editing = !editing }, enabled = !busy && !refreshing) { Icon(Icons.Default.Edit, "Edit crop"); Text("Edit") }
                    TextButton(onClick = onOrders, enabled = !busy) { Icon(Icons.Default.ShoppingBag, "View crop orders"); Text("Orders ${orderCount ?: "\u2014"}") }
                    if (!canonicalLifecycle) TextButton(onClick = onRemove, enabled = !busy && !refreshing && product.status == "ACTIVE") { Icon(Icons.Default.Delete, "Unlist crop"); Text("Unlist") }
                }
                if (!editing) {
                    TextButton(onClick = onRefresh, enabled = !busy && !refreshing) {
                        Text(if (refreshError != null) "Retry refresh" else "Refresh crop")
                    }
                    refreshError?.let {
                        Text("Could not refresh this crop. Previously loaded details are still shown. $it",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                if (editing) {
                    OutlinedTextField(title, { title = it }, label = { Text("Crop name") }, enabled = !busy)
                    CropCategoryDropdown(categories, categoryId, categoryName, !busy, onRetryCategories) {
                        categoryId = it.categoryId; categoryName = it.name; validation = null
                    }
                    OutlinedTextField(quantity, { quantity = it }, label = { Text("Available quantity") }, enabled = !busy)
                    Text("Unit: $unit")
                    Text(if (canonicalLifecycle) "Stock edits do not change approval status. Use the lifecycle actions to hide or submit a crop for Admin review."
                        else if (demoMode) "Zero quantity makes this demo listing inactive. Changes stay local."
                        else "Setting quantity to zero makes this listing inactive. Only the backend can reactivate it.", style = MaterialTheme.typography.bodySmall)
                    FlowRowUnits(unit, !busy) { unit = it }
                    OutlinedTextField(price, { price = it }, label = { Text("Unit price (KES)") }, enabled = !busy)
                    OutlinedTextField(description, { description = it }, label = { Text("Description") }, enabled = !busy)
                }
            }
        if (editing) Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                saveState.message?.let { Text(it, color = if (saveState.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                if (saveState.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(onClick = {
                    if (!busy && !saveState.saving) {
                        val draft = CropEdit(title, quantity, unit, price, description, categoryId)
                        try {
                            if (!saveState.refreshRequired) draft.request(product)
                            validation = null; onSave(draft)
                        } catch (e: IllegalArgumentException) { validation = e.message }
                    }
                }, enabled = !busy && !saveState.saving && (saveState.refreshRequired || categories.contains(categoryId)),
                    modifier = Modifier.fillMaxWidth()) { Text(saveState.buttonLabel) }
            }
        }
        }
    if (!editing) PullToRefreshDefaults.Indicator(state = pullState, isRefreshing = refreshing,
        modifier = Modifier.align(Alignment.TopCenter))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowUnits(unit: String, enabled: Boolean, onUnit: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CropUnit.entries.forEach { option -> FilterChip(selected = unit == option.label,
            onClick = { onUnit(option.label) }, label = { Text(option.label) }, enabled = enabled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FarmerOrdersScreen(orders: List<FarmerOrderResponse>?, error: String?, loading: Boolean,
    productName: String?, modifier: Modifier, onBack: (() -> Unit)?, onRefresh: () -> Unit,
    onOpenOrder: ((FarmerOrderResponse) -> Unit)? = null, onTracking: (() -> Unit)? = null) {
    val pullState = rememberPullToRefreshState()
    Box(modifier.pullToRefresh(state = pullState, isRefreshing = loading,
        enabled = !loading, onRefresh = onRefresh)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            onBack?.let { TextButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to crop"); Text("Crop details") } }
            Text(productName?.let { "Orders for $it" } ?: "Orders", style = MaterialTheme.typography.headlineSmall)
            onTracking?.let { TextButton(onClick = it) { Text("Tracking & map availability") } }
            TextButton(onClick = onRefresh, enabled = !loading) { Text(if (error != null) "Retry" else "Refresh orders") }
            if (loading && orders == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(if (orders != null) "Could not refresh orders. Previously loaded orders are still shown. $it" else it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (orders == null && !loading && error == null) Text("Orders unavailable. Refresh to try again.")
            if (orders?.isEmpty() == true) Text("No orders found.")
            orders?.forEach { order ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Order ${order.orderId}", style = MaterialTheme.typography.titleSmall)
                        Text(order.status)
                        Text(order.createdAt)
                        order.items.forEach { Text("${it.productTitle}: ${it.quantity.toPlainString()}") }
                        Text("${order.currency} ${order.totalAmount.toPlainString()}")
                        onOpenOrder?.let { open -> TextButton(onClick = { open(order) }) { Text("View details") } }
                    }
                }
            }
        }
    PullToRefreshDefaults.Indicator(state = pullState, isRefreshing = loading,
        modifier = Modifier.align(Alignment.TopCenter))
    }
}
