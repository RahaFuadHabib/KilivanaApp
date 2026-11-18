package com.example.farmapp.farmerpurchases

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.farmapp.buyers.ui.theme.AmberIcon
import com.example.farmapp.buyers.ui.theme.AmberSurface
import com.example.farmapp.buyers.ui.theme.CancelledSurface
import com.example.farmapp.buyers.ui.theme.CancelledText
import com.example.farmapp.buyers.ui.theme.DangerSurface
import com.example.farmapp.buyers.ui.theme.ErrorRed
import com.example.farmapp.buyers.ui.theme.MessaGreen
import com.example.farmapp.buyers.ui.theme.MessaTint
import com.example.farmapp.buyers.ui.theme.NeutralBackground
import com.example.farmapp.buyers.ui.theme.NeutralSurface
import com.example.farmapp.buyers.ui.theme.SuccessSurface
import com.example.farmapp.buyers.ui.theme.TextSecondary
import com.example.farmapp.checkout.DeliverySelection
import com.example.farmapp.orders.OrderPrimaryButton
import com.example.farmapp.orders.OrderSection
import com.example.farmapp.orders.OrderStatusBadge

@Composable internal fun FarmerTransactionPanel(controller: FarmerTransactionController,
    items: Map<Long, Int>, selection: DeliverySelection?, autoStart: Boolean = false) {
    val state by controller.state.collectAsStateWithLifecycle()
    var phone by remember(controller) { mutableStateOf(controller.state.value.record?.phoneNumber.orEmpty()) }
    var confirmOrder by remember { mutableStateOf(false) }
    var confirmPayment by remember { mutableStateOf(false) }
    LaunchedEffect(controller) { if (controller.enabled && controller.state.value.record != null) controller.recover() }
    // When arriving from the basket with items, auto-start a fresh quote if the previous transaction is done.
    // Key on record.phase so it fires when the phase transitions to CONFIRMED (e.g. after recover()).
    val recordPhase = state.record?.phase
    LaunchedEffect(autoStart, selection, items, recordPhase) {
        if (autoStart && controller.enabled && selection != null && items.isNotEmpty()) {
            if (recordPhase == null || recordPhase == "CONFIRMED" || recordPhase == "PAYMENT_FAILED" || recordPhase == "PAYMENT_CANCELLED") {
                controller.prepare(items, selection)
            }
        }
    }
    val record = state.record
    // When autoStart is active (arrived from basket), hide the previous CONFIRMED transaction
    // so the user sees the fresh-start UI instead of the old "Payment confirmed" banner.
    val showFresh = autoStart && record?.phase == "CONFIRMED"
    Column(Modifier.fillMaxWidth().background(NeutralBackground).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        state.notice?.let {
            Surface(shape = RoundedCornerShape(12.dp), color = if (state.busy) AmberSurface else DangerSurface, modifier = Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MessaGreen)
        if (record == null || showFresh) {
            // ── Get delivery quote ───────────────────────────────────────
            OrderSection("Get delivery quote") {
                Text("Select a delivery address or pickup station, then request a quote to see the total before creating your order.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                OrderPrimaryButton("Get delivery quote", { selection?.let { controller.prepare(items, it) } },
                    controller.enabled && selection != null && items.isNotEmpty())
            }
        } else {
            // ── Quote summary card ───────────────────────────────────────
            record.quote?.let { quote ->
                OrderSection("Order summary") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Items", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text("KES ${quote.subtotal.stripTrailingZeros()}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Delivery", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text("KES ${quote.deliveryFee.stripTrailingZeros()}", style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(color = Color(0xFFDCE3E0))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("KES ${quote.total.stripTrailingZeros()}", style = MaterialTheme.typography.titleMedium, color = com.example.farmapp.orders.OrderGreen, fontWeight = FontWeight.Bold)
                    }
                    if (quote.cartId == null && quote.items == null)
                        Text("Quote totals and delivery will be checked against the created order before payment.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            // ── Phase-specific status card ───────────────────────────────
            val statusCard = when (record.phase) {
                "QUOTE_READY" -> FarmerStatusCard(AmberSurface, AmberIcon, Icons.Default.PhoneAndroid, "Ready to order", "Review your order summary above, enter your M-Pesa number, and create the purchase order.")
                "ORDER_PENDING" -> FarmerStatusCard(AmberSurface, AmberIcon, Icons.Default.Refresh, "Creating order…", "Submitting your purchase order. Please wait.", spinner = true)
                "ORDER_CREATED" -> FarmerStatusCard(AmberSurface, AmberIcon, Icons.Default.PhoneAndroid, "Order created", "Purchase order #${record.orderId}. Enter your M-Pesa number below to send a payment prompt.")
                "PAYMENT_PENDING" -> FarmerStatusCard(AmberSurface, AmberIcon, Icons.Default.PhoneAndroid, "Awaiting M-Pesa confirmation", "M-Pesa request sent for purchase #${record.orderId}. Enter your PIN on your phone and wait for confirmation. This attempt must resolve before another prompt can be sent.", spinner = true)
                "PAYMENT_FAILED" -> FarmerStatusCard(DangerSurface, ErrorRed, Icons.Default.Cancel, "Payment was unsuccessful", "Payment for purchase #${record.orderId} failed. Retry here to pay this order without creating another one.")
                "PAYMENT_CANCELLED" -> FarmerStatusCard(CancelledSurface, CancelledText, Icons.Default.Cancel, "Payment cancelled", "Payment for purchase #${record.orderId} was cancelled. Retry here to pay this order without creating another one.")
                "CONFIRMED" -> FarmerStatusCard(SuccessSurface, com.example.farmapp.buyers.ui.theme.Green600, Icons.Default.CheckCircle, "Payment confirmed", "Your purchase has been paid. Purchase order #${record.orderId}.")
                else -> null
            }
            statusCard?.let { card ->
                FarmerStatusCardView(card)
            }

            // ── Phase-specific controls ──────────────────────────────────
            when (record.phase) {
                "PAYMENT_PENDING" -> OrderPrimaryButton("Check payment for order #${record.orderId}", controller::recover,
                    controller.enabled && !state.busy)
                "QUOTE_READY" -> {
                    OrderSection("M-Pesa payment") {
                        Text("Enter the Kenyan mobile number to use for payment.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        OutlinedTextField(phone, { phone = it }, label = { Text("M-Pesa phone number") },
                            placeholder = { Text("07XXXXXXXX or 2547XXXXXXXX") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(10.dp), enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MessaGreen))
                        if (phone.isNotBlank() && runCatching { com.example.farmapp.payments.mpesaPhone(phone) }.isFailure)
                            Text("Enter a valid Kenyan mobile number.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        OrderPrimaryButton("Create purchase order", { confirmOrder = true },
                            !state.busy && runCatching { com.example.farmapp.payments.mpesaPhone(phone) }.isSuccess)
                        TextButton(onClick = controller::refreshQuote, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Request a new quote") }
                    }
                }
                "ORDER_CREATED", "PAYMENT_FAILED", "PAYMENT_CANCELLED" -> {
                    val isRetry = record.phase == "PAYMENT_FAILED" || record.phase == "PAYMENT_CANCELLED"
                    if (state.order?.id != record.orderId)
                        OrderSection {
                            Text("Enter your number now. Refresh order details; payment stays disabled until the created order is verified.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    OrderSection("M-Pesa payment") {
                        if (isRetry)
                            Text("Your previous attempt did not go through. Enter your M-Pesa number to retry payment for purchase #${record.orderId}. The existing order will be reused.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        OutlinedTextField(phone, { phone = it }, label = { Text("M-Pesa phone number") },
                            placeholder = { Text("07XXXXXXXX or 2547XXXXXXXX") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(10.dp), enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MessaGreen))
                        if (phone.isNotBlank() && runCatching { com.example.farmapp.payments.mpesaPhone(phone) }.isFailure)
                            Text("Enter a valid Kenyan mobile number.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        OrderPrimaryButton(if (isRetry) "Retry M-Pesa prompt" else "Send M-Pesa prompt", { confirmPayment = true },
                            controller.enabled && !state.busy && phone.isNotBlank() && state.order?.id == record.orderId)
                    }
                }
                "CONFIRMED" -> {
                    // Receipt / amount details card
                    Surface(shape = RoundedCornerShape(14.dp), color = NeutralSurface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            record.quote?.total?.let {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Amount", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    Text("KES ${it.stripTrailingZeros()}", style = MaterialTheme.typography.titleSmall)
                                }
                            }
                            state.payment?.mpesaReceiptNumber?.let {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Receipt", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MessaGreen, fontWeight = FontWeight.Medium)
                                }
                            }
                            record.paymentId?.let {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Payment ID", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    Text("#$it", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            (state.payment?.paidAt ?: state.order?.paidAt)?.let {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Paid", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    Text(com.example.farmapp.buyer.buyerDate(it), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    if (state.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = MessaGreen)
                        Text("Preparing checkout…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        OrderPrimaryButton("Start another purchase", {
                            selection?.let { sel -> controller.prepare(items, sel) }
                        }, selection != null && items.isNotEmpty())
                    }
                }
            }

            // ── Payment security info ────────────────────────────────────
            if (record.phase in setOf("QUOTE_READY", "ORDER_CREATED", "PAYMENT_FAILED", "PAYMENT_CANCELLED")) {
                Surface(shape = RoundedCornerShape(14.dp), color = MessaTint, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, null, tint = MessaGreen)
                            Text("Payment security", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MessaGreen)
                        }
                        Text("\u2022 Secure payment via Safaricom M-Pesa\n\u2022 You will be prompted on your phone\n\u2022 Enter your PIN only in the M-Pesa prompt\n\u2022 Never share your M-Pesa PIN", style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 20.sp)
                    }
                }
            }

            // ── Refresh action ───────────────────────────────────────────
            TextButton(onClick = controller::recover, enabled = !state.busy && controller.enabled, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(if (state.order?.id == record.orderId) "Refresh checkout status" else "Refresh order details")
            }
        }
    }
    if (confirmOrder) AlertDialog(onDismissRequest = { confirmOrder = false }, title = { Text("Confirm purchase") },
        text = { Text("Submit this order for KES ${record?.quote?.total?.stripTrailingZeros()} using ${runCatching { com.example.farmapp.payments.mpesaPhone(phone) }.getOrDefault("")}?") },
        confirmButton = { TextButton(onClick = { confirmOrder = false; controller.submitOrder(phone) }) { Text("Submit order") } },
        dismissButton = { TextButton(onClick = { confirmOrder = false }) { Text("Cancel") } })
    if (confirmPayment) AlertDialog(onDismissRequest = { confirmPayment = false }, title = { Text("Confirm M-Pesa prompt") },
        text = { Text("Request payment for purchase #${record?.orderId}, KES ${record?.quote?.total?.stripTrailingZeros()}?") },
        confirmButton = { TextButton(onClick = { confirmPayment = false; controller.initiate(phone) }) { Text("Confirm STK Push") } },
        dismissButton = { TextButton(onClick = { confirmPayment = false }) { Text("Cancel") } })
}

private data class FarmerStatusCard(val bg: Color, val tint: Color, val icon: ImageVector, val title: String, val body: String, val spinner: Boolean = false)

@Composable private fun FarmerStatusCardView(card: FarmerStatusCard) {
    Surface(shape = RoundedCornerShape(16.dp), color = card.bg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (card.spinner) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(36.dp), color = card.tint, strokeWidth = 3.dp)
                }
            } else {
                Box(Modifier.size(48.dp).background(card.tint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(card.icon, null, Modifier.size(28.dp), tint = card.tint)
                }
            }
            Text(card.title, style = MaterialTheme.typography.titleLarge, color = card.tint)
            Text(card.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
