package com.example.farmapp.data.model.canonical

import java.math.BigDecimal

data class CanonicalProductDto(
    val id: Long? = null,
    val sellerId: Long? = null,
    val sellerType: String? = null,
    val categoryId: Long? = null,
    val name: String? = null,
    val description: String? = null,
    val unit: String? = null,
    val price: BigDecimal? = null,
    val stockQty: Int? = null,
    val reservedQty: Int? = null,
    val soldQty: Int? = null,
    val minimumOrderQty: Int? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val images: List<CanonicalProductImageDto>? = null
)

/** Provider metadata only. The backend does not return original filename, MIME type or byte size. */
data class CanonicalProductImageDto(
    val id: Long? = null,
    val url: String? = null,
    val publicId: String? = null,
    val assetId: String? = null,
    val sortOrder: Int? = null,
    val isPrimary: Boolean? = null,
    val createdAt: String? = null
)

data class CanonicalCategoryDto(
    val id: Long? = null,
    val name: String? = null,
    val type: String? = null,
    val active: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
