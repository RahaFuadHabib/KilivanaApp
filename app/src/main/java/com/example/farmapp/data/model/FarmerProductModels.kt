package com.example.farmapp.data.model

import java.math.BigDecimal

data class CreateProductRequest(
    val title: String,
    val description: String,
    val unit: String,
    val unitPrice: BigDecimal,
    val availableQuantity: BigDecimal,
    val categoryId: String? = null,
    val lowStockThreshold: BigDecimal? = null,
    val minimumOrderQuantity: BigDecimal? = null
)

data class ProductImageResponse(val imageId: String, val fileName: String?, val contentType: String?,
    val sizeBytes: Long?, val url: String, val sortOrder: Int)

data class FarmerProductResponse(val productId: String, val name: String, val description: String?,
    val unit: String, val unitPrice: BigDecimal, val availableQuantity: BigDecimal,
    val images: List<ProductImageResponse> = emptyList(),
    val status: String = "ACTIVE", val categoryId: String? = null,
    val lowStockThreshold: BigDecimal? = null, val minimumOrderQuantity: BigDecimal? = null,
    val categoryName: String? = null,
    val sellerId: String? = null, val reservedQuantity: BigDecimal? = null)

/** Presentation eligibility only. The server rechecks ownership, state and reservations. */
enum class FarmerProductAction(val label: String, val targetStatus: String?, val successMessage: String) {
    UNLIST("Unlist", "UNLISTED", "Crop unlisted. Existing orders are kept."),
    ARCHIVE("Archive", "ARCHIVED", "Crop archived. Existing orders are kept."),
    RESUBMIT("Submit for Admin approval", "PENDING_APPROVAL", "Crop submitted for Admin approval. It remains private until approved."),
    DELETE("Delete archived crop", null, "Crop deleted by the server. Historical orders and payments are retained.")
}

fun FarmerProductResponse.lifecycleActions(userId: String?, role: String?): List<FarmerProductAction> {
    if (role != "FARMER" || userId == null || sellerId != userId) return emptyList()
    return buildList {
        if (status == "ACTIVE") add(FarmerProductAction.UNLIST)
        if (status in setOf("ACTIVE", "UNLISTED", "PENDING_APPROVAL", "REJECTED")) add(FarmerProductAction.ARCHIVE)
        if (status in setOf("UNLISTED", "ARCHIVED")) add(FarmerProductAction.RESUBMIT)
        if (status == "ARCHIVED" && reservedQuantity?.signum() == 0) add(FarmerProductAction.DELETE)
    }
}

data class UpdateProductRequest(val title: String, val description: String, val unit: String,
    val unitPrice: BigDecimal, val availableQuantity: BigDecimal, val categoryId: String?,
    val lowStockThreshold: BigDecimal?, val minimumOrderQuantity: BigDecimal?)

data class FarmerOrderItem(val productId: String, val productTitle: String, val quantity: BigDecimal,
    val unitPrice: BigDecimal, val lineTotal: BigDecimal)
data class FarmerOrderResponse(val orderId: String, val sellerId: String, val status: String,
    val totalAmount: BigDecimal, val currency: String, val createdAt: String,
    val items: List<FarmerOrderItem>)

/** CategoryResponse from the shared backend catalog. UUIDs are retained as strings. */
data class CategoryResponse(val categoryId: String, val name: String, val sector: String, val active: Boolean)
