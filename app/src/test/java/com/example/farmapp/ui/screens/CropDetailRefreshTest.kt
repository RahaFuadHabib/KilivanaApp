package com.example.farmapp.ui.screens

import com.example.farmapp.data.model.*
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Details consume the same guarded mine snapshot as Home, not a second product store. */
class CropDetailRefreshTest {
    private fun product(name: String) = FarmerProductResponse("crop", name, "description",
        "kg", BigDecimal.TEN, BigDecimal.ONE, categoryName = "Vegetables")

    @Test fun refreshedMineUpdatesSelectedDetailsAndIgnoresDuplicates() = runBlocking {
        val guard = CropRefreshController()
        var products = listOf(product("old"))
        val response = CompletableDeferred<List<FarmerProductResponse>>()
        var calls = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            guard.refresh({ calls++; response.await() }, { products = it }, { throw AssertionError(it) })
        }
        assertTrue(guard.refreshing)
        assertEquals("old", products.first { it.productId == "crop" }.name)
        guard.refresh({ calls++; listOf(product("duplicate")) }, { products = it }, {})
        assertEquals(1, calls)
        response.complete(listOf(product("updated")))
        job.join()
        assertEquals("updated", products.first { it.productId == "crop" }.name)
        assertFalse(guard.refreshing)
    }

    @Test fun detailFailureRetainsProductAndRetryClearsError() = runBlocking {
        val guard = CropRefreshController()
        var products = listOf(product("saved"))
        var error: Exception? = null
        guard.refresh<List<FarmerProductResponse>>({ throw IOException() },
            { products = it; error = null }, { error = it })
        assertEquals("saved", products.single().name)
        assertNotNull(error)
        assertFalse(guard.refreshing)
        guard.refresh({ listOf(product("latest")) }, { products = it; error = null }, { error = it })
        assertEquals("latest", products.single().name)
        assertNull(error)
        assertFalse(guard.refreshing)
    }

    @Test fun productSuccessAndOrderFailureRetainIndependentValidSnapshots() = runBlocking {
        val guard = CropRefreshController()
        val orders = FarmerOrdersRefreshController()
        var products = listOf(product("saved"))
        val order = FarmerOrderResponse("order", "seller", "PLACED", BigDecimal.TEN,
            "KES", "2026-10-01", listOf(FarmerOrderItem("crop", "saved",
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN)))
        orders.refresh { listOf(order) }
        guard.refresh({ listOf(product("latest")) }, { products = it }, {})
        orders.refresh { throw IOException() }
        assertEquals("latest", products.single().name)
        assertEquals(1, cropOrders(orders.orders!!, "seller", "crop").size)
        assertNotNull(orders.error)
        assertFalse(guard.refreshing || orders.refreshing)
    }

    @Test fun productFailureStillRefreshesOrdersAndStopsDetailIndicator() = runBlocking {
        val details = CropDetailRefreshController()
        val productsGuard = CropRefreshController()
        val orders = FarmerOrdersRefreshController()
        var products = listOf(product("saved"))
        var productError: Exception? = null
        val order = FarmerOrderResponse("new", "seller", "PLACED", BigDecimal.TEN,
            "KES", "2026-10-01", emptyList())
        details.refresh(
            { productsGuard.refresh<List<FarmerProductResponse>>({ throw IOException() },
                { products = it }, { productError = it }) },
            { orders.refresh { listOf(order) } })
        assertEquals("saved", products.single().name)
        assertNotNull(productError)
        assertEquals("new", orders.orders!!.single().orderId)
        assertFalse(details.refreshing || productsGuard.refreshing || orders.refreshing)
    }

    @Test fun compositeRefreshGuardsBothStepsUntilOrderCountFinishes() = runBlocking {
        val details = CropDetailRefreshController()
        val productsDone = CompletableDeferred<Unit>()
        val ordersDone = CompletableDeferred<Unit>()
        var productCalls = 0
        var orderCalls = 0
        val loadProducts: suspend () -> Unit = { productCalls++; productsDone.await() }
        val loadOrders: suspend () -> Unit = { orderCalls++; ordersDone.await() }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { details.refresh(loadProducts, loadOrders) }
        details.refresh(loadProducts, loadOrders)
        assertEquals(1, productCalls)
        assertEquals(0, orderCalls)
        productsDone.complete(Unit)
        yield()
        assertTrue(details.refreshing)
        details.refresh(loadProducts, loadOrders)
        assertEquals(1, productCalls)
        assertEquals(1, orderCalls)
        ordersDone.complete(Unit)
        job.join()
        assertFalse(details.refreshing)
    }

    @Test fun compositeCancellationStopsIndicatorAndAllowsRetry() = runBlocking {
        val details = CropDetailRefreshController()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            details.refresh({ awaitCancellation() }, {})
        }
        job.cancelAndJoin()
        assertFalse(details.refreshing)
        var calls = 0
        details.refresh({ calls++ }, { calls++ })
        assertEquals(2, calls)
        assertFalse(details.refreshing)
    }

    @Test fun successfulEmptyMineDoesNotRetainADeletedProductAndCanRetry() = runBlocking {
        val guard = CropRefreshController()
        var products = listOf(product("saved"))
        guard.refresh({ emptyList<FarmerProductResponse>() }, { products = it }, {})
        assertNull(products.firstOrNull { it.productId == "crop" })
        guard.refresh({ listOf(product("returned")) }, { products = it }, {})
        assertEquals("returned", products.single().name)
        assertFalse(guard.refreshing)
    }
}