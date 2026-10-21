package com.example.farmapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.farmapp.data.model.AuthResponse
import com.example.farmapp.ui.navigation.FarmerDestination
import com.example.farmapp.ui.theme.ForestGreen
import com.example.farmapp.ui.theme.HarvestAmber

/** Farmer UI only. Identity and logout remain owned by the existing auth ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FarmerShell(
    auth: AuthResponse,
    isLoading: Boolean,
    errorMessage: String?,
    onLogout: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    var destination by rememberSaveable { mutableStateOf(FarmerDestination.HOME) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var trackingFrom by rememberSaveable { mutableStateOf<FarmerDestination?>(null) }
    if (searchOpen) AlertDialog(onDismissRequest = { searchOpen = false },
        title = { Text("Search your farm") },
        text = { OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
            label = { Text("Crops, orders, supplies") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { searchOpen = false }) { Text("Search") } },
        dismissButton = { TextButton(onClick = { searchQuery = ""; searchOpen = false }) { Text("Clear") } })
    val savedTabs = rememberSaveableStateHolder()
    val cropPhotos = rememberCropPhotoController(auth.userId)
    val scope = rememberCoroutineScope()
    val repository = remember { com.example.farmapp.repository.ProductRepository(com.example.farmapp.data.remote.ApiClient.farmApiService) }
    var products by remember { mutableStateOf<List<com.example.farmapp.data.model.FarmerProductResponse>>(emptyList()) }
    var productsError by remember { mutableStateOf<String?>(null) }
    val productRefresh = remember { CropRefreshController() }
    val detailRefresh = remember { CropDetailRefreshController() }
    val loadingProducts = productRefresh.refreshing
    var confirmNewAttempt by remember { mutableStateOf(false) }
    var recoveryProduct by remember { mutableStateOf<com.example.farmapp.data.model.FarmerProductResponse?>(null) }
    var publishing by remember { mutableStateOf(false) }
    var publishMessage by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf(CategoryCatalog(loading = true)) }
    suspend fun refreshCategories() {
        categories = categories.copy(loading = true, error = null)
        try { categories = CategoryCatalog(items = repository.categories(), loaded = true) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { categories = categories.copy(loading = false,
            error = "Unable to load categories. Check your connection and retry.") }
    }
    LaunchedEffect(auth.userId) { refreshCategories() }
    var selectedCropId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingCropOrders by rememberSaveable { mutableStateOf(false) }
    var managementBusy by remember { mutableStateOf(false) }
    var managementMessage by remember { mutableStateOf<String?>(null) }
    var removalId by remember { mutableStateOf<String?>(null) }
    val orderRefresh = remember { FarmerOrdersRefreshController() }
    val orders = orderRefresh.orders
    val ordersError = orderRefresh.error
    val loadingOrders = orderRefresh.refreshing
    val manager = remember(repository) { CropManagement(
        update = { id, request -> repository.update(id, request); Unit },
        unlist = { repository.unlist(it) }, mine = { repository.mine() }) }
    val cropSave = remember(manager) { CropSaveController(manager) }
    val lifecycle = remember(repository) { CropLifecycleController(
        mutate = { id, action -> repository.lifecycle(id, action); Unit },
        mine = { repository.mine() }, detail = { repository.detail(it) }) }
    suspend fun refreshOrders() { orderRefresh.refresh { repository.orders() } }
    val selectedCrop = products.firstOrNull { it.productId == selectedCropId }
    val sellerOrders = orders?.let { cropOrders(it, auth.userId) }
    val selectedOrders = orders?.let { cropOrders(it, auth.userId, selectedCropId) }
    if (removalId != null) {
        val removalProduct = products.firstOrNull { it.productId == removalId }
        AlertDialog(onDismissRequest = { removalId = null; manager.cancelRemoval() },
            title = { Text("Remove ${removalProduct?.name.orEmpty()} from your listings?") },
            text = { Text("This unlists the crop from the marketplace. Existing orders are kept.") },
            confirmButton = { TextButton(onClick = {
                val id = removalId!!
                removalId = null
                managementBusy = true
                scope.launch {
                    try {
                        products = manager.confirmRemoval(id)
                        productsError = null
                        selectedCropId = null
                        showingCropOrders = false
                        destination = FarmerDestination.HOME
                        publishMessage = "Crop removed from marketplace listings."
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { managementMessage = "Removal was not confirmed. Refresh My Crops before retrying. " + productError(e) }
                    finally { managementBusy = false }
                }
            }) { Text("Unlist crop") } },
            dismissButton = { TextButton(onClick = { removalId = null; manager.cancelRemoval() }) { Text("Cancel") } })
    }
    suspend fun refreshProducts() {
        productRefresh.refresh(
            load = {
                val loaded = repository.mine()
                val id = selectedCropId
                if (repository.canonical && id != null && loaded.any { it.productId == id }) {
                    val detail = repository.detail(id)
                    loaded.map { if (it.productId == id) detail else it }
                } else loaded
            },
            onSuccess = { products = it; productsError = null; lifecycle.refreshed() },
            onFailure = { productsError = productError(it) }
        )
    }
    LaunchedEffect(auth.userId) { refreshProducts(); refreshOrders() }
    val publish: () -> Unit = {
        if (!publishing && !cropPhotos.busy) {
            publishing = true
            scope.launch {
                val original = cropPhotos.draft
                try {
                    original.validationError()?.let { throw com.example.farmapp.repository.ProductFailure(it) }
                    if (original.productId == null && !categories.contains(original.categoryId))
                        throw com.example.farmapp.repository.ProductFailure("Load categories and select an available category before publishing.")
                    // Validate every original before creating a listing; streaming bodies are small handles.
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { original.photos.forEach { cropPhotos.storage.multipart(it) } }
                    val publisher = CropPublisher(
                        create = { repository.create(it).productId },
                        upload = { id, name -> repository.upload(id, kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { cropPhotos.storage.multipart(name) }); Unit },
                        mine = { repository.mine() })
                    products = publisher.publish(original, cropPhotos.update) { publishMessage = it }
                    productsError = null
                    original.photos.forEach { cropPhotos.storage.delete(it) }
                    publishMessage = "Crop published successfully."
                    destination = FarmerDestination.HOME
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { publishMessage = "Publishing stopped. Your draft and recorded progress are kept. " + productError(e) }
                finally { publishing = false }
            }
        }
    }
    BackHandler(enabled = trackingFrom != null || selectedCropId != null || destination.backDestination() != null) {
        if (!managementBusy) {
            if (trackingFrom != null) trackingFrom = null
            else if (showingCropOrders) showingCropOrders = false
            else if (selectedCropId != null) selectedCropId = null
            else destination = destination.backDestination() ?: FarmerDestination.HOME
        }
    }
    val checkCreation: () -> Unit = {
        if (!publishing) {
            publishing = true
            scope.launch {
                try {
                    products = repository.mine()
                    productsError = null
                    val draft = cropPhotos.draft
                    val matches = products.filter { (draft.categoryId == null || it.categoryId == draft.categoryId) && it.name == draft.name.trim() && it.unit == draft.unit.label &&
                        it.availableQuantity.compareTo(draft.quantity.toBigDecimal()) == 0 &&
                        it.unitPrice.compareTo(draft.pricePerUnit.toBigDecimal()) == 0 && it.description == draft.description.trim() }
                    if (matches.size == 1) recoveryProduct = matches.first()
                    else if (matches.isEmpty()) confirmNewAttempt = true
                    else { destination = FarmerDestination.HOME; publishMessage = "Several matching listings exist. Select the correct listing to resume." }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { publishMessage = productError(e) }
                finally { publishing = false }
            }
        }
    }
    if (confirmNewAttempt) AlertDialog(onDismissRequest = { confirmNewAttempt = false },
        title = { Text("Try creating again?") },
        text = { Text("My Crops returned no listing matching this draft. If the earlier server request is still finishing, another attempt could create a duplicate. Check the backend before retrying if unsure.") },
        confirmButton = { TextButton(onClick = {
            cropPhotos.update(cropPhotos.draft.copy(creationUncertain = false))
            confirmNewAttempt = false
            publishMessage = "Draft ready. Review and publish when you are ready."
        }) { Text("Allow new attempt") } },
        dismissButton = { TextButton(onClick = { confirmNewAttempt = false }) { Text("Keep draft") } })
    recoveryProduct?.let { product ->
        AlertDialog(onDismissRequest = { recoveryProduct = null }, title = { Text("Resume this listing?") },
            text = { Text("Use ${product.name} for the pending draft's photos? This will not create another product. Verify its quantity and price first.") },
            confirmButton = { TextButton(onClick = {
                val current = cropPhotos.draft
                if ((current.categoryId == null || product.categoryId == current.categoryId) && product.name == current.name.trim() && product.unit == current.unit.label &&
                    product.availableQuantity.compareTo(current.quantity.toBigDecimal()) == 0 &&
                    product.unitPrice.compareTo(current.pricePerUnit.toBigDecimal()) == 0 && product.description == current.description.trim()) {
                    val uploaded = product.images.mapNotNull { it.fileName }.toSet()
                    val completed = current.photos.takeWhile { photo -> uploaded.any { it.substringBeforeLast('.') == photo.substringBeforeLast('.') } }.size
                    cropPhotos.update(current.copy(productId = product.productId, creationUncertain = false, uploadedPhotos = completed,
                        categoryId = product.categoryId, categoryName = product.categoryName))
                    destination = FarmerDestination.ADD
                    publishMessage = "Existing listing selected. Resume publishing to upload pending photos."
                } else { publishMessage = "This listing does not match the pending draft. Choose the matching listing." }
                recoveryProduct = null
            }) { Text("Use listing") } }, dismissButton = { TextButton(onClick = { recoveryProduct = null }) { Text("Cancel") } })
    }
    // Farmer-scoped surfaces keep other roles' existing themes unchanged.
    val colors = MaterialTheme.colorScheme.copy(
        background = if (isDarkTheme) Color(0xFF101C16) else Color(0xFFFEFDFC),
        surface = if (isDarkTheme) Color(0xFF101C16) else Color(0xFFFEFDFC)
    )
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Kilivana", style = MaterialTheme.typography.titleLarge)
                            if (destination == FarmerDestination.HOME) {
                                Text("Farmer", style = MaterialTheme.typography.titleMedium,
                                    color = colors.primary)
                            }
                        }
                    },
                    actions = {
                        if (destination == FarmerDestination.HOME) {
                            IconButton(onClick = { searchOpen = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search your farm", tint = colors.primary)
                            }
                            com.example.farmapp.ui.components.FarmerThemeToggle(isDarkTheme, onToggleTheme, iconOnly = true)
                            IconButton(onClick = onLogout, enabled = !isLoading) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Log Out",
                                    tint = colors.primary)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface))
            },
            bottomBar = {
                FarmerBottomBar(destination) { tab ->
                    if (!managementBusy) { trackingFrom = null; selectedCropId = null; showingCropOrders = false; destination = tab }
                }
            }
        ) { padding ->
            savedTabs.SaveableStateProvider(destination.name) {
                val contentModifier = Modifier.fillMaxSize().padding(padding)
                if (trackingFrom != null) FarmerTrackingScreen(contentModifier) { trackingFrom = null }
                else when (destination) {
                    FarmerDestination.HOME -> if (selectedCrop != null) {
                        if (showingCropOrders) FarmerOrdersScreen(selectedOrders, ordersError, loadingOrders,
                            selectedCrop.name, contentModifier, { showingCropOrders = false }, { scope.launch { refreshOrders() } },
                            onTracking = { trackingFrom = FarmerDestination.ORDERS })
                        else CropDetailScreen(selectedCrop, contentModifier, managementBusy, managementMessage ?: if (loadingOrders && orders == null) "Loading order count..." else ordersError,
                            selectedOrders?.size, { selectedCropId = null },
                            { showingCropOrders = true; scope.launch { refreshOrders() } },
                            onSave = { draft ->
                                if (!managementBusy && cropSave.begin()) {
                                    managementBusy = true
                                    managementMessage = null
                                    scope.launch {
                                        try {
                                            cropSave.save(selectedCrop, draft, onProducts = {
                                                products = it
                                                productsError = null
                                            }, validate = {
                                                if (!categories.contains(draft.categoryId)) throw com.example.farmapp.repository.ProductFailure("Select an available backend category.")
                                            })
                                        } finally { managementBusy = false }
                                    }
                                }
                            }, onRemove = { manager.requestRemoval(selectedCrop.productId); removalId = selectedCrop.productId },
                            categories = categories, onRetryCategories = { scope.launch { refreshCategories() } }, saveState = cropSave.state,
                            refreshing = detailRefresh.refreshing || loadingProducts || loadingOrders,
                            refreshError = productsError,
                            canonicalLifecycle = repository.canonical, authenticatedUserId = auth.userId, authenticatedRole = auth.role,
                            lifecycleBlocked = lifecycle.refreshRequired,
                            onLifecycle = { action ->
                                if (!managementBusy && !loadingProducts && lifecycle.begin()) {
                                    managementBusy = true
                                    managementMessage = null
                                    cropSave.reset()
                                    scope.launch {
                                        try {
                                            lifecycle.execute(selectedCrop.productId, action,
                                                onProducts = { products = it; productsError = null },
                                                onDeleted = { selectedCropId = null; showingCropOrders = false })
                                            managementMessage = lifecycle.message
                                            publishMessage = lifecycle.message
                                        } finally { managementBusy = false }
                                    }
                                }
                            },
                            onRefresh = { if (!managementBusy && !loadingProducts && !loadingOrders) scope.launch { detailRefresh.refresh(::refreshProducts, ::refreshOrders) } })
                    } else FarmerScreen(contentModifier,
                        farmerName = auth.fullName, showCreateAction = false, searchQuery = searchQuery,
                        realProducts = products.filter { it.status !in setOf("HIDDEN", "DELETED") }, productsError = productsError, productsLoading = loadingProducts,
                        onRefreshProducts = { scope.launch { refreshProducts() } }, publishMessage = publishMessage,
                        onResumeExisting = if (cropPhotos.draft.creationUncertain && !publishing) ({ recoveryProduct = it }) else null,
                        onOpenCrop = { cropSave.reset(); selectedCropId = it.productId; showingCropOrders = false; managementMessage = null; scope.launch { refreshOrders() } })
                    FarmerDestination.ORDERS -> FarmerOrdersScreen(sellerOrders, ordersError, loadingOrders,
                        null, contentModifier, null, { scope.launch { refreshOrders() } },
                        onTracking = { trackingFrom = FarmerDestination.ORDERS })
                    FarmerDestination.ADD -> AddCropScreen(cropPhotos, contentModifier, publishing, publishMessage, publish, checkCreation,
                        categories, { scope.launch { refreshCategories() } })
                    FarmerDestination.MAP -> FarmerMapScreen(contentModifier)
                    FarmerDestination.SUPPLIES -> FarmerSuppliesScreen(contentModifier) { trackingFrom = FarmerDestination.SUPPLIES }
                    FarmerDestination.PROFILE -> FarmerProfileScreen(auth, errorMessage, contentModifier, isDarkTheme, onToggleTheme, onLogout, isLoading)
                }
            }
        }
    }
}

@Composable
internal fun FarmerBottomBar(destination: FarmerDestination, onSelect: (FarmerDestination) -> Unit) {
    val colors = MaterialTheme.colorScheme
                Surface(shadowElevation = 6.dp, color = colors.surface) {
                    BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    // Five equal columns put Add in the center; large text remains scrollable.
                    val itemWidth = maxOf(52.dp, maxWidth / 5, 52.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        FarmerDestination.bottomTabs.forEach { tab ->
                            val active = destination == tab
                            if (tab == FarmerDestination.ADD) {
                                Column(Modifier.width(itemWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                                    FilledIconButton(onClick = { onSelect(tab) },
                                        modifier = Modifier.size(52.dp).border(if (active) 3.dp else 0.dp,
                                            if (active) colors.primary else Color.Transparent, CircleShape)
                                            .semantics { selected = active },
                                        shape = CircleShape,
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = HarvestAmber, contentColor = ForestGreen)) {
                                        Icon(Icons.Default.Add, contentDescription = "Add crop", modifier = Modifier.size(32.dp))
                                    }
                                    Text("Add", style = MaterialTheme.typography.labelMedium,
                                        color = if (active) colors.primary else colors.onSurfaceVariant)
                                }
                            } else {
                                Surface(onClick = { onSelect(tab) },
                                    modifier = Modifier.width(itemWidth).heightIn(min = 72.dp).semantics { selected = active },
                                    color = Color.Transparent) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Surface(shape = CircleShape, color = if (active) colors.primaryContainer else Color.Transparent) {
                                            Icon(tab.icon(), contentDescription = tab.label, modifier = Modifier.padding(10.dp).size(24.dp),
                                                tint = if (active) colors.primary else colors.onSurfaceVariant)
                                        }
                                        Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                            color = if (active) colors.primary else colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    }
                }
}

private fun FarmerDestination.icon(): ImageVector = when (this) {
    FarmerDestination.HOME -> Icons.Default.Home
    FarmerDestination.ORDERS -> Icons.Default.ShoppingBag
    FarmerDestination.ADD -> Icons.Default.Add
    FarmerDestination.MAP -> Icons.Default.LocationOn
    FarmerDestination.SUPPLIES -> Icons.Default.Agriculture
    FarmerDestination.PROFILE -> Icons.Default.Person
}

@Composable
internal fun FarmerFeatureShell(title: String, icon: ImageVector, description: String, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, Modifier.padding(24.dp).size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FarmerAccount(auth: AuthResponse, errorMessage: String?, modifier: Modifier,
    isDarkTheme: Boolean, onToggleTheme: () -> Unit, onLogout: () -> Unit, isLoading: Boolean) {
    FarmerAccount(FarmerAccountDetails(auth.fullName, auth.email, auth.username, auth.phoneNumber, auth.region, auth.verificationStatus),
        errorMessage, modifier, isDarkTheme, onToggleTheme, onLogout, isLoading)
}

/** Display fields only; demo profiles are never AuthResponse/session objects. */
internal data class FarmerAccountDetails(val fullName: String, val email: String, val username: String? = null,
    val phoneNumber: String? = null, val region: String? = null, val verificationStatus: String? = null)

