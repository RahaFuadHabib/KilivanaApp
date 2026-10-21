package com.example.farmapp.ui.screens

import com.example.farmapp.data.model.*

internal data class CropEdit(val title: String, val quantity: String, val unit: String,
    val price: String, val description: String, val categoryId: String? = null) {
    fun request(product: FarmerProductResponse): UpdateProductRequest {
        require(title.isNotBlank() && title.trim().length <= 150) { "Enter a crop name of up to 150 characters." }
        require(description.isNotBlank() && description.trim().length <= 1000) { "Enter a description of up to 1000 characters." }
        require(!categoryId.isNullOrBlank()) { "Select a category." }
        require(unit.isNotBlank() && unit.length <= 30) { "Choose a unit." }
        val amount = quantity.toBigDecimalOrNull()
        require(amount != null && amount.signum() >= 0) { "Enter a quantity of zero or more." }
        val cost = price.toBigDecimalOrNull()
        require(cost != null && cost >= java.math.BigDecimal("0.01")) { "Enter a price of at least KES 0.01." }
        return UpdateProductRequest(title.trim(), description.trim(), unit, cost, amount,
            categoryId, product.lowStockThreshold, product.minimumOrderQuantity)
    }
    companion object {
        fun from(product: FarmerProductResponse) = CropEdit(product.name, product.availableQuantity.toPlainString(),
            product.unit, product.unitPrice.toPlainString(), product.description.orEmpty(), product.categoryId)
    }
}

internal fun cropOrders(orders: List<FarmerOrderResponse>, farmerId: String, productId: String? = null) =
    orders.filter { it.sellerId == farmerId && (productId == null || it.items.any { item -> item.productId == productId }) }
        .distinctBy { it.orderId }

/** Mutations never change the visible list before the server accepts them and mine is refreshed. */
internal class CropManagement(
    private val update: suspend (String, UpdateProductRequest) -> Unit,
    private val unlist: suspend (String) -> Unit,
    private val mine: suspend () -> List<FarmerProductResponse>
) {
    var pendingRemoval: String? = null
        private set
    fun requestRemoval(id: String) { pendingRemoval = id }
    fun cancelRemoval() { pendingRemoval = null }
    suspend fun edit(product: FarmerProductResponse, draft: CropEdit): List<FarmerProductResponse> {
        update(product.productId, draft.request(product))
        try { return mine() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { throw CropEditRefreshFailure(e) }
    }
    suspend fun refresh() = mine()
    suspend fun confirmRemoval(id: String): List<FarmerProductResponse> {
        check(pendingRemoval == id) { "Confirm removal first." }
        pendingRemoval = null
        unlist(id)
        return mine()
    }
}

internal data class CropDetailPresentation(val name: String, val quantity: String, val price: String,
    val description: String, val status: String, val imageUrl: String?)
internal fun FarmerProductResponse.detailPresentation() = CropDetailPresentation(name,
    "${availableQuantity.toPlainString()} $unit",
    "KES ${unitPrice.toPlainString()} / ${CropUnit.entries.firstOrNull { it.label == unit }?.priceUnit ?: unit}",
    description.orEmpty(), productStatusLabel(status), images.minByOrNull { it.sortOrder }?.url)

internal fun productStatusLabel(status: String): String = status.trim().split('_', ' ')
    .filter { it.isNotBlank() }.joinToString(" ") { word ->
        word.lowercase(java.util.Locale.ROOT).replaceFirstChar { it.titlecase(java.util.Locale.ROOT) }
    }.ifBlank { "Unknown" }

internal class CropEditRefreshFailure(cause: Exception) : IllegalStateException("Crop updated, but refreshing My Crops failed.", cause)
