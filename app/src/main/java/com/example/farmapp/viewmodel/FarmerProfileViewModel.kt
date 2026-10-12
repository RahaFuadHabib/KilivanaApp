package com.example.farmapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.farmapp.data.model.FarmerProfileResponse
import com.example.farmapp.repository.FarmerProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FarmerProfileState(val profile: FarmerProfileResponse? = null, val busy: Boolean = false,
    val message: String? = null, val failed: Boolean = false)

class FarmerProfileViewModel(private val repository: FarmerProfileRepository) : ViewModel() {
    private val mutable = MutableStateFlow(FarmerProfileState())
    val state = mutable.asStateFlow()
    init { refresh() }

    fun refresh() = request("Profile refreshed.") { repository.load() }
    fun savePhone(phone: String) = request("Phone number saved to your account.") { repository.savePhone(phone) }

    private fun request(success: String, action: suspend () -> FarmerProfileResponse) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = null, failed = false)
        viewModelScope.launch {
            try { mutable.value = FarmerProfileState(profile = action(), message = success) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.value = mutable.value.copy(busy = false, failed = true,
                    message = if (e is IllegalArgumentException || e is IllegalStateException) e.message
                    else "Could not confirm the profile request. Refresh before retrying a save.")
            }
        }
    }

    companion object {
        fun factory(repository: FarmerProfileRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = FarmerProfileViewModel(repository) as T
        }
    }
}
