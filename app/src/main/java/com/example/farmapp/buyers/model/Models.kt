// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.model

data class Product(
    val id: String, val name: String, val category: String, val pricePerUnit: java.math.BigDecimal,
    val unit: String, val description: String, val sellerName: String?,
    val sellerLocation: String?, val stockStatus: StockStatus, val minimumOrder: java.math.BigDecimal,
    val harvestNote: String? = null,
    val imageUrl: String? = null
)
enum class StockStatus { InStock, LowStock, OutOfStock }
data class CartItem(val product: Product, val quantity: Int, val lineSubtotal: java.math.BigDecimal? = null)
enum class OrderStatus { Pending, Confirmed, OutForDelivery, Delivered, Cancelled, Disputed }
data class Order(
    val id: String, val date: String, val status: OrderStatus, val items: List<CartItem>,
    val subtotal: java.math.BigDecimal?, val deliveryFee: java.math.BigDecimal?, val total: java.math.BigDecimal?, val deliveryAddress: String,
    val code: String = id, val timeline: List<OrderEvent> = emptyList(),
    val paymentStatus: String? = null, val paymentMethod: String? = null
)
data class OrderEvent(val status: String, val note: String?, val createdAt: String)
fun formatKes(amount: java.math.BigDecimal?): String = amount?.let {
    "KES " + java.text.NumberFormat.getNumberInstance(java.util.Locale.US).apply {minimumFractionDigits=0;maximumFractionDigits=2}.format(it)
} ?: "Unavailable"

