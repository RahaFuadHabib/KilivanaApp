package com.example.farmapp.orders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.example.farmapp.buyer.buyerDate
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

internal val OrderGreen = Color(0xFF00834A)
private val OrderInk = Color(0xFF101827)
private val OrderMuted = Color(0xFF526074)
private val OrderBorder = Color(0xFFE8EBEE)
internal val OrderPaleGreen = Color(0xFFECFBF2)
internal fun orderMoney(value: BigDecimal?): String = value?.let {
    "KSh " + NumberFormat.getNumberInstance(Locale.ENGLISH).apply { minimumFractionDigits = 0; maximumFractionDigits = 2 }.format(it)
} ?: "Not available"
internal fun orderMatchesFilter(order: BuyerOrderDto, filter: String): Boolean = when (filter) {
    "Pending" -> order.status == "placed"
    "Processing" -> order.status in setOf("confirmed", "in_transit")
    "Delivered" -> order.status in setOf("delivered", "completed")
    else -> true
}
/** Whole-order success requires an authoritative order status or sufficient confirmed payment records. */
internal fun orderPaymentConfirmed(order: BuyerOrderDto, state: BuyerPaymentState?): Boolean {
    if (buyerPaymentOutcome(order.paymentStatus) == "confirmed") return true
    val total = order.total ?: return false
    if (total.signum() <= 0 || state == null || state.orderId != order.id) return false
    val records = state.payments
    if (records.any { it.status == "refunded" }) return false
    return records.filter { buyerPaymentOutcome(it.status) == "confirmed" }
        .fold(BigDecimal.ZERO) { sum, p -> sum + (p.amount ?: BigDecimal.ZERO) } >= total
}
@Composable internal fun BuyerOrdersTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = OrderGreen, onPrimary = Color.White,
        background = Color.White, surface = Color.White, onSurface = OrderInk, onBackground = OrderInk,
        outline = Color(0xFF90B5A5)), content = content)
}
@Composable internal fun OrderHeader(title: String, back: () -> Unit, green: Boolean = false) {
    Surface(color = if (green) Color(0xFF00644B) else Color.White,
        contentColor = if (green) Color.White else OrderInk) {
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
@Composable internal fun OrderStatusBadge(label: String, kind: String) {
    val (bg, fg) = when (kind) {
        "paid", "delivered", "completed" -> Color(0xFFD9F6CE) to Color(0xFF175D2B)
        "cancelled", "failed", "refunded" -> Color(0xFFFFE0E0) to Color(0xFFA22323)
        "confirmed", "in_transit" -> Color(0xFFDDEEFF) to Color(0xFF174B8A)
        else -> Color(0xFFFFE3A0) to Color(0xFF633A00)
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(9.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp))
    }
}
@Composable internal fun OrderSection(title: String? = null, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, OrderBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(if (compact) 12.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
@Composable internal fun ProductThumbnail(modifier: Modifier = Modifier) {
    Surface(modifier.size(54.dp), color = Color(0xFFF1F3F3), shape = RoundedCornerShape(9.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Inventory2, "Product image unavailable", tint = Color(0xFF9AA4AA), modifier = Modifier.size(25.dp)) }
    }
}
@Composable internal fun ProductThumbnailRow(items: List<BuyerOrderItemDto>) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        items.take(3).forEach { _ -> ProductThumbnail(Modifier.size(40.dp)) }
    }
}
@Composable internal fun OrderPrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = OrderGreen)) {
        Icon(Icons.Default.Smartphone, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}
@Composable internal fun BuyerOrderCard(order: BuyerOrderDto, payment: BuyerPaymentState?, open: () -> Unit, pay: () -> Unit) {
    OrderSection(compact = true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text("Order", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(buyerDate(order.createdAt).substringBefore(","), style = MaterialTheme.typography.bodySmall, color = OrderMuted)
        }
        val paid = orderPaymentConfirmed(order, payment)
        val status = if (order.status in setOf("delivered", "completed", "cancelled", "disputed")) order.status.orEmpty() else if (paid) "paid" else order.status.orEmpty()
        OrderStatusBadge(if (status == "paid") "Paid" else if (order.paymentStatus == "pending" && order.status == "placed") "Payment Pending"
            else when (status) { "confirmed", "in_transit" -> "Processing"; else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() } }, status)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProductThumbnailRow(order.items.orEmpty())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                order.items?.let { Text("${it.size} ${if (it.size == 1) "item" else "items"}", style = MaterialTheme.typography.bodySmall, color = OrderMuted) }
                Text(orderMoney(order.total), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = open) { Icon(Icons.Default.ChevronRight, "View order details") }
        }
        if (payment != null && payment.orderId == order.id && payment.canInitiate && !payment.busy && !paid)
            OrderPrimaryButton("Pay with M-Pesa", pay)
        else OutlinedButton(onClick = open, shape = RoundedCornerShape(7.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) { Text("View Details", color = OrderGreen) }
    }
}
@Composable internal fun OrderLineItem(item: BuyerOrderItemDto) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ProductThumbnail()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.productName?.takeIf { it.isNotBlank() } ?: "Product", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            item.unit?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = OrderMuted) }
            Text("${orderMoney(item.unitPrice)} × ${item.quantity ?: "—"}", style = MaterialTheme.typography.bodySmall, color = OrderMuted)
        }
        // Both operands are backend values; prefer the actual backend line subtotal when provided.
        val line = item.subtotal ?: item.unitPrice?.let { price -> item.quantity?.let { price * it.toBigDecimal() } }
        Text(orderMoney(line), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
@Composable internal fun OrderItemsSection(order: BuyerOrderDto) {
    OrderSection("Order Items") {
        order.items.orEmpty().forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = OrderBorder)
            OrderLineItem(item)
        }
        if (order.items.isNullOrEmpty()) Text("No item details available.", color = OrderMuted)
    }
}
@Composable internal fun OrderDeliveryInformation(order: BuyerOrderDto) {
    OrderSection("Delivery Information") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Home, null, tint = OrderMuted)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Delivery location", style = MaterialTheme.typography.titleSmall)
                Text(order.deliveryAddress?.takeIf { it.isNotBlank() } ?: "Delivery details not available", style = MaterialTheme.typography.bodyMedium, color = OrderMuted)
            }
        }
    }
}
@Composable internal fun OrderTotals(order: BuyerOrderDto) {
    OrderSection("Order Summary") {
        @Composable fun amount(label: String, value: BigDecimal?, total: Boolean = false) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = if (total) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
                Text(orderMoney(value), fontWeight = if (total) FontWeight.Bold else FontWeight.Normal,
                    color = if (total) OrderGreen else OrderInk, style = if (total) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
            }
        }
        amount("Items Total", order.subtotal)
        amount("Delivery Fee", order.deliveryFee)
        HorizontalDivider(color = OrderBorder)
        amount("Total Amount", order.total, true)
    }
}
@Composable internal fun OrderPaymentConfirmation(order: BuyerOrderDto, state: BuyerPaymentState?, compact: Boolean = false) {
    if (!orderPaymentConfirmed(order, state)) return
    val mpesa = order.paymentMethod.equals("mpesa", true) || state?.payments?.filter {
        it.method.equals("mpesa", true) && buyerPaymentOutcome(it.status) == "confirmed"
    }?.fold(BigDecimal.ZERO) { sum, p -> sum + (p.amount ?: BigDecimal.ZERO) }?.let { it >= (order.total ?: BigDecimal.ONE) } == true
    Surface(color = OrderPaleGreen, shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.CheckCircle, null, tint = OrderGreen, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OrderStatusBadge("Paid", "paid")
                    Text(if (order.paidAt != null) buyerDate(order.paidAt) else "Order date: ${buyerDate(order.createdAt)}",
                        style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = OrderMuted,
                        textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 8.dp))
                }
                Text(if (compact && mpesa) "Payment completed via M-Pesa" else "Payment confirmed", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}
