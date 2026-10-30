package com.example.farmapp.buyer

import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val BUYER_SERVICE_CHARGE_ESTIMATE = BigDecimal("30.00")
internal fun buyerEstimatedTotalBeforeDelivery(subtotal: BigDecimal?) = subtotal?.add(BUYER_SERVICE_CHARGE_ESTIMATE)
internal fun buyerWelcome(name: String?): String = name?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() }
    ?.let { "Welcome, $it" } ?: "Welcome"
/** Original server identity is a stable reference; no SKU is invented. */
internal fun buyerProductReference(productId: String) = productId
internal fun buyerDate(value: String?, displayZone: ZoneId = ZoneId.systemDefault()): String {
    if (value.isNullOrBlank()) return "Date unavailable"
    val format = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
    runCatching { Instant.parse(value).atZone(displayZone) }.getOrNull()?.let { return format.format(it) }
    runCatching { OffsetDateTime.parse(value).atZoneSameInstant(displayZone) }.getOrNull()?.let { return format.format(it) }
    // Offset-free backend timestamps have no proven time zone: preserve their wall-clock time, never assume UTC.
    runCatching { LocalDateTime.parse(value) }.getOrNull()?.let { return format.format(it) }
    runCatching { LocalDate.parse(value) }.getOrNull()?.let { return DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).format(it) }
    return "Date unavailable"
}
