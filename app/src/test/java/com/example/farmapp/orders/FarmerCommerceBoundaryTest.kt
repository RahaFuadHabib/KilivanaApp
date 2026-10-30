package com.example.farmapp.orders

import com.example.farmapp.data.model.FarmerOrderItem
import com.example.farmapp.data.model.FarmerOrderResponse
import com.example.farmapp.network.SessionState
import com.example.farmapp.network.canonical.CanonicalBackendApi
import com.example.farmapp.network.canonical.CanonicalFarmService
import com.example.farmapp.payments.VerifiedPaymentStatus
import com.example.farmapp.repository.ProductFailure
import com.example.farmapp.repository.ProductRepository
import com.example.farmapp.ui.screens.FarmerOrdersRefreshController
import com.example.farmapp.ui.screens.cropOrders
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.math.BigDecimal

/** Synthetic boundaries only: no order placement, STK request or network transport exists here. */
class FarmerCommerceBoundaryTest {
    private val item = FarmerOrderItem("7", "Synthetic produce", BigDecimal("2"), BigDecimal.TEN, BigDecimal("20"))
    private fun sellerOrder(id: String, seller: String) = FarmerOrderResponse(
        id, seller, "PLACED", BigDecimal("20"), "KES", "2026-10-27", listOf(item))

    @Test fun unsupportedFarmerOrderReadNeverFallsBackToUnfilteredNetworkRequests() = runBlocking {
        var calls = 0
        val api = Proxy.newProxyInstance(CanonicalBackendApi::class.java.classLoader,
            arrayOf(CanonicalBackendApi::class.java)) { _, _, _ -> calls++; error("No backend request is authorized") } as CanonicalBackendApi
        val repository = ProductRepository(CanonicalFarmService(api, SessionState()) {
            error("No image request is expected")
        })
        val state = FarmerOrdersRefreshController()
        state.refresh { repository.orders() }
        assertEquals(0, calls)
        assertNull(state.orders)
        assertNotNull(state.error)
        assertFalse(state.refreshing)
        try {
            repository.orders()
            fail("Unsupported seller-order access must fail closed")
        } catch (e: ProductFailure) {
            assertTrue(e.message!!.contains("seller-scoped API"))
        }
        assertEquals(0, calls)
    }

    @Test fun existingSellerProjectionFiltersOwnerAndProductWithoutReassigningOtherSellers() {
        val own = sellerOrder("1", "42")
        val other = sellerOrder("2", "99")
        val rows = listOf(other, own, own, own.copy(orderId = "3", items = listOf(item.copy(productId = "8"))))
        val selected = cropOrders(rows, "42", "7")
        assertEquals(listOf(own), selected)
        assertEquals(listOf(own, rows.last()), cropOrders(rows, "42"))
        assertTrue(cropOrders(rows, "unrelated").isEmpty())
        // This filter consumes already-authorized seller projections, not mixed backend orders.
        assertEquals("99", other.sellerId)
    }

    @Test fun canonicalOrderStatesRetainBuyerOwnershipAndRejectInventedTransitions() {
        val row = BuyerOrderDto(1, 42, "placed", BigDecimal("20"), "pending", items = listOf(
            BuyerOrderItemDto(10, 7, 99, "Synthetic supply", "bag", 2, BigDecimal.TEN)))
        for (status in listOf("placed", "confirmed", "in_transit", "delivered", "completed", "disputed", "cancelled"))
            assertEquals(status, validateBuyerOrder(row.copy(status = status), 42).status)
        for (status in listOf("accepted", "processing", "shipped", "unknown")) {
            try { validateBuyerOrder(row.copy(status = status), 42); fail("Unsupported status") }
            catch (_: IllegalStateException) { }
        }
        try { validateBuyerOrder(row, 99); fail("Seller is not the purchaser") }
        catch (_: IllegalStateException) { }
    }

    @Test fun paymentStateNeverTreatsStkAcceptanceOrUnknownResultsAsPaid() {
        for (wire in listOf("pending", "paid", "held", "settled", "failed", "refunded")) {
            assertEquals(wire, VerifiedPaymentStatus.parse(wire)!!.wire)
            assertTrue(paymentStatuses.contains(wire))
        }
        assertNotEquals(VerifiedPaymentStatus.PAID, VerifiedPaymentStatus.parse("pending"))
        assertNotEquals(VerifiedPaymentStatus.SETTLED, VerifiedPaymentStatus.parse("held"))
        for (wire in listOf(null, "", "0", "STK accepted", "success", "cancelled", "timeout"))
            assertNull(VerifiedPaymentStatus.parse(wire))
    }
}
