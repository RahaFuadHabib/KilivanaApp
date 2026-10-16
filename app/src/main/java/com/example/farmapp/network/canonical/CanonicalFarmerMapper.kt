package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.CategoryResponse
import com.example.farmapp.data.model.FarmerProfileResponse
import com.example.farmapp.data.model.canonical.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

private fun String?.required(field: String): String =
    this?.takeIf { it.isNotBlank() } ?: throw CanonicalContractException("Missing $field")

private fun Long?.id(field: String): String =
    this?.takeIf { it > 0 }?.toString() ?: throw CanonicalContractException("Invalid $field")

private fun String?.localTime(field: String): LocalDateTime? = this?.let {
    try { LocalDateTime.parse(it) }
    catch (_: DateTimeParseException) { throw CanonicalContractException("Invalid $field") }
}

private fun Int?.quantity(field: String): BigDecimal {
    val value = this ?: throw CanonicalContractException("Missing $field")
    if (value < 0) throw CanonicalContractException("Negative $field")
    return BigDecimal.valueOf(value.toLong())
}

/** Exact enum mapping: unknown types are not silently treated as farmer categories. */
fun canonicalSector(sellerType: String?): String = when (sellerType) {
    "farmer", "FARMER" -> "FARM_PRODUCE"
    "supplier", "SUPPLIER" -> "AGRI_INPUTS"
    else -> throw CanonicalContractException("Unsupported seller type")
}

/** Explicit canonical wire states; HIDDEN and INACTIVE belong to the reference backend. */
fun canonicalProductStatus(wireValue: String?): CanonicalProductStatus =
    CanonicalProductStatus.entries.firstOrNull { it.wireValue == wireValue }
        ?: throw CanonicalContractException("Unsupported product status")

fun CanonicalUserDto.toAccount(): CanonicalAccount {
    val roleValue = role.required("role")
    if (roleValue !in setOf("FARMER", "BUYER", "SUPPLIER", "INSPECTOR", "DRIVER", "ADMIN"))
        throw CanonicalContractException("Unsupported role")
    val statusValue = status.required("account status")
    if (statusValue !in setOf("ACTIVE", "INACTIVE", "SUSPENDED", "PENDING_VERIFICATION"))
        throw CanonicalContractException("Unsupported account status")
    val verification = verificationStatus.required("verification status")
    if (verification !in setOf("PENDING", "UNDER_REVIEW", "VERIFIED", "REJECTED", "NOT_REQUIRED"))
        throw CanonicalContractException("Unsupported verification status")
    return CanonicalAccount(
        id.id("user id"), name.required("name"), email.required("email"), phone.required("phone"),
        username, region, referenceCode, roleValue, statusValue, verification,
        createdAt.localTime("user createdAt")
    )
}

/** Existing profile display model is safe; no active auth/session validation is invoked. */
fun CanonicalUserDto.toFarmerProfile(): FarmerProfileResponse {
    val account = toAccount()
    if (account.role != "FARMER") throw CanonicalContractException("Expected farmer profile")
    return FarmerProfileResponse(account.userId, account.email, account.fullName,
        account.phoneNumber, account.username, account.region, account.role, account.verificationStatus)
}

fun CanonicalCategoryDto.toCategory(): CategoryResponse = CategoryResponse(
    id.id("category id"), name.required("category name"), canonicalSector(type),
    active ?: throw CanonicalContractException("Missing category active flag")
)

fun CanonicalProductImageDto.toImageView(): CanonicalImageView {
    if (sortOrder != null && sortOrder < 0) throw CanonicalContractException("Negative image sort order")
    return CanonicalImageView(id.id("image id"), url.required("image url"),
        publicId.required("image public id"), assetId, sortOrder, isPrimary,
        createdAt.localTime("image createdAt"))
}

/** Pure read projection; preserves price exactly and never converts money to floating point. */
fun CanonicalProductDto.toFarmerProduct(): CanonicalFarmerProduct {
    val stock = stockQty.quantity("stockQty")
    val reserved = reservedQty.quantity("reservedQty")
    if (reserved > stock) throw CanonicalContractException("Reservations exceed stock")
    val priceValue = price ?: throw CanonicalContractException("Missing price")
    if (priceValue < BigDecimal("0.01")) throw CanonicalContractException("Invalid price")
    val minimum = minimumOrderQty.quantity("minimumOrderQty")
    if (minimum < BigDecimal.ONE) throw CanonicalContractException("Invalid minimumOrderQty")
    val imageValues = images ?: throw CanonicalContractException("Missing product images")
    return CanonicalFarmerProduct(
        id.id("product id"), sellerId.id("seller id"), canonicalSector(sellerType),
        categoryId.id("product category id"), name.required("product name"), description,
        unit.required("unit"), priceValue, stock, reserved, stock.subtract(reserved),
        soldQty.quantity("soldQty"), minimum, canonicalProductStatus(status),
        imageValues.map { it.toImageView() }, createdAt.localTime("product createdAt"),
        updatedAt.localTime("product updatedAt")
    )
}

/** Future adapter boundary only. No request conversion or current mutation behavior is changed. */
fun canonicalStockQuantity(value: BigDecimal): Int {
    if (value.signum() < 0) throw CanonicalContractException("Negative stock quantity")
    return try { value.intValueExact() }
    catch (_: ArithmeticException) {
        throw CanonicalContractException("Stock quantity must be a whole number within Int range")
    }
}
