package com.example.farmapp.ui.screens

import androidx.compose.runtime.*
import com.example.farmapp.data.model.*
import com.example.farmapp.repository.ProductFailure
import kotlinx.coroutines.CancellationException

internal class CropLifecycleController(
    private val mutate: suspend (String, FarmerProductAction) -> Unit,
    private val mine: suspend () -> List<FarmerProductResponse>,
    private val detail: suspend (String) -> FarmerProductResponse
) {
    var busy by mutableStateOf(false)
        private set
    var refreshRequired by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun begin(): Boolean {
        if (busy || refreshRequired) return false
        busy = true
        message = null
        return true
    }

    fun refreshed() { if (!busy) refreshRequired = false }

    suspend fun execute(id: String, action: FarmerProductAction,
        onProducts: (List<FarmerProductResponse>) -> Unit, onDeleted: () -> Unit) {
        check(busy)
        var confirmed = false
        refreshRequired = true
        try {
            mutate(id, action)
            confirmed = true
            if (action == FarmerProductAction.DELETE) onDeleted()
            // Detail comes from its owner-aware route, not an optimistic status projection.
            val product = if (action == FarmerProductAction.DELETE) null else detail(id)
            val products = mine().filter { it.status != "DELETED" }
            onProducts(if (product == null) products else products.map { if (it.productId == id) product else it })
            refreshRequired = false
            message = action.successMessage
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val reason = if (e is ProductFailure) e.message.orEmpty() else "Check your connection or the deployed backend version."
            message = if (confirmed) "${action.successMessage} Refresh failed. $reason Refresh My Crops; the action will not be resent."
                else "${action.label} was not confirmed. $reason Refresh My Crops before trying another action; no automatic retry was made."
        } finally { busy = false }
    }
}
