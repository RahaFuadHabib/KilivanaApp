package com.example.farmapp.supplier

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.farmapp.ui.theme.FarmAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/** Synthetic, already owner-projected records only. No API client or live order actions. */
class SupplierOrdersUiTest {
    @get:Rule val compose = createComposeRule()
    private fun screen(state: SupplierUiState, load: (Boolean) -> Unit = {}) {
        compose.setContent { FarmAppTheme {
            SupplierContent(state, "Synthetic Supplier", {}, false, {}, false, {}, {}, {}, {}, { _, _ -> },
                { _, _ -> }, {}, loadOrders = load)
        } }
        compose.onNodeWithText("Orders").performClick()
    }
    @Test fun openingOrdersLoadsOnceAndShowsLoadingWithoutFakeEmptyResult() {
        var calls = 0
        screen(SupplierUiState()) { calls++ }
        compose.onNodeWithText("Loading your orders").assertExists()
        compose.onNodeWithText("No orders yet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, calls) }
    }
    @Test fun confirmedEmptyStateCanRefresh() {
        var calls = 0
        screen(SupplierUiState(ordersLoaded = true, ordersLast = true)) { if (it) calls++ }
        compose.onNodeWithText("No orders yet").assertExists()
        compose.onNodeWithText("Refresh orders").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }
    @Test fun offlineErrorOffersExplicitRetryWithoutClaimingNoOrders() {
        var calls = 0
        screen(SupplierUiState(ordersError = "Check your connection and try again.")) { if (it) calls++ }
        compose.onNodeWithText("Couldn't load orders").assertExists()
        compose.onNodeWithText("No orders yet").assertDoesNotExist()
        compose.onNodeWithText("Retry orders").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }
    @Test fun ownedItemsDisplayExactPricesReadableDateAndNoTransactionActions() {
        val row = SupplierOrder(8, "SYNTHETIC", "confirmed", "2026-10-30T09:00:00Z", listOf(
            SupplierOrderLine(1, 7, "Synthetic seeds", "bag", 3, BigDecimal("0.10"), BigDecimal("0.30"))))
        screen(SupplierUiState(orders = listOf(row), ordersLoaded = true, ordersLast = true))
        compose.onNodeWithText("Synthetic seeds").assertExists()
        compose.onNodeWithText("3 bag × KES 0.10").assertExists()
        compose.onNodeWithText("Confirmed").assertExists()
        compose.onNodeWithText("Your items subtotal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2026-10-30T09:00:00Z").assertDoesNotExist()
        listOf("Pay now", "Cancel order", "Delete order", "Update status", "View Details").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }
    @Test fun paginationOnlyRunsFromExplicitLoadMore() {
        var calls = 0
        screen(SupplierUiState(ordersLoaded = true, ordersLast = false)) { if (!it) calls++ }
        compose.onNodeWithText("Load more orders").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }
}
