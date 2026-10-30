package com.example.farmapp.buyer

// Shopping layout adapted selectively from brianmuigai2-stack/Kilivana-buyer (1813880).
// FarmApp owns all transport, sessions, canonical data and image delivery.
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.farmapp.data.model.canonical.CanonicalFarmerProduct
import com.example.farmapp.ui.screens.ProductImage
import java.math.BigDecimal

internal enum class BuyerDestination(val label: String) { HOME("Home"), SHOP("Shop"), CART("Cart"), SAVED("Saved"), ORDERS("Orders"), ACCOUNT("Account"), ADDRESSES("Addresses"), PROFILE("Profile"), DELIVERY("Delivery") }
internal val buyerTabs = listOf(BuyerDestination.HOME, BuyerDestination.SHOP, BuyerDestination.CART, BuyerDestination.ORDERS, BuyerDestination.ACCOUNT)
internal data class BuyerActions(
    val navigate: (BuyerDestination) -> Unit = {}, val refresh: () -> Unit = {},
    val search: (String, Long?) -> Unit = { _, _ -> }, val more: () -> Unit = {},
    val open: (Long) -> Unit = {}, val close: () -> Unit = {}, val saved: (Long) -> Unit = {},
    val add: (CanonicalFarmerProduct, Int) -> Unit = { _, _ -> }, val quantity: (Long, Int) -> Unit = { _, _ -> },
    val remove: (Long) -> Unit = {}, val refreshSelections: () -> Unit = {}, val refreshAddresses: () -> Unit = {},
    val dismissNotice: () -> Unit = {}, val checkout: () -> Unit = {}
)
internal fun buyerCartSubtotal(state: BuyerShoppingState): BigDecimal? {
    var total = BigDecimal.ZERO
    for ((id, quantity) in state.selection.cart) {
        val row = state.known[id] ?: return null
        if (id in state.unavailable) return null
        total = try { total.add(buyerLineTotal(row, quantity)) } catch (_: Exception) { return null }
    }
    return total
}
internal fun buyerSortedProducts(rows: List<CanonicalFarmerProduct>, order: Int) = when (order) {
    1 -> rows.sortedBy { it.unitPrice }
    2 -> rows.sortedByDescending { it.unitPrice }
    else -> rows
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun BuyerShoppingContent(destination: BuyerDestination, state: BuyerShoppingState,
    addresses: BuyerAddressState, name: String, actions: BuyerActions, modifier: Modifier = Modifier) {
    LaunchedEffect(state.notice) {
        if (state.notice != null) { delay(3500); actions.dismissNotice() }
    }
    val showDetail = state.selectedId != null
    var sort by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(showDetail) { actions.close() }
    PullToRefreshBox(isRefreshing = state.busy && destination in setOf(BuyerDestination.HOME, BuyerDestination.SHOP),
        onRefresh = { if (!showDetail && destination in setOf(BuyerDestination.HOME, BuyerDestination.SHOP)) actions.refresh() },
        modifier = modifier.fillMaxSize().testTag("buyer-refresh")) {
    LazyColumn(Modifier.fillMaxSize().testTag("buyer-list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp,
        bottom = if (destination == BuyerDestination.CART && state.selection.cart.isNotEmpty()) 100.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showDetail) {
            item { TextButton(onClick = actions.close) { Text("Back to shopping") } }
            if (state.detailBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Checking product availability…") }
            state.detailError?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { actions.open(state.selectedId!!) }) { Text("Retry product") } } }
            state.detail?.let { row -> item { BuyerDetail(row, row.productId.toLong() in state.selection.saved, actions) } }
        } else when (destination) {
            BuyerDestination.HOME -> {
                item {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(buyerWelcome(name), style = MaterialTheme.typography.headlineSmall)
                            Text("Enjoy shopping with us for the best farm produce", style = MaterialTheme.typography.titleLarge)
                            Button(onClick = { actions.navigate(BuyerDestination.SHOP) }) { Text("Browse produce") }
                        }
                    }
                }
                if (state.categories.isNotEmpty()) item {
                    Text("Shop by category", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { actions.search("", null); actions.navigate(BuyerDestination.SHOP) }, label = { Text("All") })
                        state.categories.forEach { category -> AssistChip(onClick = { actions.search("", category.id); actions.navigate(BuyerDestination.SHOP) }, label = { Text(category.name.orEmpty()) }) }
                    }
                }
                item { Text("Produce in your current results", style = MaterialTheme.typography.titleLarge) }
                item { CatalogFeedback(state, actions) }
                items(state.products.take(4), key = { it.productId }) { row -> BuyerProductCard(row, row.productId.toLong() in state.selection.saved, actions) }
                item { TextButton(onClick = { actions.navigate(BuyerDestination.ORDERS) }) { Text("Your orders and delivery tracking") } }
            }
            BuyerDestination.SHOP -> {
                item { Text("Farmer produce", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { actions.navigate(BuyerDestination.CART) }) { Text("Cart (${state.selection.cart.values.sumOf { it.toLong() }} items)") }
                    TextButton(onClick = { actions.navigate(BuyerDestination.SAVED) }) { Text("Saved items") }
                    BuyerSearch(state, actions) }
                item { CatalogFeedback(state, actions) }
                item {
                    Text("Sort loaded results", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Newest", "Price low to high", "Price high to low").forEachIndexed { index, label ->
                            FilterChip(selected = sort == index, onClick = { sort = index }, label = { Text(label) })
                        }
                    }
                    Text("Sorting covers loaded pages only. Load more for additional produce.", style = MaterialTheme.typography.bodySmall)
                }
                items(buyerSortedProducts(state.products, sort), key = { it.productId }) { row -> BuyerProductCard(row, row.productId.toLong() in state.selection.saved, actions) }
                if (!state.last) item { OutlinedButton(onClick = actions.more, enabled = !state.busy) { Text("Load more produce") } }
            }
            BuyerDestination.SAVED -> {
                item { Text("Saved produce", style = MaterialTheme.typography.headlineSmall); Text("Saved for this account on this device."); SelectionRefresh(state, actions) }
                if (state.selection.saved.isEmpty()) item { Text("No saved items yet. Tap a heart while shopping."); Button(onClick = { actions.navigate(BuyerDestination.SHOP) }) { Text("Find produce") } }
                items(state.selection.saved.toList(), key = { it }) { id ->
                    val row = state.known[id]
                    if (row != null && id !in state.unavailable) BuyerProductCard(row, true, actions)
                    else OutlinedCard { Column(Modifier.padding(16.dp)) { Text("Product #$id"); Text("Availability could not be verified. Refresh or remove this saved item."); TextButton(onClick = { actions.saved(id) }) { Text("Remove saved item") } } }
                }
            }
            BuyerDestination.CART -> {
                item { Text("Your cart", style = MaterialTheme.typography.headlineSmall); SelectionRefresh(state, actions) }
                if (state.selection.cart.isEmpty()) item { Text("Your cart is empty."); Button(onClick = { actions.navigate(BuyerDestination.SHOP) }) { Text("Start shopping") } }
                items(state.selection.cart.entries.toList(), key = { it.key }) { (id, quantity) ->
                    BuyerCartLine(id, quantity, state.known[id], id in state.unavailable, actions)
                }
                if (state.selection.cart.isNotEmpty()) item {
                    OutlinedCard { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val subtotal = buyerCartSubtotal(state)
                        Text(subtotal?.let { "Estimated items subtotal: KSh ${it.toPlainString()}" } ?: "Subtotal unavailable. Refresh and correct items that cannot be verified.", style = MaterialTheme.typography.titleMedium)
                        Text("Service charge (estimate): KSh 30.00")
                        Text("Delivery charge: select a delivery method")
                        Text(buyerEstimatedTotalBeforeDelivery(subtotal)?.let { "Estimated total before delivery: KSh ${it.toPlainString()}" }
                            ?: "Total: awaiting verified items", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { actions.navigate(BuyerDestination.ADDRESSES) }) { Text("View address book") }
                    } }
                }
            }
            BuyerDestination.ACCOUNT -> {
                item { Text("Your Buyer account", style = MaterialTheme.typography.headlineSmall); Text(name, style = MaterialTheme.typography.titleLarge) }
                item { Text("Shopping and account settings", style = MaterialTheme.typography.titleMedium) }
                listOf(BuyerDestination.PROFILE, BuyerDestination.ORDERS, BuyerDestination.SAVED, BuyerDestination.ADDRESSES).forEach { target ->
                    item { OutlinedButton(onClick = { actions.navigate(target) }, modifier = Modifier.fillMaxWidth()) { Text(when (target) { BuyerDestination.PROFILE -> "Edit your profile"; BuyerDestination.ORDERS -> "Orders and delivery tracking"; BuyerDestination.SAVED -> "Saved items"; else -> "Address book" }) } }
                }
            }
            BuyerDestination.ADDRESSES -> {
                item { Text("Address book", style = MaterialTheme.typography.headlineSmall); Text(BUYER_ADDRESS_NOTICE) }
                item { OutlinedButton(onClick = actions.refreshAddresses, enabled = !addresses.busy) { Text("Refresh addresses") } }
                if (addresses.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading saved addresses…") }
                addresses.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error); if (addresses.rows.isNotEmpty()) Text("Previously verified addresses are shown.") } }
                if (addresses.loaded && addresses.rows.isEmpty() && !addresses.busy && addresses.error == null) item { Text("No saved addresses returned for your account.") }
                items(addresses.rows, key = { it.id!! }) { row -> OutlinedCard { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.label?.takeIf { it.isNotBlank() } ?: "Saved address", style = MaterialTheme.typography.titleMedium)
                    Text(row.addressText.orEmpty())
                } } }
                item { Button(onClick = {}, enabled = false) { Text("Address editing unavailable") } }
            }
            else -> Unit // Existing orders/profile own their screens, repositories and validation.
        }
    }
    if (!showDetail && destination == BuyerDestination.CART && state.selection.cart.isNotEmpty()) {
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), tonalElevation = 3.dp) {
            val subtotal = buyerCartSubtotal(state)
            Button(onClick = actions.checkout, enabled = subtotal != null && !state.selectionBusy,
                modifier = Modifier.padding(12.dp).fillMaxWidth().testTag("buyer-checkout")) {
                Text(if (subtotal != null) "Checkout" else "Verify cart to checkout")
            }
        }
    }
    state.notice?.let { notice ->
        Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            action = { TextButton(onClick = actions.dismissNotice) { Text("Dismiss") } }) { Text(notice) }
    }
    }
}

