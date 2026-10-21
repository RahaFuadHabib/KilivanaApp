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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
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

private val SupplyDraftSaver = listSaver<SupplyDraft, String>(
    save = { listOf(it.name, it.description, it.unit, it.price, it.available, it.minimum, it.categoryId?.toString().orEmpty()) },
    restore = { SupplyDraft(it[0], it[1], it[2], it[3], it[4], it[5], it[6].toLongOrNull()) }
)

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
    var creating by rememberSaveable(state.generation) { mutableStateOf(false) }
    var editing by rememberSaveable(state.generation) { mutableStateOf(false) }
    var draft by rememberSaveable(state.generation, stateSaver = SupplyDraftSaver) { mutableStateOf(SupplyDraft()) }
    var baseline by rememberSaveable(state.generation, stateSaver = SupplyDraftSaver) { mutableStateOf(SupplyDraft()) }
    var discardAction by remember(state.generation) { mutableStateOf<String?>(null) }
    var lastNotice by rememberSaveable(state.generation) { mutableStateOf(state.notice) }
    var confirmingReconciliation by remember(state.generation) { mutableStateOf(false) }
    val inDetails = state.selectedId != null
    LaunchedEffect(state.notice) {
        if (state.notice != lastNotice && state.notice != null) { creating = false; editing = false }
        lastNotice = state.notice
    }
    val leave: (String) -> Unit = { action ->
        if (action == "logout") onLogout()
        else if (editing) editing = false else { creating = false; close() }
    }
    val requestLeave: (String) -> Unit = { action ->
        if (!state.submitting && !signingOut) {
            if ((creating || editing) && draft != baseline) discardAction = action else leave(action)
        }
    }
    val back: () -> Unit = { requestLeave("back") }
    val startCreate: () -> Unit = { baseline = SupplyDraft(); draft = baseline; creating = true }
    BackHandler(creating || inDetails) { back() }
    Scaffold(modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Column {
            Text(if (creating) "New supply" else if (inDetails) "Supply details" else "Kilivana", fontWeight = FontWeight.Bold)
            Text("Supplier", color = KilivanaAccents.supplierContent, style = MaterialTheme.typography.labelMedium)
        } }, navigationIcon = { if (creating || inDetails) IconButton(onClick = back, enabled = !state.submitting) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to supplies")
        } }, actions = {
            IconButton(onClick = refresh, enabled = !state.submitting && !state.loading && !state.refreshing && !state.categoriesLoading && !signingOut) { Icon(Icons.Default.Refresh, "Refresh Supplier data") }
            IconButton(onClick = toggleTheme) { Icon(if (dark) Icons.Default.WbSunny else Icons.Default.Bedtime, "Toggle color theme") }
            IconButton(onClick = { requestLeave("logout") }, enabled = !signingOut && !state.submitting) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out") }
        })
    }, bottomBar = { if (!creating && !inDetails) NavigationBar {
        SupplierTab.entries.forEach { destination -> NavigationBarItem(tab == destination, { tab = destination },
            icon = { Icon(when (destination) { SupplierTab.DASHBOARD -> Icons.Default.Dashboard
                SupplierTab.SUPPLIES -> Icons.Default.Inventory2; SupplierTab.ORDERS -> Icons.Default.ShoppingBag
                SupplierTab.PROFILE -> Icons.Default.Person }, destination.title) }, label = { Text(destination.title) }) }
    } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.refreshing) item { Column { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Refreshing Supplier data. Showing previously loaded data.") } }
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
                item { SupplyEditor(draft, { draft = it }, state.categories, state.submitting || state.pending != null || state.refreshing,
                    state.categoriesLoading, state.categoryError, create) }
            } else if (inDetails) {
                if (state.detailLoading) item { SupplierLoading("Loading your supply") }
                state.detailError?.let { item { SupplierError(it) { state.selectedId?.let(open) } } }
                state.detail?.let { listing ->
                    if (editing) item {
                        state.categoryError?.let { SupplierError(it, refresh) }
                        SupplyEditor(draft, { draft = it }, state.categories, state.submitting || state.pending != null || state.refreshing,
                            state.categoriesLoading, state.categoryError) { update(listing.id, it) }
                    }
                    else item {
                        SupplyCard(listing)
                        Text(listing.description, Modifier.padding(vertical = 12.dp))
                        Text("Reserved stock: ${listing.reserved} ${listing.unit}. Minimum order: ${listing.minimum} ${listing.unit}.")
                        Button(onClick = { baseline = listing.draft(); draft = baseline; editing = true }, enabled = !state.submitting && state.pending == null && listing.status == CanonicalProductStatus.PENDING_APPROVAL) { Text("Edit pending listing") }
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
                    state.error?.let { item { SupplierError(it, refresh) } }
                    if (!state.loading && (state.catalogueLoaded || state.error == null)) {
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
                        item { Button(onClick = startCreate, enabled = !state.submitting && state.pending == null) { Text("Add supply") } }
                        item { Text("Recent listings", style = MaterialTheme.typography.titleLarge) }
                        if (state.listings.isEmpty()) item { SupplierEmpty() }
                        items(state.listings.take(5), key = { it.id }) { listing -> SupplyCard(listing) { open(listing.id) } }
                    }
                    item { Text(SUPPLIER_ORDERS_BLOCKER, style = MaterialTheme.typography.bodyMedium) }
                }
                SupplierTab.SUPPLIES -> {
                    item { Text("My supplies", style = MaterialTheme.typography.headlineSmall) }
                    item { Button(onClick = startCreate, enabled = !state.submitting && state.pending == null && !state.loading) { Text("Add supply") } }
                    if (state.loading) item { SupplierLoading("Loading your supplies") }
                    state.error?.let { item { SupplierError(it, refresh) } }
                    if (!state.loading && (state.catalogueLoaded || state.error == null)) {
                        if (state.listings.isEmpty()) item { SupplierEmpty() }
                        else items(state.listings, key = { it.id }) { listing -> SupplyCard(listing) { open(listing.id) } }
                    }
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
    if (discardAction != null) AlertDialog(onDismissRequest = { discardAction = null }, title = { Text("Discard unsaved changes?") },
        text = { Text("Your unfinished supply details will be lost.") },
        confirmButton = { TextButton(onClick = { val action = discardAction; discardAction = null; if (action != null) leave(action) }) { Text("Discard changes") } },
        dismissButton = { TextButton(onClick = { discardAction = null }) { Text("Keep editing") } })
    if (confirmingReconciliation) AlertDialog(onDismissRequest = { confirmingReconciliation = false }, title = { Text("Review uncertain submission") },
        text = { Text("Inspect My supplies and the relevant image gallery first. Reload server records and acknowledge the marker only after checking that you will not create a duplicate. Delayed server completion cannot be excluded without backend idempotency.") },
        confirmButton = { TextButton(onClick = { confirmingReconciliation = false; acknowledge() }) { Text("Reload and acknowledge") } },
        dismissButton = { TextButton(onClick = { confirmingReconciliation = false }) { Text("Keep blocked") } })
}

@Composable internal fun SupplyEditor(draft: SupplyDraft, change: (SupplyDraft) -> Unit,
    categories: List<CanonicalCategoryDto>, busy: Boolean, categoriesLoading: Boolean,
    categoryError: String?, save: (SupplyDraft) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var reviewed by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val errors = if (reviewed) draft.validationErrors(categories) else emptyMap()
    val categoriesReady = !categoriesLoading && categoryError == null && categories.isNotEmpty()
    val review: () -> Unit = {
        focus.clearFocus()
        reviewed = true
        if (!busy && categoriesReady && draft.validationErrors(categories).isEmpty()) confirm = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Supply details", style = MaterialTheme.typography.titleLarge)
        SupplierDraftField("Supply name", draft.name, { change(draft.copy(name = it)) }, SupplyField.NAME, errors, busy)
        SupplierDraftField("Description", draft.description, { change(draft.copy(description = it)) }, SupplyField.DESCRIPTION, errors, busy, multiline = true)
        Box {
            OutlinedButton(onClick = { menu = true }, enabled = !busy && categoriesReady) {
                Text(categories.firstOrNull { it.id == draft.categoryId }?.name ?: "Choose Supplier category")
            }
            DropdownMenu(menu, { menu = false }) { categories.forEach { category ->
                DropdownMenuItem(text = { Text(category.name.orEmpty()) }, onClick = { change(draft.copy(categoryId = category.id)); menu = false })
            } }
        }
        when {
            categoriesLoading -> Text("Loading Supplier categories. Your draft is preserved.")
            categoryError != null -> Text("Supplier categories are unavailable. Retry loading before submitting.", color = MaterialTheme.colorScheme.error)
            categories.isEmpty() -> Text("No active Supplier categories are available. Ask your administrator to configure them, then refresh.", color = MaterialTheme.colorScheme.error)
        }
        errors[SupplyField.CATEGORY]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SupplierDraftField("Unit (kg, bag, piece...)", draft.unit, { change(draft.copy(unit = it)) }, SupplyField.UNIT, errors, busy)
        SupplierDraftField("Price per unit (KES)", draft.price, { change(draft.copy(price = it)) }, SupplyField.PRICE, errors, busy, KeyboardType.Decimal)
        SupplierDraftField("Available stock (whole units)", draft.available, { change(draft.copy(available = it)) }, SupplyField.AVAILABLE, errors, busy, KeyboardType.Number)
        SupplierDraftField("Minimum order quantity", draft.minimum, { change(draft.copy(minimum = it)) }, SupplyField.MINIMUM, errors, busy, KeyboardType.Number, done = review)
        Text("Listings require administrator approval. Product photos are added after the listing ID is confirmed.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = review, enabled = !busy && categoriesReady) { Text("Review supply") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Confirm supply submission") },
        text = { Text("${draft.name.trim()} | KES ${draft.price} / ${draft.unit}\nAvailable stock: ${draft.available}. Submit once and wait for the server response.") },
        confirmButton = { TextButton(onClick = {
            if (confirm && !busy && categoriesReady) {
                confirm = false
                if (draft.validationErrors(categories).isEmpty()) save(draft)
            }
        }, enabled = !busy && categoriesReady) { Text("Submit supply") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep editing") } })
}

@Composable private fun SupplierDraftField(label: String, value: String, change: (String) -> Unit,
    field: SupplyField, errors: Map<SupplyField, String>, busy: Boolean,
    keyboard: KeyboardType = KeyboardType.Text, multiline: Boolean = false, done: (() -> Unit)? = null) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value, change, label = { Text(label) }, enabled = !busy,
        modifier = Modifier.fillMaxWidth(), singleLine = !multiline, minLines = if (multiline) 3 else 1,
        isError = errors.containsKey(field), supportingText = errors[field]?.let { message -> ({ Text(message) }) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (done == null) ImeAction.Next else ImeAction.Done),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { done?.invoke() }))
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
