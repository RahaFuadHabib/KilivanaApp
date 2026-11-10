package com.example.farmapp.checkout

import com.google.gson.annotations.JsonAdapter
import java.math.BigDecimal

/** Existing wire names and string phase values are a persistence contract, not UI state. */
internal data class CheckoutJournal(
    val phase: String = "NONE",
    @field:JsonAdapter(JournalProductMapAdapter::class, nullSafe=false)
    val items: Map<Long, Int> = emptyMap(),
    val expectedSubtotal: BigDecimal? = null,
    val cartId: Long? = null,
    val quote: CheckoutQuote? = null,
    val request: CheckoutQuoteRequest? = null,
    val orderId: Long? = null,
    val beforeOrderIds: Set<Long> = emptySet()
) {
    override fun toString() = "CheckoutJournal(redacted)"
}

internal interface CheckoutJournalStore {
    fun read(): CheckoutJournal
    fun write(value: CheckoutJournal)
}

internal data class StoredCheckoutJournal(val version: Long, val json: String?) {
    override fun toString() = "StoredCheckoutJournal(redacted)"
}

/** Implementations must read atomically and compare the version before a durable write. */
internal interface CheckoutJournalStorage {
    fun read(): StoredCheckoutJournal
    /** Throws on conflict or failed persistence; increments the stored version only on success. */
    fun write(expectedVersion: Long, json: String)
}

/** Codec/recovery/version lifecycle shared independently of Android's preferences implementation. */
internal class VersionedCheckoutJournalStore(private val storage: CheckoutJournalStorage) : CheckoutJournalStore {
    private var version = -1L

    @Synchronized override fun read(): CheckoutJournal {
        val stored = storage.read()
        version = stored.version
        return stored.json?.let {
            runCatching { CheckoutJournalJson.decode(it) }.getOrElse { CheckoutJournal("UNRESOLVED") }
        } ?: CheckoutJournal()
    }

    @Synchronized override fun write(value: CheckoutJournal) {
        check(version >= 0) { "Another checkout changed. Reopen checkout before continuing." }
        storage.write(version, CheckoutJournalJson.encode(value))
        version++
    }
}
