package com.example.farmapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.farmapp.data.model.Farmer
import com.example.farmapp.repository.FarmerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State representing the farmer list screen.
 */
sealed interface FarmerUiState {
    data object Loading : FarmerUiState
    data class Success(val farmers: List<Farmer>) : FarmerUiState
    data class Error(val message: String) : FarmerUiState
}

/**
 * ViewModel managing the Farmer UI state and actions.
 */
class FarmerViewModel(
    private val repository: FarmerRepository = FarmerRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<FarmerUiState>(FarmerUiState.Loading)
    val uiState: StateFlow<FarmerUiState> = _uiState.asStateFlow()

    init {
        fetchFarmers()
    }

    /**
     * Fetches farmers from the repository and updates [_uiState].
     */
    fun fetchFarmers() {
        viewModelScope.launch {
            _uiState.value = FarmerUiState.Loading
            repository.getFarmersFlow().collect { result ->
                result.fold(
                    onSuccess = { farmers ->
                        _uiState.value = FarmerUiState.Success(farmers)
                    },
                    onFailure = { error ->
                        _uiState.value = FarmerUiState.Error(
                            error.localizedMessage ?: "An unexpected error occurred while fetching farmers."
                        )
                    }
                )
            }
        }
    }

    /**
     * Alias for [fetchFarmers] to support standard naming conventions.
     */
    fun loadFarmers() = fetchFarmers()

    /**
     * Refreshes the farmer list.
     */
    fun refresh() = fetchFarmers()

    /**
     * Safely creates a farmer using the repository and updates UI state on success.
     *
     * @param farmer The farmer entity to create.
     * @param onSuccess Optional callback invoked when the farmer is created successfully.
     * @param onError Optional callback invoked with the error message upon failure.
     */
    fun createFarmer(
        farmer: Farmer,
        onSuccess: ((Farmer) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.createFarmer(farmer)
            result.fold(
                onSuccess = { createdFarmer ->
                    val currentState = _uiState.value
                    if (currentState is FarmerUiState.Success) {
                        _uiState.value = FarmerUiState.Success(listOf(createdFarmer) + currentState.farmers)
                    } else {
                        fetchFarmers()
                    }
                    onSuccess?.invoke(createdFarmer)
                },
                onFailure = { error ->
                    val message = error.localizedMessage ?: "Failed to create farmer."
                    onError?.invoke(message)
                }
            )
        }
    }
}
