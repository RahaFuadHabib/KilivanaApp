package com.example.farmapp.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data model representing agricultural produce listed for sale.
 *
 * @property id Unique product identifier.
 * @property name Name of the crop or produce.
 * @property category Agricultural category (e.g. Fruits, Cereals, Vegetables).
 * @property pricePerKg Selling price per kilogram.
 * @property quantityKg Available quantity in kilograms.
 * @property farmerName Name of the producer farmer.
 * @property county County where the produce originated.
 * @property isVerified Quality and compliance verification flag.
 */
data class Product(
    @SerializedName("id")
    val id: Long = 0L,

    @SerializedName("name")
    val name: String,

    @SerializedName("category")
    val category: String,

    @SerializedName("pricePerKg")
    val pricePerKg: Double,

    @SerializedName("quantityKg")
    val quantityKg: Double,

    @SerializedName("farmerName")
    val farmerName: String,

    @SerializedName("county")
    val county: String,

    @SerializedName("isVerified")
    val isVerified: Boolean = false
) {
    companion object {
        val mockList: List<Product> = listOf(
            Product(
                id = 1L,
                name = "Hass Avocados",
                category = "Fruits",
                pricePerKg = 120.0,
                quantityKg = 850.0,
                farmerName = "Wanjiku Mwangi",
                county = "Murang'a",
                isVerified = true
            ),
            Product(
                id = 2L,
                name = "French Beans (Export Grade)",
                category = "Vegetables",
                pricePerKg = 180.0,
                quantityKg = 420.0,
                farmerName = "Peter Kiprop",
                county = "Uasin Gishu",
                isVerified = true
            ),
            Product(
                id = 3L,
                name = "Yellow Maize",
                category = "Cereals & Grains",
                pricePerKg = 65.0,
                quantityKg = 2500.0,
                farmerName = "Alice Chebet",
                county = "Trans Nzoia",
                isVerified = true
            ),
            Product(
                id = 4L,
                name = "Purple Tea Leaves",
                category = "Cash Crops",
                pricePerKg = 240.0,
                quantityKg = 310.0,
                farmerName = "David Njoroge",
                county = "Nyeri",
                isVerified = false
            ),
            Product(
                id = 5L,
                name = "Macadamia Nuts (In Shell)",
                category = "Nuts & Seeds",
                pricePerKg = 320.0,
                quantityKg = 600.0,
                farmerName = "Grace Mutua",
                county = "Embu",
                isVerified = true
            ),
            Product(
                id = 6L,
                name = "Red Bulb Onions",
                category = "Vegetables",
                pricePerKg = 95.0,
                quantityKg = 1200.0,
                farmerName = "Hassan Abdi",
                county = "Kajiado",
                isVerified = true
            )
        )
    }
}

val mockProducts: List<Product> get() = Product.mockList
