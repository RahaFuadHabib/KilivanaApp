package com.example.farmapp.buyer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.farmapp.data.model.canonical.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/** Synthetic screens only; no session, API, map, payment or order controller is constructed. */
class BuyerDeliveryPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val row = CanonicalFarmerProduct("7", "99", "FARM_PRODUCE", "2", "Synthetic", "", "kg",
        BigDecimal("125.57"), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE,
        CanonicalProductStatus.ACTIVE, emptyList(), null, null)
    private val cart = BuyerShoppingState(selection = BuyerSelection(cart = mapOf(7L to 2)), known = mapOf(7L to row))
    private fun show(initial: BuyerAddressState = BuyerAddressState(loaded = true), initialMethod: BuyerDeliveryMethod = BuyerDeliveryMethod.PICKUP,
        small: Boolean = false) {
        compose.setContent { MaterialTheme {
            var method by remember { mutableStateOf(initialMethod) }
            var addresses by remember { mutableStateOf(initial) }
            BuyerDeliveryFlow(cart, addresses, BuyerCheckoutReview(), method, { method = it },
                { addresses = addresses.copy(selectedId = it) },
                { request -> addresses = addresses.copy(rows = listOf(BuyerAddressDto(9, 42, request.label, request.addressText)), selectedId = 9, createdId = 9) },
                {}, {}, {}, if (small) Modifier.width(280.dp).height(420.dp) else Modifier)
        } }
    }
    @Test fun pickupEmptyStateSwitchesToUnavailableInsteadOfFakeStation() {
        show()
        compose.onNodeWithText("Pickup Stations").assertIsSelected()
        compose.onNodeWithText("No saved pickup station yet!").assertIsDisplayed()
        compose.onNodeWithText("Add New Pickup Station").performScrollTo().performClick()
        compose.onNodeWithText("Pickup stations unavailable").assertIsDisplayed()
        compose.onNodeWithText("Choose Door Delivery").performClick()
        compose.onNodeWithText("Door Delivery").assertIsSelected()
        compose.onNodeWithText("No saved delivery address yet!").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("delivery-review").performScrollTo().assertIsNotEnabled()
    }
    @Test fun addAndSelectConfirmedLocationThenBackPreservesSelection() {
        show(initialMethod = BuyerDeliveryMethod.DOOR)
        compose.onNodeWithText("Add New Address").performScrollTo().performClick()
        compose.onNodeWithText("Save address").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Full delivery address").performScrollTo().performTextInput("Synthetic county, town and landmark")
        compose.onNodeWithText("Save address").performScrollTo().performClick()
        compose.onNodeWithTag("delivery-address-9").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("delivery-review").performScrollTo().performClick()
        compose.onNodeWithText("Review Order").assertIsDisplayed()
        compose.onNodeWithTag("delivery-place-order").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Change delivery location").performScrollTo().performClick()
        compose.onNodeWithTag("delivery-address-9").performScrollTo().assertIsSelected()
    }
    @Test fun checkoutNavigatesToDeliveryAndBackWithoutChangingCart() {
        var opens = 0
        compose.setContent { MaterialTheme {
            var delivery by remember { mutableStateOf(false) }
            if (delivery) BuyerDeliveryFlow(cart, BuyerAddressState(loaded = true), BuyerCheckoutReview(), BuyerDeliveryMethod.PICKUP,
                {}, {}, {}, {}, {}, { delivery = false })
            else BuyerShoppingContent(BuyerDestination.CART, cart, BuyerAddressState(), "Synthetic", BuyerActions(checkout = { opens++; delivery = true }))
        } }
        compose.onNodeWithTag("buyer-checkout").performClick()
        compose.onNodeWithText("Select delivery method").assertIsDisplayed(); assertEquals(1, opens)
        compose.onNodeWithContentDescription("Back to previous checkout step").performClick()
        compose.onNodeWithText("Your cart").assertIsDisplayed()
        assertEquals(mapOf(7L to 2), cart.selection.cart)
    }
    @Test fun selectingExistingAddressSurvivesMethodSwitchAndReviewShowsNoInventedFee() {
        show(BuyerAddressState(rows = listOf(BuyerAddressDto(4, 42, "Home", "Synthetic address")), loaded = true), BuyerDeliveryMethod.DOOR)
        compose.onNodeWithTag("delivery-review").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("delivery-address-4").performScrollTo().performClick()
        compose.onNodeWithText("Pickup Stations").performScrollTo().performClick()
        compose.onNodeWithText("Door Delivery").performClick()
        compose.onNodeWithTag("delivery-address-4").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("delivery-review").performScrollTo().performClick()
        compose.onNodeWithTag("delivery-list").performScrollToNode(hasText("Items subtotal: KSh 251.14"))
        compose.onNodeWithText("Items subtotal: KSh 251.14").assertIsDisplayed()
        compose.onNodeWithTag("delivery-list").performScrollToNode(hasText("Final total: not yet confirmed"))
        compose.onNodeWithText("Final total: not yet confirmed").assertIsDisplayed()
        compose.onNodeWithText("Confirm STK Push").assertDoesNotExist()
    }
    @Test fun verifiedQuoteTotalsKeepDecimals() {
        compose.setContent { MaterialTheme { BuyerDeliveryTotals(BigDecimal("251.14"), BuyerDeliveryQuote("opaque", 4,
            BigDecimal("251.14"), BigDecimal("50.01"), BigDecimal("301.15"), "KES", "2030-01-01T00:00:00Z", "DOOR_DELIVERY")) } }
        compose.onNodeWithText("Final total: KSh 301.15").assertIsDisplayed()
        compose.onNodeWithText("Delivery fee: KSh 50.01").assertIsDisplayed()
    }
    @Test fun smallDeviceEmptyStateAndAddButtonRemainScrollable() {
        show(small = true)
        compose.onNodeWithText("Add New Pickup Station").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Choose Door Delivery").performScrollTo().assertIsDisplayed()
    }
}
