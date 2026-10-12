package com.example.farmapp.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.farmapp.data.model.FarmerProductResponse
import com.example.farmapp.repository.ProductFailure
import kotlinx.coroutines.CancellationException
import java.net.SocketTimeoutException
import java.io.IOException

internal data class CropSaveState(val saving: Boolean = false, val message: String? = null,
    val error: Boolean = false, val refreshRequired: Boolean = false, val completedSaves: Int = 0) {
    val buttonLabel get() = if (saving) "Saving…" else if (refreshRequired) "Retry refresh" else "Save changes"
}

/** begin() runs before launching the coroutine, closing the rapid double-tap window. */
internal class CropSaveController(private val manager: CropManagement) {
    var state by mutableStateOf(CropSaveState())
        private set
    fun reset() { if (!state.saving) state = CropSaveState() }
    fun begin(): Boolean {
        if (state.saving) return false
        state = state.copy(saving = true, message = null, error = false)
        return true
    }
    suspend fun save(product: FarmerProductResponse, draft: CropEdit,
        onProducts: (List<FarmerProductResponse>) -> Unit, validate: () -> Unit = {}) {
        check(state.saving)
        val refreshOnly = state.refreshRequired
        try {
            val products = if (refreshOnly) manager.refresh() else {
                validate()
                manager.edit(product, draft)
            }
            onProducts(products)
            state = CropSaveState(message = "Crop updated successfully.", completedSaves = state.completedSaves + 1)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val refreshRequired = refreshOnly || e is CropEditRefreshFailure
            val reason = saveError(if (e is CropEditRefreshFailure) e.cause ?: e else e)
            state = state.copy(message = if (refreshRequired)
                "Crop updated, but My Crops could not refresh. $reason Tap Retry refresh; the update will not be sent again."
                else "Could not save changes. $reason Your edits are kept; you can retry.",
                error = true, refreshRequired = refreshRequired)
        } finally { state = state.copy(saving = false) }
    }
}

private fun saveError(error: Throwable): String = when (error) {
    is ProductFailure -> error.message ?: "The crop service rejected the request."
    is SocketTimeoutException -> "The request timed out."
    is IOException -> "Unable to reach Kilivana. Check your connection."
    is IllegalArgumentException -> error.message ?: "Check the crop details."
    else -> "The crop service returned an unexpected response. Refresh My Crops before retrying."
}
