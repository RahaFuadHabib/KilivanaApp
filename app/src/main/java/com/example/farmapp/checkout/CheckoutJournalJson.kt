package com.example.farmapp.checkout

import com.example.farmapp.network.canonical.CanonicalJson
import com.google.gson.JsonParseException
import com.google.gson.Strictness
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.StringReader
import java.time.Instant

/** Only CheckoutJournal.items uses stringified Long keys. API number adapters stay unchanged. */
internal class JournalProductMapAdapter : TypeAdapter<Map<Long, Int>>() {
    override fun read(input: JsonReader): Map<Long, Int> {
        if (input.peek() != JsonToken.BEGIN_OBJECT) throw JsonParseException("Invalid journal product map")
        val items = linkedMapOf<Long, Int>()
        input.beginObject()
        while (input.hasNext()) {
            val key = input.nextName()
            val id = key.takeIf { PRODUCT_ID.matches(it) }?.toLongOrNull()
                ?: throw JsonParseException("Invalid journal product ID")
            if (id <= 0 || items.containsKey(id)) throw JsonParseException("Invalid or duplicate journal product ID")
            if (input.peek() != JsonToken.NUMBER) throw JsonParseException("Invalid journal quantity")
            val quantity = input.nextString().takeIf { PRODUCT_ID.matches(it) }?.toIntOrNull()
                ?: throw JsonParseException("Invalid journal quantity")
            items[id] = quantity
        }
        input.endObject()
        return items
    }

    override fun write(output: JsonWriter, value: Map<Long, Int>?) {
        require(value != null && value.all { (id, quantity) -> id > 0 && quantity > 0 }) { "Invalid journal product map" }
        output.beginObject()
        value.forEach { (id, quantity) -> output.name(id.toString()).value(quantity) }
        output.endObject()
    }

    private companion object { val PRODUCT_ID = Regex("[1-9][0-9]*") }
}

/** Local persistence codec, never installed in Retrofit or the canonical Gson singleton. */
internal object CheckoutJournalJson {
    private val fields = setOf("phase", "items", "expectedSubtotal", "cartId", "quote", "request", "orderId", "beforeOrderIds")
    private val phases = setOf("NONE", "UNRESOLVED", "UPLOAD_PENDING", "QUANTITY_PENDING", "CART_REJECTED",
        "UPLOAD_ACKNOWLEDGED", "CHECKOUT_PENDING", "CHECKOUT_ACKNOWLEDGED", "QUOTE_READY", "ORDER_PENDING", "ORDER_RETURNED", "ORDER_VERIFIED")
    private val processing = setOf("CHECKOUT_PENDING", "CHECKOUT_ACKNOWLEDGED", "QUOTE_READY", "ORDER_PENDING", "ORDER_RETURNED", "ORDER_VERIFIED")
    private val ordering = setOf("ORDER_PENDING", "ORDER_RETURNED", "ORDER_VERIFIED")

    fun decode(json: String): CheckoutJournal {
        // Gson's object reader would otherwise overwrite duplicate fields. Inspect the original
        // token stream first, including nested quote/request objects, before converting to DTOs.
        JsonReader(StringReader(json)).use { input ->
            input.strictness = Strictness.STRICT
            val names = scanObject(input, 0)
            if (!names.containsAll(setOf("phase", "items")) || !fields.containsAll(names) || input.peek() != JsonToken.END_DOCUMENT)
                throw JsonParseException("Invalid checkout journal fields")
        }
        return validate(CanonicalJson.gson.fromJson(json, CheckoutJournal::class.java)
            ?: throw JsonParseException("Missing checkout journal"))
    }

    fun encode(journal: CheckoutJournal): String = CanonicalJson.gson.toJson(validate(journal))

    private fun scanObject(input: JsonReader, depth: Int): Set<String> {
        if (input.peek() != JsonToken.BEGIN_OBJECT) throw JsonParseException("Invalid checkout journal object")
        val names = mutableSetOf<String>()
        input.beginObject()
        while (input.hasNext()) {
            if (!names.add(input.nextName())) throw JsonParseException("Duplicate checkout journal field")
            scanValue(input, depth + 1)
        }
        input.endObject()
        return names
    }

    private fun scanValue(input: JsonReader, depth: Int) {
        if (depth > 16) throw JsonParseException("Invalid checkout journal nesting")
        when (input.peek()) {
            JsonToken.BEGIN_OBJECT -> scanObject(input, depth)
            JsonToken.BEGIN_ARRAY -> {
                input.beginArray()
                while (input.hasNext()) scanValue(input, depth + 1)
                input.endArray()
            }
            JsonToken.STRING, JsonToken.NUMBER -> input.nextString()
            JsonToken.BOOLEAN -> input.nextBoolean()
            JsonToken.NULL -> input.nextNull()
            else -> throw JsonParseException("Invalid checkout journal value")
        }
    }

    private fun validate(journal: CheckoutJournal): CheckoutJournal {
        require(journal.phase in phases) { "Invalid checkout journal phase" }
        val items = checkNotNull(journal.items) { "Missing journal product map" }
        require(items.all { (id, quantity) -> id > 0 && quantity > 0 }) { "Invalid journal product map" }
        require(journal.cartId == null || journal.cartId > 0) { "Invalid journal cart ID" }
        require(journal.orderId == null || journal.orderId > 0) { "Invalid journal order ID" }
        val beforeIds = checkNotNull(journal.beforeOrderIds) { "Missing previous order IDs" }
        require(beforeIds.all { it > 0 }) { "Invalid previous order ID" }
        require(journal.expectedSubtotal == null || journal.expectedSubtotal.signum() >= 0) { "Invalid journal subtotal" }
        require(items.isEmpty() || journal.expectedSubtotal?.signum() == 1) { "Missing journal subtotal" }
        require(journal.phase in setOf("NONE", "UNRESOLVED") || items.isNotEmpty()) { "Missing journal items" }
        require(journal.orderId == null || journal.phase in setOf("ORDER_RETURNED", "ORDER_VERIFIED")) { "Invalid journal order reservation" }
        if (journal.phase in processing) require(journal.cartId != null && journal.request != null) { "Missing checkout evidence" }
        if (journal.phase in ordering) require(journal.quote != null) { "Missing order quote" }
        if (journal.phase in setOf("ORDER_RETURNED", "ORDER_VERIFIED")) require(journal.orderId != null) { "Missing returned order ID" }
        journal.request?.let { request ->
            // Gson bypasses Kotlin init blocks; reconstruct to apply the existing location rules.
            CheckoutQuoteRequest(request.deliveryMethod, request.addressId, request.pickupLocation, request.pickupStationId, request.cartId)
            require(request.cartId == null || request.cartId == journal.cartId)
        }
        journal.quote?.let { quote ->
            val request = checkNotNull(journal.request) { "Missing quote request" }
            // Expired quotes must still load for recovery; they cannot create an order until refreshed.
            verifiedCheckoutQuote(quote, request, Instant.MIN)
            require(quote.subtotal.compareTo(journal.expectedSubtotal) == 0) { "Journal quote subtotal mismatch" }
        }
        return journal
    }
}