@Composable private fun CatalogFeedback(state: BuyerShoppingState, actions: BuyerActions) {
    if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading produce…") }
    state.categoryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (state.loaded && state.products.isEmpty() && !state.busy && state.error == null) Text(if (state.last) "No available produce matches these filters." else "No available items on this page. Load more to continue.")
    TextButton(onClick = actions.refresh, enabled = !state.busy) { Text(if (state.error == null) "Refresh produce" else "Retry produce") }
}
@Composable private fun SelectionRefresh(state: BuyerShoppingState, actions: BuyerActions) {
    TextButton(onClick = actions.refreshSelections, enabled = !state.selectionBusy) { Text("Refresh availability") }
    if (state.selectionBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
}
@Composable private fun BuyerSearch(state: BuyerShoppingState, actions: BuyerActions) {
    var query by rememberSaveable(state.query) { mutableStateOf(state.query) }
    var categoryMenu by remember { mutableStateOf(false) }
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search produce") }, singleLine = true)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = state.category == null, onClick = { actions.search(query, null) }, enabled = !state.busy, label = { Text("All") })
        state.categories.forEach { category -> FilterChip(selected = state.category == category.id,
            onClick = { actions.search(query, category.id) }, enabled = !state.busy, label = { Text(category.name.orEmpty()) }) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { actions.search(query, state.category) }, enabled = !state.busy) { Text("Search") }
        TextButton(onClick = { query = ""; actions.search("", null) }, enabled = !state.busy) { Text("Clear filters") }
        Box {
            OutlinedButton(onClick = { categoryMenu = true }, enabled = !state.busy) { Text(state.categories.firstOrNull { it.id == state.category }?.name ?: if (state.category == null) "All categories" else "Selected category") }
            DropdownMenu(categoryMenu, { categoryMenu = false }) {
                DropdownMenuItem(text = { Text("All categories") }, onClick = { categoryMenu = false; actions.search(query, null) })
                state.categories.forEach { category -> DropdownMenuItem(text = { Text(category.name.orEmpty()) }, onClick = { categoryMenu = false; actions.search(query, category.id) }) }
            }
        }
    }
}
@Composable private fun BuyerProductCard(row: CanonicalFarmerProduct, saved: Boolean, actions: BuyerActions) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val image = row.images.firstOrNull { it.isPrimary == true } ?: row.images.firstOrNull()
        Box(Modifier.fillMaxWidth().height(160.dp).testTag("buyer-image-${row.productId}"), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().clickable { actions.open(row.productId.toLong()) }) {
                if (image != null) ProductImage(image.url, row.name)
                else Text("No product photo available", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { actions.saved(row.productId.toLong()) }, modifier = Modifier.align(Alignment.TopEnd)
                .padding(8.dp).size(48.dp).background(MaterialTheme.colorScheme.surface, CircleShape).testTag("buyer-heart-${row.productId}")) {
                Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (saved) "Unsave ${row.name}" else "Save ${row.name}", tint = MaterialTheme.colorScheme.primary)
            }
            FilledIconButton(onClick = { actions.add(row, 1) }, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(48.dp).testTag("buyer-add-${row.productId}"),
                enabled = row.status == com.example.farmapp.data.model.canonical.CanonicalProductStatus.ACTIVE && row.availableQuantity >= BigDecimal.ONE) {
                Icon(Icons.Default.Add, contentDescription = "Add one ${row.name} to cart")
            }
        }
        Text(row.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clickable { actions.open(row.productId.toLong()) })
        Text("KSh ${row.unitPrice.toPlainString()} / ${row.unit}", color = MaterialTheme.colorScheme.primary)
        Text("${row.availableQuantity.toPlainString()} ${row.unit} available")
        Text("Product reference: ${buyerProductReference(row.productId)}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { actions.open(row.productId.toLong()) }) { Text("View product") }

        }
    } }
}
@Composable private fun BuyerDetail(row: CanonicalFarmerProduct, saved: Boolean, actions: BuyerActions) {
    var quantity by rememberSaveable(row.productId) { mutableStateOf(row.minimumOrderQuantity.toPlainString()) }
    BuyerProductCard(row, saved, actions)
    Text(row.description.orEmpty(), Modifier.padding(vertical = 12.dp))
    val primary = row.images.firstOrNull { it.isPrimary == true } ?: row.images.firstOrNull()
    row.images.filter { it.imageId != primary?.imageId }.sortedBy { it.sortOrder ?: 0 }.forEach { ProductImage(it.url, row.name) }
    Text("Minimum order: ${row.minimumOrderQuantity.toPlainString()} ${row.unit}")
    val amount = quantity.toIntOrNull()
    val valid = amount != null && runCatching { buyerQuantity(row, amount) }.isSuccess
    OutlinedTextField(quantity, { quantity = it.filter { c -> c in '0'..'9' }.take(10) }, label = { Text("Quantity in ${row.unit}") }, singleLine = true,
        isError = !valid, modifier = Modifier.fillMaxWidth())
    if (valid) Text("Item estimate: KSh ${buyerLineTotal(row, amount!!).toPlainString()}")
    else Text("Enter a whole quantity between the minimum order and available stock.")
    Button(onClick = { actions.add(row, amount!!) }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("Add to cart") }
}
@Composable private fun BuyerCartLine(id: Long, quantity: Int, row: CanonicalFarmerProduct?, unavailable: Boolean, actions: BuyerActions) {
    val valid = row != null && !unavailable && runCatching { buyerQuantity(row, quantity) }.isSuccess
    OutlinedCard { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val image = row?.images?.firstOrNull { it.isPrimary == true } ?: row?.images?.firstOrNull()
            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                if (image != null) ProductImage(image.url, row?.name.orEmpty(), Modifier.fillMaxSize())
                else Text("No photo", style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row?.name ?: "Product #$id", style = MaterialTheme.typography.titleMedium)
                row?.let { Text("KSh ${it.unitPrice.toPlainString()} / ${it.unit}") }
                Text("Quantity: $quantity${row?.let { " ${it.unit}" }.orEmpty()}")
                if (valid) Text("Subtotal: KSh ${buyerLineTotal(row!!, quantity).toPlainString()}")
                else Text("Refresh to verify quantity and availability.", color = MaterialTheme.colorScheme.error)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            OutlinedIconButton(onClick = { actions.quantity(id, quantity - 1) }, modifier = Modifier.size(48.dp).testTag("buyer-minus-$id"),
                enabled = quantity > 1 && row != null && !unavailable && BigDecimal.valueOf((quantity - 1).toLong()) >= row.minimumOrderQuantity) {
                Icon(Icons.Default.Remove, "Decrease quantity of ${row?.name ?: id}")
            }
            Text(quantity.toString(), Modifier.padding(horizontal = 8.dp).testTag("buyer-quantity-$id"), style = MaterialTheme.typography.titleMedium)
            OutlinedIconButton(onClick = { actions.quantity(id, quantity + 1) }, modifier = Modifier.size(48.dp).testTag("buyer-plus-$id"),
                enabled = row != null && !unavailable && quantity < Int.MAX_VALUE && BigDecimal.valueOf(quantity.toLong() + 1) <= row.availableQuantity) {
                Icon(Icons.Default.Add, "Increase quantity of ${row?.name ?: id}")
            }
            IconButton(onClick = { actions.remove(id) }, modifier = Modifier.size(48.dp).testTag("buyer-remove-$id")) {
                Icon(Icons.Default.DeleteOutline, "Remove ${row?.name ?: "product"} from cart")
            }
        }
        TextButton(onClick = { actions.open(id) }) { Text("Review product") }
    } }
}
