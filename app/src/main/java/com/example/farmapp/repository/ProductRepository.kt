//is responsible for communicating with the backend to perform crop-related operations.
//It acts as an intermediary between the application logic and the Retrofit API service.
package com.example.farmapp.repository

import com.example.farmapp.data.model.*
import com.example.farmapp.data.remote.FarmApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import retrofit2.Response
import java.io.IOException

class ProductFailure(message: String, val rejected: Boolean = false, val httpStatus: Int? = null) : IOException(message)

class ProductRepository(private val api: FarmApiService) {
    val canonical get() = api.canonical  //This is called constructor dependency injection.

    // suspend:Marks a function that can suspend execution within a coroutine. It allows asynchronous operations without blocking the calling thread while suspended.
    suspend fun categories() = withContext(Dispatchers.IO) { checked(api.categories("FARM_PRODUCE")) }
    suspend fun update(id: String, request: UpdateProductRequest) = withContext(Dispatchers.IO) { managedResponse(api.updateProduct(id, request), "Update crop") }
    suspend fun orders() = withContext(Dispatchers.IO) { checked(api.farmerOrders()) }
    suspend fun unlist(id: String) = withContext(Dispatchers.IO) {
        val response = api.unlistProduct(id)
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw ProductFailure("The server could not remove this listing (${response.code()}). Refresh and try again.", true)
        }
    }
    suspend fun create(request: CreateProductRequest) = withContext(Dispatchers.IO) { checked(api.createProduct(request)) }
    suspend fun mine() = withContext(Dispatchers.IO) { managedResponse(api.myProducts(), "Refresh My Crops") }
    suspend fun upload(id: String, photo: MultipartBody.Part) = withContext(Dispatchers.IO) {
        if (api.canonical) return@withContext checked(api.uploadProductImage(id, photo))
        // Reference backend reconciliation only; canonical uncertainty is checkpointed by CropPublisher.
        val disposition = photo.headers?.get("Content-Disposition").orEmpty()
        val filename = Regex("filename=\"([^\"]+)\"").find(disposition)?.groupValues?.get(1)
        val existing = checked(api.productImages(id)).firstOrNull { it.fileName == filename }
        existing ?: checked(api.uploadProductImage(id, photo))
    }

    private fun <T> managedResponse(response: Response<T>, operation: String): T {
        if (response.isSuccessful) return checked(response)
        // Only display the backend's ApiError fields, never raw HTML, headers or stack traces.
        val details = response.errorBody()?.use { body ->
            runCatching {
                val buffer = CharArray(8192)
                val length = body.charStream().use { reader ->
                    var total = 0
                    while (total < buffer.size) {
                        val count = reader.read(buffer, total, buffer.size - total)
                        if (count <= 0) break
                        total += count
                    }
                    total
                }
                val json = com.google.gson.JsonParser.parseString(if (length > 0) String(buffer, 0, length) else "{}").asJsonObject
                fun value(key: String) = json.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString?.filter { !it.isISOControl() }?.take(300)
                val fields = json.getAsJsonArray("fieldErrors")?.take(3)?.mapNotNull {
                    val field = it.asJsonObject.get("field")?.asString
                    val message = it.asJsonObject.get("message")?.asString
                    if (field != null && message != null) "$field: $message" else null
                }?.joinToString("; ")?.filter { !it.isISOControl() }?.take(500)
                listOfNotNull(value("message"), fields?.takeIf { it.isNotBlank() },
                    value("requestId")?.let { "Reference: $it" }).joinToString(". ")
            }.getOrDefault("")
        }.orEmpty()
        val fallback = when (response.code()) {
            401, 403 -> "Your session or permissions could not be verified."
            404 -> "The crop could not be found."
            409 -> "The crop conflicts with its current server state."
            400, 422 -> "Check the crop details and category."
            else -> "The crop service could not complete the request."
        }
        throw ProductFailure("$operation failed (HTTP ${response.code()}). ${details.ifBlank { fallback }}",
            rejected = response.code() in setOf(400, 401, 403, 404, 413, 415, 422, 429), httpStatus = response.code())
    }

    private fun <T> checked(response: Response<T>): T {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw ProductFailure(when(response.code()) {
                401, 403 -> "Your session or permissions could not be verified. Please sign in again."
                400, 422 -> "The server rejected these crop details or photo. Check the description, quantity and image format."
                413 -> "This photo is too large. Choose an image no larger than 5 MiB."
                415 -> "Choose a JPEG, PNG, GIF or WebP photo."
                else -> "The crop service is unavailable. Please try again."
            }, rejected = response.code() in setOf(400, 401, 403, 404, 413, 415, 422, 429))
        }
        return response.body() ?: throw ProductFailure("The server returned an incomplete response. Refresh My Crops before trying again.")
    }
}
