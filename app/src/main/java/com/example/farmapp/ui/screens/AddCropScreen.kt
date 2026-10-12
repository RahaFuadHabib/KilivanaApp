package com.example.farmapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AddCropScreen(controller: CropPhotoController, modifier: Modifier = Modifier, publishing: Boolean = false, publishMessage: String? = null, onPublish: () -> Unit = {}, onCheckCreation: () -> Unit = {}, categories: CategoryCatalog = CategoryCatalog(), onRetryCategories: () -> Unit = {}) {
    val draft = controller.draft
    val locked = publishing || draft.productId != null || draft.creationUncertain
    val editable = !controller.busy && !locked
    val categoryReady = draft.productId != null || categories.contains(draft.categoryId)
    val categoryName = categories.name(draft.categoryId, draft.categoryName)
    var review by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    Column(modifier.imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Add Crop", style = MaterialTheme.typography.headlineSmall)
        Text("Crop photos", style = MaterialTheme.typography.titleMedium)
        if (draft.photos.isEmpty()) {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.AddAPhoto, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Show your crop", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Take a clear photo or choose images from your gallery.",
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(draft.photos, key = { _, name -> name }) { index, name ->
                    Card(Modifier.width(190.dp)) {
                        CropPhotoPreview(controller.storage, name, index + 1)
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Photo ${index + 1}", style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { controller.removePhoto(name) }, enabled = editable) {
                                Icon(Icons.Default.Close, "Remove crop photo ${index + 1}")
                            }
                        }
                    }
                }
            }
        }
        Text("${draft.photos.size} / $MAX_CROP_PHOTOS photos", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val enabled = editable && draft.photos.size < MAX_CROP_PHOTOS
        // Stacked actions stay touch-friendly on narrow phones and at larger font sizes.
        FilledTonalButton(onClick = controller.takePhoto, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Default.PhotoCamera, null)
            Spacer(Modifier.width(8.dp))
            Text("Take Photo")
        }
        OutlinedButton(onClick = controller.choosePhotos, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Default.PhotoLibrary, null)
            Spacer(Modifier.width(8.dp))
            Text("Choose from Gallery")
        }
        if (controller.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        controller.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        HorizontalDivider()
        Text("Crop details", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(draft.name, { controller.update(draft.copy(name = it)); formError = null },
            label = { Text("Crop name") }, singleLine = true, enabled = editable, modifier = Modifier.fillMaxWidth())
        CropCategoryDropdown(categories, draft.categoryId, draft.categoryName, editable, onRetryCategories) {
            controller.update(draft.copy(categoryId = it.categoryId, categoryName = it.name)); formError = null
        }
        OutlinedTextField(draft.quantity, { controller.update(draft.copy(quantity = it)); formError = null },
            label = { Text("Quantity") }, singleLine = true,
            enabled = editable, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        CropUnitDropdown(draft.unit, editable) { controller.update(draft.copy(unit = it)); formError = null }
        OutlinedTextField(draft.pricePerUnit, { controller.update(draft.copy(pricePerUnit = it)); formError = null },
            label = { Text(draft.unit.priceLabel) }, singleLine = true,
            enabled = editable, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.description, { controller.update(draft.copy(description = it)) },
            label = { Text("Description") }, minLines = 3, maxLines = 6, enabled = editable, modifier = Modifier.fillMaxWidth())
        Text("Review your crop before publishing. Photos must be JPEG, PNG, GIF or WebP, up to 5 MiB each.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        publishMessage?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (publishing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (draft.productId != null) Text("Listing created. Retry resumes remaining uploads; details are locked to this listing.")
        if (draft.creationUncertain) {
            Text("Creation result unknown. Check My Crops before creating another listing.")
            OutlinedButton(onClick = onCheckCreation, enabled = !publishing) { Text("Check creation result") }
        }
        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { formError = draft.validationError() ?: if (!categoryReady) "Select an available backend category." else null; review = formError == null },
            enabled = !controller.busy && !publishing && !draft.creationUncertain && categoryReady, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (draft.productId == null) "Review draft" else "Resume publishing")
        }
    }
    if (review) AlertDialog(onDismissRequest = { review = false },
        title = { Text("Review crop draft") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(draft.name)
            Text("Category: ${categoryName.ifBlank { "Not assigned" }}")
            Text("Quantity: ${draft.quantity} ${draft.unit.label}")
            Text("Price: KES ${draft.pricePerUnit} / ${draft.unit.priceUnit}")
            Text("${draft.photos.size} photos")
            if (draft.description.isNotBlank()) Text(draft.description)
            Text("Publish this crop to Kilivana?")
        } }, confirmButton = { TextButton(onClick = { review = false; onPublish() }, enabled = !publishing && categoryReady) { Text("Publish") } },
        dismissButton = { TextButton(onClick = { review = false }) { Text("Keep editing") } })
}

@Composable
private fun CropPhotoPreview(storage: CropPhotoStorage, name: String, number: Int) {
    val preview by produceState<Pair<Boolean, ImageBitmap?>>(true to null, name) {
        val bitmap = try { withContext(Dispatchers.IO) { storage.preview(name).asImageBitmap() } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        value = false to bitmap
    }
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        val bitmap = preview.second
        if (bitmap != null) Image(bitmap, "Crop photo $number", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (preview.first) CircularProgressIndicator(Modifier.size(24.dp))
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Image, null)
            Text("Preview unavailable", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CropUnitDropdown(unit: CropUnit, enabled: Boolean = true, onSelect: (CropUnit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(value = unit.label, onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text("Unit") }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CropUnit.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
