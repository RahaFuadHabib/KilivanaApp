package com.example.farmapp.buyer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

internal data class BuyerPickupActions(val refresh: (String?) -> Unit = {}, val select: (Long) -> Unit = {},
    val save: (Long, String, Boolean) -> Unit = { _, _, _ -> }, val remove: (Long) -> Unit = {}, val setDefault: (Long) -> Unit = {})
internal fun LazyListScope.pickupFeedback(state: BuyerPickupState, actions: BuyerPickupActions) {
    if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { actions.refresh(state.region) }, enabled = !state.busy) { Text("Retry pickup stations") } } }
    state.notice?.let { item { Text(it) } }
}
@Composable internal fun BuyerSavedPickupCard(row: BuyerSavedPickup, state: BuyerPickupState, actions: BuyerPickupActions) {
    var confirmRemove by rememberSaveable(row.id) { mutableStateOf(false) }
    val selected = state.selected?.id == row.pickupStationId
    val usable = !state.busy && state.error == null && state.available && row.pickupStationAvailable == true
    OutlinedCard(onClick = { if (usable) actions.select(row.pickupStationId!!) }, enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().testTag("saved-pickup-${row.id}").semantics { this.selected = selected }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected, { actions.select(row.pickupStationId!!) }, enabled = usable)
                Column(Modifier.weight(1f)) {
                    Text(row.label?.takeIf { it.isNotBlank() } ?: row.pickupStationName ?: "Saved pickup station", style = MaterialTheme.typography.titleMedium)
                    row.pickupStationName?.let { Text(it) }
                    row.pickupStationAddress?.let { Text(it) }
                    row.pickupStationRegion?.let { Text(it) }
                }
            }
            if (selected) Text("Selected", color = MaterialTheme.colorScheme.primary)
            if (row.pickupStationAvailable != true) Text("Currently unavailable")
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.isDefault == true) Text("Default station", Modifier.weight(1f))
                else TextButton(onClick = { actions.setDefault(row.id!!) }, enabled = usable && !state.uncertain, modifier = Modifier.weight(1f)) { Text("Set default") }
                IconButton(onClick = { confirmRemove = true }, enabled = !state.busy && !state.uncertain && state.error == null, modifier = Modifier.testTag("remove-saved-pickup-${row.id}")) {
                    Icon(Icons.Default.DeleteOutline, "Remove saved pickup station")
                }
            }
        }
    }
    if (confirmRemove) AlertDialog(onDismissRequest = { confirmRemove = false }, title = { Text("Remove saved station?") },
        text = { Text("This removes the station from your saved list.") },
        confirmButton = { TextButton(onClick = { confirmRemove = false; actions.remove(row.id!!) }) { Text("Remove station") } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } })
}
@Composable internal fun BuyerAvailablePickupCard(row: BuyerPickupStation, state: BuyerPickupState, actions: BuyerPickupActions) {
    var saving by rememberSaveable(row.id) { mutableStateOf(false) }
    var label by rememberSaveable(row.id) { mutableStateOf("") }
    var makeDefault by rememberSaveable(row.id) { mutableStateOf(false) }
    val selected = state.selected?.id == row.id
    OutlinedCard(onClick = { actions.select(row.id!!) }, enabled = !state.busy && state.error == null && row.isAvailable == true,
        modifier = Modifier.fillMaxWidth().testTag("available-pickup-${row.id}").semantics { this.selected = selected }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(row.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
            Text(row.address.orEmpty()); Text(row.region.orEmpty())
            row.openingHours?.takeIf { it.isNotBlank() }?.let { Text(it) }
            row.contactPhone?.takeIf { it.isNotBlank() }?.let { Text(it) }
            row.pickupInstructions?.takeIf { it.isNotBlank() }?.let { Text(it) }
            if (selected) Text("Selected", color = MaterialTheme.colorScheme.primary)
            if (state.saved.any { it.pickupStationId == row.id }) Text("Saved")
            else OutlinedButton(onClick = { saving = true }, enabled = !state.busy && !state.uncertain && state.error == null) { Text("Save station") }
        }
    }
    if (saving) AlertDialog(onDismissRequest = { saving = false }, title = { Text("Save pickup station") },
        text = { Column {
            Text(row.name.orEmpty())
            OutlinedTextField(label, { label = it }, label = { Text("Label (optional)") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(makeDefault, { makeDefault = it }); Text("Use as default") }
        } },
        confirmButton = { TextButton(onClick = { saving = false; actions.save(row.id!!, label, makeDefault) }, enabled = !state.busy && !state.uncertain) { Text("Save pickup station") } },
        dismissButton = { TextButton(onClick = { saving = false }) { Text("Cancel") } })
}
internal fun LazyListScope.pickupRegionChips(state: BuyerPickupState, actions: BuyerPickupActions) {
    item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.region == null, modifier = Modifier.testTag("pickup-all-regions"), onClick = { actions.refresh(null) }, enabled = !state.busy, label = { Text("All regions") })
        state.regions.forEach { region -> FilterChip(state.region == region, modifier = Modifier.testTag("pickup-region-$region"), onClick = { actions.refresh(region) }, enabled = !state.busy, label = { Text(region) }) }
    } }
}
