package com.example.farmapp.ui.screens

import androidx.compose.foundation.layout.imePadding
import com.example.farmapp.ui.theme.KilivanaAccents
import com.example.farmapp.ui.theme.KilivanaSpacing
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmapp.data.model.Order
import com.example.farmapp.data.model.Product

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutBottomSheet(
    product: Product,
    onDismiss: () -> Unit,
    onCheckoutSuccess: (Order) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var quantityKg by remember { mutableDoubleStateOf(50.0.coerceAtMost(product.quantityKg)) }
    val totalPrice = quantityKg * product.pricePerKg

    val paymentRails = listOf("MPESA", "VISA", "GOOGLE_PAY")
    var selectedRail by remember { mutableStateOf("MPESA") }

    // Rail-specific form states
    var mpesaPhone by remember { mutableStateOf("+254 712 345 678") }
    var showStkPushPrompt by remember { mutableStateOf(false) }
    var mpesaPin by remember { mutableStateOf("") }

    var cardNumber by remember { mutableStateOf("4520 8920 1450 7820") }
    var cardExpiry by remember { mutableStateOf("08/28") }
    var cardCvv by remember { mutableStateOf("381") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Checkout & Escrow Protection",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Produce Order: ${product.name} from ${product.farmerName} (${product.county})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Order Quantity Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Quantity (kg):", fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { if (quantityKg > 10) quantityKg -= 10.0 },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text("-10") }

                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${"%.0f".format(quantityKg)} kg",
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = { if (quantityKg + 10 <= product.quantityKg) quantityKg += 10.0 },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text("+10") }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Rate: KES ${"%.0f".format(product.pricePerKg)} / kg", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "Total: KES ${"%,.0f".format(totalPrice)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Kilivana Escrow Guarantee Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(KilivanaAccents.success.container)
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = KilivanaAccents.success.content,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Funds will be held securely in Kilivana Escrow until you provide your delivery OTP to the driver upon produce inspection.",
                        color = KilivanaAccents.success.content,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Payment Rail Selection Chips
            Text("Select Payment Rail", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                paymentRails.forEach { rail ->
                    FilterChip(
                        selected = selectedRail == rail,
                        onClick = { selectedRail = rail },
                        label = {
                            Text(
                                text = when (rail) {
                                    "MPESA" -> "M-Pesa STK"
                                    "VISA" -> "Visa Card"
                                    else -> "Google Pay"
                                },
                                fontWeight = if (selectedRail == rail) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Rail-specific form
            when (selectedRail) {
                "MPESA" -> {
                    Column(modifier = Modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.medium)) {
                        Text("M-Pesa Express Checkout", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = mpesaPhone,
                            onValueChange = { mpesaPhone = it },
                            label = { Text("Safaricom Phone Number") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = { showStkPushPrompt = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00A859), // Safaricom Green
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Pay KES ${"%,.0f".format(totalPrice)} via M-Pesa", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "VISA" -> {
                    Column(modifier = Modifier.imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.medium)) {
                        Text("Visa / Mastercard Debit & Credit", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = cardNumber,
                            onValueChange = { cardNumber = it },
                            label = { Text("Card Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = cardExpiry,
                                onValueChange = { cardExpiry = it },
                                label = { Text("Expiry (MM/YY)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = cardCvv,
                                onValueChange = { cardCvv = it },
                                label = { Text("CVV") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                val newOrder = Order(
                                    id = System.currentTimeMillis() % 10000,
                                    buyerName = "You (Verified Buyer)",
                                    productName = product.name,
                                    quantityKg = quantityKg,
                                    totalPrice = totalPrice,
                                    orderStatus = "IN_TRANSIT",
                                    deliveryOtp = (1000..9999).random().toString(),
                                    driverName = "David Karanja",
                                    driverPhone = "+254 722 112 233",
                                    paymentMethod = "VISA",
                                    paymentStatus = "ESCROW"
                                )
                                onCheckoutSuccess(newOrder)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Pay KES ${"%,.0f".format(totalPrice)} with Visa", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "GOOGLE_PAY" -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Fast, secure checkout backed by your Google Wallet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                val newOrder = Order(
                                    id = System.currentTimeMillis() % 10000,
                                    buyerName = "You (Verified Buyer)",
                                    productName = product.name,
                                    quantityKg = quantityKg,
                                    totalPrice = totalPrice,
                                    orderStatus = "IN_TRANSIT",
                                    deliveryOtp = (1000..9999).random().toString(),
                                    driverName = "David Karanja",
                                    driverPhone = "+254 722 112 233",
                                    paymentMethod = "GOOGLE_PAY",
                                    paymentStatus = "ESCROW"
                                )
                                onCheckoutSuccess(newOrder)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Pay with Google Pay", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyLarge.fontSize)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Simulated Safaricom M-Pesa STK Push Dialog
    if (showStkPushPrompt) {
        AlertDialog(
            onDismissRequest = { showStkPushPrompt = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00A859)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SIM Toolkit: M-PESA STK Push", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyLarge.fontSize)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Do you want to pay KES ${"%,.0f".format(totalPrice)} to Kilivana Escrow Account (Paybill: 489201)?",
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    )
                    OutlinedTextField(
                        value = mpesaPin,
                        onValueChange = { if (it.length <= 4) mpesaPin = it },
                        label = { Text("Enter M-PESA PIN") },
                        placeholder = { Text("••••") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newOrder = Order(
                            id = System.currentTimeMillis() % 10000,
                            buyerName = "You (Verified Buyer)",
                            productName = product.name,
                            quantityKg = quantityKg,
                            totalPrice = totalPrice,
                            orderStatus = "IN_TRANSIT",
                            deliveryOtp = (1000..9999).random().toString(),
                            driverName = "David Karanja",
                            driverPhone = "+254 722 112 233",
                            paymentMethod = "MPESA",
                            paymentStatus = "ESCROW"
                        )
                        showStkPushPrompt = false
                        onCheckoutSuccess(newOrder)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A859))
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStkPushPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
