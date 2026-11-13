package com.example.farmapp.farmerpurchases

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.checkout.*
import com.example.farmapp.data.model.canonical.CanonicalFarmerProduct
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.marketplace.*
import com.example.farmapp.network.TokenManager
import com.example.farmapp.orders.BuyerPaymentControls
import com.example.farmapp.orders.BuyerPaymentSession

internal class FarmerPurchasesModel(application: Application) : AndroidViewModel(application) {
    private val expected = TokenManager.session.value
    private val persistence = FarmerPurchasePersistence(application, expected.auth!!.userId)
    val transactions = FarmerTransactionController(ApiClient.authenticatedService(FarmerTransactionApi::class.java),
        ApiClient.buyerOrdersApi, TokenManager, expected, FarmerTransactionPreferences(application, expected.auth!!.userId), viewModelScope,
        true)
    private val orders = FarmerCheckoutRepository(ApiClient.buyerOrdersApi, TokenManager, expected)
    val controller = FarmerPurchaseController(MarketplaceRepository(ApiClient.marketplaceApi, TokenManager), orders,
        FarmerAddressRepository(ApiClient.buyerAddressApi, TokenManager, expected),
        FarmerPickupRepository(ApiClient.buyerPickupApi, TokenManager, expected),
        FarmerTrackingRepository(ApiClient.deliveryTrackingApi, orders, TokenManager, expected),
        FarmerPaymentRepository(ApiClient.buyerOrdersApi, TokenManager, expected), TokenManager, expected,
        persistence.journal, persistence.attempts, persistence.serverBasket, viewModelScope,
        FarmerCartRepository(ApiClient.authenticatedService(PurchaserCartApi::class.java), TokenManager, expected, persistence.cartWrites))
    init { if (transactions.enabled) transactions.recover() }
}

