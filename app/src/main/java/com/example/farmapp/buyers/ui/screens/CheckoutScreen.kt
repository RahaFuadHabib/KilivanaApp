// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.data.DeliveryAddress
import com.example.farmapp.buyers.data.DeliveryQuote
import com.example.farmapp.buyers.model.CartItem
import com.example.farmapp.buyers.ui.theme.*
import com.example.farmapp.network.KenyanCounties

/** Pickup sub-choice: select from available stations or type a free-text location. */
enum class PickupChoice { STATION, TYPED }

@Composable
fun CheckoutScreen(
    items: List<CartItem>,
    addresses: List<DeliveryAddress>,
    addressesLoading: Boolean,
    addressError: String?,
    quote: DeliveryQuote?,
    invalidateQuote: () -> Unit,
    requestQuote: (Long) -> Unit,
    manageAddresses: () -> Unit,
    place: (String, Boolean, String) -> Unit,
    back: () -> Unit,
    submitting: Boolean = false,
    initialAddressId: Long? = null,
    onAddressSelected: (Long) -> Unit = {},
    /** Non-null when delivery method is PICKUP_STATION — shows selected station name/address. */
    deliveryContent: (@Composable () -> Unit)? = null,
    feedback: String? = null,
    deliveryMethodContent: @Composable () -> Unit = {}
) {
    var phone by remember { mutableStateOf("") }
    var selectedAddressId by remember(addresses) {
        mutableStateOf(initialAddressId?.takeIf { id -> addresses.any { it.id == id } } ?: addresses.firstOrNull()?.id)
    }
    var addressMenuExpanded by remember { mutableStateOf(false) }
    var countyMenuExpanded by remember { mutableStateOf(false) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var county by remember { mutableStateOf("") }
    val validPhone = runCatching { com.example.farmapp.orders.buyerMpesaPhone(phone) }.isSuccess
    var now by remember { mutableStateOf(java.time.Instant.now()) }
    LaunchedEffect(quote?.quoteId) { if (quote != null) while (true) { now = java.time.Instant.now(); kotlinx.coroutines.delay(1000) } }
    val activeQuote = quote?.takeIf {
        (it.deliveryMethod == "DOOR_DELIVERY" && deliveryContent == null && it.addressId == selectedAddressId ||
            it.deliveryMethod == "PICKUP_STATION" && deliveryContent != null) &&
            runCatching { java.time.Instant.parse(it.expiresAt).isAfter(now) }.getOrDefault(false)
    }
    val isPickup = deliveryContent != null

    Column(
        Modifier.fillMaxSize().background(NeutralBackground).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Checkout", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }

        // Delivery method tabs (Door / Pickup)
        deliveryMethodContent()

        // ── Step 1: Delivery ──────────────────────────────────────────────
        CheckoutSection("1. Delivery details") {
            if (isPickup) {
                // Pickup: show selected station or prompt
                deliveryContent!!()
            } else {
                // Door delivery: name, address, county
                OutlinedTextField(
                    firstName, { firstName = it }, Modifier.fillMaxWidth(),
                    label = { Text("First name") }, singleLine = true, enabled = !submitting
                )
                OutlinedTextField(
                    lastName, { lastName = it }, Modifier.fillMaxWidth(),
                    label = { Text("Last name") }, singleLine = true, enabled = !submitting
                )
                when {
                    addressesLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    addresses.isEmpty() -> {
                        Text(addressError ?: "No saved delivery addresses yet.",
                            color = if (addressError != null) ErrorRed else TextSecondary)
                        OutlinedButton(onClick = manageAddresses, modifier = Modifier.fillMaxWidth()) {
                            Text("Add delivery address")
                        }
                    }
                    else -> {
                        Box {
                            OutlinedButton(onClick = { addressMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(addresses.firstOrNull { it.id == selectedAddressId }?.label ?: "Select delivery address")
                            }
                            DropdownMenu(expanded = addressMenuExpanded, onDismissRequest = { addressMenuExpanded = false }) {
                                addresses.forEach { address ->
                                    DropdownMenuItem(
                                        text = { Text(address.label) },
                                        onClick = {
                                            if (selectedAddressId != address.id) invalidateQuote()
                                            selectedAddressId = address.id
                                            onAddressSelected(address.id)
                                            addressMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        TextButton(onClick = manageAddresses, modifier = Modifier.align(Alignment.End)) {
                            Text("Manage addresses")
                        }
                    }
                }
                // County dropdown
                Box {
                    OutlinedButton(onClick = { countyMenuExpanded = true }, modifier = Modifier.fillMaxWidth(), enabled = !submitting) {
                        Text(county.ifBlank { "Select county" })
                    }
                    DropdownMenu(expanded = countyMenuExpanded, onDismissRequest = { countyMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 300.dp)) {
                        KenyanCounties.all.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { county = c; countyMenuExpanded = false })
                        }
                    }
                }
                // Calculate delivery button
                Button(
                    onClick = { selectedAddressId?.let { onAddressSelected(it); requestQuote(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedAddressId != null && !addressesLoading && !submitting && validPhone &&
                        firstName.isNotBlank() && lastName.isNotBlank() && county.isNotBlank()
                ) { Text(if (submitting && activeQuote == null) "Calculating delivery…" else "Calculate delivery") }
            }
        }

        // ── Step 2: Payment ───────────────────────────────────────────────
        CheckoutSection("2. Payment") {
            Text("M-Pesa STK Push", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
            OutlinedTextField(
                phone, { phone = it }, Modifier.fillMaxWidth(),
                label = { Text("Kenyan mobile number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true, enabled = !submitting,
                isError = phone.isNotEmpty() && !validPhone,
                supportingText = { if (phone.isNotEmpty() && !validPhone) Text("Enter a valid Kenyan number") }
            )
        }

        // Feedback / errors
        feedback?.let { Text(it, color = ErrorRed) }
        if (quote != null && activeQuote == null)
            Text("Delivery quote expired or selection changed. Calculate delivery again.", color = ErrorRed)

        // ── Order summary + Place order (shown once quote is active) ──────
        activeQuote?.let { deliveryQuote ->
            CheckoutSection("Order summary") {
                items.forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${line.quantity} × ${line.product.name}", Modifier.weight(1f))
                        Text(com.example.farmapp.buyers.model.formatKes(
                            line.product.pricePerUnit.multiply(line.quantity.toBigDecimal())),
                            color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(color = NeutralDivider)
                SummaryLine("Items subtotal", "${deliveryQuote.currency} ${"%.2f".format(deliveryQuote.subtotal)}")
                SummaryLine("Delivery fee", "${deliveryQuote.currency} ${"%.2f".format(deliveryQuote.deliveryFee)}")
                SummaryLine("Total", "${deliveryQuote.currency} ${"%.2f".format(deliveryQuote.total)}", emphasize = true)
            }

            Button(
                onClick = { place(deliveryQuote.quoteId, true, phone) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting && validPhone
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else { Icon(Icons.Default.PhoneAndroid, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Create order & pay with M-Pesa") }
            }
        }

        if (items.isEmpty()) Text("Your cart is empty.", color = TextSecondary)
    }
}

@Composable
internal fun CheckoutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = NeutralSurface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            content()
        }
    }
}

@Composable
internal fun SummaryLine(label: String, value: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
        Text(value, style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (emphasize) Green700 else TextPrimary)
    }
}

/** Payment state machine display — shown on the payment screen after order creation. */
enum class MpesaPaymentState { IDLE, PENDING, PAID, FAILED }

@Composable
internal fun PaymentStatusScreen(
    status: com.example.farmapp.buyers.data.PaymentStatus,
    busy: Boolean,
    notice: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val paymentState = when {
        busy -> MpesaPaymentState.PENDING
        status.status in setOf("PAID", "HELD", "SETTLED", "COMPLETED") -> MpesaPaymentState.PAID
        status.status in setOf("FAILED", "CANCELLED", "EXPIRED") -> MpesaPaymentState.FAILED
        else -> MpesaPaymentState.IDLE
    }
    Column(
        Modifier.fillMaxSize().background(NeutralBackground).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(onClick = onBack) { Text("← Back to order") }
        when (paymentState) {
            MpesaPaymentState.PENDING -> {
                CircularProgressIndicator(color = Green600)
                Text("Waiting for M-Pesa confirmation…", style = MaterialTheme.typography.titleMedium)
                Text("Check your phone and enter your M-Pesa PIN.", color = TextSecondary)
            }
            MpesaPaymentState.PAID -> {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = Green600)
                Text("Payment confirmed", style = MaterialTheme.typography.headlineSmall, color = Green700)
                status.amount?.let { Text("Amount: KES ${"%.2f".format(it)}", color = TextSecondary) }
            }
            MpesaPaymentState.FAILED -> {
                Icon(Icons.Default.Cancel, null, Modifier.size(64.dp), tint = ErrorRed)
                Text("Payment failed", style = MaterialTheme.typography.headlineSmall, color = ErrorRed)
                notice?.let { Text(it, color = TextSecondary) }
                // Retry uses the existing pendingOrderId — no duplicate order created
                Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Retry payment") }
            }
            MpesaPaymentState.IDLE -> {
                notice?.let { Text(it, color = TextSecondary) }
            }
        }
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Text("Refresh payment status")
        }
    }
}
