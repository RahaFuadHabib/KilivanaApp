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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
        Modifier.fillMaxSize().background(NeutralBackground)
    ) {
        // Branded header
        Surface(color = GreenCard) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                Text("Checkout", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            }
        }
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
            Text("M-Pesa STK Push", style = MaterialTheme.typography.titleSmall, color = MessaGreen, fontWeight = FontWeight.SemiBold)
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
                HorizontalDivider(color = NeutralDivider)
                SummaryLine("Total", "${deliveryQuote.currency} ${"%.2f".format(deliveryQuote.total)}", emphasize = true)
            }

            Button(
                onClick = { place(deliveryQuote.quoteId, true, phone) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting && validPhone,
                colors = ButtonDefaults.buttonColors(containerColor = MessaGreen)
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else { Icon(Icons.Default.PhoneAndroid, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Create order & pay with M-Pesa") }
            }
        }

        if (items.isEmpty()) Text("Your cart is empty.", color = TextSecondary)
        }
    }
}

@Composable
internal fun CheckoutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = NeutralSurface, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(4.dp, 18.dp).background(Green600, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
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
enum class MpesaPaymentState { IDLE, PENDING, PAID, FAILED, CANCELLED }

private data class PaymentCard(val bg: Color, val tint: Color, val icon: ImageVector?, val title: String, val body: String)

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
        status.status.lowercase() in setOf("paid", "held", "settled", "completed") -> MpesaPaymentState.PAID
        status.status.lowercase() == "cancelled" -> MpesaPaymentState.CANCELLED
        status.status.lowercase() in setOf("failed", "expired") -> MpesaPaymentState.FAILED
        status.status.lowercase() == "pending" -> MpesaPaymentState.PENDING
        else -> MpesaPaymentState.IDLE
    }
    val card = when (paymentState) {
        MpesaPaymentState.PENDING -> PaymentCard(AmberSurface, AmberIcon, Icons.Default.PhoneAndroid,
            "Awaiting M-Pesa confirmation",
            "Enter your M-Pesa PIN on your phone. We'll update the status automatically once Safaricom responds.")
        MpesaPaymentState.PAID -> PaymentCard(SuccessSurface, Green600, Icons.Default.CheckCircle,
            "Payment confirmed",
            status.reference?.let { "Receipt: $it" } ?: "Your payment has been verified.")
        MpesaPaymentState.FAILED -> PaymentCard(DangerSurface, ErrorRed, Icons.Default.Cancel,
            "Payment was unsuccessful",
            notice ?: "The M-Pesa transaction did not complete. You can try again.")
        MpesaPaymentState.CANCELLED -> PaymentCard(CancelledSurface, CancelledText, Icons.Default.Cancel,
            "Payment cancelled",
            "You cancelled the M-Pesa prompt. Your order has not been paid. You can try again when ready.")
        MpesaPaymentState.IDLE -> PaymentCard(NeutralSurface, TextSecondary, Icons.Default.Info,
            "Payment status",
            notice ?: "Check your payment status below.")
    }
    Column(
        Modifier.fillMaxSize().background(NeutralBackground).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(onClick = onBack) { Text("← Back to order") }
        Surface(shape = RoundedCornerShape(20.dp), color = card.bg, modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (paymentState == MpesaPaymentState.PENDING) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(48.dp), color = card.tint, strokeWidth = 3.dp)
                    }
                } else {
                    card.icon?.let { Icon(it, null, Modifier.size(64.dp), tint = card.tint) }
                }
                Text(card.title, style = MaterialTheme.typography.titleLarge, color = card.tint)
                Text(card.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        if (status.amount != null || status.reference != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = NeutralSurface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    status.amount?.let {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Amount", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text("KES ${"%.2f".format(it)}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        }
                    }
                    status.reference?.let {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Receipt", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = Green600, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        if (paymentState == MpesaPaymentState.FAILED || paymentState == MpesaPaymentState.CANCELLED) {
            Button(
                onClick = onRefresh, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Green600)
            ) { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Retry payment") }
        }
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Refresh payment status")
        }
    }
}
