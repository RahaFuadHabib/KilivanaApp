package com.example.farmapp.buyer

import org.junit.Assert.*
import org.junit.Test

class DebugCartStructureTest {
    @Test fun populatedFixtureReportsStructureButNoValuesOrSensitiveKeys() {
        val body = """{"success":true,"data":{"id":987654321,"buyerId":987654322,"status":"ACTIVE","items":[{"productId":987654323,"quantity":2,"unitPrice":123.45,"subtotal":246.90,"productName":"PRIVATE_PRODUCT","email":"PRIVATE_EMAIL","phone":"PRIVATE_PHONE","addressText":"PRIVATE_ADDRESS"}]},"accessToken":"PRIVATE_ACCESS","refreshToken":{"nested":"PRIVATE_REFRESH"},"PRIVATE_DYNAMIC_NAME":"hidden"}"""
        val report = sanitizeBuyerCartStructure(200, parseCartStructureJson(body), 1, 2)
        assertTrue(report.contains("item count: 1"))
        assertTrue(report.contains("product identifier fields=true"))
        assertTrue(report.contains("quantity fields=true"))
        assertTrue(report.contains("price fields=true"))
        assertTrue(report.contains("total fields=true"))
        assertTrue(report.contains("data"))
        for (secret in listOf("987654321", "987654322", "987654323", "123.45", "246.90", "PRIVATE_", "accessToken", "refreshToken"))
            assertFalse(report.contains(secret))
    }
    @Test fun metadataDoesNotClaimEmptyOrPopulatedServerCart() {
        val report = sanitizeBuyerCartStructure(200, parseCartStructureJson("""{"data":{"id":123456,"buyerId":123457,"status":"ACTIVE"}}"""), 3, 5)
        assertTrue(report.contains("Local cart product rows: 3"))
        assertTrue(report.contains("server item count: not observable"))
        assertTrue(report.contains("metadata is not proof"))
        assertFalse(report.contains("item count: 0"))
    }
    @Test fun emptyCandidateDoesNotManufactureProductsAndWrapperIsShown() {
        val report = sanitizeBuyerCartStructure(200, parseCartStructureJson("""{"result":{"cart":{"items":[]}}}"""), 2, 4)
        assertTrue(report.contains("root.result.cart.items"))
        assertTrue(report.contains("item count: 0"))
        assertTrue(report.contains("Local/server contents match: unverified"))
    }
    @Test fun errorsAndNonJsonNeverEchoResponseText() {
        val report = sanitizeBuyerCartStructure(403, parseCartStructureJson("""{"message":"PRIVATE_PHONE","error":{"details":"PRIVATE_ADDRESS"}}"""), 0, 0)
        assertTrue(report.contains("HTTP status: 403"))
        assertFalse(report.contains("PRIVATE_"))
        assertFalse(sanitizeBuyerCartStructure(500, null, 0, 0).contains("Exception"))
    }
    @Test fun malformedAndNestedProductValuesAreSafe() {
        val report = sanitizeBuyerCartStructure(200, parseCartStructureJson("""{"data":{"items":[{"product":"PRIVATE_NAME","qty":2},null,{"product":{"id":987654321}}]}}"""), 1, 1)
        assertTrue(report.contains("item count: 3"))
        assertFalse(report.contains("PRIVATE_NAME"))
        assertFalse(report.contains("987654321"))
    }
    @Test fun oversizedListsAreExplicitlyPartiallyInspected() {
        val root = com.google.gson.JsonObject()
        root.add("items", com.google.gson.JsonArray().apply { repeat(12) { add(com.google.gson.JsonObject().apply { addProperty("productId", 123456); addProperty("quantity", 1) }) } })
        val report = sanitizeBuyerCartStructure(200, root, 1, 1)
        assertTrue(report.contains("item count: 12"))
        assertTrue(report.contains("Only the first 10"))
        assertFalse(report.contains("123456"))
    }
    @Test fun reportCacheHasNoNetworkTriggerAndIsCleared() {
        DebugCartReportCache.clear()
        assertTrue(DebugCartReportCache.read().startsWith("No user-triggered"))
        DebugCartReportCache.publish("HTTP status: 200")
        assertEquals("HTTP status: 200", DebugCartReportCache.read())
        DebugCartReportCache.clear()
        assertTrue(DebugCartReportCache.read().startsWith("No user-triggered"))
    }
}
