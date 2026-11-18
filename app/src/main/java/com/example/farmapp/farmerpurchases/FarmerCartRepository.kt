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
    @PUT("carts/{cartId}/items/{productId}") suspend fun quantity(@Path("cartId") cartId: Long, @Path("productId") productId: Long, @Query("quantity") quantity: Int, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<JsonObject>>
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
        check(it.status != "CHECKOUT_PROCESSING") {
            "This cart is being checked out. Wait for the current checkout to finish before adding more items."
        }
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
    // GET /cart intentionally returns cart metadata only. It cannot resolve an
    // ambiguous write, so a read must never discard the write-ahead marker.
    suspend fun read() = metadata(accepted(requests.call(CheckoutOperation.READ_CART, api::cart)))
    suspend fun quantity(productId: Long, quantity: Int): Long {
        require(productId > 0 && quantity > 0)
        val before = read()
        val cartId = checkNotNull(before.id)
        val result = metadata(write { requests.call(CheckoutOperation.SET_QUANTITY, { api.quantity(cartId, productId, quantity, it) }) })
        check(result.id == cartId && result.status == "ACTIVE") { "The server cart changed during the quantity update." }
        val after = read()
        check(after.id == cartId && after.status == "ACTIVE") { "The server cart could not be verified after setting quantity." }
        journal.pending(false)
        return cartId
    }
    fun hasPendingWrite() = journal.pending()
    private suspend fun <T> write(action: suspend () -> Response<CanonicalApiResponse<T>>): T {
        check(!journal.pending()) { "A previous cart write is unresolved. The backend must verify its item quantities before another write." }
        journal.pending(true)
        val response = action()
        if (!response.isSuccessful && response.code() in setOf(400, 401, 403, 404, 409, 422, 429)) journal.pending(false)
        return accepted(response)
    }
    suspend fun add(productId: Long, quantity: Int): Long {
        require(productId > 0 && quantity > 0)
        check(!journal.pending()) { "A previous cart write is unresolved. Refresh and ask support to verify the server cart." }
        val before = read()
        val cartId = checkNotNull(before.id)
        journal.pending(true)
        val response = requests.call(CheckoutOperation.UPLOAD_CART, { api.add(CartUpload(productId, quantity), it) })
        val duplicate = response.code() == 409 && runCatching {
            response.errorBody()?.use { body ->
                val json = com.google.gson.JsonParser.parseString(body.string()).asJsonObject
                json.get("message")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString == "Product already in cart"
            } == true
        }.getOrDefault(false)
        if (!response.isSuccessful && !duplicate) {
            if (response.code() in setOf(400, 401, 403, 404, 409, 422, 429)) journal.pending(false)
            accepted(response)
        }
        val added = if (duplicate) before else metadata(accepted(response))
        check(added.id == cartId && added.status == "ACTIVE") { "The server cart changed during the add. Verify its contents before continuing." }
        val exactResponse = requests.call(CheckoutOperation.SET_QUANTITY, { api.quantity(cartId, productId, quantity, it) })
        // POST already changed the cart; even a definite PUT rejection leaves a
        // partially applied add that cannot be reconciled from metadata alone.
        val exact = metadata(accepted(exactResponse))
        check(exact.id == cartId && exact.status == "ACTIVE") { "The server cart changed after setting quantity." }
        val after = read()
        check(after.id == cartId && after.status == "ACTIVE") { "The server cart could not be verified after adding the item." }
        journal.pending(false)
        return after.id!!
    }
    suspend fun remove(productId: Long): Long {
        require(productId > 0)
        check(!journal.pending()) { "A previous cart write is unresolved. Verify the server cart before removing items." }
        val before = read()
        // Successful deletion may legitimately return no data.
        journal.pending(true)
        val cartId = checkNotNull(before.id)
        val response = requests.call(CheckoutOperation.SET_QUANTITY, { api.remove(cartId, productId, it) })
        if (!response.isSuccessful && response.code() in setOf(400, 401, 403, 404, 409, 422, 429)) journal.pending(false)
        if (!response.isSuccessful) accepted(response)
        check(response.body()?.success == true) { "Cart removal could not be verified." }
        val after = read()
        check(after.id == cartId && after.status == "ACTIVE") { "The server cart could not be verified after removal." }
        journal.pending(false)
        return cartId
    }
}
