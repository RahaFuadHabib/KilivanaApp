package com.example.farmapp.buyer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.farmapp.data.model.canonical.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/** Pure presentation tests. No login/session store, image request or live API is constructed. */
class BuyerShoppingPresentationTest {
    @get:Rule val compose = createComposeRule()
    private fun product() = CanonicalFarmerProduct("7", "99", "FARM_PRODUCE", "2", "Synthetic beans", "Synthetic description", "kg",
        BigDecimal("125.57"), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal("2"),
        CanonicalProductStatus.ACTIVE, emptyList(), null, null)
    private fun show(destination: BuyerDestination, state: BuyerShoppingState = BuyerShoppingState(),
        addresses: BuyerAddressState = BuyerAddressState(), actions: BuyerActions = BuyerActions()) {
        compose.setContent { MaterialTheme { BuyerShoppingContent(destination, state, addresses, "Synthetic Buyer", actions) } }
    }
    @Test fun dashboardNavigationUsesRealCallbacks() {
        var opened = false
        show(BuyerDestination.HOME, actions = BuyerActions(navigate = { opened = it == BuyerDestination.SHOP }))
        compose.onNodeWithText("Browse produce").performClick()
        assertTrue(opened)
        compose.onNodeWithText("Welcome, Synthetic").assertIsDisplayed()
    }
    @Test fun cornerAddCallsCartWithoutOpeningDetail() {
        var quantity = 0; var opened = false
        show(BuyerDestination.SHOP, BuyerShoppingState(products = listOf(product())), actions = BuyerActions(
            add = { _, value -> quantity += value }, open = { opened = true }))
        compose.onNodeWithTag("buyer-image-7").performScrollTo()
        compose.onNodeWithTag("buyer-add-7").performClick()
        org.junit.Assert.assertEquals(1, quantity)
        org.junit.Assert.assertFalse(opened)
        val image = compose.onNodeWithTag("buyer-image-7").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag("buyer-add-7").fetchSemanticsNode().boundsInRoot
        assertTrue(button.center.x > image.center.x)
        assertTrue(button.center.y > image.center.y)
        assertTrue(button.right < image.right && button.bottom < image.bottom)
        compose.onNodeWithText("Save item").assertDoesNotExist()
    }
    @Test fun homeHasNoShoppingManagementShortcuts() {
        show(BuyerDestination.HOME)
        compose.onNodeWithText("Address book").assertDoesNotExist()
        compose.onNodeWithText("Saved (0)").assertDoesNotExist()
        compose.onNodeWithText("Cart (0 products)").assertDoesNotExist()
    }
    @Test fun savedEmptyStateHasShoppingAction() {
        show(BuyerDestination.SAVED)
        compose.onNodeWithText("No saved items yet. Tap a heart while shopping.").assertIsDisplayed()
        compose.onNodeWithText("Find produce").assertIsDisplayed()
    }
    @Test fun cartPreservesCentsAndOffersCheckoutReview() {
        show(BuyerDestination.CART, BuyerShoppingState(selection = BuyerSelection(cart = mapOf(7L to 2)), known = mapOf(7L to product())))
        compose.onNodeWithText("Estimated items subtotal: KSh 251.14").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("buyer-checkout").assertIsEnabled()
    }
    @Test fun quantityButtonsUpdateTotalsAndHonorOneAndStockLimits() {
        val row = product().copy(minimumOrderQuantity = BigDecimal.ONE, availableQuantity = BigDecimal("2"))
        val shopping = androidx.compose.runtime.mutableStateOf(BuyerShoppingState(selection = BuyerSelection(cart = mapOf(7L to 1)), known = mapOf(7L to row)))
        compose.setContent { MaterialTheme { BuyerShoppingContent(BuyerDestination.CART, shopping.value, BuyerAddressState(), "Synthetic",
            BuyerActions(quantity = { id, qty -> shopping.value = shopping.value.copy(selection = BuyerSelection(cart = mapOf(id to qty))) })) } }
        compose.onNodeWithTag("buyer-minus-7").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("buyer-plus-7").performClick()
        compose.onNodeWithTag("buyer-quantity-7").assertTextEquals("2")
        compose.onNodeWithTag("buyer-plus-7").assertIsNotEnabled()
        compose.onNodeWithText("Subtotal: KSh 251.14").assertIsDisplayed()
        compose.onNodeWithTag("buyer-minus-7").performClick()
        compose.onNodeWithTag("buyer-quantity-7").assertTextEquals("1")
        compose.onNodeWithTag("buyer-minus-7").assertIsNotEnabled()
    }
    @Test fun downwardGestureTriggersMarketplaceRefresh() {
        var requests = 0
        show(BuyerDestination.SHOP, BuyerShoppingState(loaded = true), actions = BuyerActions(refresh = { requests++ }))
        compose.onNodeWithTag("buyer-list").performTouchInput { swipeDown(startY = 10f, endY = height * 0.9f, durationMillis = 600) }
        compose.waitForIdle()
        assertTrue(requests > 0)
    }
    @Test fun heartChangesSavedStateWithoutAddingToCart() {
        var adds = 0
        val shopping = androidx.compose.runtime.mutableStateOf(BuyerShoppingState(products = listOf(product())))
        compose.setContent { MaterialTheme { BuyerShoppingContent(BuyerDestination.SHOP, shopping.value, BuyerAddressState(), "Synthetic Buyer",
            BuyerActions(saved = { id -> shopping.value = shopping.value.copy(selection = BuyerSelection(saved =
                if (id in shopping.value.selection.saved) emptySet() else setOf(id))) }, add = { _, _ -> adds++ })) } }
        compose.onNodeWithTag("buyer-image-7").performScrollTo()
        compose.onNodeWithTag("buyer-heart-7").performClick()
        compose.onNodeWithContentDescription("Unsave Synthetic beans").assertIsDisplayed(); org.junit.Assert.assertEquals(0, adds)
        compose.onNodeWithTag("buyer-heart-7").performClick()
        compose.onNodeWithContentDescription("Save Synthetic beans").assertIsDisplayed(); org.junit.Assert.assertEquals(0, adds)
    }
    @Test fun trashRemovesLocallyAndPricingShowsExplicitEstimate() {
        val shopping = androidx.compose.runtime.mutableStateOf(BuyerShoppingState(selection = BuyerSelection(cart = mapOf(7L to 2)), known = mapOf(7L to product())))
        compose.setContent { MaterialTheme { BuyerShoppingContent(BuyerDestination.CART, shopping.value, BuyerAddressState(), "Synthetic",
            BuyerActions(remove = { shopping.value = shopping.value.copy(selection = BuyerSelection()) })) } }
        compose.onNodeWithTag("buyer-list").performScrollToNode(hasText("Service charge (estimate): KSh 30.00"))
        compose.onNodeWithText("Service charge (estimate): KSh 30.00").assertIsDisplayed()
        compose.onNodeWithTag("buyer-list").performScrollToNode(hasText("Estimated total before delivery: KSh 281.14"))
        compose.onNodeWithText("Estimated total before delivery: KSh 281.14").assertIsDisplayed()
        compose.onNodeWithTag("buyer-remove-7").performScrollTo().performClick()
        compose.onNodeWithText("Your cart is empty.").assertIsDisplayed()
    }
    @Test fun allChipClearsCategoryAndRetainsSearch() {
        var category: Long? = 99
        show(BuyerDestination.SHOP, BuyerShoppingState(category = 2, query = "coffee", categories = listOf(CanonicalCategoryDto(2, "Tea & Coffee", "farmer", true))),
            actions = BuyerActions(search = { query, id -> org.junit.Assert.assertEquals("coffee", query); category = id }))
        compose.onNodeWithText("All").performClick(); org.junit.Assert.assertNull(category)
        compose.onAllNodesWithText("Tea & Coffee").onFirst().assertIsDisplayed()
    }
    @Test fun addressFailureDoesNotShowFakeAddressesOrEnableEditing() {
        show(BuyerDestination.ADDRESSES, addresses = BuyerAddressState(error = "Synthetic service unavailable"))
        compose.onNodeWithText("Synthetic service unavailable").assertIsDisplayed()
        compose.onNodeWithText("Address editing unavailable").assertIsNotEnabled()
    }
    @Test fun detailQuantityHonorsMinimumAndStockBeforeAdd() {
        var added = false
        show(BuyerDestination.SHOP, BuyerShoppingState(selectedId = 7, detail = product()), actions = BuyerActions(add = { _, qty -> added = qty == 2 }))
        compose.onNodeWithText("Quantity in kg").performScrollTo().performTextReplacement("1")
        compose.onNodeWithText("Add to cart").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Quantity in kg").performScrollTo().performTextReplacement("2")
        compose.onNodeWithText("Add to cart").performScrollTo().performClick()
        assertTrue(added)
    }
    @Test fun catalogLoadingAndFailureHaveRetryWithoutInventedProducts() {
        show(BuyerDestination.SHOP, BuyerShoppingState(busy = true, error = "Synthetic catalog failure"))
        compose.onNodeWithText("Loading produce…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Synthetic catalog failure").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Retry produce").performScrollTo().assertIsNotEnabled()
    }
}
