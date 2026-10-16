package com.example.farmapp.supplier

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.data.model.canonical.CanonicalCategoryDto
import com.example.farmapp.data.model.canonical.CanonicalProductStatus
import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.payments.PaymentStatusCard
import com.example.farmapp.ui.screens.ProductImage
import com.example.farmapp.ui.theme.*
import kotlinx.coroutines.*
import java.math.BigDecimal

private enum class SupplierTab(val title: String) { DASHBOARD("Dashboard"), SUPPLIES("Supplies"), ORDERS("Orders"), PROFILE("Profile") }

@Composable
fun SupplierShell(session: SessionSnapshot, onLogout: () -> Unit, isDarkTheme: Boolean,
    onToggleTheme: () -> Unit, signingOut: Boolean, modifier: Modifier = Modifier) {
    val model: SupplierViewModel = viewModel(key = "supplier", factory = SupplierViewModel.factory(LocalContext.current))
    val state by model.uiState.collectAsStateWithLifecycle()
    val visible = if (!session.loggingOut && state.generation == session.generation) state else SupplierUiState(loading = true, profileLoading = true)
    SupplierContent(visible, session.auth?.fullName.orEmpty(), onLogout, isDarkTheme, onToggleTheme, signingOut || session.loggingOut,
        model::refresh, model::open, model::closeDetail, model::create, model::update, model::upload, model::acknowledgePending, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SupplierContent(state: SupplierUiState, name: String, onLogout: () -> Unit, dark: Boolean,
    toggleTheme: () -> Unit, signingOut: Boolean, refresh: () -> Unit, open: (Long) -> Unit, close: () -> Unit,
    create: (SupplyDraft) -> Unit, update: (Long, SupplyDraft) -> Unit, upload: (Long, SupplierPhoto) -> Unit,
    acknowledge: () -> Unit, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(SupplierTab.DASHBOARD) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmingReconciliation by remember { mutableStateOf(false) }
    val inDetails = state.selectedId != null
    LaunchedEffect(state.notice) { if (state.notice != null) { creating = false; editing = false } }
    val back: () -> Unit = { if (!state.submitting) { if (editing) editing = false else { creating = false; close() } } }
    BackHandler(creating || inDetails) { back() }
    Scaffold(modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Column {
            Text(if (creating) "New supply" else if (inDetails) "Supply details" else "Kilivana", fontWeight = FontWeight.Bold)
            Text("Supplier", color = KilivanaAccents.supplierContent, style = MaterialTheme.typography.labelMedium)
        } }, navigationIcon = { if (creating || inDetails) IconButton(onClick = back, enabled = !state.submitting) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to supplies")
        } }, actions = {
            IconButton(onClick = refresh, enabled = !state.submitting && !state.loading && !signingOut) { Icon(Icons.Default.Refresh, "Refresh Supplier data") }
            IconButton(onClick = toggleTheme) { Icon(if (dark) Icons.Default.WbSunny else Icons.Default.Bedtime, "Toggle color theme") }
            IconButton(onClick = onLogout, enabled = !signingOut) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out") }
        })
    }, bottomBar = { if (!creating && !inDetails) NavigationBar {
        SupplierTab.entries.forEach { destination -> NavigationBarItem(tab == destination, { tab = destination },
            icon = { Icon(when (destination) { SupplierTab.DASHBOARD -> Icons.Default.Dashboard
                SupplierTab.SUPPLIES -> Icons.Default.Inventory2; SupplierTab.ORDERS -> Icons.Default.ShoppingBag
                SupplierTab.PROFILE -> Icons.Default.Person }, destination.title) }, label = { Text(destination.title) }) }
    } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.notice?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            state.actionError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (state.submitting) item { SupplierLoading("Submitting once. Waiting for the backend response…") }
            state.pending?.let { pending -> item {
                OutlinedCard { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Submission needs review", style = MaterialTheme.typography.titleMedium)
                    Text("A ${pending.kind.name.lowercase()} operation has an uncertain outcome. Inspect your listings and photos before starting another submission.")
                    OutlinedButton(onClick = { confirmingReconciliation = true }, enabled = !state.submitting && !state.loading && state.error == null) { Text("Review submission safety") }
                } }
            } }
            if (creating) {
                state.categoryError?.let { item { SupplierError(it, refresh) } }
                if (state.categoriesLoading) item { SupplierLoading("Loading Supplier categories") }
                item { SupplyEditor(SupplyDraft(), state.categories, state.submitting || state.pending != null, create) }
            } else if (inDetails) {
                if (state.detailLoading) item { SupplierLoading("Loading your supply") }
                state.detailError?.let { item { SupplierError(it) { state.selectedId?.let(open) } } }
                state.detail?.let { listing ->
                    if (editing) item { SupplyEditor(listing.draft(), state.categories, state.submitting || state.pending != null) { update(listing.id, it) } }
                    else item {
                        SupplyCard(listing)
                        Text(listing.description, Modifier.padding(vertical = 12.dp))
                        Text("Reserved stock: ${listing.reserved} ${listing.unit}. Minimum order: ${listing.minimum} ${listing.unit}.")
                        Button(onClick = { editing = true }, enabled = !state.submitting && state.pending == null && listing.status == CanonicalProductStatus.PENDING_APPROVAL) { Text("Edit pending listing") }
                        if (listing.status != CanonicalProductStatus.PENDING_APPROVAL) Text("Edits and photo changes are restricted to pending listings until reapproval rules are defined.")
                    }
                    if (!editing) {
                        item { Text("Product photos", style = MaterialTheme.typography.titleLarge) }
                        items(listing.images, key = { it.id!! }) { image -> itemPhoto(image.url.orEmpty(), listing.name) }
                        if (listing.images.isEmpty()) item { Text("No product photographs uploaded yet.") }
                        item { key(listing.id) { SupplierPhotoSection(listing, state, upload) } }
                    }
                }
            } else when (tab) {
                SupplierTab.DASHBOARD -> {
                    item { Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("YOUR AGRICULTURAL INPUTS BUSINESS", style = MaterialTheme.typography.labelMedium)
                            Text("Welcome, ${state.profile?.business?.businessName ?: name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Manage seeds, inputs, equipment and tools from your Supplier account.")
                        }
                    } }
                    if (state.loading) item { SupplierLoading("Loading your supplies") }
                    else if (state.error != null) item { SupplierError(state.error, refresh) }
                    else {
                        item { val counts = supplierMetrics(state.listings)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SupplierMetric("Total listings", counts.total, Modifier.weight(1f)); SupplierMetric("Active", counts.active, Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SupplierMetric("Pending approval", counts.pendingApproval, Modifier.weight(1f)); SupplierMetric("Out of stock", counts.outOfStock, Modifier.weight(1f))
                                }
                                Text("Stock availability excludes reserved units. No low-stock threshold is inferred.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        item { Button(onClick = { creating = true }, enabled = !state.submitting && state.pending == null) { Text("Add supply") } }
                        item { Text("Recent listings", style = MaterialTheme.typography.titleLarge) }
                        if (state.listings.isEmpty()) item { SupplierEmpty() }
                        items(state.listings.take(5), key = { it.id }) { listing -> SupplyCard(listing) { open(listing.id) } }
                    }
                    item { Text(SUPPLIER_ORDERS_BLOCKER, style = MaterialTheme.typography.bodyMedium) }
                }
                SupplierTab.SUPPLIES -> {
                    item { Text("My supplies", style = MaterialTheme.typography.headlineSmall) }
                    item { Button(onClick = { creating = true }, enabled = !state.submitting && state.pending == null && !state.loading) { Text("Add supply") } }
                    if (state.loading) item { SupplierLoading("Loading your supplies") }
                    else if (state.error != null) item { SupplierError(state.error, refresh) }
                    else if (state.listings.isEmpty()) item { SupplierEmpty() }
                    else items(state.listings, key = { it.id }) { listing -> SupplyCard(listing) { open(listing.id) } }
                }
                SupplierTab.ORDERS -> {
                    item { Text("Orders and fulfillment", style = MaterialTheme.typography.headlineSmall) }
                    item { SupplierUnavailable("Incoming farmer orders", SUPPLIER_ORDERS_BLOCKER) }
                    item { SupplierUnavailable("Delivery tracking", SUPPLIER_DELIVERY_BLOCKER) }
                    item { PaymentStatusCard() }
                }
                SupplierTab.PROFILE -> {
                    item { Text("Supplier profile", style = MaterialTheme.typography.headlineSmall) }
                    if (state.profileLoading) item { SupplierLoading("Loading your business profile") }
                    state.profileError?.let { item { SupplierError(it, refresh) } }
                    state.profile?.let { profile -> item { Card(shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SupplierField("Name", profile.account.fullName); SupplierField("Email", profile.account.email)
                            SupplierField("Phone", profile.account.phoneNumber); SupplierField("Account status", profile.account.accountStatus)
                            SupplierField("Verification status", profile.account.verificationStatus); SupplierField("Region", profile.account.region)
                            if (profile.business == null) Text("No Supplier business profile was returned. Ask your administrator to complete provisioning.")
                            profile.business?.let { business ->
                                SupplierField("Business name", business.businessName); SupplierField("Business details", business.businessDetails)
                                SupplierField("Location", business.location)
                                val image = business.images.orEmpty().firstOrNull { it.isPrimary == true } ?: business.images.orEmpty().firstOrNull()
                                image?.url?.let { itemPhoto(it, "Supplier business profile") }
                            }
                            Text("Your account and business profile are provisioned by the administrator. Sign-out clears this device; server token revocation is unavailable.", style = MaterialTheme.typography.bodySmall)
                        }
                    } } }
                }
            }
        }
    }
    if (confirmingReconciliation) AlertDialog(onDismissRequest = { confirmingReconciliation = false }, title = { Text("Review uncertain submission") },
        text = { Text("Inspect My supplies and the relevant image gallery first. Reload server records and acknowledge the marker only after checking that you will not create a duplicate. Delayed server completion cannot be excluded without backend idempotency.") },
        confirmButton = { TextButton(onClick = { confirmingReconciliation = false; acknowledge() }) { Text("Reload and acknowledge") } },
        dismissButton = { TextButton(onClick = { confirmingReconciliation = false }) { Text("Keep blocked") } })
}

