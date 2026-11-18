package com.example.farmapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.farmapp.network.canonical.CanonicalFarmRequest
import com.example.farmapp.repository.FarmerFarm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FarmerFarmsScreen(farms: List<FarmerFarm>, loading: Boolean, busy: Boolean, error: String?,
    message: String?, onRefresh: () -> Unit, onCreate: (CanonicalFarmRequest) -> Unit,
    modifier: Modifier = Modifier) {
    var showForm by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var county by rememberSaveable { mutableStateOf("") }
    var subCounty by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var ownership by rememberSaveable { mutableStateOf("OWNED") }
    var ownershipMenu by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("My Farms", style = MaterialTheme.typography.headlineSmall)
            Text("Register a farm for Admin inspection. Its current status comes from the shared backend.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { showForm = !showForm }, enabled = !busy) { Text(if (showForm) "Close form" else "Register a farm") }
                OutlinedButton(onClick = onRefresh, enabled = !loading && !busy) { Text(if (loading) "Loading…" else "Refresh") }
            }
        }
        if (message != null) item { Text(message, color = MaterialTheme.colorScheme.primary) }
        if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (showForm) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Farm details", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(name, { name = it; validation = null }, label = { Text("Farm name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(county, { county = it; validation = null }, label = { Text("County") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(subCounty, { subCounty = it }, label = { Text("Sub-county (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(address, { address = it; validation = null }, label = { Text("Physical address / location") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(size, { size = it; validation = null }, label = { Text("Farm size (acres)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ExposedDropdownMenuBox(expanded = ownershipMenu, onExpandedChange = { ownershipMenu = it }) {
                        OutlinedTextField(value = ownership.lowercase().replaceFirstChar { it.uppercase() }, onValueChange = {},
                            readOnly = true, label = { Text("Land ownership") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ownershipMenu) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                        ExposedDropdownMenu(expanded = ownershipMenu, onDismissRequest = { ownershipMenu = false }) {
                            listOf("OWNED", "LEASED", "COMMUNAL").forEach { option ->
                                DropdownMenuItem(text = { Text(option.lowercase().replaceFirstChar { it.uppercase() }) },
                                    onClick = { ownership = option; ownershipMenu = false })
                            }
                        }
                    }
                    OutlinedTextField(description, { description = it }, label = { Text("Farm description (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Text("The backend assigns the farm status. Admin can review the farm and arrange an inspection.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = {
                        val acreage = size.trim().toDoubleOrNull()
                        validation = when {
                            name.isBlank() -> "Enter the farm name."
                            county.isBlank() -> "Enter the county."
                            address.isBlank() -> "Enter the physical farm address."
                            acreage == null || !acreage.isFinite() || acreage <= 0 -> "Enter a farm size greater than zero."
                            else -> null
                        }
                        if (validation == null) {
                            showForm = false
                            onCreate(CanonicalFarmRequest(name.trim(), county.trim(), subCounty.trim().ifBlank { null },
                                address.trim(), sizeAcres = acreage!!, ownershipType = ownership,
                                description = description.trim().ifBlank { null }))
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Submitting…" else "Submit farm for review") }
                }
            }
        }
        if (loading && farms.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (!loading && farms.isEmpty() && error == null) item {
            Text("No farms registered yet. Add your farm so Admin can review it and arrange an inspector visit.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(farms, key = { it.id }) { farm ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(farm.name, style = MaterialTheme.typography.titleMedium)
                        AssistChip(onClick = {}, label = { Text(farm.status.replace('_', ' ')) })
                    }
                    Text("${farm.county}${farm.subCounty?.let { ", $it" }.orEmpty()} · ${farm.sizeAcres} acres")
                    farm.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(when (farm.status) {
                        "PENDING" -> "Waiting for Admin review and farm inspection assignment."
                        "ACTIVE" -> if (farm.canDispatchFrom) "Approved. You can now submit produce linked to this farm for product approval."
                            else "Approved, but add a physical address before using this farm for produce dispatch."
                        "FALLOW" -> "This farm is not currently available as a produce dispatch location."
                        else -> "This farm is not active for produce dispatch. Contact Admin if you think this is incorrect."
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
