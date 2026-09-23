package com.example.farmapp.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data model representing a customer or wholesale produce order with payment, delivery, and rating tracking.
 *
 * @property id Unique order identifier.
 * @property buyerName Name of the purchasing customer or cooperative.
 * @property productName Name of the ordered produce.
 * @property quantityKg Ordered quantity in kilograms.
 * @property totalPrice Total amount payable.
 * @property orderStatus Current status ("PLACED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED").
 * @property deliveryOtp Confidential 4-digit handshake OTP for verifying receipt upon delivery.
 * @property driverName Assigned logistics delivery driver.
 * @property driverPhone Driver contact phone number.
 * @property paymentMethod Selected payment rail ("MPESA", "VISA", "GOOGLE_PAY").
 * @property paymentStatus Settlement state ("PENDING", "RECEIVED", "ESCROW").
 * @property ratingGiven Star rating given by receiver/buyer (1.0 to 5.0).
 * @property ratingReceived Star rating received by seller/driver (1.0 to 5.0).
 */
data class Order(
    @SerializedName("id")
    val id: Long = 0L,

    @SerializedName("buyerName")
    val buyerName: String,

    @SerializedName("productName")
    val productName: String,

    @SerializedName("quantityKg")
    val quantityKg: Double,

    @SerializedName("totalPrice")
    val totalPrice: Double,

    @SerializedName("orderStatus")
    val orderStatus: String,

    @SerializedName("deliveryOtp")
    val deliveryOtp: String = "4821",

    @SerializedName("driverName")
    val driverName: String = "John Kamau",

    @SerializedName("driverPhone")
    val driverPhone: String = "+254 712 345 678",

    @SerializedName("paymentMethod")
    val paymentMethod: String = "MPESA",

    @SerializedName("paymentStatus")
    val paymentStatus: String = "ESCROW",

    @SerializedName("ratingGiven")
    val ratingGiven: Float? = null,

    @SerializedName("ratingReceived")
    val ratingReceived: Float? = null
) {
    companion object {
        val mockList: List<Order> = listOf(
            Order(
                id = 501L,
                buyerName = "Nairobi Fresh Grocers Ltd",
                productName = "Hass Avocados",
                quantityKg = 500.0,
                totalPrice = 60000.0,
                orderStatus = "OUT_FOR_DELIVERY",
                deliveryOtp = "4821",
                driverName = "John Kamau",
                driverPhone = "+254 712 345 678",
                paymentMethod = "MPESA",
                paymentStatus = "ESCROW"
            ),
            Order(
                id = 502L,
                buyerName = "Highland Exporters Co.",
                productName = "French Beans (Export Grade)",
                quantityKg = 300.0,
                totalPrice = 54000.0,
                orderStatus = "IN_TRANSIT",
                deliveryOtp = "7319",
                driverName = "Kiprono Koech",
                driverPhone = "+254 722 890 123",
                paymentMethod = "VISA",
                paymentStatus = "ESCROW"
            ),
            Order(
                id = 503L,
                buyerName = "Eldoret Millers",
                productName = "Yellow Maize",
                quantityKg = 2000.0,
                totalPrice = 130000.0,
                orderStatus = "DELIVERED",
                deliveryOtp = "9154",
                driverName = "Samuel Mwangi",
                driverPhone = "+254 733 456 789",
                paymentMethod = "MPESA",
                paymentStatus = "RECEIVED",
                ratingGiven = 5.0f,
                ratingReceived = 4.9f
            ),
            Order(
                id = 504L,
                buyerName = "Mombasa Food Hub",
                productName = "Red Bulb Onions",
                quantityKg = 800.0,
                totalPrice = 76000.0,
                orderStatus = "OUT_FOR_DELIVERY",
                deliveryOtp = "2648",
                driverName = "Hassan Juma",
                driverPhone = "+254 701 987 654",
                paymentMethod = "GOOGLE_PAY",
                paymentStatus = "ESCROW"
            ),
            Order(
                id = 505L,
                buyerName = "Safari Nut Processors",
                productName = "Macadamia Nuts (In Shell)",
                quantityKg = 450.0,
                totalPrice = 144000.0,
                orderStatus = "PLACED",
                deliveryOtp = "5830",
                driverName = "Pending Dispatch",
                driverPhone = "Logistics Queue",
                paymentMethod = "MPESA",
                paymentStatus = "ESCROW"
            )
        )
    }
}

val mockOrders: List<Order> get() = Order.mockList
