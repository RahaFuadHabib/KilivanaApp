// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.data.DeliveryAddress
import com.example.farmapp.buyers.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AddressBookScreen(
    addresses: List<DeliveryAddress>,
    userId: String?,
    loading: Boolean,
    save: suspend (Long?, String?, String, String, Double?, Double?) -> String?,
    delete: suspend (Long) -> String?,
    back: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingId by remember { mutableStateOf<Long?>(null) }
    var label by remember { mutableStateOf("") }
    var addressText by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<DeliveryAddress?>(null) }

    fun beginEdit(address: DeliveryAddress?) {
        showEditor = true
        editingId = address?.id
        label = address?.label.orEmpty()
        addressText = address?.addressText.orEmpty()
        latitude = address?.latitude?.toString().orEmpty()
        longitude = address?.longitude?.toString().orEmpty()
        feedback = null
    }

    fun closeEditor() {
        showEditor = false
        editingId = null
        label = ""
        addressText = ""
        latitude = ""
        longitude = ""
        feedback = null
    }

    Column(Modifier.fillMaxSize().background(NeutralBackground)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Delivery addresses", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(onClick = { beginEdit(null) }, modifier = Modifier.fillMaxWidth()) { Text("Add address") }
            }
            if (loading) item { CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally)) }
            items(addresses, key = DeliveryAddress::id) { address ->
                Surface(shape = RoundedCornerShape(12.dp), color = NeutralSurface) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(address.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(address.addressText, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { beginEdit(address) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ${address.label}")
                        }
                        IconButton(onClick = { deleteTarget = address }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${address.label}", tint = ErrorRed)
                        }
                    }
                }
            }
            if (addresses.isEmpty() && !loading) {
                item { Text("No saved addresses yet.", color = TextSecondary) }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { closeEditor() },
            title = { Text(if (editingId == null) "Add delivery address" else "Edit delivery address") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text("Label, e.g. Home") }, singleLine = true)
                    OutlinedTextField(addressText, { addressText = it }, label = { Text("Full address") }, minLines = 2)
                    Text("Coordinates are optional. Leave both blank if you do not know them.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            latitude, { latitude = it.filter { char -> char.isDigit() || char == '-' || char == '.' } },
                            modifier = Modifier.weight(1f), label = { Text("Latitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                        )
                        OutlinedTextField(
                            longitude, { longitude = it.filter { char -> char.isDigit() || char == '-' || char == '.' } },
                            modifier = Modifier.weight(1f), label = { Text("Longitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                        )
                    }
                    feedback?.let { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    if (label.isBlank() || addressText.isBlank()) {
                        feedback = "Enter a label and full address."
                    } else {
                        scope.launch {
                            saving = true
                            feedback = null
                            val lat=latitude.trim().takeIf{it.isNotEmpty()}?.toDoubleOrNull()
                            val lon=longitude.trim().takeIf{it.isNotEmpty()}?.toDoubleOrNull()
                            feedback=if(latitude.isBlank()!=longitude.isBlank() || latitude.isNotBlank() && (lat==null || lon==null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0))
                                "Enter valid coordinates or leave both blank."
                            else save(editingId,userId,label.trim(),addressText.trim(),lat,lon)
                            if (feedback == null) closeEditor()
                            saving = false
                        }
                    }
                }) { if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save") }
            },
            dismissButton = { TextButton(onClick = { closeEditor() }, enabled = !saving) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { address ->
        LaunchedEffect(address.id) { deleteError = null }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete address?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${address.label}: ${address.addressText}")
                    deleteError?.let { Text(it, color = ErrorRed) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        saving = true
                        deleteError = delete(address.id)
                        saving = false
                        if (deleteError == null) deleteTarget = null
                    }
                }, enabled = !saving) { Text("Delete", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }, enabled = !saving) { Text("Cancel") } }
        )
    }
}