@Composable internal fun FarmerPurchasesScreen(modifier: Modifier = Modifier) {
    val session by TokenManager.session.collectAsStateWithLifecycle()
    if (session.isDemo || session.auth?.role != "FARMER" || session.auth?.backendContract != "canonical") {
        MarketplaceScreen(modifier); return
    }
    val model: FarmerPurchasesModel = viewModel(key = "farmer-purchases-${session.generation}")
    val purchaseState by model.controller.state.collectAsStateWithLifecycle()
    FarmerPurchasesContent(model.controller, modifier, transactions = model.transactions) { add ->
        MarketplaceScreen(Modifier.fillMaxSize(), cardActions = { product -> FarmerCardCartAction(product, purchaseState, add) }) { product ->
            FarmerProductSelection(product, !purchaseState.busy && !purchaseState.cartWriteBlocked, add)
            purchaseState.items[product.productId.toLong()]?.let { Text("Added to cart (quantity $it)", color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable internal fun FarmerProductSelection(product: CanonicalFarmerProduct, enabled: Boolean, add: (CanonicalFarmerProduct, Int) -> Unit) {
    var quantity by remember(product.productId) { mutableStateOf(product.minimumOrderQuantity.setScale(0, java.math.RoundingMode.CEILING).toPlainString()) }
    OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, singleLine = true)
    Button(onClick = { quantity.toIntOrNull()?.let { add(product, it) } }, enabled = enabled && quantity.toIntOrNull()?.let { it > 0 } == true) {
        Text("Add to cart")
    }
    Text("Supplier products are added to your authenticated server cart.")
}

@Composable internal fun FarmerPurchasesContent(controller: FarmerPurchaseController, modifier: Modifier = Modifier,
    transactions: FarmerTransactionController? = null,
    catalogue: @Composable ((CanonicalFarmerProduct, Int) -> Unit) -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val checkout by controller.checkout.state.collectAsStateWithLifecycle()
    val payment by controller.payments.state.collectAsStateWithLifecycle()
    val transactionState = transactions?.state?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(transactionState?.record?.key, transactionState?.record?.phase, state.busy) {
        val record = transactionState?.record
        if (record?.phase == "CONFIRMED" && !state.busy) controller.clearPurchasedItems(record.items)
    }
    var page by remember { mutableStateOf("catalogue") }
    fun basket() { page = "basket"; controller.reviewBasket() }
    BackHandler(page != "catalogue") { page = if (page == "delivery") "basket" else "catalogue" }
    val estimatedItems = if (state.items.isNotEmpty() && state.items.keys.all { id ->
            state.products.any { it.productId == id.toString() }
        }) state.items.entries.fold(java.math.BigDecimal.ZERO) { sum, (id, quantity) ->
            val product = state.products.first { it.productId == id.toString() }
            sum + product.unitPrice.multiply(quantity.toBigDecimal())
        } else null
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { page = "catalogue" }) { Text("Catalogue") }
            TextButton(onClick = ::basket, enabled = !state.busy) { Text(if (controller.backendCartEnabled) "Cart (${state.items.values.sumOf { it.toLong() }})" else "Basket (${state.items.size})") }
            TextButton(onClick = { page = "history"; controller.history() }, enabled = !state.busy) { Text("My purchases") }
            if (transactions?.enabled == true && transactionState?.record != null)
                TextButton(onClick = { page = "transaction" }, enabled = !state.busy) { Text("Resume checkout") }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.notice?.let {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (state.noticeIsError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) {
                Text(it, Modifier.padding(12.dp), color = if (state.noticeIsError)
                    MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (page == "catalogue") Box(Modifier.weight(1f)) { catalogue(controller::add) }
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page == "basket" || page == "delivery") {
                item {
                    Text(if (page == "basket") "Your cart" else "Delivery details", style = MaterialTheme.typography.headlineSmall)
                    if (page == "basket") Text("${state.items.values.sumOf { it.toLong() }} items", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (page == "basket" && state.items.isEmpty()) item {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Your cart is empty", style = MaterialTheme.typography.titleMedium)
                            Text("Browse supplier products to add items.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                state.serverCartId?.let { id -> if (page == "basket") item {
                    Text("Server cart #$id", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                items(state.items.entries.toList(), key = { it.key }) { (id, quantity) ->
                    val product = state.products.firstOrNull { it.productId == id.toString() }
                    FarmerBasketLine(product?.name ?: state.labels[id] ?: "Product #$id", quantity,
                        product?.unit, product?.unitPrice, !state.busy,
                        onDecrease = { controller.quantity(id, quantity - 1) },
                        onIncrease = { controller.quantity(id, Math.addExact(quantity, 1)) },
                        onRemove = { controller.remove(id) })
                }
                if (page == "basket" && state.items.isNotEmpty()) item {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Order summary", style = MaterialTheme.typography.titleSmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Estimated items")
                                Text(estimatedItems?.let { "KES ${it.toPlainString()}" } ?: "Refresh prices")
                            }
                            Text("Delivery and final total are confirmed during checkout.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = controller::reviewBasket, enabled = !state.busy) { Text("Refresh items and prices") }
                            checkout.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                if (page == "delivery") {
                    item { Text(if (transactions?.enabled == true) "Delivery selection" else "Delivery selection (read-only checkout review)") }
                    items(state.addresses, key = { "a${it.id}" }) { address ->
                        OutlinedButton(onClick = { controller.selectAddress(address.id!!) }, enabled = !state.busy) {
                            Text("Address #${address.id}: ${address.addressText}")
                        }
                    }
                    items(state.stations, key = { "s${it.id}" }) { station ->
                        OutlinedButton(onClick = { controller.selectStation(station.id!!) }, enabled = !state.busy) {
                            Text("Station #${station.id}: ${station.name}")
                        }
                    }
                    item {
                        state.selection?.let { Text("Selected: $it") }
                        if (transactions?.enabled == true) FarmerTransactionPanel(transactions, state.items, state.selection)
                        else {
                            Button(onClick = {}, enabled = false) { Text("Get delivery quote") }
                            Button(onClick = {}, enabled = false) { Text("Create purchase order") }
                        }
                    }
                }
            } else if (page == "transaction" && transactions?.enabled == true) {
                item { FarmerTransactionPanel(transactions, state.items, state.selection) }
            } else if (page == "history") {
                item { Text("My purchase orders", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = controller::history, enabled = !state.busy) { Text("Refresh purchases") } }
                if (state.selected == null) {
                    if (state.orders.isEmpty() && !state.busy) item { Text("No verified purchase orders loaded.") }
                    items(state.orders, key = { it.id!! }) { order ->
                        OutlinedButton(onClick = { controller.openOrder(order.id!!); page = "details" }, enabled = !state.busy) {
                            Text("Purchase #${order.id}: KES ${order.total} — ${order.status}")
                        }
                    }
                }
            } else if (page == "details") {
                state.selected?.let { order ->
                    item { Text("Purchase #${order.id}", style = MaterialTheme.typography.headlineSmall)
                        Text("Purchaser #${order.buyerId}")
                        Text("Total: KES ${order.total}")
                        Text("Order: ${order.status} · Payment: ${order.paymentStatus}") }
                    items(order.items.orEmpty(), key = { it.id!! }) { Text("${it.productName ?: "Product #${it.productId}"}: ${it.quantity}") }
                    item {
                        BuyerPaymentSession(order) { paymentState, refresh, initiate ->
                            BuyerPaymentControls(order, paymentState, { refresh(); controller.payments.refresh(order.id!!) }, initiate, startExpanded = true)
                        }
                        TextButton(onClick = controller::track, enabled = !state.busy) { Text("View purchase tracking") }
                    }
                    items(state.jobs, key = { "j${it.id}" }) { Text("Delivery #${it.id}: ${it.status}") }
                    items(state.tracking) { Text(it) }
                }
            }
        }
        if (page == "basket") {
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp, tonalElevation = 1.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.items.isEmpty()) "Your cart is empty" else
                        "${state.items.values.sumOf { it.toLong() }} items${estimatedItems?.let { " · KES ${it.toPlainString()} estimated" }.orEmpty()}",
                        style = MaterialTheme.typography.labelLarge)
                    Button(onClick = { page = "delivery"; controller.delivery() },
                        enabled = !state.busy && !state.cartWriteBlocked && state.items.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                        Text("Proceed to checkout")
                    }
                    if (state.cartWriteBlocked) Text("Resolve the pending cart update before continuing.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    else if (state.items.isEmpty()) Text("Add supplier products to continue.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FarmerBasketLine(name: String, quantity: Int, unit: String?, unitPrice: java.math.BigDecimal?,
    enabled: Boolean, onDecrease: () -> Unit, onIncrease: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (unitPrice != null) Text("KES ${unitPrice.toPlainString()}${unit?.let { " / $it" }.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove $name from cart",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedIconButton(onClick = onDecrease, enabled = enabled && quantity > 1,
                        modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease $name quantity")
                    }
                    Text(quantity.toString(), Modifier.widthIn(min = 44.dp).padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.titleSmall)
                    OutlinedIconButton(onClick = onIncrease, enabled = enabled && quantity < Int.MAX_VALUE,
                        modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Increase $name quantity")
                    }
                }
                Spacer(Modifier.weight(1f))
                val lineTotal = unitPrice?.multiply(quantity.toBigDecimal())
                Text(lineTotal?.let { "KES ${it.toPlainString()}" } ?: "Price unavailable",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
