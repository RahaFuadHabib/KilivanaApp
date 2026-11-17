package com.example.farmapp.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.farmapp.data.model.FarmerOrderResponse
import com.example.farmapp.farmerpurchases.FarmerSellerOrderSummary
import java.io.IOException

/** One seller-order snapshot shared by the Orders tab, crop orders and detail counts. */
internal class FarmerOrdersRefreshController {
    private val request = CropRefreshController()
    val refreshing get() = request.refreshing
    var orders by mutableStateOf<List<FarmerOrderResponse>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun refresh(load: suspend () -> List<FarmerOrderResponse>) {
        request.refresh(load, onSuccess = { orders = it; error = null }, onFailure = {
            error = "Unable to load orders. Check your connection and retry."
        })
    }
}

internal class FarmerSellerOrdersRefreshController {
    private val request = CropRefreshController()
    val refreshing get() = request.refreshing
    var orders by mutableStateOf<List<FarmerSellerOrderSummary>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun refresh(load: suspend () -> List<FarmerSellerOrderSummary>) {
        request.refresh(load, onSuccess = { orders = it; error = null }, onFailure = { failure ->
            error = if (failure is IOException) "Couldn't connect to incoming orders. Check your connection and retry."
                else failure.message ?: "Incoming orders could not be verified."
        })
    }
}