package com.example.farmapp.ui.screens

import com.example.farmapp.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

class CropLifecycleControllerTest {
    private val product = FarmerProductResponse("7", "Synthetic crop", "Description", "kg", BigDecimal.TEN,
        BigDecimal.TEN, status = "UNLISTED", sellerId = "42", reservedQuantity = BigDecimal.ZERO)

    @Test fun beginClosesDoubleTapWindowAndRefreshesDetailAndListAfterConfirmedWrite() = runBlocking {
        val calls = mutableListOf<String>()
        var products: List<FarmerProductResponse>? = null
        val controller = CropLifecycleController({ _, _ -> calls += "write" },
            { calls += "list"; listOf(product.copy(status = "ACTIVE")) }, { calls += "detail"; product })
        assertTrue(controller.begin()); assertFalse(controller.begin())
        controller.execute("7", FarmerProductAction.UNLIST, { products = it }, { fail() })
        assertEquals(listOf("write", "detail", "list"), calls)
        assertEquals("UNLISTED", products!!.single().status)
        assertEquals(FarmerProductAction.UNLIST.successMessage, controller.message)
        assertFalse(controller.busy); assertFalse(controller.refreshRequired)
    }
    @Test fun refreshFailureAfterWriteBlocksRepeatUntilExplicitSuccessfulRefresh() = runBlocking {
        var writes = 0
        val controller = CropLifecycleController({ _, _ -> writes++ }, { throw IOException("Synthetic") }, { product })
        controller.begin()
        controller.execute("7", FarmerProductAction.UNLIST, { fail() }, { fail() })
        assertEquals(1, writes)
        assertTrue(controller.message!!.contains("Crop unlisted"))
        assertTrue(controller.message!!.contains("Refresh failed"))
        assertFalse(controller.begin())
        controller.refreshed()
        assertTrue(controller.begin())
    }
    @Test fun uncertainWriteDoesNotRefreshOrRemoveVisibleProductAndBlocksRetry() = runBlocking {
        var reads = 0
        val controller = CropLifecycleController({ _, _ -> throw IOException("Synthetic lost response") },
            { reads++; emptyList() }, { reads++; product })
        controller.begin()
        controller.execute("7", FarmerProductAction.DELETE, { fail() }, { fail() })
        assertEquals(0, reads)
        assertTrue(controller.message!!.contains("not confirmed"))
        assertTrue(controller.refreshRequired)
        assertFalse(controller.begin())
    }
    @Test fun deletionClosesDetailOnlyAfterServerAcceptanceAndNeverLoadsDeletedDetail() = runBlocking {
        var confirmed = false; var closed = false
        val controller = CropLifecycleController({ _, _ -> confirmed = true }, { emptyList() }, { fail(); product })
        controller.begin()
        controller.execute("7", FarmerProductAction.DELETE, { assertTrue(it.isEmpty()) }, { assertTrue(confirmed); closed = true })
        assertTrue(closed)
        assertEquals(FarmerProductAction.DELETE.successMessage, controller.message)
    }
}
