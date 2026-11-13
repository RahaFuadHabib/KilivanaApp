package com.example.farmapp.farmerpurchases

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.farmapp.checkout.DeliverySelection

@Composable internal fun FarmerTransactionPanel(controller: FarmerTransactionController,
    items: Map<Long, Int>, selection: DeliverySelection?) {
    val state by controller.state.collectAsStateWithLifecycle()
    var phone by remember(controller) { mutableStateOf(controller.state.value.record?.phoneNumber.orEmpty()) }
    var confirmOrder by remember { mutableStateOf(false) }
    var confirmPayment by remember { mutableStateOf(false) }
    LaunchedEffect(controller) { if (controller.enabled && controller.state.value.record != null) controller.recover() }
    val record = state.record
    Column {
        state.notice?.let { Text(it) }
        if (state.busy) LinearProgressIndicator()
        if (record == null) {
            Button(onClick = { selection?.let { controller.prepare(items, it) } },
                enabled = controller.enabled && !state.busy && selection != null && items.isNotEmpty()) { Text("Get delivery quote") }
        } else {
            record.quote?.let {
                Text("Items: KES ${it.subtotal}")
                Text("Delivery: KES ${it.deliveryFee}")
                Text("Total: KES ${it.total}")
                if (it.cartId == null && it.items == null)
                    Text("Quote totals and delivery will be checked against the created order before payment.")
            }
            if (record.phase == "QUOTE_READY") {
                OutlinedTextField(phone, { phone = it }, label = { Text("M-Pesa phone number") }, enabled = !state.busy)
                Button(onClick = { confirmOrder = true }, enabled = !state.busy &&
                    runCatching { com.example.farmapp.payments.mpesaPhone(phone) }.isSuccess) { Text("Create purchase order") }
                TextButton(onClick = controller::refreshQuote, enabled = !state.busy) { Text("Request a new quote") }
            }
            record.orderId?.let { Text("Purchase order #$it") }
            if (record.phase in setOf("ORDER_CREATED", "PAYMENT_FAILED")) {
                OutlinedTextField(phone, { phone = it }, label = { Text("M-Pesa phone number") }, enabled = !state.busy)
                Button(onClick = { confirmPayment = true }, enabled = controller.enabled && !state.busy && phone.isNotBlank() && state.order?.id == record.orderId) {
                    Text("Send M-Pesa prompt")
                }
                if (state.order?.id != record.orderId)
                    Text("Enter your number now. Refresh order details; payment stays disabled until the created order is verified.")
            }
            if (record.phase == "PAYMENT_PENDING") Text("Payment pending. Status is checked automatically; an uncertain prompt is never repeated.")
            if (record.phase == "PAYMENT_FAILED") Text("Payment was not completed. A new prompt requires confirmation.")
            if (record.phase == "CONFIRMED") {
                Text("Payment confirmed. Purchase order #${record.orderId}")
                Button(onClick = { selection?.let { controller.prepare(items, it) } },
                    enabled = !state.busy && selection != null && items.isNotEmpty()) { Text("Start another purchase") }
            }
            TextButton(onClick = controller::recover, enabled = !state.busy && controller.enabled) {
                Text(if (state.order?.id == record.orderId) "Refresh checkout status" else "Refresh order details")
            }
        }
    }
    if (confirmOrder) AlertDialog(onDismissRequest = { confirmOrder = false }, title = { Text("Confirm purchase") },
        text = { Text("Submit this order for KES ${record?.quote?.total} using ${runCatching { com.example.farmapp.payments.mpesaPhone(phone) }.getOrDefault("")}?") },
        confirmButton = { TextButton(onClick = { confirmOrder = false; controller.submitOrder(phone) }) { Text("Submit order") } },
        dismissButton = { TextButton(onClick = { confirmOrder = false }) { Text("Cancel") } })
    if (confirmPayment) AlertDialog(onDismissRequest = { confirmPayment = false }, title = { Text("Confirm M-Pesa prompt") },
        text = { Text("Request payment for purchase #${record?.orderId}, KES ${record?.quote?.total}?") },
        confirmButton = { TextButton(onClick = { confirmPayment = false; controller.initiate(phone) }) { Text("Confirm STK Push") } },
        dismissButton = { TextButton(onClick = { confirmPayment = false }) { Text("Cancel") } })
}
