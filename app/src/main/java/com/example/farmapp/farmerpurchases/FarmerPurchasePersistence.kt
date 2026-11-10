package com.example.farmapp.farmerpurchases

import android.content.Context
import com.example.farmapp.BuildConfig
import com.example.farmapp.buyer.buyerScope
import com.example.farmapp.checkout.*
import com.example.farmapp.payments.*

/** Separate role + backend + user namespaces; never migrates or rewrites Buyer records. */
internal class FarmerPurchasePersistence(context: Context, user: String, origin: String = BuildConfig.API_BASE_URL) : FarmerBasketStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        "farmer-purchases-" + buyerScope(origin, user), Context.MODE_PRIVATE)
    val journal: CheckoutJournalStore = VersionedCheckoutJournalStore(object : CheckoutJournalStorage {
        override fun read() = synchronized(lock) { StoredCheckoutJournal(prefs.getLong("version", 0), prefs.getString("journal", null)) }
        override fun write(expectedVersion: Long, json: String) = synchronized(lock) {
            check(prefs.getLong("version", 0) == expectedVersion) { "Another checkout changed. Reopen checkout." }
            check(prefs.edit().putString("journal", json).putLong("version", expectedVersion + 1).commit())
        }
    })
    val attempts: PaymentAttemptStore = DurablePaymentAttempts(object : PaymentAttemptPersistence {
        override fun started(orderId: Long) = prefs.getBoolean("payment-$orderId", false)
        override fun markStarted(orderId: Long) { check(prefs.edit().putBoolean("payment-$orderId", true).commit()) }
    }, lock)
    val cartWrites = object : CartWriteJournal {
        override fun pending() = prefs.getBoolean("cart-write-pending", false)
        override fun pending(value: Boolean) = synchronized(lock) {
            if (value) check(!prefs.getBoolean("cart-write-pending", false)) { "Another cart write is unresolved." }
            check(prefs.edit().putBoolean("cart-write-pending", value).commit())
        }
    }
    /** Keep historical local-only selections separate from acknowledged server-cart selections. */
    val serverBasket = object : FarmerBasketStore {
        override fun labels(): Map<Long, String> = synchronized(lock) {
            val raw = prefs.getString("server-cart-labels-v1", null) ?: return@synchronized emptyMap()
            com.google.gson.JsonParser.parseString(raw).asJsonObject.entrySet().associate { (key, value) ->
                val id = key.toLong().also { check(it > 0) }
                check(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                id to value.asString.also { check(it.isNotBlank() && it.length <= 1000) }
            }
        }
        override fun saveLabels(labels: Map<Long, String>) = synchronized(lock) {
            val json = com.google.gson.JsonObject()
            labels.forEach { (id, name) -> json.addProperty(id.toString(), name.take(1000)) }
            check(prefs.edit().putString("server-cart-labels-v1", json.toString()).commit())
        }

        override fun basket() = synchronized(lock) {
            prefs.getString("server-cart-selections-v1", null)?.let(FarmerBasketJson::decode) ?: emptyMap()
        }
        override fun saveBasket(items: Map<Long, Int>) = synchronized(lock) {
            check(prefs.edit().putString("server-cart-selections-v1", FarmerBasketJson.encode(items)).commit())
        }
    }

    /** IDs and quantities only. This local basket is not backend cart evidence or a quote. */
    override fun basket(): Map<Long, Int> = synchronized(lock) {
        val raw = prefs.getString("local-basket", null) ?: return@synchronized emptyMap()
        FarmerBasketJson.decode(raw) // Corruption blocks rather than silently erasing selections.
    }
    override fun saveBasket(items: Map<Long, Int>) = synchronized(lock) {
        check(prefs.edit().putString("local-basket", FarmerBasketJson.encode(items)).commit())
    }
    companion object { private val lock = Any() }
}