@Composable internal fun BuyerOrderDetailsDesign(order: BuyerOrderDto, payment: BuyerPaymentState?, onBack: () -> Unit,
    onPay: () -> Unit, extras: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        OrderHeader("Order Details", onBack)
        HorizontalDivider(color = OrderBorder)
        LazyColumn(Modifier.weight(1f).testTag("order-details-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                if (orderPaymentConfirmed(order, payment)) OrderPaymentConfirmation(order, payment)
                else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        val status = order.paymentStatus.orEmpty()
                        OrderStatusBadge(if (status == "pending") "Payment Pending" else status.replaceFirstChar { it.uppercase() }, status)
                        Text(buyerDate(order.createdAt), style = MaterialTheme.typography.labelSmall, color = OrderMuted, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    }
                    if (payment?.canInitiate == true) Text("Pay securely with M-Pesa. Review your order before sending a prompt to your phone.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { OrderItemsSection(order) }
            item { OrderDeliveryInformation(order) }
            item { OrderTotals(order) }
            if (orderPaymentConfirmed(order, payment)) item { OrderPaymentConfirmation(order, payment, true) }
            item { extras() }
        }
        if (payment != null && payment.orderId == order.id && payment.canInitiate && !payment.busy && !orderPaymentConfirmed(order, payment))
            Box(Modifier.fillMaxWidth().padding(16.dp)) { OrderPrimaryButton("Pay with M-Pesa", onPay) }
    }
}
@Composable internal fun BuyerOrdersListDesign(rows: List<BuyerOrderDto>, back: () -> Unit,
    content: @Composable (BuyerOrderDto) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    Column(Modifier.fillMaxSize().background(Color.White)) {
        OrderHeader("My Orders", back, green = true)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("All", "Pending", "Processing", "Delivered").forEach { label ->
                FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label, style = MaterialTheme.typography.labelMedium) }, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, if (filter == label) OrderGreen else OrderBorder),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = OrderGreen, selectedLabelColor = Color.White))
            }
        }
        LazyColumn(Modifier.weight(1f).testTag("orders-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val shown = rows.filter { orderMatchesFilter(it, filter) }
            if (shown.isEmpty()) item { Text("No orders to show.", modifier = Modifier.padding(vertical = 24.dp), color = OrderMuted) }
            items(shown, key = { it.id!! }) { content(it) }
        }
    }
}
