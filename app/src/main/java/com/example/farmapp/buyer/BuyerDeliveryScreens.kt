package com.example.farmapp.buyer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

internal enum class BuyerDeliveryMethod { PICKUP, DOOR }
private enum class DeliveryStep { METHOD, PICKUP, ADDRESS, REVIEW }

/** Pure presentation boundary; screens never submit orders or payments on navigation. */
@Composable internal fun BuyerDeliveryFlow(shopping: BuyerShoppingState, addresses: BuyerAddressState,
    checkout: BuyerCheckoutReview, method: BuyerDeliveryMethod, onMethod: (BuyerDeliveryMethod) -> Unit,
    onSelect: (Long) -> Unit, onCreate: (BuyerAddressRequest) -> Unit, onRefresh: () -> Unit,
    onCheckCart: () -> Unit, onBackToCart: () -> Unit, modifier: Modifier = Modifier,
    pickups: BuyerPickupState = BuyerPickupState(loaded = true), pickupActions: BuyerPickupActions = BuyerPickupActions(), onOrders: (() -> Unit)? = null) {
    var step by rememberSaveable { mutableStateOf(DeliveryStep.METHOD) }
    fun back() { if (step == DeliveryStep.METHOD) onBackToCart() else step = DeliveryStep.METHOD }
    BackHandler { back() }
    LaunchedEffect(addresses.createdId) { if (addresses.createdId != null) step = DeliveryStep.METHOD }
    LazyColumn(modifier.fillMaxSize().imePadding().testTag("delivery-list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to previous checkout step") }
            Text(when (step) {
                DeliveryStep.METHOD -> "Select delivery method"
                DeliveryStep.PICKUP -> "Add pickup station"
                DeliveryStep.ADDRESS -> "Add New Address"
                DeliveryStep.REVIEW -> "Review Order"
            }, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        } }
        when (step) {
            DeliveryStep.METHOD -> {
                item { TabRow(selectedTabIndex = if (method == BuyerDeliveryMethod.PICKUP) 0 else 1) {
                    Tab(selected = method == BuyerDeliveryMethod.PICKUP, onClick = { onMethod(BuyerDeliveryMethod.PICKUP) },
                        text = { Text("Pickup Stations", textAlign = TextAlign.Center) })
                    Tab(selected = method == BuyerDeliveryMethod.DOOR, onClick = { onMethod(BuyerDeliveryMethod.DOOR) },
                        text = { Text("Door Delivery", textAlign = TextAlign.Center) })
                } }
                if (method == BuyerDeliveryMethod.PICKUP) {
                    pickupFeedback(pickups, pickupActions)
                    item { TextButton(onClick = { pickupActions.refresh(pickups.region) }, enabled = !pickups.busy) { Text("Refresh pickup stations") } }
                    if (pickups.loaded && pickups.saved.isEmpty() && !pickups.busy && pickups.error == null)
                        item { DeliveryEmpty(true, { step = DeliveryStep.PICKUP }) }
                    else {
                        items(pickups.saved, key = { it.id!! }) { row -> BuyerSavedPickupCard(row, pickups, pickupActions) }
                        item { Button(onClick = { step = DeliveryStep.PICKUP }, modifier = Modifier.fillMaxWidth()) { Text("Add New Pickup Station") } }
                    }
                    if (pickups.loaded && !pickups.available && pickups.error == null) item { Text("Pickup is currently unavailable.") }
                    item { Button(onClick = { step = DeliveryStep.REVIEW; onCheckCart() },
                        enabled = pickupSelectionValid(pickups) && shopping.selection.cart.isNotEmpty() && buyerCartSubtotal(shopping) != null,
                        modifier = Modifier.fillMaxWidth().testTag("pickup-review")) { Text("Continue to review") } }
                } else {
                    item { TextButton(onClick = onRefresh, enabled = !addresses.busy && !addresses.saving) { Text("Refresh saved addresses") } }
                    if (addresses.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading saved addresses...") }
                    addresses.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRefresh, enabled = !addresses.busy) { Text("Retry addresses") } } }
                    addresses.saveError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                    if (addresses.loaded && addresses.rows.isEmpty() && !addresses.busy && addresses.error == null) {
                        item { DeliveryEmpty(false, { step = DeliveryStep.ADDRESS }) }
                    } else {
                        items(addresses.rows, key = { it.id!! }) { address ->
                            val selected = addresses.selectedId == address.id
                            OutlinedCard(onClick = { onSelect(address.id!!) }, enabled = !addresses.busy && !addresses.saving && addresses.error == null, modifier = Modifier.fillMaxWidth()
                                .testTag("delivery-address-${address.id}").semantics { this.selected = selected }) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected, onClick = { onSelect(address.id!!) }, enabled = !addresses.busy && !addresses.saving && addresses.error == null)
                                    Column(Modifier.weight(1f)) {
                                        Text(address.label?.takeIf { it.isNotBlank() } ?: "Delivery address", style = MaterialTheme.typography.titleMedium)
                                        Text(address.addressText.orEmpty())
                                        if (selected) Text("Selected", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        item { Button(onClick = { step = DeliveryStep.ADDRESS }, enabled = !addresses.saving,
                            modifier = Modifier.fillMaxWidth()) { Text("Add New Address") } }
                    }
                    item { Button(onClick = { step = DeliveryStep.REVIEW; onCheckCart() },
                        enabled = buyerDeliverySelectionValid(addresses) && shopping.selection.cart.isNotEmpty() && buyerCartSubtotal(shopping) != null,
                        modifier = Modifier.fillMaxWidth().testTag("delivery-review")) { Text("Continue to review") } }
                    if (addresses.saveUncertain) item { Text("An earlier address save is unresolved. Refresh the list to check whether it was saved; do not submit it again.") }
                }
            }
            DeliveryStep.PICKUP -> {
                pickupFeedback(pickups, pickupActions)
                pickupRegionChips(pickups, pickupActions)
                if (pickups.loaded && pickups.stations.isEmpty() && !pickups.busy && pickups.error == null) item {
                    Text(if (pickups.available) "No available pickup stations in this region." else "Pickup stations unavailable", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { onMethod(BuyerDeliveryMethod.DOOR); step = DeliveryStep.METHOD }) { Text("Choose Door Delivery") }
                }
                items(pickups.stations, key = { it.id!! }) { row -> BuyerAvailablePickupCard(row, pickups, pickupActions) }
                item { Button(onClick = { step = DeliveryStep.REVIEW; onCheckCart() }, enabled = pickupSelectionValid(pickups) &&
                    shopping.selection.cart.isNotEmpty() && buyerCartSubtotal(shopping) != null, modifier = Modifier.fillMaxWidth()) { Text("Review pickup delivery") } }
            }
            DeliveryStep.ADDRESS -> item { BuyerAddressForm(addresses, onCreate) }
            DeliveryStep.REVIEW -> {
                val selected = addresses.rows.firstOrNull { it.id == addresses.selectedId }
                item { Text(if (method == BuyerDeliveryMethod.DOOR) "Door Delivery" else "Pickup Station", style = MaterialTheme.typography.titleMedium) }
                item { Text(if (method == BuyerDeliveryMethod.DOOR) selected?.addressText ?: "Select a delivery address."
                    else pickups.selected?.let { "${it.name}\n${it.address}" } ?: "Select an available pickup station.") }
                item { OutlinedButton(onClick = { step = DeliveryStep.METHOD }) { Text("Change delivery location") } }
                item { BuyerDeliveryTotals(buyerCartSubtotal(shopping)) }
                if (method == BuyerDeliveryMethod.DOOR && !buyerDeliverySelectionValid(addresses) || method == BuyerDeliveryMethod.PICKUP && !pickupSelectionValid(pickups)) item { Text("Refresh and select a delivery location.", color = MaterialTheme.colorScheme.error) }
                if (checkout.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item { Text("We can?t verify the items in your server cart yet, so a new order cannot be placed. Your shopping cart is saved. To pay for an existing order, open Orders.") }
                if (onOrders != null) item { OutlinedButton(onClick = onOrders, modifier = Modifier.fillMaxWidth()) { Text("View existing orders") } }
                item { Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().testTag("delivery-place-order")) { Text("Place order unavailable") } }
            }
        }
    }
}
@Composable private fun DeliveryEmpty(pickup: Boolean, add: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Icon(if (pickup) Icons.Default.Storefront else Icons.Default.LocalShipping, null,
            Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text(if (pickup) "No saved pickup station yet!" else "No saved delivery address yet!",
            style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(if (pickup) "Add a pickup station to continue. It'll be saved so you won't need to select it again next time."
            else "Add a delivery address to continue. You can select it again next time.", textAlign = TextAlign.Center)
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Text(if (pickup) "Add New Pickup Station" else "Add New Address") }
    }
}
@Composable private fun BuyerAddressForm(state: BuyerAddressState, save: (BuyerAddressRequest) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    val request = runCatching { buyerAddressRequest(label, text, latitude, longitude) }.getOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Save this location to your account. Include county, town or area, street or landmark, and any delivery instructions in the full address.")
        OutlinedTextField(label, { label = it }, label = { Text("Label (optional)") }, singleLine = true,
            enabled = !state.saving, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(text, { text = it }, label = { Text("Full delivery address") }, minLines = 3,
            enabled = !state.saving, modifier = Modifier.fillMaxWidth())
        Text("Coordinates are optional. Leave both blank if you do not know them.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(latitude, { latitude = it }, label = { Text("Latitude (optional)") }, singleLine = true,
            enabled = !state.saving, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(longitude, { longitude = it }, label = { Text("Longitude (optional)") }, singleLine = true,
            enabled = !state.saving, modifier = Modifier.fillMaxWidth())
        state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.saveUncertain) Text("A previous save may have reached the server. Go back and refresh saved addresses instead of repeating it.")
        if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = { request?.let(save) }, enabled = request != null && !state.saving && !state.saveUncertain,
            modifier = Modifier.fillMaxWidth()) { Text("Save address") }
    }
}
@Composable internal fun BuyerDeliveryTotals(subtotal: BigDecimal?, quote: BuyerDeliveryQuote? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Items subtotal: KSh ${(quote?.subtotal ?: subtotal)?.toPlainString() ?: "unverified"}")
        Text(if (quote == null) "Service charge (estimate): KSh 30.00" else "Service charge estimate (not in confirmed total): KSh 30.00")
        Text(quote?.deliveryFee?.let { "Delivery fee: KSh ${it.toPlainString()}" } ?: "Delivery fee: awaiting a confirmed quote")
        Text(quote?.total?.let { "Final total: KSh ${it.toPlainString()}" } ?: "Final total: not yet confirmed",
            style = MaterialTheme.typography.titleMedium)
    }
}
