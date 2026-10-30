// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    initialAddressId:Long?=null, onAddressSelected:(Long)->Unit={},
    deliveryContent: (@Composable ()->Unit)?=null, feedback:String?=null, deliveryMethodContent:@Composable ()->Unit={}
) {
    var phone by remember { mutableStateOf("") }
    var selectedAddressId by remember(addresses) {
        mutableStateOf(initialAddressId?.takeIf {id->addresses.any{it.id==id}} ?: addresses.firstOrNull()?.id)
    }
    var addressMenuExpanded by remember { mutableStateOf(false) }
    var payment by remember { mutableStateOf("M-Pesa") }
    val validPhone = runCatching { com.example.farmapp.orders.buyerMpesaPhone(phone) }.isSuccess
    var now by remember { mutableStateOf(java.time.Instant.now()) }
    LaunchedEffect(quote?.quoteId) { if(quote!=null)while(true){now=java.time.Instant.now();kotlinx.coroutines.delay(1000)} }
    val activeQuote = quote?.takeIf {
        (it.deliveryMethod=="DOOR_DELIVERY" && deliveryContent==null && it.addressId==selectedAddressId || it.deliveryMethod=="PICKUP_STATION" && deliveryContent!=null) &&
            runCatching{java.time.Instant.parse(it.expiresAt).isAfter(now)}.getOrDefault(false)
    }

    Column(
        Modifier.fillMaxSize().background(NeutralBackground).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(back) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Checkout", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }
        Text("Delivery and payment details", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)

        deliveryMethodContent()
        CheckoutSection("Phone number") {
            OutlinedTextField(
                phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Kenyan mobile number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true,
                isError = phone.isNotEmpty() && !validPhone,
                supportingText = { if (!validPhone) Text("Enter a valid Kenyan number") }
            )
        }

        if(deliveryContent!=null) deliveryContent() else CheckoutSection("Delivery address") {
            when {
                addressesLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                addresses.isEmpty() -> {
                    Text(addressError ?: "No saved delivery addresses yet.", color = if (addressError != null) ErrorRed else TextSecondary)
                    OutlinedButton(onClick = manageAddresses, modifier = Modifier.fillMaxWidth()) { Text("Add delivery address") }
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
                }
            }
            if (addresses.isNotEmpty()) {
                TextButton(onClick = manageAddresses, modifier = Modifier.align(Alignment.End)) { Text("Manage addresses") }
            }
            Button(
                onClick = { selectedAddressId?.let {onAddressSelected(it);requestQuote(it)} },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedAddressId != null && !addressesLoading && !submitting && validPhone
            ) { Text(if (submitting && activeQuote == null) "Calculating delivery…" else "Calculate delivery") }
        }

        feedback?.let {Text(it,color=ErrorRed)}
        if(quote!=null && activeQuote==null)Text("Delivery quote expired or selection changed. Calculate delivery again.",color=ErrorRed)
        activeQuote?.let { deliveryQuote ->
            CheckoutSection("Order summary") {
                items.forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${line.quantity} × ${line.product.name}", Modifier.weight(1f))
                    }
                }
                HorizontalDivider(color = NeutralDivider)
                SummaryLine("Items subtotal", "${deliveryQuote.currency} ${"%.2f".format(deliveryQuote.subtotal)}")
                SummaryLine("Delivery", "${deliveryQuote.currency} ${"%.2f".format(deliveryQuote.deliveryFee)}")
                SummaryLine("Total", "${deliveryQuote.currency} ${"%.2f".format(deliveryQuote.total)}", emphasize = true)
            }

            CheckoutSection("Payment method") {
                listOf("M-Pesa").forEach { method ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(method, Modifier.weight(1f))
                        RadioButton(selected = payment == method, onClick = { payment = method })
                    }
                }
            }
            Button(
                onClick = { place(deliveryQuote.quoteId, payment == "M-Pesa", phone) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting && validPhone
            ) {
                if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Create order")
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
        Text(value, style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, color = if (emphasize) Green700 else TextPrimary)
    }
}

