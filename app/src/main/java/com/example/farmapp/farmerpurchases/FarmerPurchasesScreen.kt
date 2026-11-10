package com.example.farmapp.farmerpurchases

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

internal class FarmerPurchasesModel(application: Application) : AndroidViewModel(application) {
    private val expected = TokenManager.session.value
    private val persistence = FarmerPurchasePersistence(application, expected.auth!!.userId)
    val transactions = FarmerTransactionController(ApiClient.authenticatedService(FarmerTransactionApi::class.java),
        ApiClient.buyerOrdersApi, TokenManager, expected, FarmerTransactionPreferences(application, expected.auth!!.userId), viewModelScope,
        com.example.farmapp.BuildConfig.DEBUG && com.example.farmapp.BuildConfig.FARMER_TRANSACTIONS_ENABLED)
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
    var page by remember { mutableStateOf("catalogue") }
    fun basket() { page = "basket"; controller.reviewBasket() }
    BackHandler(page != "catalogue") { page = "catalogue" }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { page = "catalogue" }) { Text("Catalogue") }
            TextButton(onClick = ::basket, enabled = !state.busy) { Text(if (controller.backendCartEnabled) "Cart (${state.items.values.sumOf { it.toLong() }})" else "Basket (${state.items.size})") }
            TextButton(onClick = { page = "history"; controller.history() }, enabled = !state.busy) { Text("My purchases") }
            if (transactions?.enabled == true && transactionState?.record != null)
                TextButton(onClick = { page = "transaction" }, enabled = !state.busy) { Text("Resume checkout") }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.notice?.let { Text(it, Modifier.padding(12.dp), color = if (state.noticeIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        if (page == "catalogue") Box(Modifier.weight(1f)) { catalogue(controller::add) }
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page == "basket" || page == "delivery") {
                item { Text(if (controller.backendCartEnabled) "Your cart" else "Supplier purchase basket", style = MaterialTheme.typography.headlineSmall)
                    if (controller.backendCartEnabled) Text("Saved after server acknowledgement. Full server cart lines are not available.") }
                if (state.items.isEmpty()) item { Text(if (controller.backendCartEnabled) "No saved cart selections on this device." else "Your local basket is empty.") }
                state.serverCartId?.let { id -> item { Text("Server cart #$id. Item quantities are not included in the published cart response.") } }
                items(state.items.entries.toList(), key = { it.key }) { (id, quantity) ->
                    val product = state.products.firstOrNull { it.productId == id.toString() }
                    Text("${product?.name ?: state.labels[id] ?: "Product #$id"} — quantity $quantity")
                    if (controller.backendCartEnabled) Text("Added to cart", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    Row {
                        TextButton(onClick = { controller.quantity(id, state.items.getValue(id) - 1) }, enabled = !state.busy && state.items.getValue(id) > 1) { Text("?") }
                        Text("Quantity ${state.items.getValue(id)}")
                        TextButton(onClick = { controller.quantity(id, Math.addExact(state.items.getValue(id), 1)) }, enabled = !state.busy && state.items.getValue(id) < Int.MAX_VALUE) { Text("+") }
                    }
                    TextButton(onClick = { controller.remove(id) }, enabled = !state.busy) { Text("Remove #$id") }
                }
                item {
                    Text(state.estimatedSubtotal?.let { "Estimated items: KES ${it.toPlainString()}" } ?: "Item prices and availability need refreshing.")
                    TextButton(onClick = controller::reviewBasket, enabled = !state.busy) { Text("Refresh basket") }
                    Text("Delivery fee and final payable amount are not verified.")
                    if (transactions?.enabled != true) Text(FARMER_CHECKOUT_BLOCKED)
                    checkout.notice?.let { Text(it) }
                    Button(onClick = { page = "delivery"; controller.delivery() },
                        enabled = !state.busy && state.basketVerified && state.items.isNotEmpty()) { Text("Review delivery options") }
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
                        Text("Saved station associations are not checkout station IDs.")
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
                        Text("Payment review")
                        payment.payments.forEach { Text("Payment #${it.id}: ${it.status} (KES ${it.amount})") }
                        payment.notice?.let { Text(it) }
                        Text(FARMER_PAYMENT_BLOCKED)
                        Button(onClick = {}, enabled = false) { Text("Send M-Pesa prompt") }
                        TextButton(onClick = { controller.payments.refresh(order.id!!) }, enabled = !payment.busy) { Text("Refresh payment status") }
                        TextButton(onClick = controller::track, enabled = !state.busy) { Text("View purchase tracking") }
                    }
                    items(state.jobs, key = { "j${it.id}" }) { Text("Delivery #${it.id}: ${it.status}") }
                    items(state.tracking) { Text(it) }
                }
            }
        }
    }
}
