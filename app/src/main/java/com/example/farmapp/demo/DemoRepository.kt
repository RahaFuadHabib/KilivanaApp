package com.example.farmapp.demo

import com.example.farmapp.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal

/** UI demonstration data only. No API, token, authenticated identity or disk-store dependencies. */
internal data class DemoProfile(val name: String, val email: String, val phone: String, val county: String, val farm: String)
internal data class DemoLocation(val name: String, val latitude: Double, val longitude: Double, val description: String)
internal data class DemoSnapshot(
    val crops: List<FarmerProductResponse>, val orders: List<FarmerOrderResponse>,
    val activity: List<String>, val nextCropId: Int = 5
)

internal object DemoData {
    const val SELLER_ID = "demo-mary"
    val buyers = mapOf("DEMO-1001" to "Nakuru Fresh Grocers", "DEMO-1002" to "Njoro Millers",
        "DEMO-1003" to "Rift Valley Produce Traders", "DEMO-1004" to "Naivasha Food Co-operative")
    val profile = DemoProfile("Mary Wanjiku", "mary.wanjiku@example.test", "+254 700 000 000", "Nakuru County", "Wanjiku Family Farm · Njoro")
    val categories = listOf(
        CategoryResponse("demo-cereals", "Cereals", "FARM_PRODUCE", true),
        CategoryResponse("demo-vegetables", "Vegetables", "FARM_PRODUCE", true),
        CategoryResponse("demo-legumes", "Legumes", "FARM_PRODUCE", true)
    )
    val locations = listOf(
        DemoLocation("Wanjiku Family Farm", -0.330, 35.940, "Representative farm location near Njoro; demonstration coordinates."),
        DemoLocation("Nakuru market", -0.283, 36.067, "Representative produce market in Nakuru."),
        DemoLocation("Njoro collection point", -0.334, 35.952, "Illustrative collection point for this review; not an operational depot.")
    )
    fun initial(): DemoSnapshot {
        fun crop(id: Int, name: String, quantity: String, price: String, category: Int, description: String) =
            FarmerProductResponse("demo-crop-$id", name, description, "kg", BigDecimal(price), BigDecimal(quantity),
                categoryId = categories[category].categoryId, categoryName = categories[category].name)
        val crops = listOf(
            crop(1, "Maize", "2400", "55", 0, "Dry white maize from the Njoro harvest. Cleaned and bagged for local millers."),
            crop(2, "Tomatoes", "650", "90", 1, "Fresh ripe tomatoes, carefully graded for Nakuru grocers."),
            crop(3, "Potatoes", "1800", "65", 1, "Shangi potatoes grown in Nakuru County. Sorted and ready for collection."),
            crop(4, "Beans", "900", "140", 2, "Dry red beans, cleaned and sorted for wholesale supply.")
        )
        fun order(id: Int, crop: FarmerProductResponse, quantity: String, status: String, date: String): FarmerOrderResponse {
            val amount = BigDecimal(quantity)
            val total = amount * crop.unitPrice
            return FarmerOrderResponse("DEMO-$id", SELLER_ID, status, total, "KES", date,
                listOf(FarmerOrderItem(crop.productId, crop.name, amount, crop.unitPrice, total)))
        }
        return DemoSnapshot(crops, listOf(
            order(1001, crops[1], "120", "PLACED", "2026-10-08T07:30:00+03:00"),
            order(1002, crops[0], "500", "CONFIRMED", "2026-10-07T10:15:00+03:00"),
            order(1003, crops[2], "300", "IN_TRANSIT", "2026-10-06T09:00:00+03:00"),
            order(1004, crops[3], "150", "DELIVERED", "2026-10-05T14:20:00+03:00")
        ), listOf("08 Oct · Tomato order received · 120 kg", "07 Oct · Maize order confirmed · 500 kg", "05 Oct · Beans delivery recorded · 150 kg"))
    }
}

internal class DemoRepository(initial: DemoSnapshot = DemoData.initial()) {
    private val mutable = MutableStateFlow(initial)
    val state = mutable.asStateFlow()

    fun add(request: CreateProductRequest): String {
        validate(request.title, request.description, request.unit, request.unitPrice, request.availableQuantity, request.categoryId, false)
        val id = "demo-crop-${mutable.value.nextCropId}"
        mutable.update { current ->
            val category = DemoData.categories.first { it.categoryId == request.categoryId }
            val crop = FarmerProductResponse(id, request.title.trim(), request.description.trim(), request.unit,
                request.unitPrice, request.availableQuantity, categoryId = category.categoryId, categoryName = category.name)
            current.copy(crops = listOf(crop) + current.crops, nextCropId = current.nextCropId + 1,
                activity = (listOf("Added locally · ${crop.name} · ${crop.availableQuantity} ${crop.unit}") + current.activity).take(8))
        }
        return id
    }

    fun edit(id: String, request: UpdateProductRequest) {
        validate(request.title, request.description, request.unit, request.unitPrice, request.availableQuantity, request.categoryId, true)
        require(mutable.value.crops.any { it.productId == id }) { "Demo crop not found." }
        mutable.update { current -> current.copy(crops = current.crops.map {
            if (it.productId != id) it else it.copy(name = request.title.trim(), description = request.description.trim(),
                unit = request.unit, unitPrice = request.unitPrice, availableQuantity = request.availableQuantity,
                categoryId = request.categoryId, categoryName = DemoData.categories.first { category -> category.categoryId == request.categoryId }.name,
                status = if (request.availableQuantity.signum() == 0) "INACTIVE" else it.status)
        }, activity = (listOf("Updated locally · ${request.title.trim()}") + current.activity).take(8)) }
    }

    fun unlist(id: String) {
        require(mutable.value.crops.any { it.productId == id }) { "Demo crop not found." }
        mutable.update { current -> current.copy(crops = current.crops.map { if (it.productId == id) it.copy(status = "HIDDEN") else it },
            activity = (listOf("Unlisted locally · ${current.crops.first { it.productId == id }.name}") + current.activity).take(8)) }
    }

    /** One explicitly simulated review action, not a fulfillment/payment engine. */
    fun confirmOrder(id: String) {
        require(mutable.value.orders.any { it.orderId == id && it.status == "PLACED" }) { "Only a placed demo order can be confirmed." }
        mutable.update { current -> current.copy(orders = current.orders.map { if (it.orderId == id) it.copy(status = "CONFIRMED") else it },
            activity = (listOf("Confirmed locally · $id") + current.activity).take(8)) }
    }

    fun savedSnapshot(): String = Gson().toJson(mutable.value)
    companion object {
        fun restore(snapshot: String) = DemoRepository(Gson().fromJson(snapshot, DemoSnapshot::class.java))
        private fun validate(name: String, description: String, unit: String, price: BigDecimal, quantity: BigDecimal, categoryId: String?, allowZero: Boolean) {
            require(name.isNotBlank() && name.trim().length <= 150) { "Enter a crop name of up to 150 characters." }
            require(description.isNotBlank() && description.trim().length <= 1000) { "Enter a description of up to 1000 characters." }
            require(unit in setOf("kg", "bags", "crates", "pieces", "bunches")) { "Select a supported unit." }
            require(price >= BigDecimal("0.01")) { "Enter a price of at least KES 0.01." }
            require(if (allowZero) quantity.signum() >= 0 else quantity.signum() > 0) { "Enter a valid quantity greater than ${if (allowZero) "or equal to " else ""}zero." }
            require(DemoData.categories.any { it.categoryId == categoryId }) { "Select a demo category." }
        }
    }
}
