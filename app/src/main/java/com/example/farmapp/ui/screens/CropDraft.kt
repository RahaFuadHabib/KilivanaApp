package com.example.farmapp.ui.screens

/** Editable local draft plus a retry checkpoint for its backend listing. */
internal data class CropDraft(
    val name: String = "",
    val quantity: String = "",
    val pricePerUnit: String = "",
    val description: String = "",
    val photos: List<String> = emptyList(),
    val unit: CropUnit = CropUnit.KG,
    val productId: String? = null,
    val uploadedPhotos: Int = 0,
    val creationUncertain: Boolean = false,
    val categoryId: String? = null,
    val categoryName: String? = null
) {
    fun validationError(): String? = when {
        name.isBlank() || name.trim().length > 150 -> "Enter a crop name."
        // Existing pre-category listings can still resume their photo uploads without a new create.
        productId == null && categoryId.isNullOrBlank() -> "Select a category."
        quantity.toBigDecimalOrNull()?.let { it.signum() > 0 } != true -> "Enter a quantity greater than zero in ${unit.label}."
        pricePerUnit.toBigDecimalOrNull()?.let { it >= java.math.BigDecimal("0.01") } != true -> "Enter a price greater than zero in KES per ${unit.priceUnit}."
        description.isBlank() || description.trim().length > 1000 -> "Enter a description of up to 1000 characters."
        photos.size > MAX_CROP_PHOTOS -> "Choose up to 10 photos."
        photos.isEmpty() -> "Add at least one crop photo."
        else -> null
    }

    fun savedValues(): List<String> = listOf("crop-draft-v4", name, quantity, pricePerUnit, description, unit.name, productId.orEmpty(), uploadedPhotos.toString(), creationUncertain.toString(), categoryId.orEmpty(), categoryName.orEmpty()) + photos

    companion object {
        fun fromSavedValues(values: List<String>): CropDraft =
            if (values.firstOrNull() == "crop-draft-v4") {
                CropDraft(values[1], values[2], values[3], values[4], values.drop(11), CropUnit.valueOf(values[5]), values[6].ifBlank { null }, values[7].toInt(), values[8].toBoolean(), values[9].ifBlank { null }, values[10].ifBlank { null })
            } else if (values.firstOrNull() == "crop-draft-v3") {
                CropDraft(values[1], values[2], values[3], values[4], values.drop(9), CropUnit.valueOf(values[5]), values[6].ifBlank { null }, values[7].toInt(), values[8].toBoolean())
            } else if (values.firstOrNull() == "crop-draft-v2") {
                CropDraft(values[1], values[2], values[3], values[4], values.drop(6), CropUnit.valueOf(values[5]))
            } else {
                // Drafts saved before unit selection were always kilograms.
                CropDraft(values[0], values[1], values[2], values[3], values.drop(4))
            }
    }

    fun addPhotos(names: List<String>) = copy(photos = (photos + names).distinct().take(MAX_CROP_PHOTOS))
    fun removePhoto(name: String) = copy(photos = photos.filterNot { it == name })
}

// A local resource limit, not a backend contract limit.
internal const val MAX_CROP_PHOTOS = 10

/** One mapping shared by the dropdown, price label, validation and review. */
internal enum class CropUnit(val label: String, val priceUnit: String) {
    KG("kg", "kg"), BAGS("bags", "bag"), CRATES("crates", "crate"),
    PIECES("pieces", "piece"), BUNCHES("bunches", "bunch");

    val priceLabel: String get() = "Price (KES/$priceUnit)"
}
