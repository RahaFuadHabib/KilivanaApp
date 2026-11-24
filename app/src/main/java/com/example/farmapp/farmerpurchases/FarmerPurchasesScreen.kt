package com.example.farmapp.farmerpurchases

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.buyer.buyerDate
import com.example.farmapp.buyers.ui.theme.NeutralBackground
import com.example.farmapp.buyers.ui.theme.NeutralSurface
import com.example.farmapp.buyers.ui.theme.TextSecondary
import com.example.farmapp.checkout.*
import com.example.farmapp.data.model.canonical.CanonicalFarmerProduct
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.marketplace.*
import com.example.farmapp.network.TokenManager
import com.example.farmapp.orders.*

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
    var lastClearedTxKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(transactionState?.record?.key, transactionState?.record?.phase, state.busy) {
        val record = transactionState?.record
        if (record?.phase == "CONFIRMED" && !state.busy && lastClearedTxKey != record.key) {
            lastClearedTxKey = record.key
            controller.clearPurchasedItems(record.items)
        }
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
    Column(modifier.fillMaxSize().background(NeutralBackground)) {
        Box(Modifier.fillMaxWidth().background(OrderGreen)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("My Purchases", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        // Tab row as filter chips
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("catalogue" to "Catalogue", "basket" to (if (controller.backendCartEnabled) "Cart (${state.items.values.sumOf { it.toLong() }})" else "Basket (${state.items.size})"), "history" to "My purchases").forEach { (key, label) ->
                FilterChip(selected = page == key, onClick = {
                    when (key) {
                        "catalogue" -> page = "catalogue"
                        "basket" -> basket()
                        "history" -> { page = "history"; controller.history() }
                    }
                }, enabled = !state.busy, label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, if (page == key) OrderGreen else Color(0xFFDCE3E0)),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OrderGreen, selectedLabelColor = Color.White))
            }
            if (transactions?.enabled == true && transactionState?.record != null)
                FilterChip(selected = page == "transaction", onClick = { page = "transaction" }, enabled = !state.busy,
                    label = { Text("Resume checkout", style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, if (page == "transaction") OrderGreen else Color(0xFFDCE3E0)),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OrderGreen, selectedLabelColor = Color.White))
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
                    OrderSection {
                        Text(if (page == "basket") "Your cart" else "Delivery details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (page == "basket") Text("${state.items.values.sumOf { it.toLong() }} items", style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary)
                    }
                }
                if (page == "basket" && state.items.isEmpty()) item {
                    OrderSection {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(48.dp), tint = TextSecondary)
                            Text("Your cart is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Browse supplier products to add items.", color = TextSecondary)
                        }
                    }
                }
                state.serverCartId?.let { id -> if (page == "basket") item {
                    Text("Server cart #$id", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(start = 4.dp))
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
                    OrderSection("Order summary") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Estimated items", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text(estimatedItems?.let { "KES ${it.toPlainString()}" } ?: "Refresh prices", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Delivery and final total are confirmed during checkout.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        TextButton(onClick = controller::reviewBasket, enabled = !state.busy) { Text("Refresh items and prices") }
                        checkout.notice?.let { notice ->
                            Column {
                                Text(notice, color = MaterialTheme.colorScheme.error)
                                if (checkout.uncertain) {
                                    TextButton(onClick = { controller.checkout.recover() },
                                        enabled = !checkout.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Refresh checkout status")
                                    }
                                }
                            }
                        }
                    }
                }
                if (page == "delivery") {
                    item { Text("Select a pickup station (required)", style = MaterialTheme.typography.titleMedium) }
                    items(state.stations, key = { "s${it.id}" }) { station ->
                        val selected = state.selection == DeliverySelection.Station(station.id!!)
                        OutlinedButton(onClick = { controller.selectStation(station.id!!) }, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (selected) ButtonDefaults.outlinedButtonColors(contentColor = OrderGreen, containerColor = com.example.farmapp.buyers.ui.theme.MessaTint) else ButtonDefaults.outlinedButtonColors()) {
                            Icon(Icons.Default.Place, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text("Station #${station.id}: ${station.name}")
                        }
                    }
                    item {
                        state.selection?.let {
                            OrderSection("Selected delivery") {
                                val label = when (it) {
                                    is DeliverySelection.Station -> "Pickup station: ${state.stations.firstOrNull { row -> row.id == it.pickupStationId }?.name ?: "#${it.pickupStationId}"}"
                                    is DeliverySelection.TypedPickup, is DeliverySelection.Door -> "Select an available pickup station."
                                }
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = OrderGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (state.selection !is DeliverySelection.Station) {
                            Text("Choose an available pickup station before requesting a quote or creating an order.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (transactions?.enabled == true) FarmerTransactionPanel(transactions, state.items, state.selection, autoStart = true)
                        else {
                            OrderPrimaryButton("Get delivery quote", {}, false)
                            OrderPrimaryButton("Create purchase order", {}, false)
                        }
                    }
                }
            } else if (page == "transaction" && transactions?.enabled == true) {
                item { FarmerTransactionPanel(transactions, state.items, state.selection) }
            } else if (page == "history") {
                item {
                    OrderSection {
                        Text("My purchase orders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = controller::history, enabled = !state.busy) { Text("Refresh purchases") }
                    }
                }
                if (state.selected == null) {
                    if (state.orders.isEmpty() && !state.busy) item { OrderSection { Text("No verified purchase orders loaded.", color = TextSecondary) } }
                    items(state.orders, key = { it.id!! }) { order ->
                        val paymentConfirmed = orderPaymentConfirmed(order, null)
                        val badgeStatus = if (order.status in setOf("delivered", "completed", "cancelled", "disputed")) order.status.orEmpty() else if (paymentConfirmed) "paid" else order.status.orEmpty()
                        OrderSection(compact = true) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                Text("Purchase #${order.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(buyerDate(order.createdAt).substringBefore(","), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            OrderStatusBadge(
                                when (badgeStatus) {
                                    "paid" -> "Paid"
                                    "confirmed", "in_transit" -> "Processing"
                                    else -> badgeStatus.replace('_', ' ').replaceFirstChar { it.uppercase() }
                                }, badgeStatus)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(orderMoney(order.total), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (order.paymentStatus?.lowercase() == "pending" && order.status == "placed")
                                    OrderStatusBadge("Payment Pending", "pending")
                            }
                            OutlinedButton(onClick = { controller.openOrder(order.id!!); page = "details" }, enabled = !state.busy,
                                shape = RoundedCornerShape(7.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("View Details", color = OrderGreen) }
                        }
                    }
                }
            } else if (page == "details") {
                state.selected?.let { order ->
                    item {
                        OrderSection {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Purchase #${order.id}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                val status = order.paymentStatus?.lowercase().orEmpty()
                                OrderStatusBadge(if (status == "pending") "Payment Pending" else "Payment ${status.replaceFirstChar { it.uppercase() }}", status)
                            }
                            Text("Total: ${orderMoney(order.total)}", style = MaterialTheme.typography.bodyLarge)
                            Text("Order: ${order.status} · Payment: ${order.paymentStatus}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                    items(order.items.orEmpty(), key = { it.id!! }) { item ->
                        OrderSection(compact = true) {
                            Text(item.productName ?: "Product #${item.productId}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Quantity: ${item.quantity}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                    item {
                        BuyerPaymentSession(order) { paymentState, timedOut, refresh, initiate ->
                            BuyerPaymentControls(order, paymentState, timedOut, { refresh(); controller.payments.refresh(order.id!!) }, initiate, startExpanded = true)
                        }
                        TextButton(onClick = controller::track, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("View purchase tracking") }
                    }
                    items(state.jobs, key = { "j${it.id}" }) { job ->
                        OrderSection(compact = true) { Text("Delivery #${job.id}: ${job.status}", style = MaterialTheme.typography.bodyMedium) }
                    }
                    items(state.tracking) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
        if (page == "basket") {
            Surface(Modifier.fillMaxWidth(), color = NeutralSurface, shadowElevation = 6.dp, tonalElevation = 1.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (state.items.isEmpty()) "Your cart is empty" else
                        "${state.items.values.sumOf { it.toLong() }} items${estimatedItems?.let { " · KES ${it.toPlainString()} estimated" }.orEmpty()}",
                        style = MaterialTheme.typography.labelLarge)
                    OrderPrimaryButton("Proceed to checkout", { page = "delivery"; controller.delivery() },
                        !state.busy && !state.cartWriteBlocked && state.items.isNotEmpty())
                    if (state.cartWriteBlocked) {
                        Text("A cart update may have reached the server, but its item quantities cannot be read back. Checkout and further cart changes are paused to prevent duplicate items. Contact support to reconcile this cart.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    else if (state.items.isEmpty()) Text("Add supplier products to continue.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun FarmerBasketLine(name: String, quantity: Int, unit: String?, unitPrice: java.math.BigDecimal?,
    enabled: Boolean, onDecrease: () -> Unit, onIncrease: () -> Unit, onRemove: () -> Unit) {
    OrderSection(compact = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Product thumbnail placeholder
            Box(Modifier.size(48.dp).background(com.example.farmapp.buyers.ui.theme.NeutralSurface, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ShoppingCart, "Product image", tint = TextSecondary, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (unitPrice != null) Text("KES ${unitPrice.toPlainString()}${unit?.let { " / $it" }.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove $name from cart", tint = MaterialTheme.colorScheme.error)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton(onClick = onDecrease, enabled = enabled && quantity > 1, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease $name quantity")
                }
                Text(quantity.toString(), Modifier.widthIn(min = 40.dp).padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                OutlinedIconButton(onClick = onIncrease, enabled = enabled && quantity < Int.MAX_VALUE, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Increase $name quantity")
                }
            }
            Spacer(Modifier.weight(1f))
            val lineTotal = unitPrice?.multiply(quantity.toBigDecimal())
            Text(lineTotal?.let { orderMoney(it) } ?: "Price unavailable",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OrderGreen)
        }
    }
}
