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
    val isVerified: Boolean = false,

    @SerializedName("inspectionStatus")
    val inspectionStatus: String = "Approved"
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
                isVerified = true,
                inspectionStatus = "Approved"
            ),
            Product(
                id = 2L,
                name = "French Beans (Export Grade)",
                category = "Veggies",
                pricePerKg = 180.0,
                quantityKg = 420.0,
                farmerName = "Peter Kiprop",
                county = "Uasin Gishu",
                isVerified = true,
                inspectionStatus = "Pending"
            ),
            Product(
                id = 3L,
                name = "Yellow Maize",
                category = "Cereals",
                pricePerKg = 65.0,
                quantityKg = 2500.0,
                farmerName = "Alice Chebet",
                county = "Trans Nzoia",
                isVerified = true,
                inspectionStatus = "Approved"
            ),
            Product(
                id = 4L,
                name = "Irish Potatoes (Shangi)",
                category = "Tubers",
                pricePerKg = 85.0,
                quantityKg = 1600.0,
                farmerName = "David Njoroge",
                county = "Nyandarua",
                isVerified = true,
                inspectionStatus = "Approved"
            ),
            Product(
                id = 5L,
                name = "Apple Mangoes",
                category = "Fruits",
                pricePerKg = 140.0,
                quantityKg = 750.0,
                farmerName = "Grace Mutua",
                county = "Machakos",
                isVerified = true,
                inspectionStatus = "Approved"
            ),
            Product(
                id = 6L,
                name = "Red Bulb Onions",
                category = "Veggies",
                pricePerKg = 95.0,
                quantityKg = 1200.0,
                farmerName = "Hassan Abdi",
                county = "Kajiado",
                isVerified = true,
                inspectionStatus = "Approved"
            ),
            Product(
                id = 7L,
                name = "Purple Tea Leaves",
                category = "Veggies",
                pricePerKg = 240.0,
                quantityKg = 310.0,
                farmerName = "David Karanja",
                county = "Nyeri",
                isVerified = false,
                inspectionStatus = "Flagged"
            ),
            Product(
                id = 8L,
                name = "Sweet Potatoes (Orange-Fleshed)",
                category = "Tubers",
                pricePerKg = 70.0,
                quantityKg = 900.0,
                farmerName = "Mary Achieng",
                county = "Homa Bay",
                isVerified = true,
                inspectionStatus = "Pending"
            ),
            Product(
                id = 9L,
                name = "Pearl Millet",
                category = "Cereals",
                pricePerKg = 110.0,
                quantityKg = 650.0,
                farmerName = "John Musyoka",
                county = "Kitui",
                isVerified = true,
                inspectionStatus = "Approved"
            )
        )
    }
}

val mockProducts: List<Product> get() = Product.mockList
