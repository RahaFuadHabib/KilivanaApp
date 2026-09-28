package com.example.farmapp.ui.components

import androidx.compose.foundation.layout.imePadding
import com.example.farmapp.ui.theme.KilivanaAccents
import com.example.farmapp.ui.theme.KilivanaSpacing
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.farmapp.ui.theme.ForestGreen
import com.example.farmapp.ui.theme.HarvestGold
import com.example.farmapp.ui.theme.LeafGreen

/**
 * Role Profile Governance Modal implementing role-based field locking:
 * - For Buyer, Supplier, and Inspector: allow direct editing of contact details and address with "Save Changes".
 * - For Farmer: lock phone and address fields with Lock icons (read-only), render a dedicated governance
 *   lock warning banner, and provide a "Request Audit to Update Details" action button.
 */
@Composable
fun ProfileModal(
    userRole: String,
    userName: String = "User",
    userPhone: String = "+254 700 000 000",
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val normalizedRole = userRole.uppercase()
    val isFarmer = normalizedRole == "FARMER" || userRole.equals("Farmer", ignoreCase = true)
    val displayRole = userRole.lowercase().replaceFirstChar { it.uppercase() }

    // Initial mock profile information based on role and authenticated user details
    val (defaultName, defaultPhone, defaultAddress) = when {
        isFarmer -> Triple("David Karanja", "+254 712 345 678", "Plot 42, Subukia Ridge, Nakuru County")
        normalizedRole == "BUYER" || userRole.equals("Buyer", ignoreCase = true) ->
            Triple("Sarah Ochieng", "+254 722 987 654", "Kilimani Wholesale Hub, Store #12, Nairobi")
        normalizedRole == "SUPPLIER" || userRole.equals("Supplier", ignoreCase = true) ->
            Triple("Joseph Kamau", "+254 733 456 789", "Industrial Area Logistics Park, Bay 4, Nairobi")
        normalizedRole == "INSPECTOR" || userRole.equals("Inspector", ignoreCase = true) ->
            Triple("Grace Wanjiku", "+254 701 234 567", "KEPHIS Field Audit Station, Central Region")
        else -> Triple("David Karanja", "+254 712 345 678", "Plot 42, Subukia Ridge, Nakuru County")
    }

    var fullName by rememberSaveable(userName) {
        mutableStateOf(if (userName.isNotBlank() && userName != "User") userName else defaultName)
    }
    var phoneNumber by rememberSaveable(userPhone) {
        mutableStateOf(if (userPhone.isNotBlank() && userPhone != "+254 700 000 000") userPhone else defaultPhone)
    }
    var locationAddress by rememberSaveable { mutableStateOf(defaultAddress) }

    var isAuditRequested by rememberSaveable { mutableStateOf(false) }
    var isSavedSuccessfully by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .imePadding().verticalScroll(rememberScrollState())
            ) {
                // Header: Title & Close Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ForestGreen.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Profile & Role Governance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Role: $displayRole",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Farmer Governance Banner (Requirement 1)
                if (isFarmer) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(KilivanaAccents.warning.container)
                            .border(1.dp, HarvestGold, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Governance Protected",
                                tint = KilivanaAccents.warning.content,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Governance Lock Protected: Address and contact updates require verification by an Assigned Field Inspector and Super Admin approval.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KilivanaAccents.warning.content,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Audit Request Confirmation
                if (isAuditRequested) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(KilivanaAccents.success.container)
                            .border(1.dp, LeafGreen, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Requested",
                                tint = LeafGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Audit Request Dispatched! Ticket #AUD-2026-92 created. An assigned Field Inspector will visit your parcel for address re-certification.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (isSavedSuccessfully) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(KilivanaAccents.success.container)
                            .border(1.dp, LeafGreen, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Saved",
                                tint = LeafGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Profile details saved successfully.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Fields Section
                // 1. Full Name
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name / Enterprise") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = "Full Name")
                    },
                    singleLine = true,
                    readOnly = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Phone Number
                // For Farmer: locked with Lock icon (read-only)
                // For Buyer, Supplier, Inspector: editable
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = "Phone")
                    },
                    trailingIcon = {
                        if (isFarmer) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked Field",
                                tint = KilivanaAccents.error.content
                            )
                        }
                    },
                    readOnly = isFarmer,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = if (isFarmer) {
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    } else OutlinedTextFieldDefaults.colors()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Location / Address
                // For Farmer: locked with Lock icon (read-only)
                // For Buyer, Supplier, Inspector: editable
                OutlinedTextField(
                    value = locationAddress,
                    onValueChange = { locationAddress = it },
                    label = { Text("Location / Registered Address") },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = "Location")
                    },
                    trailingIcon = {
                        if (isFarmer) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked Field",
                                tint = KilivanaAccents.error.content
                            )
                        }
                    },
                    readOnly = isFarmer,
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = if (isFarmer) {
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    } else OutlinedTextFieldDefaults.colors()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                if (isFarmer) {
                    // Farmer action: Request Audit to Update Details
                    Button(
                        onClick = {
                            isAuditRequested = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = KilivanaSpacing.touchTarget)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FactCheck,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Request Audit to Update Details",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Buyer, Supplier, Inspector: Save Changes action
                    Button(
                        onClick = {
                            isSavedSuccessfully = true
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = KilivanaSpacing.touchTarget)
                    ) {
                        Text(
                            text = "Save Changes",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
