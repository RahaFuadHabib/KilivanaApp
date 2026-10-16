package com.example.farmapp.payments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Backend PaymentStatus wire values, not an STK-initiation response or client-calculated receipt. */
enum class VerifiedPaymentStatus(val wire: String, val label: String) {
    PENDING("pending", "Pending provider confirmation"), PAID("paid", "Paid"), HELD("held", "Held in escrow"),
    SETTLED("settled", "Settled to seller"), FAILED("failed", "Payment failed"), REFUNDED("refunded", "Refunded");
    companion object { fun parse(wire: String?) = entries.firstOrNull { it.wire == wire } }
}
const val MPESA_SUPPLIER_BLOCKER = "M-Pesa requests are unavailable until Supplier-authorized orders, payment-status access and duplicate-safe initiation are verified. An STK prompt or accepted request is not proof of payment."

/** Reusable read-only component; no Daraja credentials, amount input, initiation or client-side paid mutation. */
@Composable
fun PaymentStatusCard(status: VerifiedPaymentStatus? = null, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("M-Pesa payments", style = MaterialTheme.typography.titleLarge)
        Text(status?.label ?: "Payment status unavailable")
        Text(if (status == null) MPESA_SUPPLIER_BLOCKER else "Status reported by the backend. An initiation response alone does not confirm payment.")
    } }
}
