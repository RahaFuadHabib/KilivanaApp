package com.example.farmapp.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data model representing agricultural inputs or equipment supplies.
 *
 * @property id Unique supply item identifier.
 * @property itemName Description or name of the input/supply.
 * @property supplierName Name of the manufacturer or supplier.
 * @property unitPrice Price per unit in local currency.
 * @property stockAvailable Current quantity in stock.
 * @property paymentStatus Payment status (e.g. "PAID", "PENDING", "PARTIAL").
 */
data class SupplyItem(
    @SerializedName("id")
    val id: Long = 0L,

    @SerializedName("itemName")
    val itemName: String,

    @SerializedName("supplierName")
    val supplierName: String,

    @SerializedName("unitPrice")
    val unitPrice: Double,

    @SerializedName("stockAvailable")
    val stockAvailable: Int,

    @SerializedName("paymentStatus")
    val paymentStatus: String
) {
    companion object {
        val mockList: List<SupplyItem> = listOf(
            SupplyItem(
                id = 101L,
                itemName = "CAN Planting Fertilizer (50kg Bag)",
                supplierName = "Kenya Farmers Association (KFA)",
                unitPrice = 3500.0,
                stockAvailable = 140,
                paymentStatus = "PAID"
            ),
            SupplyItem(
                id = 102L,
                itemName = "Certified Hybrid Maize Seeds H614 (2kg)",
                supplierName = "Kenya Seed Company",
                unitPrice = 580.0,
                stockAvailable = 320,
                paymentStatus = "PAID"
            ),
            SupplyItem(
                id = 103L,
                itemName = "1-Acre Gravity Drip Irrigation Kit",
                supplierName = "Amiran Kenya Ltd",
                unitPrice = 45000.0,
                stockAvailable = 15,
                paymentStatus = "PENDING"
            ),
            SupplyItem(
                id = 104L,
                itemName = "Organic Neem Biopesticide (5 Litres)",
                supplierName = "Osho Chemical Industries",
                unitPrice = 2800.0,
                stockAvailable = 85,
                paymentStatus = "PAID"
            ),
            SupplyItem(
                id = 105L,
                itemName = "Solar Submersible Water Pump (1.5 HP)",
                supplierName = "Davis & Shirtliff",
                unitPrice = 68000.0,
                stockAvailable = 8,
                paymentStatus = "PENDING"
            ),
            SupplyItem(
                id = 106L,
                itemName = "NPK 17:17:17 Top-Dressing Fertilizer (50kg)",
                supplierName = "Yara East Africa",
                unitPrice = 4100.0,
                stockAvailable = 90,
                paymentStatus = "PAID"
            )
        )
    }
}

val mockSupplyItems: List<SupplyItem> get() = SupplyItem.mockList
