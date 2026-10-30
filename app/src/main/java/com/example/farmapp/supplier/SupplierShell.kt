package com.example.farmapp.supplier

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.data.model.canonical.CanonicalCategoryDto
import com.example.farmapp.data.model.canonical.CanonicalProductStatus
import com.example.farmapp.network.SessionSnapshot
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
        model::refresh, model::open, model::closeDetail, model::create, model::update, model::upload, model::acknowledgePending, modifier, model::gallery, model::loadOrders)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SupplierContent(state: SupplierUiState, name: String, onLogout: () -> Unit, dark: Boolean,
    toggleTheme: () -> Unit, signingOut: Boolean, refresh: () -> Unit, open: (Long) -> Unit, close: () -> Unit,
    create: (SupplyDraft) -> Unit, update: (Long, SupplyDraft) -> Unit, upload: (Long, SupplierPhoto) -> Unit,
    acknowledge: () -> Unit, modifier: Modifier = Modifier, gallery: ((Long, GalleryAction) -> Unit)? = null,
    loadOrders: (Boolean) -> Unit = {}) {
    SupplierTheme(dark) {
        SupplierScreenContent(state, name, onLogout, dark, toggleTheme, signingOut, refresh, open, close,
            create, update, upload, acknowledge, modifier, gallery, loadOrders)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplierScreenContent(state: SupplierUiState, name: String, onLogout: () -> Unit, dark: Boolean,
    toggleTheme: () -> Unit, signingOut: Boolean, refresh: () -> Unit, open: (Long) -> Unit, close: () -> Unit,
    create: (SupplyDraft) -> Unit, update: (Long, SupplyDraft) -> Unit, upload: (Long, SupplierPhoto) -> Unit,
    acknowledge: () -> Unit, modifier: Modifier, gallery: ((Long, GalleryAction) -> Unit)?, loadOrders: (Boolean) -> Unit) {
    val compactNavigation = LocalDensity.current.fontScale > 1.2f
    var tab by rememberSaveable { mutableStateOf(SupplierTab.DASHBOARD) }
    var creating by rememberSaveable(state.generation) { mutableStateOf(false) }
    var draftPhoto by remember(state.generation) { mutableStateOf<SupplierPhoto?>(null) }
    var profileEditor by remember { mutableStateOf(false) }
    var editing by rememberSaveable(state.generation) { mutableStateOf(false) }
    var draft by rememberSaveable(state.generation, stateSaver = SupplyDraftSaver) { mutableStateOf(SupplyDraft()) }
    var baseline by rememberSaveable(state.generation, stateSaver = SupplyDraftSaver) { mutableStateOf(SupplyDraft()) }
    var discardAction by remember(state.generation) { mutableStateOf<String?>(null) }
    var lastNotice by rememberSaveable(state.generation) { mutableStateOf(state.notice) }
    var confirmingReconciliation by remember(state.generation) { mutableStateOf(false) }
    var search by rememberSaveable(state.generation) { mutableStateOf("") }
    var category by rememberSaveable(state.generation) { mutableStateOf<Long?>(null) }
    var status by rememberSaveable(state.generation) { mutableStateOf<String?>(null) }
    // Filters are a projection of loaded, owned records; never an additional network feed.
    val selectedCategory = category?.takeIf { id -> state.categories.any { it.id == id } }
    val filtered = remember(state.listings, search, selectedCategory, status) {
        state.listings.filter { listing ->
            (search.isBlank() || listing.name.contains(search.trim(), true) || listing.description.contains(search.trim(), true)) &&
                (selectedCategory == null || listing.categoryId == selectedCategory) &&
                (status == null || listing.status.name == status)
        }
    }
    val inDetails = state.selectedId != null
    LaunchedEffect(tab) {
        if (tab == SupplierTab.ORDERS && !state.ordersLoaded && !state.ordersLoading && state.ordersError == null) loadOrders(true)
    }
    val scroll = rememberLazyListState()
    LaunchedEffect(creating, inDetails, editing, tab) { scroll.scrollToItem(0) }
    LaunchedEffect(state.notice) {
        if (state.notice != lastNotice && state.notice != null && state.completedOperation !in setOf(SupplyOperation.UPLOAD, SupplyOperation.GALLERY)) { creating = false; editing = false }
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
    val startCreate: () -> Unit = { baseline = SupplyDraft(); draft = baseline; draftPhoto = null; creating = true }
    BackHandler(creating || inDetails) { back() }
    Scaffold(modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Column {
            Text(if (creating) "New supply" else if (inDetails) "Supply details" else "Kilivana", fontWeight = FontWeight.Bold,
                style = if (compactNavigation) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Supplier", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        } }, navigationIcon = { if (creating || inDetails) IconButton(onClick = back, enabled = !state.submitting) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to supplies")
        } }, actions = {
            IconButton(onClick = toggleTheme) { Icon(if (dark) Icons.Default.WbSunny else Icons.Default.Bedtime, "Toggle color theme") }
            IconButton(onClick = { requestLeave("logout") }, enabled = !signingOut && !state.submitting) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out") }
        })
    }, bottomBar = { if (!creating && !inDetails) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        SupplierTab.entries.forEach { destination -> NavigationBarItem(tab == destination, { tab = destination },
            icon = { Icon(when (destination) { SupplierTab.DASHBOARD -> Icons.Default.Dashboard
                SupplierTab.SUPPLIES -> Icons.Default.Inventory2; SupplierTab.ORDERS -> Icons.Default.ShoppingBag
                SupplierTab.PROFILE -> Icons.Default.Person }, destination.title) },
                label = if (compactNavigation) null else { { Text(destination.title, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary)) }
    } }) { padding ->
        PullToRefreshBox(isRefreshing = if (tab == SupplierTab.ORDERS && !inDetails && !creating) state.ordersLoading && state.ordersLoaded else state.refreshing, onRefresh = {
            if (!creating && !editing && !state.submitting && !signingOut) {
                if (tab == SupplierTab.ORDERS && !inDetails) loadOrders(true) else if (!state.loading) refresh()
            }
        }, modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(Modifier.fillMaxSize().imePadding().testTag("supplier-list"), state = scroll, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.notice?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            state.actionError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (state.submitting) item { SupplierLoading("Saving changes…") }
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
                item { SupplyEditorForm(draft, { draft = it }, state.categories, state.submitting || state.pending != null || state.refreshing,
                    state.categoriesLoading, state.categoryError,
                    photos = { SupplierDraftPhotoSelector(draftPhoto, { draftPhoto = it }, state.submitting || state.pending != null) }, save = create) }
            } else if (inDetails) {
                if (state.detailLoading) item { SupplierLoading("Loading your supply") }
                state.detailError?.let { item { SupplierError(it) { state.selectedId?.let(open) } } }
                state.detail?.let { listing ->
                    if (editing) item {
                        state.categoryError?.let { SupplierError(it, refresh) }
                        SupplyEditorForm(draft, { draft = it }, state.categories, state.submitting || state.pending != null || state.refreshing,
                            state.categoriesLoading, state.categoryError,
                            photos = { key(listing.id) { SupplierPhotoSection(listing, state, upload) } }) { update(listing.id, it) }
                    }
                    else item {
                        SupplierGallery(listing)
                        Spacer(Modifier.height(20.dp))
                        SupplierStatus(listing.status)
                        Text(listing.name, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.headlineMedium)
                        Text("KES ${listing.price.toPlainString()} / ${listing.unit}", Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        SupplierSection("Stock & ordering") {
                            SupplierField("Available", "${listing.available} ${listing.unit}")
                            SupplierField("Reserved", "${listing.reserved} ${listing.unit}")
                            SupplierField("Minimum order", "${listing.minimum} ${listing.unit}")
                        }
                        Text("About this supply", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleMedium)
                        Text(listing.description, Modifier.padding(vertical = 12.dp))
                        Text("Reserved stock: ${listing.reserved} ${listing.unit}. Minimum order: ${listing.minimum} ${listing.unit}.")
                        Button(onClick = { baseline = listing.draft(); draft = baseline; editing = true }, enabled = !state.submitting && state.pending == null && listing.status == CanonicalProductStatus.PENDING_APPROVAL) { Text("Edit pending listing") }
                        if (listing.status != CanonicalProductStatus.PENDING_APPROVAL) Text("Only products awaiting approval can be edited here.")
                    }
                    if (editing) item { SupplierGallery(listing) }
                    if (!editing) item { Text("Product images", style = MaterialTheme.typography.titleLarge) }
                    if (listing.images.isEmpty()) item { Text("No product photographs uploaded yet.") }
                    if (!editing) item { key(listing.id) { SupplierPhotoSection(listing, state, upload, draftPhoto) { draftPhoto = null } } }
                    if (gallery != null) item { SupplierGalleryControls(listing, state.submitting || state.refreshing || state.pending != null, gallery) }
                }
            } else when (tab) {
                SupplierTab.DASHBOARD -> {
                    item { SupplierHero(state.profile?.business?.businessName?.takeIf { it.isNotBlank() }
                        ?: state.profile?.account?.fullName ?: name, !state.submitting && state.pending == null, startCreate) }
                    if (state.catalogueWaiting) item { SupplierLoading("Loading your supplies") }
                    state.error?.let { item { SupplierCatalogueError(it, state.catalogueVisible, refresh) } }
                    if (state.catalogueVisible) {
                        item { Text("Inventory overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        item { val counts = remember(state.listings) { supplierMetrics(state.listings) }
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SupplierMetric("Total listings", counts.total, Modifier.weight(1f)); SupplierMetric("Active", counts.active, Modifier.weight(1f))
                                }
                                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SupplierMetric("Pending approval", counts.pendingApproval, Modifier.weight(1f)); SupplierMetric("Out of stock", counts.outOfStock, Modifier.weight(1f))
                                }
                                Text("Available stock excludes reserved units.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item { Text("Recent listings", style = MaterialTheme.typography.titleLarge) }
                        if (state.listings.isEmpty()) item { SupplierEmpty() }
                        items(state.listings.take(5), key = { it.id }) { listing -> SupplyCard(listing) { open(listing.id) } }
                    }
                }
                SupplierTab.SUPPLIES -> {
                    item { Text("My supplies", style = MaterialTheme.typography.headlineSmall) }
                    item { Button(onClick = startCreate, modifier = Modifier.fillMaxWidth(), enabled = !state.submitting && state.pending == null && !state.loading) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add supply")
                    } }
                    item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Search supplies") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(20.dp)) }
                    item { SupplierFilters(state.categories, selectedCategory, { category = it }, status, { status = it }) }
                    state.categoryError?.let { item { SupplierError(it, refresh) } }
                    if (state.catalogueWaiting) item { SupplierLoading("Loading your supplies") }
                    state.error?.let { item { SupplierCatalogueError(it, state.catalogueVisible, refresh) } }
                    if (state.catalogueVisible) {
                        if (state.listings.isNotEmpty()) item { Text("${filtered.size} of ${state.listings.size} listings", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (state.listings.isEmpty()) item { SupplierEmpty() }
                        else if (filtered.isEmpty()) item { SupplierSection("No matching supplies", "Try another search or clear your filters.") {
                            TextButton(onClick = { search = ""; category = null; status = null }) { Text("Clear filters") }
                        } }
                        else items(filtered, key = { it.id }) { listing -> SupplyCard(listing) { open(listing.id) } }
                    }
                }
                SupplierTab.ORDERS -> {
                    supplierOrders(state, loadOrders)
                }
                SupplierTab.PROFILE -> {
                    item { Text("Supplier profile", style = MaterialTheme.typography.headlineSmall) }
                    item { TextButton(onClick = { profileEditor = true }, enabled = !state.submitting) { Text("Edit profile") } }
                    if (state.profileLoading) item { SupplierLoading("Loading your business profile") }
                    state.profileError?.let { item { SupplierError(it, refresh) } }
                    state.profile?.let { profile -> item { Card(shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Default.Storefront, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(profile.business?.businessName?.takeIf { it.isNotBlank() } ?: profile.account.fullName,
                                style = MaterialTheme.typography.headlineSmall)
                            Text("BUSINESS IDENTITY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            SupplierField("Name", profile.account.fullName); SupplierField("Email", profile.account.email)
                            SupplierField("Phone", profile.account.phoneNumber); SupplierField("Account status", profile.account.accountStatus)
                            SupplierField("Verification status", profile.account.verificationStatus); SupplierField("Region", profile.account.region)
                            if (profile.business == null) Text("No Supplier business profile was returned. Ask your administrator to complete provisioning.")
                            profile.business?.let { business ->
                                SupplierField("Business details", business.businessDetails)
                                SupplierField("Location", business.location)
                                val image = business.images.orEmpty().firstOrNull { it.isPrimary == true } ?: business.images.orEmpty().firstOrNull()
                                image?.url?.let { itemPhoto(it, "Supplier business profile") }
                            }
                            Text("Account details and business details are edited separately. Verification and approval remain managed by Kilivana.", style = MaterialTheme.typography.bodySmall)
                        }
                    } } }
                    item { com.example.farmapp.profile.AboutKilivana() }
                }
            }
        }
        }
    }
    if (profileEditor) com.example.farmapp.profile.ProfileEditor { profileEditor = false; refresh() }
    if (discardAction != null) AlertDialog(onDismissRequest = { discardAction = null }, title = { Text("Discard unsaved changes?") },
        text = { Text("Your unfinished supply details will be lost.") },
        confirmButton = { TextButton(onClick = { val action = discardAction; discardAction = null; if (action != null) leave(action) }) { Text("Discard changes") } },
        dismissButton = { TextButton(onClick = { discardAction = null }) { Text("Keep editing") } })
    if (confirmingReconciliation) AlertDialog(onDismissRequest = { confirmingReconciliation = false }, title = { Text("Review uncertain submission") },
        text = { Text("Inspect My supplies and the relevant image gallery first. Reload server records and acknowledge the marker only after checking that you will not create a duplicate. Delayed server completion cannot be excluded without backend idempotency.") },
        confirmButton = { TextButton(onClick = { confirmingReconciliation = false; acknowledge() }) { Text("Reload and acknowledge") } },
        dismissButton = { TextButton(onClick = { confirmingReconciliation = false }) { Text("Keep blocked") } })
}

@Composable private fun SupplierGalleryControls(listing: SupplyListing, busy: Boolean, change: (Long, GalleryAction) -> Unit) {
    var pending by remember(listing.id) { mutableStateOf<GalleryAction?>(null) }
    val enabled = !busy && listing.status == CanonicalProductStatus.PENDING_APPROVAL
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listing.images.sortedBy { it.sortOrder ?: 0 }.forEachIndexed { index, image ->
            Text("Photo ${index + 1}${if (image.isPrimary == true) " · Primary" else ""}")
            Row {
                TextButton(onClick = { pending = GalleryAction(image.id!!, GalleryChange.PRIMARY) }, enabled = enabled && image.isPrimary != true) { Text("Make primary") }
                TextButton(onClick = { pending = GalleryAction(image.id!!, GalleryChange.MOVE_UP) }, enabled = enabled && index > 0) { Text("Move up") }
                TextButton(onClick = { pending = GalleryAction(image.id!!, GalleryChange.DELETE) }, enabled = enabled) { Text("Delete photo") }
            }
        }
    }
    pending?.let { action -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("Change product photo?") },
        text = { Text(when(action.change) { GalleryChange.DELETE -> "Delete this uploaded photo?"; GalleryChange.PRIMARY -> "Use this photo as the primary product image?"; GalleryChange.MOVE_UP -> "Move this photo earlier in the gallery?" }) },
        confirmButton = { TextButton(onClick = { pending = null; change(listing.id, action) }, enabled = enabled) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }) }
}

