package com.example.farmapp.farmerpurchases

import com.example.farmapp.buyer.buyerCartEvidence
import com.example.farmapp.buyers.CartUpload
import com.example.farmapp.checkout.CheckoutOperation
import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.network.SessionStore
import com.example.farmapp.network.canonical.CanonicalApiResponse
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.*

internal interface PurchaserCartApi {
    @GET("cart") suspend fun cart(@Tag session: SessionSnapshot): Response<CanonicalApiResponse<JsonObject>>
    @POST("cart/items") suspend fun add(@Body item: CartUpload, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<JsonObject>>
    @DELETE("carts/{cartId}/items/{productId}") suspend fun remove(@Path("cartId") cartId: Long,
        @Path("productId") productId: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<Unit>>
}

internal interface CartWriteJournal {
    fun pending(): Boolean
    fun pending(value: Boolean)
}

/** Metadata readback cannot prove line quantities. Never repeat an ambiguous POST or DELETE. */
internal class FarmerCartRepository(private val api: PurchaserCartApi, sessions: SessionStore,
    expected: SessionSnapshot, private val journal: CartWriteJournal) {
    private val requests = FarmerPurchaseRequests(sessions, expected)
    private fun metadata(value: JsonObject) = try {
        buyerCartEvidence(value, requests.purchaserId()).metadata
    } catch (_: IllegalStateException) {
        error("Server cart identity or ownership could not be verified. No additional cart write was sent.")
    }.also {
        check(it.status == "ACTIVE") { "This cart is already in checkout. Resolve the existing checkout first." }
    }
    private fun <T> accepted(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            error("Cart request failed (HTTP ${response.code()}). " + when (response.code()) {
                401, 403 -> "Sign in again or ask the backend team to verify Farmer cart permission."
                409 -> "The server reported a conflict. Refresh the cart before another explicit action."
                400, 422 -> "Check product eligibility, stock and quantity."
                404 -> "The product or cart endpoint is unavailable."
                else -> "The outcome is uncertain. No cart write was repeated."
            })
        }
        return response.body()?.requireData() ?: error("Cart response is incomplete.")
    }
    suspend fun read() = metadata(accepted(requests.call(CheckoutOperation.READ_CART, api::cart)))
    fun hasPendingWrite() = journal.pending()
    private suspend fun <T> write(action: suspend () -> Response<CanonicalApiResponse<T>>): T {
        check(!journal.pending()) { "A previous cart write is unresolved. The backend must verify its item quantities before another write." }
        journal.pending(true)
        val response = action()
        if (response.code() in setOf(400, 401, 403, 404, 409, 422, 429)) journal.pending(false)
        return accepted(response)
    }
    suspend fun add(productId: Long, quantity: Int): Long {
        require(productId > 0 && quantity > 0)
        check(!journal.pending()) { "A previous cart write is unresolved. Refresh and ask support to verify the server cart." }
        val before = read()
        val result = metadata(write { requests.call(CheckoutOperation.UPLOAD_CART, { api.add(CartUpload(productId, quantity), it) }) })
        check(result.id == before.id) { "The server cart changed during the request. Verify its contents before continuing." }
        val after = read()
        check(after.id == result.id)
        journal.pending(false)
        return after.id!!
    }
    suspend fun remove(productId: Long): Long {
        require(productId > 0)
        check(!journal.pending()) { "A previous cart write is unresolved. Verify the server cart before removing items." }
        val before = read()
        // Successful deletion may legitimately return no data.
        journal.pending(true)
        val response = requests.call(CheckoutOperation.SET_QUANTITY, { api.remove(before.id!!, productId, it) })
        if (response.code() in setOf(400, 401, 403, 404, 409, 422, 429)) journal.pending(false)
        if (!response.isSuccessful) accepted(response)
        check(response.body()?.success == true) { "Cart removal could not be verified." }
        val after = read()
        check(after.id == before.id)
        journal.pending(false)
        return after.id!!
    }
}
