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
    val categoryName: String? = null)

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