@Composable internal fun SupplyEditor(draft: SupplyDraft, change: (SupplyDraft) -> Unit,
    categories: List<CanonicalCategoryDto>, busy: Boolean, categoriesLoading: Boolean,
    categoryError: String?, save: (SupplyDraft) -> Unit) {
    SupplyEditorForm(draft, change, categories, busy, categoriesLoading, categoryError, null, save)
}

@Composable private fun SupplyEditorForm(draft: SupplyDraft, change: (SupplyDraft) -> Unit,
    categories: List<CanonicalCategoryDto>, busy: Boolean, categoriesLoading: Boolean,
    categoryError: String?, photos: (@Composable () -> Unit)?, save: (SupplyDraft) -> Unit) {
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
        Text("Describe your input, set a price and submit it for review.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SupplierSection("Product information") {
        SupplierDraftField("Supply name", draft.name, { change(draft.copy(name = it)) }, SupplyField.NAME, errors, busy)
        SupplierDraftField("Description", draft.description, { change(draft.copy(description = it)) }, SupplyField.DESCRIPTION, errors, busy, multiline = true)
        }
        SupplierSection("Category", "Choose from your active Supplier categories.") {
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
        }
        SupplierSection("Pricing & stock") {
        SupplierDraftField("Unit (kg, bag, piece...)", draft.unit, { change(draft.copy(unit = it)) }, SupplyField.UNIT, errors, busy)
        SupplierDraftField("Price per unit (KES)", draft.price, { change(draft.copy(price = it)) }, SupplyField.PRICE, errors, busy, KeyboardType.Decimal)
        SupplierDraftField("Available stock (whole units)", draft.available, { change(draft.copy(available = it)) }, SupplyField.AVAILABLE, errors, busy, KeyboardType.Number)
        SupplierDraftField("Minimum order quantity", draft.minimum, { change(draft.copy(minimum = it)) }, SupplyField.MINIMUM, errors, busy, KeyboardType.Number, done = review)
        }
        if (photos != null) SupplierSection("Product images") { photos() }
        Text("Listings require administrator approval.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = review, modifier = Modifier.fillMaxWidth(), enabled = !busy && categoriesReady) { Text("Review supply") }
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
        shape = RoundedCornerShape(16.dp),
        isError = errors.containsKey(field), supportingText = errors[field]?.let { message -> ({ Text(message) }) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (done == null) ImeAction.Next else ImeAction.Done),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { done?.invoke() }))
}

