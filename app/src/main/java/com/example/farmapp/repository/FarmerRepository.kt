package com.example.farmapp.repository

import com.example.farmapp.data.model.Farmer
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.data.remote.FarmApiService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repository responsible for managing Farmer data operations.
 * Communicates with [FarmApiService] and exposes Result-wrapped Flows and suspend functions.
 */
class FarmerRepository(
    private val apiService: FarmApiService = ApiClient.farmApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Emits a [Result] wrapping the list of fetched farmers via Flow.
     */
    fun getFarmersFlow(): Flow<Result<List<Farmer>>> = flow {
        try {
            val farmers = apiService.getFarmers()
            emit(Result.success(farmers))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(ioDispatcher)

    /**
     * Safely fetches the list of farmers using a suspend function.
     */
    suspend fun getFarmers(): Result<List<Farmer>> = withContext(ioDispatcher) {
        runCatching {
            apiService.getFarmers()
        }
    }

    /**
     * Safely creates a new farmer using a suspend function.
     */
    suspend fun createFarmer(farmer: Farmer): Result<Farmer> = withContext(ioDispatcher) {
        runCatching {
            apiService.addFarmer(farmer)
        }
    }

    /**
     * Emits a [Result] wrapping the created farmer via Flow.
     */
    fun createFarmerFlow(farmer: Farmer): Flow<Result<Farmer>> = flow {
        try {
            val created = apiService.addFarmer(farmer)
            emit(Result.success(created))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(ioDispatcher)

    /**
     * Safely fetches a farmer by ID.
     */
    suspend fun getFarmerById(id: Long): Result<Farmer> = withContext(ioDispatcher) {
        runCatching {
            apiService.getFarmerById(id)
        }
    }

    /**
     * Safely updates an existing farmer.
     */
    suspend fun updateFarmer(id: Long, farmer: Farmer): Result<Farmer> = withContext(ioDispatcher) {
        runCatching {
            apiService.updateFarmer(id, farmer)
        }
    }

    /**
     * Safely deletes a farmer by ID.
     */
    suspend fun deleteFarmer(id: Long): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val response = apiService.deleteFarmer(id)
            if (!response.isSuccessful) {
                throw Exception("Failed to delete farmer: HTTP ${response.code()} ${response.message()}")
            }
        }
    }
}
