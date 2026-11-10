package com.example.farmapp.farmerpurchases

import org.junit.Assert.*
import org.junit.Test

class FarmerBasketJsonTest {
    @Test fun nonemptyLocalBasketRoundTripsWithoutFabricatingJournalSubtotal() {
        val items = mapOf(7L to 3, 9007199254740993L to 1)
        val raw = FarmerBasketJson.encode(items)
        assertEquals(items, FarmerBasketJson.decode(raw))
        assertEquals(mapOf(7L to 3), FarmerBasketJson.decode("""{"7":3}"""))
        assertEquals(emptyMap<Long, Int>(), FarmerBasketJson.decode("{}"))
        assertFalse(raw.contains("subtotal", ignoreCase = true))
    }
    @Test fun duplicateMalformedAndTrailingDataCannotBecomeSelections() {
        listOf("null", "[]", """{"07":3}""", """{"7":0}""", """{"7":-1}""", """{"7":1.5}""",
            """{"7":"3"}""", """{"7":3,"7":4}""", """{"9223372036854775808":3}""", """{"7":3}{}""",
            """{"7":2147483648}""", """{"phase":"NONE","items":{"7":3}}""").forEach { raw ->
            try { FarmerBasketJson.decode(raw); fail("Expected corrupt local basket rejection") } catch (_: Exception) { }
        }
    }
    @Test fun invalidSelectionsCannotBePersisted() {
        listOf(mapOf(0L to 1), mapOf(7L to 0), mapOf(-1L to 3)).forEach { items ->
            try { FarmerBasketJson.encode(items); fail("Expected invalid basket rejection") } catch (_: Exception) { }
        }
    }
}
