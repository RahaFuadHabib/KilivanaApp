package com.example.farmapp.supplier

import java.math.BigDecimal

// Deliberately exclude buyer/contact/address/payment fields and whole-order monetary totals.
data class SupplierOrderPageDto(val content: List<SupplierOrderDto>? = null, val number: Int? = null,
    val size: Int? = null, val last: Boolean? = null, val totalPages: Int? = null, val totalElements: Long? = null)
data class SupplierOrderDto(val id: Long? = null, val code: String? = null, val status: String? = null,
    val createdAt: String? = null, val items: List<SupplierOrderLineDto>? = null)
data class SupplierOrderLineDto(val id: Long? = null, val productId: Long? = null, val sellerId: Long? = null,
    val productName: String? = null, val unit: String? = null, val quantity: Int? = null,
    val unitPrice: BigDecimal? = null, val subtotal: BigDecimal? = null)
data class SupplierOrderLine(val id: Long, val productId: Long, val name: String?, val unit: String?,
    val quantity: Int, val unitPrice: BigDecimal, val subtotal: BigDecimal)
data class SupplierOrder(val id: Long, val code: String?, val status: String?, val createdAt: String?,
    val lines: List<SupplierOrderLine>) {
    val supplierSubtotal: BigDecimal get() = lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.subtotal }
}
data class SupplierOrderPage(val orders: List<SupplierOrder>, val number: Int, val last: Boolean)

internal fun SupplierOrderPageDto.verified(owner: Long, requestedPage: Int, requestedSize: Int): SupplierOrderPage {
    fun invalid(): Nothing = throw SupplierFailure("Order information could not be verified. Please try again.")
    val rows = content ?: invalid()
    if (number != requestedPage || size != requestedSize || last == null || totalPages == null || totalPages < 0 ||
        totalElements == null || totalElements < rows.size || rows.size > requestedSize ||
        (totalPages == 0 && (requestedPage != 0 || rows.isNotEmpty() || totalElements != 0L)) ||
        (totalPages > 0 && requestedPage >= totalPages) || last != (totalPages == 0 || requestedPage == totalPages - 1) ||
        (!last && rows.isEmpty())) invalid()
    val orders = rows.map { row ->
        val id = row.id?.takeIf { it > 0 } ?: invalid()
        val items = row.items?.takeIf { it.isNotEmpty() } ?: invalid()
        if (items.any { it.sellerId == null || it.sellerId <= 0 }) invalid()
        val owned = items.filter { it.sellerId == owner }.takeIf { it.isNotEmpty() } ?: invalid()
        val lines = owned.map { line ->
            val lineId = line.id?.takeIf { it > 0 } ?: invalid()
            val productId = line.productId?.takeIf { it > 0 } ?: invalid()
            val quantity = line.quantity?.takeIf { it > 0 } ?: invalid()
            val price = line.unitPrice?.takeIf { it.signum() >= 0 } ?: invalid()
            val subtotal = line.subtotal?.takeIf { it.signum() >= 0 } ?: invalid()
            if ((price * quantity.toBigDecimal()).compareTo(subtotal) != 0) invalid()
            SupplierOrderLine(lineId, productId, line.productName?.takeIf { it.isNotBlank() },
                line.unit?.takeIf { it.isNotBlank() }, quantity, price, subtotal)
        }
        if (lines.map { it.id }.distinct().size != lines.size) invalid()
        // Unknown status values remain unknown, never inferred as paid/delivered.
        SupplierOrder(id, row.code?.takeIf { it.isNotBlank() }, SupplierOrderStatus.parse(row.status?.lowercase())?.wire,
            row.createdAt, lines)
    }
    if (orders.map { it.id }.distinct().size != orders.size) invalid()
    return SupplierOrderPage(orders, requestedPage, last)
}
