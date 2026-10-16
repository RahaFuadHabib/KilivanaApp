package com.example.farmapp.supplier

import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.canonical.*
import java.math.BigDecimal

data class SupplierProfileDto(val id: Long? = null, val userId: Long? = null, val businessName: String? = null,
    val businessDetails: String? = null, val location: String? = null, val verificationInfo: String? = null,
    val createdAt: String? = null, val updatedAt: String? = null, val images: List<CanonicalProductImageDto>? = null)
data class SupplierProfile(val account: CanonicalAccount, val business: SupplierProfileDto?)
data class SupplyListing(val id: Long, val name: String, val description: String, val unit: String,
    val price: BigDecimal, val available: Int, val reserved: Int, val minimum: Int, val categoryId: Long,
    val status: CanonicalProductStatus, val images: List<CanonicalProductImageDto>)
data class SupplyDraft(val name: String = "", val description: String = "", val unit: String = "kg",
    val price: String = "", val available: String = "", val minimum: String = "1", val categoryId: Long? = null) {
    override fun toString() = "SupplyDraft(redacted)"
}
data class SupplierMetrics(val total: Int, val active: Int, val pendingApproval: Int, val outOfStock: Int)
fun supplierMetrics(listings: List<SupplyListing>) = SupplierMetrics(listings.size,
    listings.count { it.status == CanonicalProductStatus.ACTIVE },
    listings.count { it.status == CanonicalProductStatus.PENDING_APPROVAL }, listings.count { it.available == 0 })
class SupplierFailure(message: String, val rejected: Boolean = false) : java.io.IOException(message)

fun CanonicalProductDto.toSupply(owner: Long): SupplyListing {
    if (sellerId != owner || sellerType !in setOf("supplier", "SUPPLIER")) throw SupplierFailure("Supply ownership could not be verified.")
    // Reuse the existing pure canonical product validator, not Farmer UI/repository/state.
    val validated = toFarmerProduct()
    return SupplyListing(id!!, validated.name, description.orEmpty(), validated.unit, validated.unitPrice,
        stockQty!! - reservedQty!!, reservedQty, minimumOrderQty!!, categoryId!!, validated.status, images.orEmpty())
}
fun SupplyDraft.request(categories: List<CanonicalCategoryDto>, reserved: Int = 0): CanonicalProductRequest {
    val quantity = available.toIntOrNull()
    val min = minimum.toIntOrNull()
    val money = price.toBigDecimalOrNull()
    if (name.trim().length !in 2..200 || description.trim().isEmpty() || description.length > 4000 ||
        unit.trim().isEmpty() || unit.trim().length > 30 || quantity == null || quantity < 0 || min == null || min < 1 ||
        money == null || money < BigDecimal("0.01") || money.scale() > 2 || money.precision() > 16)
        throw SupplierFailure("Check name (2–200 characters), description, unit, price (at most 2 decimal places), whole stock and minimum order quantity.", true)
    val category = categories.firstOrNull { it.id == categoryId && it.active == true && it.type in setOf("supplier", "SUPPLIER") }
        ?: throw SupplierFailure("Select an active Supplier category.", true)
    val total = try { Math.addExact(quantity, reserved) } catch (_: ArithmeticException) { throw SupplierFailure("Stock quantity is too large.", true) }
    return CanonicalProductRequest(name.trim(), description.trim(), unit.trim(), money, total, min, category.id!!)
}
fun SupplyListing.draft() = SupplyDraft(name, description, unit, price.toPlainString(), available.toString(), minimum.toString(), categoryId)
const val SUPPLIER_ORDERS_BLOCKER = "Incoming orders are unavailable: the backend does not provide a Supplier-scoped order feed. No customer orders are fetched through buyer or administrator endpoints."
const val SUPPLIER_DELIVERY_BLOCKER = "Delivery tracking is unavailable until the backend exposes Supplier-authorized order deliveries and permitted driver details."

data class SupplierPhoto(val bytes: ByteArray, val mime: String = "image/jpeg", val filename: String = "supply.jpg") {
    override fun toString() = "SupplierPhoto(redacted)"
}
fun validateSupplierPhoto(photo: SupplierPhoto) {
    if (photo.mime !in setOf("image/jpeg", "image/png", "image/webp", "image/gif") || photo.bytes.isEmpty() || photo.bytes.size > 5 * 1024 * 1024)
        throw SupplierFailure("Choose a JPEG, PNG, GIF or WebP image no larger than 5 MiB.", true)
    if (!photo.filename.matches(Regex("[a-zA-Z0-9_-]+\\.(jpg|jpeg|png|webp|gif)")))
        throw SupplierFailure("Invalid photo filename.", true)
    val bytes = photo.bytes
    fun starts(vararg signature: Int) = bytes.size >= signature.size && signature.indices.all { (bytes[it].toInt() and 255) == signature[it] }
    val signatureMatches = when (photo.mime) {
        "image/jpeg" -> starts(0xFF, 0xD8, 0xFF)
        "image/png" -> starts(137, 80, 78, 71, 13, 10, 26, 10)
        "image/gif" -> bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")
        "image/webp" -> bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        else -> false
    }
    if (!signatureMatches) throw SupplierFailure("The selected file does not match its image format.", true)
}
