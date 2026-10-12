package com.example.farmapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.farmapp.data.model.CategoryResponse

/** Selection always belongs to the caller; fetching never selects a category. */
internal data class CategoryCatalog(
    val items: List<CategoryResponse> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false
) {
    val selectable: List<CategoryResponse> get() = items.filter { it.active && it.sector == "FARM_PRODUCE" }
    fun contains(id: String?) = !loading && error == null && loaded && selectable.any { it.categoryId == id }
    fun name(id: String?, previousName: String? = null): String =
        selectable.firstOrNull { it.categoryId == id }?.name ?: previousName?.takeIf { it.isNotBlank() }.orEmpty()
    val message: String? get() = when {
        loading -> "Loading categories…"
        error != null -> error
        !loaded -> "Categories are unavailable. Retry to load them."
        selectable.isEmpty() -> "No active crop categories are available. Retry or contact Kilivana."
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CropCategoryDropdown(catalog: CategoryCatalog, selectedId: String?, selectedName: String?,
    enabled: Boolean, onRetry: () -> Unit, onSelect: (CategoryResponse) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val canOpen = enabled && !catalog.loading && catalog.error == null && catalog.selectable.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded && canOpen, onExpandedChange = { if (canOpen) expanded = it }) {
            OutlinedTextField(value = catalog.name(selectedId, selectedName), onValueChange = {}, readOnly = true,
                label = { Text("Category") }, placeholder = { Text("Select category") }, enabled = canOpen,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && canOpen) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
            ExposedDropdownMenu(expanded = expanded && canOpen, onDismissRequest = { expanded = false }) {
                catalog.selectable.forEach { category ->
                    DropdownMenuItem(text = { Text(category.name) }, onClick = { onSelect(category); expanded = false })
                }
            }
        }
        if (catalog.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        catalog.message?.let { Text(it, color = if (catalog.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!catalog.loading && catalog.message != null) TextButton(onClick = onRetry) { Text("Retry categories") }
        if (selectedId != null && catalog.loaded && !catalog.loading && catalog.error == null && !catalog.contains(selectedId)) {
            Text("This category is no longer available. Select an active category before saving or publishing.",
                color = MaterialTheme.colorScheme.error)
        }
    }
}
