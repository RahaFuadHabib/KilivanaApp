package com.example.farmapp.farmerpurchases

import com.example.farmapp.checkout.JournalProductMapAdapter
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

/** Local intent has no authoritative subtotal. Reuse only the strict product-map codec,
 * not the checkout journal schema, which correctly requires a subtotal for nonempty journals.
 */
internal object FarmerBasketJson {
    fun encode(items: Map<Long, Int>): String = JournalProductMapAdapter().toJson(items)
    fun decode(json: String): Map<Long, Int> = JsonReader(StringReader(json)).use { reader ->
        reader.strictness = Strictness.STRICT
        val items = JournalProductMapAdapter().read(reader)
        require(reader.peek() == JsonToken.END_DOCUMENT) { "Corrupt saved basket." }
        items
    }
}
