package com.example.farmapp.buyers

import android.content.Context
import com.example.farmapp.BuildConfig
import com.example.farmapp.buyer.buyerScope
import com.example.farmapp.checkout.CheckoutJournalStorage
import com.example.farmapp.checkout.StoredCheckoutJournal
import com.example.farmapp.checkout.VersionedCheckoutJournalStore

/** Preserve the established Buyer storage namespace and constructor for existing callers. */
internal class CheckoutPreferences(context: Context, user: String) : CheckoutJournalStore by
    VersionedCheckoutJournalStore(BuyerCheckoutJournalStorage(context, user))

private class BuyerCheckoutJournalStorage(context: Context, user: String) : CheckoutJournalStorage {
    private val prefs = context.applicationContext.getSharedPreferences(
        "buyer-checkout-" + buyerScope(BuildConfig.API_BASE_URL, user), Context.MODE_PRIVATE)

    override fun read(): StoredCheckoutJournal = synchronized(lock) {
        StoredCheckoutJournal(prefs.getLong("version", 0), prefs.getString("journal", null))
    }

    override fun write(expectedVersion: Long, json: String): Unit = synchronized(lock) {
        check(prefs.getLong("version", 0) == expectedVersion) { "Another checkout changed. Reopen checkout before continuing." }
        check(prefs.edit().putString("journal", json).putLong("version", expectedVersion + 1).commit())
    }

    companion object { private val lock = Any() }
}
