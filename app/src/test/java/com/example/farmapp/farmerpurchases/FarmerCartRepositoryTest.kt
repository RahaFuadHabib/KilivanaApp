package com.example.farmapp.farmerpurchases

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import com.example.farmapp.network.canonical.toFarmerProduct

class FarmerCartRepositoryTest {
    private class Journal : CartWriteJournal {
        var value = false
        override fun pending() = value
        override fun pending(value: Boolean) { this.value = value }
    }
    private val cart = """{"success":true,"data":{"id":11,"buyerId":42,"status":"ACTIVE"}}"""
    private suspend fun scenario(action: suspend (FarmerPurchaseFixture, Journal, FarmerCartRepository) -> Unit) {
        val f = FarmerPurchaseFixture()
        val journal = Journal()
        f.handler = { cart }
        try { action(f, journal, FarmerCartRepository(f.api(), f.sessions, f.expected, journal)) }
        finally { f.scope.cancel() }
    }
    @Test fun addUsesAuthenticatedPrincipalCartAndReadsBackServerIdentity() = runBlocking {
        scenario { f, journal, repository ->
            assertEquals(11L, repository.add(7, 3))
            assertEquals(listOf("GET", "POST", "PUT", "GET"), f.requests.map { it.method })
            assertEquals(listOf("/api/v1/cart", "/api/v1/cart/items", "/api/v1/carts/11/items/7", "/api/v1/cart"), f.requests.map { it.url.encodedPath })
            assertTrue(f.requests.all { it.header("Authorization")?.startsWith("Bearer ") == true })
            val buffer = okio.Buffer(); f.requests[1].body!!.writeTo(buffer)
            assertEquals("{\"productId\":7,\"quantity\":3}", buffer.readUtf8())
            assertFalse(journal.value)
        }
    }
    @Test fun uncertainPostSurvivesRepositoryRestartAndCannotBeRepeated() = runBlocking {
        scenario { f, journal, repository ->
            f.handler = { if (it.method == "POST") throw IOException("synthetic timeout") else cart }
            assertTrue(runCatching { repository.add(7, 3) }.isFailure)
            assertTrue(journal.value)
            val restarted = FarmerCartRepository(f.api(), f.sessions, f.expected, journal)
            assertTrue(runCatching { restarted.add(7, 3) }.isFailure)
            assertEquals(1, f.requests.count { it.method == "POST" })
        }
    }
    @Test fun documentedDuplicateConflictSetsExactQuantityWithoutRepeatingPost() = runBlocking {
        scenario { f, journal, repository ->
            f.handler = { request -> when (request.method) {
                "POST" -> { f.status = 409; """{"success":false,"message":"Product already in cart"}""" }
                "PUT" -> { f.status = 200; cart }
                else -> { f.status = 200; cart }
            } }
            assertEquals(11L, repository.add(7, 3))
            assertFalse(journal.value)
            assertEquals(listOf("GET", "POST", "PUT", "GET"), f.requests.map { it.method })
        }
    }
    @Test fun foreignCartBlocksMutationAndStaleSessionCannotPublishAcknowledgement() = runBlocking {
        scenario { f, _, repository ->
            f.handler = { cart.replace("\"buyerId\":42", "\"buyerId\":99") }
            assertTrue(runCatching { repository.add(7, 3) }.isFailure)
            assertTrue(f.requests.none { it.method == "POST" })
        }
        scenario { f, journal, repository ->
            f.handler = { if (it.method == "POST") f.stale(); cart }
            assertTrue(runCatching { repository.add(7, 3) }.isFailure)
            assertTrue(journal.value)
        }
    }
    @Test fun removalUsesDocumentedCartProductRouteAndChecksReadback() = runBlocking {
        scenario { f, journal, repository ->
            f.handler = { if (it.method == "DELETE") """{"success":true}""" else cart }
            assertEquals(11L, repository.remove(7))
            assertEquals("/api/v1/carts/11/items/7", f.requests[1].url.encodedPath)
            assertEquals(listOf("GET", "DELETE", "GET"), f.requests.map { it.method })
            assertFalse(journal.value)
        }
    }
    @Test fun processingCartAndInvalidQuantityNeverDispatchWrites() = runBlocking {
        scenario { f, _, repository ->
            f.handler = { cart.replace("ACTIVE", "CHECKOUT_PROCESSING") }
            assertTrue(runCatching { repository.add(7, 3) }.isFailure)
            assertTrue(runCatching { repository.add(7, 0) }.isFailure)
            assertTrue(f.requests.none { it.method != "GET" })
        }
    }
    @Test fun productionControllerPublishesOnlyAcknowledgedWritesAndGuardsRapidTaps() = runBlocking {
        scenario { f, _, repository ->
            val original = f.handler
            val entered = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            f.handler = { request ->
                if (request.method == "POST") {
                    entered.countDown()
                    check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                }
                original(request)
            }
            val product = com.example.farmapp.network.canonical.CanonicalJson.gson.fromJson(
                com.google.gson.JsonParser.parseString(FarmerPurchaseFixture.productJson).asJsonObject["data"],
                com.example.farmapp.data.model.canonical.CanonicalProductDto::class.java)
            val controller = f.controller(repository)
            val view = product.toFarmerProduct()
            controller.add(view, 3)
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            try {
                controller.add(view, 3)
                assertTrue(controller.state.value.items.isEmpty())
            } finally { release.countDown() }
            f.idle(controller)
            assertEquals(mapOf(7L to 3), controller.state.value.items)
            assertEquals(11L, controller.state.value.serverCartId)
            assertEquals("Synthetic seed", controller.state.value.labels[7L])
            val restarted = f.controller(repository)
            assertEquals(mapOf(7L to 3), restarted.state.value.items)
            assertEquals("Synthetic seed", restarted.state.value.labels[7L])
            f.handler = { request -> if (request.url.encodedPath.endsWith("/products/7")) throw IOException("Synthetic unavailable catalogue") else cart }
            restarted.reviewBasket(); f.idle(restarted)
            assertEquals(mapOf(7L to 3), restarted.state.value.items)
            assertEquals("Synthetic seed", restarted.state.value.labels[7L])
            assertFalse(restarted.state.value.basketVerified)
            assertEquals(1, f.requests.count { it.method == "POST" })
        }
    }
}
