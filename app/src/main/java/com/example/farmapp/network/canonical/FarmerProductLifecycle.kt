package com.example.farmapp.network.canonical

import com.example.farmapp.data.model.*
import com.example.farmapp.data.model.canonical.CanonicalProductDto
import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.SessionStore
import com.example.farmapp.repository.ProductFailure
import retrofit2.Response
import java.io.IOException

/** Reads protected identity and current product before a single, session-tagged mutation.
 * These checks guide the client; backend ownership/locking/lifecycle validation remains authoritative.
 */
internal class FarmerProductLifecycle(private val api: CanonicalBackendApi, private val sessions: SessionStore) {
    private fun snapshot(): SessionSnapshot = sessions.session.value.also {
        if (it.auth?.role != "FARMER" || it.auth.backendContract != "canonical" || it.isDemo || it.loggingOut)
            throw ProductFailure("Sign in with your Farmer account to manage crops.", true, 401)
    }

    private fun current(expected: SessionSnapshot) {
        val now = snapshot()
        if (now.generation != expected.generation || now.auth?.userId != expected.auth?.userId ||
            now.auth?.sessionOrigin != expected.auth?.sessionOrigin)
            throw ProductFailure("The session changed. Refresh My Crops before continuing.", true, 401)
    }

    private fun owned(dto: CanonicalProductDto, id: Long, expected: SessionSnapshot): FarmerProductResponse {
        current(expected)
        if (dto.id != id || dto.sellerId?.toString() != expected.auth?.userId ||
            dto.sellerType !in setOf("farmer", "FARMER"))
            throw ProductFailure("This crop does not belong to your Farmer account.", true, 403)
        val product = dto.toAndroidProduct()
        if (product.status == "DELETED") throw ProductFailure("This crop is no longer available.", true, 404)
        return product
    }

    private fun <T> error(response: Response<*>): Response<T> =
        Response.error(response.code(), response.errorBody() ?: throw IOException("Missing error response"))

    private suspend fun load(id: Long, expected: SessionSnapshot): Response<FarmerProductResponse> {
        val identity = api.managementIdentity(expected)
        current(expected)
        if (!identity.isSuccessful) return error(identity)
        val account = identity.body()?.requireData()?.toAccount() ?: throw IOException("Missing identity")
        if (account.userId != expected.auth?.userId || account.role != "FARMER")
            throw ProductFailure("Farmer identity could not be verified.", true, 403)
        val response = api.managementProduct(id, expected)
        current(expected)
        if (!response.isSuccessful) return error(response)
        return Response.success(owned(response.body()?.requireData() ?: throw IOException("Missing product"), id, expected))
    }

    suspend fun detail(id: Long): Response<FarmerProductResponse> = load(id, snapshot())

    suspend fun change(id: Long, action: FarmerProductAction): Response<Unit> {
        val expected = snapshot()
        val response = load(id, expected)
        if (!response.isSuccessful) return error(response)
        val product = response.body() ?: throw IOException("Missing product")
        if (action !in product.lifecycleActions(expected.auth?.userId, "FARMER"))
            throw ProductFailure("This action is no longer available for the crop's current state or reserved stock. Refresh My Crops.", true, 400)
        current(expected)
        if (action == FarmerProductAction.DELETE) {
            val deleted = api.deleteArchivedProduct(id, expected)
            current(expected)
            if (!deleted.isSuccessful) return error(deleted)
            (deleted.body() ?: throw IOException("Missing deletion confirmation")).requireSuccess()
        } else {
            val changed = api.sellerProductStatus(id, requireNotNull(action.targetStatus), expected)
            current(expected)
            if (!changed.isSuccessful) return error(changed)
            val confirmed = owned(changed.body()?.requireData() ?: throw IOException("Missing status confirmation"), id, expected)
            if (confirmed.status != action.targetStatus)
                throw IOException("The lifecycle response did not confirm the requested state. Refresh My Crops before retrying.")
        }
        return Response.success(Unit)
    }
}