@Composable private fun SupplyEditor(initial: SupplyDraft, categories: List<CanonicalCategoryDto>, busy: Boolean, save: (SupplyDraft) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var menu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Supply details", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, label = { Text("Supply name") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.description, { draft = draft.copy(description = it) }, label = { Text("Description") }, minLines = 3, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Box {
            OutlinedButton(onClick = { menu = true }, enabled = !busy) { Text(categories.firstOrNull { it.id == draft.categoryId }?.name ?: "Choose Supplier category") }
            DropdownMenu(menu, { menu = false }) { categories.forEach { category -> DropdownMenuItem(text = { Text(category.name.orEmpty()) }, onClick = { draft = draft.copy(categoryId = category.id); menu = false }) } }
        }
        OutlinedTextField(draft.unit, { draft = draft.copy(unit = it) }, label = { Text("Unit (kg, bag, piece…)") }, enabled = !busy, singleLine = true)
        OutlinedTextField(draft.price, { draft = draft.copy(price = it) }, label = { Text("Price per unit (KES)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !busy)
        OutlinedTextField(draft.available, { draft = draft.copy(available = it) }, label = { Text("Available stock (whole units)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy)
        OutlinedTextField(draft.minimum, { draft = draft.copy(minimum = it) }, label = { Text("Minimum order quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy)
        Text("Listings require administrator approval. Product photos are added after the listing ID is confirmed.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            error = try { draft.request(categories); null } catch (e: SupplierFailure) { e.message }
            confirm = error == null
        }, enabled = !busy && categories.isNotEmpty()) { Text("Review supply") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Confirm supply submission") },
        text = { Text("${draft.name.trim()} · KES ${draft.price} / ${draft.unit}\nAvailable stock: ${draft.available}. Submit once and wait for the server response.") },
        confirmButton = { TextButton(onClick = { confirm = false; save(draft) }, enabled = !busy) { Text("Submit supply") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep editing") } })
}

@Composable private fun SupplierPhotoSection(listing: SupplyListing, state: SupplierUiState, upload: (Long, SupplierPhoto) -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<SupplierPhoto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val enabled = !state.submitting && state.pending == null && listing.status == CanonicalProductStatus.PENDING_APPROVAL && !preparing
    LaunchedEffect(state.notice, state.submitting) { if (state.notice != null && !state.submitting) photo = null }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            preparing = true; error = null
            try { photo = withContext(Dispatchers.IO) { readSupplierPhoto(context, uri) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is SupplierFailure) e.message else "The selected photo could not be prepared." }
            finally { preparing = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = enabled) { Text("Choose product photo") }
        Text("JPEG, PNG, GIF or WebP, up to 5 MiB. Prepared as a bounded JPEG with metadata removed.", style = MaterialTheme.typography.bodySmall)
        if (preparing) SupplierLoading("Preparing photo preview")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        photo?.let { selected ->
            val bitmap = remember(selected) { BitmapFactory.decodeByteArray(selected.bytes, 0, selected.bytes.size)?.asImageBitmap() }
            bitmap?.let { Image(it, "Selected supply photo preview", Modifier.fillMaxWidth().height(180.dp)) }
            Button(onClick = { confirm = true }, enabled = enabled) { Text("Upload photo") }
        }
        if (state.submitting) Text("Submission in progress; completion is confirmed only by the backend response.")
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Upload product photo?") },
        text = { Text("Attach this photo to supply #${listing.id}. Do not upload identity documents or private verification material as product images.") },
        confirmButton = { TextButton(onClick = { confirm = false; photo?.let { upload(listing.id, it) } }, enabled = enabled) { Text("Confirm upload") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable private fun SupplyCard(listing: SupplyListing, onClick: (() -> Unit)? = null) {
    val content: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(listing.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("KES ${listing.price.toPlainString()} / ${listing.unit}")
            Text("${listing.available} ${listing.unit} available")
            Text("Status: ${listing.status.name.replace('_', ' ')}", color = KilivanaAccents.supplierContent)
            Text("Supply #${listing.id}", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (onClick == null) Card(shape = RoundedCornerShape(20.dp), content = content) else Card(onClick, shape = RoundedCornerShape(20.dp), content = content)
}
@Composable private fun SupplierMetric(label: String, count: Int, modifier: Modifier) {
    OutlinedCard(modifier, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) {
        Text(count.toString(), style = MaterialTheme.typography.headlineLarge, color = KilivanaAccents.supplierContent); Text(label)
    } }
}
@Composable private fun SupplierField(label: String, value: String?) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value?.takeIf { it.isNotBlank() } ?: "Not provided") }
}
@Composable private fun SupplierUnavailable(title: String, message: String) {
    OutlinedCard { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(message) } }
}
@Composable private fun SupplierLoading(text: String) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(); Text(text) } }
@Composable private fun SupplierEmpty() { SupplierUnavailable("No supplies listed", "Your own agricultural input listings will appear here. Add a supply to start administrator review.") }
@Composable private fun SupplierError(message: String, retry: () -> Unit) {
    OutlinedCard { Column(Modifier.fillMaxWidth().padding(20.dp)) { Text(message, color = MaterialTheme.colorScheme.error); TextButton(onClick = retry) { Text("Try again") } } }
}
@Composable private fun itemPhoto(url: String, name: String) { ProductImage(url, name) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun SupplierDashboardPreview() {
    FarmAppTheme { SupplierContent(SupplierUiState(listings = listOf(SupplyListing(1, "Synthetic seed listing (preview)", "Synthetic description", "bag", BigDecimal("250.00"), 8, 0, 1, 1,
        CanonicalProductStatus.PENDING_APPROVAL, emptyList()))), "Synthetic Supplier (preview)", {}, false, {}, false, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}) }
}
