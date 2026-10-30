package com.example.farmapp.buyer

import android.content.Context
import com.example.farmapp.BuildConfig
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.marketplace.MarketplaceRepository
import com.example.farmapp.network.TokenManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

internal class BuyerPreferences(context: Context, origin: String, userId: String) : BuyerSelectionStore, BuyerDeliveryStore, BuyerPickupStore {
    private val preferences = context.applicationContext.getSharedPreferences("buyer-shopping-${buyerScope(origin, userId)}", Context.MODE_PRIVATE)
    override fun selectedPickup(): Long? = preferences.getLong("pickup-station-id", 0).takeIf { it > 0 }
    override fun selectPickup(id: Long?) { check(preferences.edit().putLong("pickup-station-id", id ?: 0).commit()) }
    override fun pendingPickup(): BuyerPickupMutation? = preferences.getString("pickup-write-pending", null)?.let {
        com.example.farmapp.network.canonical.CanonicalJson.gson.fromJson(it, BuyerPickupMutation::class.java)
    }
    override fun pendingPickup(value: BuyerPickupMutation?) {
        check(preferences.edit().putString("pickup-write-pending", value?.let { com.example.farmapp.network.canonical.CanonicalJson.gson.toJson(it) }).commit())
    }
    override fun selectedAddress(): Long? = preferences.getLong("delivery-address-id", 0).takeIf { it > 0 }
    override fun selectAddress(id: Long?) { check(preferences.edit().putLong("delivery-address-id", id ?: 0).commit()) }
    override fun addressSavePending() = preferences.getBoolean("address-save-pending", false)
    override fun markAddressSave(pending: Boolean) { check(preferences.edit().putBoolean("address-save-pending", pending).commit()) }
    override fun read(): BuyerSelection = BuyerSelection(
        preferences.getStringSet("saved", emptySet()).orEmpty().mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0 } }.take(100).toSet(),
        preferences.getStringSet("cart", emptySet()).orEmpty().mapNotNull { entry ->
            val fields = entry.split(':')
            val id = fields.getOrNull(0)?.toLongOrNull()
            val qty = fields.getOrNull(1)?.toIntOrNull()
            if (fields.size == 2 && id != null && id > 0 && qty != null && qty > 0) id to qty else null
        }.take(100).toMap()
    )
    override fun write(value: BuyerSelection) {
        check(preferences.edit().putStringSet("saved", value.saved.map { it.toString() }.toSet())
            .putStringSet("cart", value.cart.map { (id, qty) -> "$id:$qty" }.toSet()).commit())
    }
}

internal class BuyerShoppingModel(context: Context) : ViewModel() {
    private val expected = TokenManager.session.value
    private val selections = BuyerPreferences(context, BuildConfig.API_BASE_URL, expected.auth!!.userId)
    val shopping = BuyerShoppingController(ExistingBuyerCatalog(MarketplaceRepository(ApiClient.marketplaceApi, TokenManager)),
        TokenManager, expected, selections, viewModelScope)
    val checkout = BuyerCheckoutReviewController(ApiClient.buyerCheckoutApi, TokenManager, expected, viewModelScope)
    val pickups = BuyerPickupController(ApiClient.buyerPickupApi, TokenManager, expected, selections, viewModelScope)
    val addresses = BuyerAddressController(ApiClient.buyerAddressApi, TokenManager, expected, viewModelScope, selections)
    init { shopping.start() }
    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return BuyerShoppingModel(context.applicationContext) as T
            }
        }
    }
}
