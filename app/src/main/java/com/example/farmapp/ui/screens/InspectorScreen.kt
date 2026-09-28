package com.example.farmapp.ui.screens

import com.example.farmapp.ui.components.KilivanaHeaderMetric
import com.example.farmapp.ui.theme.KilivanaAccents
import com.example.farmapp.ui.theme.KilivanaSpacing
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.farmapp.ui.components.FoodcourtHeaderBlock
import com.example.farmapp.ui.theme.ForestGreen
import com.example.farmapp.ui.theme.HarvestAmber
import com.example.farmapp.ui.theme.InspectorHeroGradient
import com.example.farmapp.ui.theme.InspectorPurple
import com.example.farmapp.ui.theme.LeafGreen

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
    var inspectorSearchQuery by remember { mutableStateOf("") }

    val filteredRecords = remember(records, inspectorSearchQuery) {
        if (inspectorSearchQuery.isBlank()) records
        else records.filter {
            it.farmerName.contains(inspectorSearchQuery, ignoreCase = true) ||
            it.parcelNumber.contains(inspectorSearchQuery, ignoreCase = true) ||
            it.cropType.contains(inspectorSearchQuery, ignoreCase = true)
        }
    }

    var selectedRecordIndex by remember { mutableStateOf(0) }
    val currentRecord = filteredRecords.getOrNull(selectedRecordIndex) ?: filteredRecords.firstOrNull() ?: records.first()

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
    ) {
        // Foodcourt Shared Header Block: Greeting, Rounded Search Bar, Promo Card
        FoodcourtHeaderBlock(
            greetingName = "Grace",
            searchPlaceholder = "Search farm parcels, land registries...",
            searchQuery = inspectorSearchQuery,
            onSearchQueryChange = { inspectorSearchQuery = it },
            promoHighlight = "4 inspections due this week",
            promoSubtitle = "Next: Parcel #42 (Nakuru Organic Farm)",
            promoActionText = "Inspect",
            onPromoAction = { selectedRecordIndex = 0 }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // -------------------------------------------------------------------
        // 1. Deep Royal Purple Gradient Header (Officer Credentials & Stats Row)
        // -------------------------------------------------------------------
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InspectorPurple)
                    .padding(KilivanaSpacing.content)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Officer Identity Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Avatar with Golden / Purple Ring
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .border(2.5.dp, HarvestAmber, CircleShape)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Inspector Avatar",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Grace Wambui",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Field Compliance Officer • Gov #802",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                                )
                            }
                        }

                        // KEPHIS Regulatory Badge
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), MaterialTheme.shapes.large)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = HarvestAmber,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "KEPHIS Official",
                                    color = Color.White,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Stats Row: Pending, Approved, Flagged
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InspectorStatPill(
                            title = "Pending",
                            count = "3",
                            badgeColor = HarvestAmber,
                            modifier = Modifier.weight(1f)
                        )
                        InspectorStatPill(
                            title = "Approved",
                            count = "12",
                            badgeColor = LeafGreen,
                            modifier = Modifier.weight(1f)
                        )
                        InspectorStatPill(
                            title = "Flagged",
                            count = "1",
                            badgeColor = Color(0xFFEF5350),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Body Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Farm Record Selector Chips
            Text(
                text = "Active Land Parcels for Field Audit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (filteredRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No farm parcels found matching \"$inspectorSearchQuery\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredRecords.indices.toList()) { index ->
                        val rec = filteredRecords[index]
                        val isSelected = index == selectedRecordIndex
                        Card(
                            modifier = Modifier.clickable {
                                selectedRecordIndex = index
                                inspectorNotes = rec.notes
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) InspectorPurple else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = "${rec.farmerName} (Parcel #${rec.parcelNumber})",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // -------------------------------------------------------------------
            // 2. Assigned Land Parcel Card (GPS Tag & Soil pH / Moisture Readout)
            // -------------------------------------------------------------------
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Parcel #${currentRecord.parcelNumber}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Farmer: ${currentRecord.farmerName} (Farm #${currentRecord.farmId})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val isApproved = currentRecord.status == "APPROVED"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isApproved) KilivanaAccents.success.container else KilivanaAccents.warning.container)
                                .border(1.dp, if (isApproved) LeafGreen else HarvestAmber, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentRecord.status,
                                color = if (isApproved) KilivanaAccents.success.content else KilivanaAccents.warning.content,
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // GPS Coordinates Box (-1.1824° S, 36.9312° E)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(InspectorPurple.copy(alpha = 0.08f))
                            .border(1.dp, InspectorPurple.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "GPS Coordinates",
                                    tint = KilivanaAccents.inspectorContent,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Audited GPS Coordinates",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "-1.1824° S, 36.9312° E",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = KilivanaAccents.inspectorContent
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(InspectorPurple.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Accuracy: ±1.2m",
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KilivanaAccents.inspectorContent
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Soil pH & Moisture Readout Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Soil pH
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Science,
                                    contentDescription = "pH",
                                    tint = LeafGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(text = "Soil pH Level", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = "6.4 (Optimal)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            // Soil Moisture
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = "Moisture",
                                    tint = KilivanaAccents.information.content,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(text = "Moisture Content", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = "28% (Well Drained)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = KilivanaAccents.information.content)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // -------------------------------------------------------------------
            // 3. Evidence Photo Thumbnail Strip with Dashed "Snap" Camera Tile
            // -------------------------------------------------------------------
            Text(
                text = "Geotagged Evidence Photo Strip",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Dashed "Snap" Camera Tile
                item {
                    DashedSnapCameraTile(
                        onClick = {
                            capturedPhotos["Field Soil Core"] = true
                        }
                    )
                }

                // Captured Evidence Photo Thumbnails
                items(capturedPhotos.entries.toList()) { (photoLabel, isDone) ->
                    EvidencePhotoThumbnail(
                        label = photoLabel,
                        isCaptured = isDone,
                        onToggle = { capturedPhotos[photoLabel] = !isDone }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // -------------------------------------------------------------------
            // Statutory Audit Checklist
            // -------------------------------------------------------------------
            Text(
                text = "Statutory Agronomic Checklist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                auditItems.forEach { item ->
                    val checked = checkedStates[item.id] ?: false
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checkedStates[item.id] = !checked },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (checked) Color(0xFFF1F8E9) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { checkedStates[item.id] = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Inspector Observations & Notes
            Text(
                text = "Inspector Observations & Notes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = inspectorNotes,
                onValueChange = { inspectorNotes = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // -------------------------------------------------------------------
            // 4. Split Decision Actions: Green "Grant Green Badge" vs Red "Flag"
            // -------------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Red "Flag" Button
                Button(
                    onClick = { decisionResult = "FLAGGED" },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = KilivanaSpacing.touchTarget),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Flag Issues", fontWeight = FontWeight.Bold)
                }

                // Green "Grant Green Badge" Button
                Button(
                    onClick = { decisionResult = "APPROVED" },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = KilivanaSpacing.touchTarget),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForestGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Grant Green Badge", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }

    // Decision Confirmation Dialog
    decisionResult?.let { decision ->
        AlertDialog(
            onDismissRequest = { decisionResult = null },
            icon = {
                Icon(
                    imageVector = if (decision == "APPROVED") Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (decision == "APPROVED") LeafGreen else KilivanaAccents.error.content
                )
            },
            title = {
                Text(
                    text = if (decision == "APPROVED") "Green Badge Awarded" else "Compliance Issues Flagged"
                )
            },
            text = {
                Text(
                    text = if (decision == "APPROVED")
                        "Farm #${currentRecord.farmId} (${currentRecord.farmerName}) has met all agronomic, statutory, and quality compliance metrics. Green Badge issued."
                    else
                        "Audit flagged with compliance remediation items for Farm #${currentRecord.farmId}. An automated notification has been dispatched to the farmer."
                )
            },
            confirmButton = {
                Button(
                    onClick = { decisionResult = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForestGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Done")
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Inspector Stat Pill Composable
// ---------------------------------------------------------------------------
@Composable
private fun InspectorStatPill(
    title: String,
    count: String,
    badgeColor: Color,
    modifier: Modifier = Modifier
) {
    KilivanaHeaderMetric(title = title, value = count, indicatorColor = badgeColor, modifier = modifier)
}

// ---------------------------------------------------------------------------
// Dashed Snap Camera Tile Composable
// ---------------------------------------------------------------------------
@Composable
private fun DashedSnapCameraTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 110.dp, height = 105.dp)
            .clip(MaterialTheme.shapes.large)
            .border(1.5.dp, InspectorPurple.copy(alpha = 0.6f), MaterialTheme.shapes.large)
            .background(InspectorPurple.copy(alpha = 0.06f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(InspectorPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Snap Evidence",
                    tint = KilivanaAccents.inspectorContent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "+ Snap Evidence",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = KilivanaAccents.inspectorContent,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Evidence Photo Thumbnail Composable
// ---------------------------------------------------------------------------
@Composable
private fun EvidencePhotoThumbnail(
    label: String,
    isCaptured: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .size(width = 115.dp, height = 105.dp)
            .clickable { onToggle() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isCaptured) KilivanaAccents.success.container else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCaptured) LeafGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
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
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isCaptured) LeafGreen else Color.Gray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCaptured) Icons.Default.Check else Icons.Default.AddAPhoto,
                    contentDescription = label,
                    tint = if (isCaptured) Color.White else Color.Gray,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                maxLines = 1
            )
            Text(
                text = if (isCaptured) "-1.1824° S" else "Tap to Snap",
                fontSize = 9.sp,
                color = if (isCaptured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
