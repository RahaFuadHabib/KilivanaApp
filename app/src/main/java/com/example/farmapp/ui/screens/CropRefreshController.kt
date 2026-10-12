package com.example.farmapp.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/** All My Crops load triggers share this foreground, main-thread request guard. */
internal class CropRefreshController {
    var refreshing by mutableStateOf(false)
        private set

    suspend fun <T> refresh(load: suspend () -> T, onSuccess: (T) -> Unit, onFailure: (Exception) -> Unit) {
        if (refreshing) return
        refreshing = true
        try {
            val result = load()
            onSuccess(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Success is the only path that replaces existing crops.
            onFailure(e)
        } finally {
            refreshing = false
        }
    }
}
