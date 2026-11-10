package com.example.farmapp.farmerpurchases

import android.content.Context
import com.example.farmapp.BuildConfig
import com.example.farmapp.buyer.buyerScope
import com.example.farmapp.network.canonical.CanonicalJson

/** New Farmer-only namespace. No Buyer or earlier unresolved checkout journal is overwritten. */
internal class FarmerTransactionPreferences(context: Context, user: String) : FarmerTransactionStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        "farmer-transactions-v1-" + buyerScope(BuildConfig.API_BASE_URL, user), Context.MODE_PRIVATE)
    private var version = -1L
    override fun read(): FarmerTransactionRecord? = synchronized(lock) {
        version = prefs.getLong("version", 0)
        prefs.getString("record", null)?.let { json ->
            CanonicalJson.gson.fromJson(json, FarmerTransactionRecord::class.java).also { record ->
                check(record.key.isNotBlank() && record.items.isNotEmpty() && record.items.all { it.key > 0 && it.value > 0 })
                check(record.phase in setOf("PREPARING", "QUOTE_READY", "ORDER_PENDING", "ORDER_CREATED", "PAYMENT_PENDING", "PAYMENT_FAILED", "CONFIRMED"))
                check(record.orderId == null || record.orderId > 0)
            }
        }
    }
    override fun write(record: FarmerTransactionRecord) = synchronized(lock) {
        check(version >= 0 && version == prefs.getLong("version", 0)) { "Another checkout changed" }
        check(prefs.edit().putString("record", CanonicalJson.gson.toJson(record)).putLong("version", version + 1).commit())
        version++
        Unit
    }
    private companion object { val lock = Any() }
}
