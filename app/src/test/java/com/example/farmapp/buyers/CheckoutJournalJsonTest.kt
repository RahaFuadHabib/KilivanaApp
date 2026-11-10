package com.example.farmapp.buyers

import com.example.farmapp.network.canonical.CanonicalJson
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CheckoutJournalJsonTest {
    private fun record(items: String) = """{"phase":"UPLOAD_PENDING","items":$items,"expectedSubtotal":3.21,"cartId":10,"beforeOrderIds":[]}"""

    @Test fun legacyObjectKeysReadAsExactPositiveLongsWithoutFloatingPointConversion() {
        val json = record("""{"7":3,"9223372036854775807":2147483647}""")
        val restored = CheckoutJournalJson.decode(json)
        assertEquals(mapOf(7L to 3, Long.MAX_VALUE to Int.MAX_VALUE), restored.items)
        assertEquals(restored, CheckoutJournalJson.decode(CheckoutJournalJson.encode(restored)))
    }

    @Test fun emptyFreshAndUnresolvedJournalsRemainCompatible() {
        for (journal in listOf(CheckoutJournal(), CheckoutJournal("UNRESOLVED"))) {
            assertEquals(journal, CheckoutJournalJson.decode(CheckoutJournalJson.encode(journal)))
        }
    }

    @Test fun malformedProductKeysAreRejectedInsteadOfCoerced() {
        for (key in listOf("", "0", "-7", "+7", "07", "7.0", "7e0", " 7", "7 ", "٧", "9223372036854775808", "null")) {
            val items = "{" + JsonPrimitive(key).toString() + ":3}"
            assertThrows("key=$key", Exception::class.java) { CheckoutJournalJson.decode(record(items)) }
        }
    }

    @Test fun invalidQuantityTokensAreRejectedWithoutCoercionOrTruncation() {
        for (quantity in listOf("\"3\"", "0", "-1", "1.5", "1.0", "1e0", "true", "null", "2147483648", "[]", "{}")) {
            assertThrows("quantity=$quantity", Exception::class.java) { CheckoutJournalJson.decode(record("""{"7":$quantity}""")) }
        }
    }

    @Test fun duplicateProductKeysIncludingEscapedAliasesAreRejected() {
        for (items in listOf("""{"7":3,"7":4}""", """{"7":3,"\u0037":4}""")) {
            assertThrows(JsonParseException::class.java) { CheckoutJournalJson.decode(record(items)) }
            // The field adapter also rejects duplicates when called directly by Gson.
            assertThrows(JsonParseException::class.java) { CanonicalJson.gson.fromJson(record(items), CheckoutJournal::class.java) }
        }
    }

    @Test fun corruptedStructureMissingEvidenceAndUnknownPhasesFailClosed() {
        val records = listOf(
            "", "null", "[]", "{}", """{"phase":"NONE"}""", """{"items":{}}""",
            record("null"), record("[]"), record("""{"7":3}""").dropLast(1),
            record("""{"7":3}""") + "{}",
            """{"phase":"UNKNOWN","items":{}}""",
            """{"phase":"ORDER_PENDING","items":{"7":3},"expectedSubtotal":3.21}""",
            """{"phase":"NONE","items":{},"orderId":105}""",
            """{"phase":"NONE","items":{},"cartId":"10"}""",
            """{"phase":"NONE","items":{},"cartId":-1}""",
            """{"phase":"NONE","items":{},"beforeOrderIds":null}""",
            """{"phase":"NONE","items":{},"unexpected":true}""",
            record("""{"7":3}""").replace("3.21", "-3.21"),
            """{"phase":"NONE","phase":"ORDER_PENDING","items":{}}""",
            """{"phase":"NONE","items":{},"request":{"deliveryMethod":"DOOR_DELIVERY","deliveryMethod":"PICKUP_STATION"}}"""
        )
        records.forEach { json -> assertThrows(Exception::class.java) { CheckoutJournalJson.decode(json) } }
    }

    @Test fun gsonBypassedDeliveryConstructorsAreStillValidated() {
        val request = """{"deliveryMethod":"PICKUP_STATION","addressId":8,"pickupStationId":3}"""
        assertThrows(IllegalArgumentException::class.java) {
            CheckoutJournalJson.decode("""{"phase":"NONE","items":{},"request":$request}""")
        }
    }

    @Test fun invalidInMemoryItemsCannotOverwritePersistence() {
        for (items in listOf(mapOf(0L to 1), mapOf(7L to 0), mapOf(7L to -1))) {
            assertThrows(IllegalArgumentException::class.java) {
                CheckoutJournalJson.encode(CheckoutJournal("UPLOAD_PENDING", items, BigDecimal.ONE))
            }
        }
    }

    private data class BackendNumbers(val id: Long, val quantity: Int)

    @Test fun backendNumericAdaptersStillRejectStringsFractionsAndOverflows() {
        for (json in listOf(
            """{"id":"7","quantity":3}""", """{"id":7,"quantity":"3"}""",
            """{"id":7.5,"quantity":3}""", """{"id":7,"quantity":3.5}""",
            """{"id":9223372036854775808,"quantity":3}""", """{"id":7,"quantity":2147483648}"""
        )) assertThrows(JsonParseException::class.java) { CanonicalJson.gson.fromJson(json, BackendNumbers::class.java) }
        assertEquals(BackendNumbers(7, 3), CanonicalJson.gson.fromJson("""{"id":7,"quantity":3}""", BackendNumbers::class.java))
    }

    @Test fun journalExceptionDoesNotApplyToOtherLongKeyedMaps() {
        val type = object : TypeToken<Map<Long, Int>>() {}.type
        assertThrows(JsonParseException::class.java) { CanonicalJson.gson.fromJson<Map<Long, Int>>("""{"7":3}""", type) }
    }
}