@Composable
internal fun FarmerAccount(auth: FarmerAccountDetails, errorMessage: String?, modifier: Modifier,
    isDarkTheme: Boolean, onToggleTheme: () -> Unit, onLogout: () -> Unit, isLoading: Boolean, demoMode: Boolean = false) {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    if (showAbout) AlertDialog(onDismissRequest = { showAbout = false },
        title = { Text("About Kilivana") },
        text = { Text("Kilivana connects farmers, buyers, suppliers and inspectors. Manage your crop listings, incoming orders and farm map from your farmer account.") },
        confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } })
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall)
        Text(auth.fullName, style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (demoMode) "Demonstration farmer · Sample details" else "Farmer account", style = MaterialTheme.typography.titleMedium)
                Text("Email: ${auth.email}")
                auth.username?.let { Text("Username: $it") }
                auth.phoneNumber?.let { Text("Phone: $it") }
                auth.region?.let { Text("Region: $it") }
                auth.verificationStatus?.let { Text("Verification: $it") }
            }
        }
        if (demoMode) Text("Demo changes are local to this session and are never sent to a server.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { showAbout = true }, modifier = Modifier.fillMaxWidth()) { Text("About") }
        OutlinedButton(onClick = onToggleTheme, modifier = Modifier.fillMaxWidth()) {
            Text(if (isDarkTheme) "Theme: Dark · Switch to light" else "Theme: Light · Switch to dark")
        }
        OutlinedButton(onClick = onLogout, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) { Text(if (demoMode) "Exit Demo Mode / Logout" else "Logout") }
        if (errorMessage != null) Text(errorMessage, color = MaterialTheme.colorScheme.error)
    }
}

private fun productError(error: Exception): String = when (error) {
    is com.example.farmapp.repository.ProductFailure -> error.message ?: "Unable to publish crop."
    is IllegalStateException -> "The previous creation result is unknown. Refresh My Crops to check your listing."
    else -> "Unable to reach Kilivana. Check your connection and try again."
}
