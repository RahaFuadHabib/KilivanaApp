package com.example.farmapp.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data model representing a Farmer entity.
 *
 * @property id Unique identifier for the farmer.
 * @property name Full name of the farmer.
 * @property phone Contact phone number.
 * @property county County or region where the farm is located.
 * @property cropTypes List of crop varieties cultivated by the farmer.
 */
data class Farmer(
    @SerializedName("id")
    val id: Long = 0L,

    @SerializedName("name")
    val name: String,

    @SerializedName("phone")
    val phone: String,

    @SerializedName("county")
    val county: String,

    @SerializedName("cropTypes")
    val cropTypes: List<String> = emptyList()
)
