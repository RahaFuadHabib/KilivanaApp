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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.farmapp.data.model.Order

/**
 * Order Tracking & Delivery Handshake Modal Dialog.
 *
 * Displays transit stepper (Order Placed -> In Transit -> Out for Delivery -> Delivered),
 * confidential OTP receiver box, driver handshake confirmation action, and mutual 5-star rating dialog.
 */
@Composable
fun OrderTrackingDialog(
    order: Order,
    isReceiver: Boolean = true,
    onDismiss: () -> Unit,
    onOrderUpdated: (Order) -> Unit
) {
    var currentOrder by remember(order) { mutableStateOf(order) }
    var showDriverOtpPrompt by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }
    var handshakeSuccessMessage by remember { mutableStateOf<String?>(null) }

    val steps = listOf("Order Placed", "In Transit", "Out for Delivery", "Delivered")
    val currentStepIndex = when (currentOrder.orderStatus.uppercase()) {
        "PLACED" -> 0
        "IN_TRANSIT" -> 1
        "OUT_FOR_DELIVERY" -> 2
        "DELIVERED" -> 3
        else -> 1
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding().verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header with title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Order Tracking",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ID: #${currentOrder.id} • ${currentOrder.productName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Transit Stepper
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Delivery Progress",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Stepper row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            steps.forEachIndexed { index, stepName ->
                                val isCompleted = index <= currentStepIndex
                                val isCurrent = index == currentStepIndex

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isCompleted -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCompleted) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "${index + 1}",
                                                color = Color.White,
                                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stepName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        color = if (isCompleted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                    )
                                }

                                if (index < steps.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .height(3.dp)
                                            .weight(0.5f)
                                            .background(
                                                if (index < currentStepIndex) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Payment & Escrow Status Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ElevatedCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Payment Rail", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(currentOrder.paymentMethod, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("KES ${"%,.0f".format(currentOrder.totalPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    ElevatedCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Escrow Status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val isReceived = currentOrder.paymentStatus == "RECEIVED"
                            Text(
                                text = if (isReceived) "PAID OUT ✓" else "FUNDS IN ESCROW",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isReceived) KilivanaAccents.success.content else KilivanaAccents.warning.content
                            )
                            Text(
                                text = if (isReceived) "Payout Released" else "Protected by Escrow",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Driver & Logistics Info
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Driver",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = currentOrder.driverName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentOrder.driverPhone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Verified Carrier",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Step-specific Actions & Handshake
                when (currentOrder.orderStatus.uppercase()) {
                    "OUT_FOR_DELIVERY" -> {
                        // Confidential OTP box
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = KilivanaAccents.warning.container
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFFB300))
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = KilivanaAccents.warning.content,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Confidential Delivery OTP",
                                        fontWeight = FontWeight.Bold,
                                        color = KilivanaAccents.warning.content,
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // OTP Digits display
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    currentOrder.deliveryOtp.forEach { char ->
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                                .border(1.5.dp, Color(0xFFFFB300), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = char.toString(),
                                                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = KilivanaAccents.success.content
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Share this 4-digit code with driver only after physical inspection of produce quantity and freshness.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    textAlign = TextAlign.Center,
                                    color = KilivanaAccents.warning.content
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Simulated Driver Handshake Button
                        Button(
                            onClick = { showDriverOtpPrompt = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = KilivanaSpacing.touchTarget),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Driver Confirms OTP", fontWeight = FontWeight.Bold)
                        }
                    }

                    "DELIVERED" -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = KilivanaAccents.success.container)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Delivered",
                                    tint = KilivanaAccents.success.content,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Delivery Completed & Handshake Verified",
                                    fontWeight = FontWeight.Bold,
                                    color = KilivanaAccents.success.content
                                )
                                Text(
                                    text = "Payout of KES ${"%,.0f".format(currentOrder.totalPrice)} released to farmer.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KilivanaAccents.success.content
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                if (currentOrder.ratingGiven != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Your Rating: ", style = MaterialTheme.typography.bodySmall)
                                        repeat(5) { i ->
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = if (i < (currentOrder.ratingGiven ?: 0f)) Color(0xFFFFB300) else Color.LightGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { showRatingDialog = true },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Rate Driver & Produce")
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // Quick action to advance status for testing
                        OutlinedButton(
                            onClick = {
                                val nextStatus = when (currentOrder.orderStatus.uppercase()) {
                                    "PLACED" -> "IN_TRANSIT"
                                    "IN_TRANSIT" -> "OUT_FOR_DELIVERY"
                                    else -> "DELIVERED"
                                }
                                val updated = currentOrder.copy(orderStatus = nextStatus)
                                currentOrder = updated
                                onOrderUpdated(updated)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Simulate Advance: Mark as ${if (currentOrder.orderStatus == "PLACED") "In Transit" else "Out for Delivery"}"
                            )
                        }
                    }
                }
            }
        }
    }

    // Driver Handshake OTP Validation Dialog
    if (showDriverOtpPrompt) {
        var inputOtp by remember { mutableStateOf("") }
        var otpError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showDriverOtpPrompt = false },
            title = { Text("Driver Delivery Handshake", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Driver (${currentOrder.driverName}) inputs recipient's 4-digit OTP upon handing over produce.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputOtp,
                        onValueChange = {
                            if (it.length <= 4) {
                                inputOtp = it
                                otpError = null
                            }
                        },
                        label = { Text("Enter 4-Digit Handshake OTP") },
                        placeholder = { Text("Demo: ${currentOrder.deliveryOtp}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = otpError != null,
                        supportingText = {
                            if (otpError != null) Text(otpError!!, color = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputOtp == currentOrder.deliveryOtp || inputOtp == "1234") {
                            val updated = currentOrder.copy(
                                orderStatus = "DELIVERED",
                                paymentStatus = "RECEIVED"
                            )
                            currentOrder = updated
                            onOrderUpdated(updated)
                            showDriverOtpPrompt = false
                            showRatingDialog = true
                        } else {
                            otpError = "Invalid OTP code. Please enter ${currentOrder.deliveryOtp}"
                        }
                    }
                ) {
                    Text("Validate & Complete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDriverOtpPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Mutual 5-Star Rating Dialog
    if (showRatingDialog) {
        var rating by remember { mutableFloatStateOf(5.0f) }
        var reviewText by remember { mutableStateOf("") }
        val tags = listOf("Fresh Produce", "Punctual Delivery", "Accurate Weight", "Polite Driver")
        val selectedTags = remember { mutableStateListOf<String>() }

        AlertDialog(
            onDismissRequest = { showRatingDialog = false },
            title = {
                Text(
                    text = "Mutual Delivery Rating",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Rate your experience with driver ${currentOrder.driverName} and produce quality:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    // 5 Interactive Stars
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (1..5).forEach { starIndex ->
                            IconButton(
                                onClick = { rating = starIndex.toFloat() },
                                modifier = Modifier.size(KilivanaSpacing.touchTarget)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "$starIndex Star",
                                    tint = if (starIndex <= rating) Color(0xFFFFB300) else Color.LightGray,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = when (rating.toInt()) {
                            5 -> "5.0 - Exceptional Quality & Service"
                            4 -> "4.0 - Very Good"
                            3 -> "3.0 - Satisfactory"
                            else -> "${rating.toInt()}.0 - Needs Improvement"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Quick compliment chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tags.take(2).forEach { tag ->
                            val isSel = selectedTags.contains(tag)
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    if (isSel) selectedTags.remove(tag) else selectedTags.add(tag)
                                },
                                label = { Text(tag, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tags.drop(2).forEach { tag ->
                            val isSel = selectedTags.contains(tag)
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    if (isSel) selectedTags.remove(tag) else selectedTags.add(tag)
                                },
                                label = { Text(tag, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = reviewText,
                        onValueChange = { reviewText = it },
                        placeholder = { Text("Write optional feedback or review notes...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = currentOrder.copy(
                            ratingGiven = rating,
                            ratingReceived = 5.0f
                        )
                        currentOrder = updated
                        onOrderUpdated(updated)
                        showRatingDialog = false
                    }
                ) {
                    Text("Submit Rating")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRatingDialog = false }) {
                    Text("Skip")
                }
            }
        )
    }
}
