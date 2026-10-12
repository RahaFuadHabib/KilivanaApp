package com.example.farmapp.ui.screens

/** Guards the whole two-step refresh without storing a second product/order snapshot. */
internal class CropDetailRefreshController {
    private val request = CropRefreshController()
    val refreshing get() = request.refreshing

    suspend fun refresh(refreshProducts: suspend () -> Unit, refreshOrders: suspend () -> Unit) {
        request.refresh(load = { refreshProducts(); refreshOrders() },
            onSuccess = {}, onFailure = { throw it })
    }
}