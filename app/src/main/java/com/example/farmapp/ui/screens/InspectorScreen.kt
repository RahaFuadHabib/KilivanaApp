package com.example.farmapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmapp.data.model.InspectionRecord
import com.example.farmapp.data.model.mockInspectionRecords

data class AuditCheckItem(
    val id: Int,
    val title: String,
    val description: String
)

@Composable
fun InspectorScreen(
    modifier: Modifier = Modifier,
    records: List<InspectionRecord> = mockInspectionRecords
) {
    val scrollState = rememberScrollState()
    var selectedRecordIndex by remember { mutableStateOf(0) }
    val currentRecord = records.getOrNull(selectedRecordIndex) ?: records.first()

    val auditItems = remember {
        listOf(
            AuditCheckItem(1, "Statutory Land Ownership Title Verified", "Valid title deed or certified lease agreement inspected"),
            AuditCheckItem(2, "Soil Quality & Drainage Standards", "Soil pH, salinity, and contour drainage meet agricultural standards"),
            AuditCheckItem(3, "Irrigation Water Safety Tested", "No heavy metals or microbiological contaminants present in water source"),
            AuditCheckItem(4, "Pesticide & Chemical Storage Compliant", "Locked ventilated storage with KEPHIS-approved labeling and PPE"),
            AuditCheckItem(5, "Traceability & Harvesting Logbooks Current", "Batch harvesting and field spray records are up to date"),
            AuditCheckItem(6, "Worker Welfare & Fair Labor Standards", "Fair wages, sanitary facilities, and protective gear provided")
        )
    }

    val checkedStates = remember {
        mutableStateMapOf<Int, Boolean>().apply {
            auditItems.forEach { put(it.id, true) }
            put(6, false) // uncheck one by default to show interactive checklist
        }
    }

    // Photo capture state (simulated captured vs placeholder)
    val capturedPhotos = remember {
        mutableStateMapOf(
            "Soil Core Sample" to true,
            "Irrigation Source" to true,
            "Chemical Storage" to false,
            "Crop Canopy & Field" to false
        )
    }

    var inspectorNotes by remember { mutableStateOf(currentRecord.notes) }
    var decisionResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Farm Selection Header
        Text(
            text = "Active Farm Audit",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Farm Record Selector Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(records.indices.toList()) { index ->
                val rec = records[index]
                val isSelected = index == selectedRecordIndex
                Card(
                    modifier = Modifier.clickable {
                        selectedRecordIndex = index
                        inspectorNotes = rec.notes
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "${rec.farmerName} (Farm #${rec.farmId})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Inspection Details Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Farm #${currentRecord.farmId}: ${currentRecord.farmerName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val isApproved = currentRecord.status == "APPROVED"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isApproved) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = currentRecord.status,
                            color = if (isApproved) Color(0xFF2E7D32) else Color(0xFFE65100),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Scheduled Audit Date: ${currentRecord.inspectionDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Title Documentation: ${if (currentRecord.documentVerified) "Verified ✓" else "Pending Review"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (currentRecord.documentVerified) Color(0xFF2E7D32) else Color(0xFFE65100),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Land Audit Checklist Section
        Text(
            text = "Land Audit Checklist",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Verify regulatory requirements according to agricultural compliance protocols.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(8.dp))

        auditItems.forEach { item ->
            val checked = checkedStates[item.id] ?: false
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { checkedStates[item.id] = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Photo Capture Placeholders Section
        Text(
            text = "Photo Capture & Evidence",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Attach photographic evidence for the audit registry.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            capturedPhotos.keys.take(2).forEach { label ->
                PhotoCaptureCard(
                    label = label,
                    isCaptured = capturedPhotos[label] == true,
                    onToggleCapture = {
                        capturedPhotos[label] = !(capturedPhotos[label] ?: false)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            capturedPhotos.keys.drop(2).forEach { label ->
                PhotoCaptureCard(
                    label = label,
                    isCaptured = capturedPhotos[label] == true,
                    onToggleCapture = {
                        capturedPhotos[label] = !(capturedPhotos[label] ?: false)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Inspector Notes
        Text(
            text = "Inspector Observations & Notes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = inspectorNotes,
            onValueChange = { inspectorNotes = it },
            placeholder = { Text("Enter field inspection observations, findings, and remediation items...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Decision Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Flag Issues Button (Red/Amber)
            OutlinedButton(
                onClick = { decisionResult = "FLAGGED" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFC62828)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Flag Issues",
                    tint = Color(0xFFC62828),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Flag Issues", fontWeight = FontWeight.Bold)
            }

            // Approve Farm Button (Green)
            Button(
                onClick = { decisionResult = "APPROVED" },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Approve Farm",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Approve Farm", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }

    // Decision Confirmation Dialog
    decisionResult?.let { decision ->
        AlertDialog(
            onDismissRequest = { decisionResult = null },
            icon = {
                Icon(
                    imageVector = if (decision == "APPROVED") Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (decision == "APPROVED") Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            },
            title = {
                Text(
                    text = if (decision == "APPROVED") "Farm Audit Approved" else "Compliance Issues Flagged"
                )
            },
            text = {
                Text(
                    text = if (decision == "APPROVED")
                        "Farm #${currentRecord.farmId} (${currentRecord.farmerName}) has met all agronomic, statutory, and quality compliance metrics. Certification issued."
                    else
                        "Audit flagged with compliance remediation items for Farm #${currentRecord.farmId}. An automated notification has been dispatched to the farmer."
                )
            },
            confirmButton = {
                Button(onClick = { decisionResult = null }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun PhotoCaptureCard(
    label: String,
    isCaptured: Boolean,
    onToggleCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .clickable { onToggleCapture() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCaptured) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isCaptured) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCaptured) Icons.Default.Check else Icons.Default.Info,
                    contentDescription = label,
                    tint = if (isCaptured) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )

            Text(
                text = if (isCaptured) "Attached ✓" else "Tap to capture",
                style = MaterialTheme.typography.labelSmall,
                color = if (isCaptured) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                fontSize = 10.sp
            )
        }
    }
}
