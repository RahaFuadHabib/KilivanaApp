package com.example.farmapp.buyers

/** Compatibility entry point for existing Buyer callers and saved-record regression tests. */
internal object CheckoutJournalJson {
    fun decode(json: String): CheckoutJournal = com.example.farmapp.checkout.CheckoutJournalJson.decode(json)
    fun encode(journal: CheckoutJournal): String = com.example.farmapp.checkout.CheckoutJournalJson.encode(journal)
}
