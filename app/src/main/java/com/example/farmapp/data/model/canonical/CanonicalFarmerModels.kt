package com.example.farmapp.data.model.canonical

import java.math.BigDecimal
import java.time.LocalDateTime

/** Validated display data, not an AuthResponse and never passed to TokenManager. */
data class CanonicalAccount(
    val userId: String,
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    val username: String?,
    val region: String?,
    val referenceCode: String?,
    val role: String,
    val accountStatus: String,
    val verificationStatus: String,
    val createdAt: LocalDateTime?
)

enum class CanonicalProductStatus(val wireValue: String) {
    ACTIVE("active"),
    PENDING_APPROVAL("pending"),
    REJECTED("rejected"),
    SUSPENDED("suspended")
}

/** Display metadata, not the active upload/reconciliation ProductImageResponse. */
data class CanonicalImageView(
    val imageId: String,
    val url: String,
    val publicId: String,
    val assetId: String?,
    val sortOrder: Int?,
    val isPrimary: Boolean?,
    val createdAt: LocalDateTime?
)

/**
 * Separate read model: stockQty is total stock; availableQuantity is stock minus reservations.
 * Do not feed this into the existing mutation flow without its separately approved stock adapter.
 */
data class CanonicalFarmerProduct(
    val productId: String,
    val sellerId: String,
    val sector: String,
    val categoryId: String,
    val name: String,
    val description: String?,
    val unit: String,
    val unitPrice: BigDecimal,
    val stockQuantity: BigDecimal,
    val reservedQuantity: BigDecimal,
    val availableQuantity: BigDecimal,
    val soldQuantity: BigDecimal,
    val minimumOrderQuantity: BigDecimal,
    val status: CanonicalProductStatus,
    val images: List<CanonicalImageView>,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)
