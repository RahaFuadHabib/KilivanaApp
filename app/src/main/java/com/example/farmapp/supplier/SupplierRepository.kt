package com.example.farmapp.supplier

import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import kotlinx.coroutines.CancellationException

class SupplierRepository(private val api: SupplierApi, private val sessions: SessionStore, private val origin: String) {
    internal fun owner(expected: SessionSnapshot): Long {
        val current = sessions.session.value
        val auth = current.auth
        if (current.generation != expected.generation || current.loggingOut || current.isDemo || auth == null ||
            auth.role != "SUPPLIER" || auth.backendContract != "canonical" || auth.sessionOrigin != origin || auth.userId != expected.auth?.userId)
            throw SupplierFailure("Sign in with your administrator-provisioned Supplier account.", true)
        return auth.userId.toLongOrNull()?.takeIf { it > 0 && it.toString() == auth.userId }
            ?: throw SupplierFailure("Supplier identity could not be verified.", true)
    }
    internal fun key(expected: SessionSnapshot) = origin + "|" + owner(expected)
    private suspend fun authenticatedOwner(expected: SessionSnapshot): Long {
        val id = owner(expected)
        // This protected route cannot silently succeed with an anonymous/public projection.
        val account = read(api.me(expected)).toAccount()
        owner(expected)
        if (account.userId != id.toString() || account.role != "SUPPLIER")
            throw SupplierFailure("Supplier identity could not be verified.", true)
        return id
    }
    suspend fun profile(expected: SessionSnapshot): SupplierProfile {
        val id = owner(expected)
        val account = read(api.me(expected)).toAccount()
        owner(expected)
        if (account.userId != id.toString() || account.role != "SUPPLIER") throw SupplierFailure("Supplier identity could not be verified.")
        val response = api.profile(id, expected)
        val business = if (response.code() == 404) { response.errorBody()?.close(); null } else read(response)
        owner(expected)
        if (business != null && (business.userId != id || business.id == null || business.id <= 0)) throw SupplierFailure("Business profile ownership could not be verified.")
        return SupplierProfile(account, business)
    }
    suspend fun categories(expected: SessionSnapshot): List<CanonicalCategoryDto> {
        owner(expected)
        val result = read(api.categories(expected))
        owner(expected)
        if (result.any { it.id == null || it.id <= 0 || it.name.isNullOrBlank() || it.active == null || it.type !in setOf("supplier", "SUPPLIER") } ||
            result.map { it.id }.distinct().size != result.size) throw SupplierFailure("Supplier categories could not be verified.")
        return result.filter { it.active == true }
    }
    suspend fun listings(expected: SessionSnapshot): List<SupplyListing> {
        val id = authenticatedOwner(expected)
        val result = read(api.listings(id, expected)).map { it.toSupply(id) }
        owner(expected)
        if (result.map { it.id }.distinct().size != result.size) throw SupplierFailure("Duplicate supply records received.")
        return result.sortedByDescending { it.id }
    }
    suspend fun listing(expected: SessionSnapshot, id: Long): SupplyListing {
        val owner = authenticatedOwner(expected)
        if (id <= 0) throw SupplierFailure("Invalid supply selection.", true)
        val result = read(api.listing(id, expected)).toSupply(owner)
        this.owner(expected)
        if (result.id != id) throw SupplierFailure("Supply selection could not be verified.")
        return result
    }
    suspend fun create(expected: SessionSnapshot, draft: SupplyDraft): SupplyListing {
        val id = owner(expected)
        val request = draft.request(categories(expected))
        val created = read(api.create(request, expected))
        val result = afterWrite { created.toSupply(id).also { owner(expected) } }
        if (result.status != CanonicalProductStatus.PENDING_APPROVAL) throw SupplierFailure("Unexpected creation status. Inspect My supplies before continuing.")
        return result
    }
    suspend fun update(expected: SessionSnapshot, id: Long, draft: SupplyDraft): SupplyListing {
        val current = listing(expected, id)
        if (current.status != CanonicalProductStatus.PENDING_APPROVAL)
            throw SupplierFailure("Only pending listings can be edited here until the backend defines reapproval for published listings.", true)
        val request = draft.request(categories(expected), current.reserved)
        val updated = read(api.update(id, request, expected))
        val result = afterWrite { updated.toSupply(owner(expected)) }
        if (result.id != id) throw SupplierFailure("Updated supply could not be verified.")
        return result
    }
    suspend fun upload(expected: SessionSnapshot, id: Long, photo: SupplierPhoto): SupplyListing {
        validateSupplierPhoto(photo)
        val current = listing(expected, id)
        if (current.status != CanonicalProductStatus.PENDING_APPROVAL) throw SupplierFailure("Photo changes are limited to pending listings until reapproval is defined.", true)
        val images = read(api.images(id, expected))
        owner(expected)
        images.forEach { it.toImageView() }
        val next = try { Math.addExact(images.mapNotNull { it.sortOrder }.maxOrNull() ?: -1, 1) }
            catch (_: ArithmeticException) { throw SupplierFailure("Image ordering is unavailable.", true) }
        val image = read(api.upload(id, MultipartBody.Part.createFormData("image", photo.filename,
            photo.bytes.toRequestBody(photo.mime.toMediaType())), next, images.isEmpty(), expected))
        return afterWrite {
            owner(expected)
            image.toImageView()
            listing(expected, id) // Failure after accepted upload must remain uncertain, not become a safe retry.
        }
    }
    private suspend fun <T> afterWrite(block: suspend () -> T): T = try { block() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { throw SupplierFailure("The submission may have reached the server. Inspect existing supplies and photos before continuing.") }
    private fun <T> read(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw SupplierFailure(when (response.code()) {
                400, 422 -> "The server rejected these supply details. Check the form and category."
                401 -> "Your session could not be verified. Sign in again."
                403 -> "Access denied. Ask your administrator to check this Supplier account."
                404 -> "This supply or API route is unavailable. Refresh and contact your administrator."
                409 -> "The listing changed on the server. Refresh before continuing."
                413 -> "The image exceeds the server upload limit. Choose a smaller photo."
                415 -> "This image format is unsupported."
                429 -> "Too many requests. Wait before trying again."
                else -> "The Supplier service is unavailable. Check your connection."
            }, response.code() in setOf(400, 401, 403, 404, 409, 413, 415, 422, 429))
        }
        return response.body()?.requireData() ?: throw SupplierFailure("Incomplete Supplier response.")
    }
}