@Composable private fun SupplierPhotoSection(listing: SupplyListing, state: SupplierUiState, upload: (Long, SupplierPhoto) -> Unit,
    initialPhoto: SupplierPhoto? = null, consumed: () -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf(initialPhoto) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val enabled = !state.submitting && state.pending == null && listing.status == CanonicalProductStatus.PENDING_APPROVAL && !preparing
    LaunchedEffect(state.notice, state.submitting) {
        if (uploading && state.notice != null && !state.submitting && state.completedOperation == SupplyOperation.UPLOAD) { photo = null; uploading = false; consumed() }
        if (uploading && !state.submitting && state.actionError != null) uploading = false
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            preparing = true; error = null
            try { photo = withContext(Dispatchers.IO) { readSupplierPhoto(context, uri) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is SupplierFailure) e.message else "The selected photo could not be prepared." }
            finally { preparing = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProductPhotoPickerControl(enabled) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        Text(if (photo == null) "Add a photo, up to 5 MiB." else "Tap the icon to replace this photo.", style = MaterialTheme.typography.bodySmall)
        if (preparing) SupplierLoading("Preparing photo preview")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        photo?.let { selected ->
            SupplierPhotoPreview(selected)
            Button(onClick = { confirm = true }, enabled = enabled) { Text("Upload photo") }
        }
        if (state.submitting) Text("Saving changes…")
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Upload product photo?") },
        text = { Text("Add this photo to your product?") },
        confirmButton = { TextButton(onClick = { confirm = false; photo?.let { uploading = true; upload(listing.id, it) } }, enabled = enabled) { Text("Confirm upload") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable private fun SupplierDraftPhotoSelector(photo: SupplierPhoto?, selected: (SupplierPhoto?) -> Unit, busy: Boolean) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !preparing) scope.launch {
            preparing = true; error = null
            try { selected(withContext(Dispatchers.IO) { readSupplierPhoto(context, uri) }) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is SupplierFailure) e.message else "Unable to prepare this photo. Choose another image." }
            finally { preparing = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProductPhotoPickerControl(!busy && !preparing) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        if (preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        photo?.let {
            SupplierPhotoPreview(it)
            Text("Photo selected. Save your product, then upload the photo.")
            TextButton(onClick = { selected(null) }, enabled = !busy && !preparing) { Text("Remove selected photo") }
        }
    }
}

@Composable internal fun ProductPhotoPickerControl(enabled: Boolean, choose: () -> Unit) {
    FilledTonalIconButton(onClick = choose, enabled = enabled, modifier = Modifier.size(64.dp)) {
        Icon(Icons.Default.AddPhotoAlternate, "Add product photo", Modifier.size(36.dp))
    }
}

@Composable private fun SupplyCard(listing: SupplyListing, onClick: (() -> Unit)? = null) {
    val content: @Composable ColumnScope.() -> Unit = {
        SupplierListingPhoto(listing, Modifier.fillMaxWidth().height(144.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)))
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SupplierStatus(listing.status)
            Text(listing.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("KES ${listing.price.toPlainString()} / ${listing.unit}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(if (listing.available == 0) "Out of stock" else "${listing.available} ${listing.unit} available", style = MaterialTheme.typography.bodyMedium)
            }
            Text("Supply #${listing.id}", style = MaterialTheme.typography.bodySmall)
        }
    }
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val elevation = CardDefaults.cardElevation(1.dp)
    if (onClick == null) Card(shape = SupplierStyle.card, colors = colors, elevation = elevation, content = content)
    else Card(onClick, shape = SupplierStyle.card, colors = colors, elevation = elevation, content = content)
}
@Composable private fun SupplierMetric(label: String, count: Int, modifier: Modifier) {
    Card(modifier.fillMaxHeight(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) { Column(Modifier.padding(16.dp)) {
        val pending = label == "Pending approval"
        Icon(if (pending) Icons.Default.HourglassEmpty else if (label == "Out of stock") Icons.Default.Inventory2 else Icons.Default.Eco,
            null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(count.toString(), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary); Text(label)
    } }
}
@Composable private fun SupplierField(label: String, value: String?) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value?.takeIf { it.isNotBlank() } ?: "Not provided") }
}
@Composable private fun SupplierUnavailable(title: String, message: String) {
    SupplierSection(title, message) {
        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
        Text("Currently unavailable", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun SupplierLoading(text: String) { SupplierSection(text) { LinearProgressIndicator(Modifier.fillMaxWidth()) } }
@Composable private fun SupplierEmpty() {
    SupplierSection("No supplies listed", "Add your first supply to start building your inventory. New listings are reviewed before publication.") {
        Icon(Icons.Default.Inventory2, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
@Composable private fun SupplierCatalogueError(message: String, retained: Boolean, retry: () -> Unit) {
    OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (retained) "Couldn't refresh supplies" else "Couldn't load supplies", style = MaterialTheme.typography.titleMedium)
            Text(message)
            if (retained) Text("Showing previously loaded listings. Stock and prices may have changed.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = retry) { Text("Try again") }
        }
    }
}
@Composable private fun SupplierError(message: String, retry: () -> Unit) {
    OutlinedCard { Column(Modifier.fillMaxWidth().padding(20.dp)) { Text(message, color = MaterialTheme.colorScheme.error); TextButton(onClick = retry) { Text("Try again") } } }
}
@Composable private fun itemPhoto(url: String, name: String) { ProductImage(url, name) }

@Composable
private fun SupplierFilters(categories: List<CanonicalCategoryDto>, category: Long?, chooseCategory: (Long?) -> Unit,
    status: String?, chooseStatus: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(category == null, { chooseCategory(null) }, label = { Text("All categories") }, colors = supplierFilterColors())
            categories.forEach { item ->
                FilterChip(category == item.id, { chooseCategory(item.id) }, label = { Text(item.name.orEmpty()) },
                    leadingIcon = { Icon(supplierCategoryIcon(item.name.orEmpty()), null, Modifier.size(18.dp)) }, colors = supplierFilterColors())
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(status == null, { chooseStatus(null) }, label = { Text("All statuses") }, colors = supplierFilterColors())
            CanonicalProductStatus.entries.forEach { item ->
                FilterChip(status == item.name, { chooseStatus(item.name) }, label = { Text(item.name.lowercase().replace('_', ' ')) }, colors = supplierFilterColors())
            }
        }
    }
}

@Composable private fun supplierFilterColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun SupplierDashboardPreview() {
    FarmAppTheme { SupplierContent(SupplierUiState(listings = listOf(SupplyListing(1, "Synthetic seed listing (preview)", "Synthetic description", "bag", BigDecimal("250.00"), 8, 0, 1, 1,
        CanonicalProductStatus.PENDING_APPROVAL, emptyList()))), "Synthetic Supplier (preview)", {}, false, {}, false, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}) }
}
