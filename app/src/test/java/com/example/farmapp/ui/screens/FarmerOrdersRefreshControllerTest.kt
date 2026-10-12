package com.example.farmapp.ui.screens

import com.example.farmapp.data.model.*
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class FarmerOrdersRefreshControllerTest {
    private fun order(id: String) = FarmerOrderResponse(id, "seller", "PLACED",
        BigDecimal.TEN, "KES", "2026-10-01", emptyList())

    @Test fun refreshKeepsOldOrdersUntilSuccessAndGuardsDuplicateRequests() = runBlocking {
        val state = FarmerOrdersRefreshController()
        state.refresh { listOf(order("old")) }
        val response = CompletableDeferred<List<FarmerOrderResponse>>()
        var calls = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            state.refresh { calls++; response.await() }
        }
        assertTrue(state.refreshing)
        assertEquals("old", state.orders!!.single().orderId)
        repeat(3) { state.refresh { calls++; listOf(order("duplicate")) } }
        assertEquals(1, calls)
        response.complete(listOf(order("new")))
        job.join()
        assertEquals("new", state.orders!!.single().orderId)
        assertFalse(state.refreshing)
        assertNull(state.error)
    }

    @Test fun failureRetainsOrdersAndRetryUpdatesDataAndClearsError() = runBlocking {
        val state = FarmerOrdersRefreshController()
        state.refresh { listOf(order("saved")) }
        state.refresh { throw IOException() }
        assertEquals("saved", state.orders!!.single().orderId)
        assertNotNull(state.error)
        assertFalse(state.refreshing)
        state.refresh { listOf(order("latest")) }
        assertEquals("latest", state.orders!!.single().orderId)
        assertNull(state.error)
        assertFalse(state.refreshing)
    }

    @Test fun emptyOrdersCanRefreshAndSuccessfulEmptyResponseClearsOldOrders() = runBlocking {
        val state = FarmerOrdersRefreshController()
        state.refresh { emptyList() }
        state.refresh { listOf(order("new")) }
        assertEquals(1, state.orders!!.size)
        state.refresh { emptyList() }
        assertTrue(state.orders!!.isEmpty())
        assertFalse(state.refreshing)
    }

    @Test fun initialFailureCanRetryWithoutInventingAnEmptySuccessfulSnapshot() = runBlocking {
        val state = FarmerOrdersRefreshController()
        state.refresh { throw IOException() }
        assertNull(state.orders)
        assertNotNull(state.error)
        assertFalse(state.refreshing)
        state.refresh { emptyList() }
        assertEquals(emptyList<FarmerOrderResponse>(), state.orders)
        assertNull(state.error)
    }

    @Test fun cancellationRetainsOrdersAndReleasesRequestGuard() = runBlocking {
        val state = FarmerOrdersRefreshController()
        state.refresh { listOf(order("saved")) }
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            state.refresh { awaitCancellation() }
        }
        job.cancelAndJoin()
        assertFalse(state.refreshing)
        assertEquals("saved", state.orders!!.single().orderId)
        assertNull(state.error)
        state.refresh { listOf(order("next")) }
        assertEquals("next", state.orders!!.single().orderId)
    }
}