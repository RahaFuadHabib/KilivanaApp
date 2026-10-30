package com.example.farmapp.buyer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.farmapp.data.model.canonical.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/** Synthetic presentation callbacks only. No live pickup/cart/order/payment API is constructed. */
class BuyerPickupPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val station = BuyerPickupStation(11,"Synthetic station","Synthetic address",region="Synthetic North",isAvailable=true,openingHours="Synthetic hours")
    private val row = CanonicalFarmerProduct("7","99","FARM_PRODUCE","2","Synthetic","","kg",BigDecimal("125.57"),BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ONE,CanonicalProductStatus.ACTIVE,emptyList(),null,null)
    private val cart = BuyerShoppingState(selection=BuyerSelection(cart=mapOf(7L to 2)), known=mapOf(7L to row))
    @Test fun browseRegionSaveDefaultAndSelectUsesPlatformIdentity() {
        var savedId: Long? = null; var filter: String? = null
        compose.setContent { MaterialTheme {
            var state by remember { mutableStateOf(BuyerPickupState(loaded=true,available=true,stations=listOf(station),allStations=listOf(station))) }
            BuyerDeliveryFlow(cart,BuyerAddressState(),BuyerCheckoutReview(),BuyerDeliveryMethod.PICKUP,{}, {}, {}, {}, {}, {},
                pickups=state,pickupActions=BuyerPickupActions(refresh={ filter=it; state=state.copy(region=it) },
                    select={ state=state.copy(selected=station) },save={ id,label,default -> savedId=id; state=state.copy(saved=listOf(BuyerSavedPickup(1011,42,id,station.name,station.address,station.region,true,label,default))) }))
        } }
        compose.onNodeWithText("No saved pickup station yet!").assertIsDisplayed()
        compose.onNodeWithText("Add New Pickup Station").performScrollTo().performClick()
        compose.onNodeWithTag("pickup-region-Synthetic North").performClick(); assertEquals("Synthetic North",filter)
        compose.onNodeWithTag("available-pickup-11").performScrollTo().performClick()
        compose.onNodeWithText("Save station").performScrollTo().performClick()
        compose.onNodeWithText("Label (optional)").performTextInput("Favourite")
        compose.onNode(isToggleable()).performClick()
        compose.onAllNodesWithText("Save pickup station").filter(hasClickAction()).onFirst().performClick()
        assertEquals(11L,savedId)
        compose.onNodeWithContentDescription("Back to previous checkout step").performClick()
        compose.onNodeWithTag("saved-pickup-1011").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Default station").assertIsDisplayed()
    }
    @Test fun defaultAndRemovalUseSavedRecordIdAndRequireExplicitRemoval() {
        var defaultId: Long?=null; var removeId: Long?=null
        compose.setContent { MaterialTheme {
            var state by remember { mutableStateOf(BuyerPickupState(loaded=true,available=true,stations=listOf(station),allStations=listOf(station),
                saved=listOf(BuyerSavedPickup(1011,42,11,station.name,station.address,station.region,true,"Favourite",false)))) }
            BuyerDeliveryFlow(cart,BuyerAddressState(),BuyerCheckoutReview(),BuyerDeliveryMethod.PICKUP,{}, {}, {}, {}, {}, {},
                pickups=state,pickupActions=BuyerPickupActions(setDefault={ defaultId=it;state=state.copy(saved=state.saved.map { r->r.copy(isDefault=true) }) },
                    remove={ removeId=it;state=state.copy(saved=emptyList(),selected=null) }))
        } }
        compose.onNodeWithText("Set default").performScrollTo().performClick();assertEquals(1011L,defaultId)
        compose.onNodeWithTag("remove-saved-pickup-1011").performClick();assertNull(removeId)
        compose.onNodeWithText("Cancel").performClick();assertNull(removeId)
        compose.onNodeWithTag("remove-saved-pickup-1011").performClick()
        compose.onNodeWithText("Remove station").performClick();assertEquals(1011L,removeId)
        compose.onNodeWithText("No saved pickup station yet!").performScrollTo().assertIsDisplayed()
    }
    @Test fun pickupReviewPreservesCartAndNeverInitiatesPayment() {
        var checks=0
        compose.setContent { MaterialTheme {
            BuyerDeliveryFlow(cart,BuyerAddressState(),BuyerCheckoutReview(),BuyerDeliveryMethod.PICKUP,{}, {}, {}, {}, {checks++}, {},
                pickups=BuyerPickupState(loaded=true,available=true,selected=station,stations=listOf(station),allStations=listOf(station),
                    saved=listOf(BuyerSavedPickup(1011,42,11,station.name,station.address,station.region,true,null,true))))
        } }
        compose.onNodeWithTag("pickup-review").performScrollTo().performClick();assertEquals(1,checks)
        compose.onNodeWithText("Pickup Station").assertIsDisplayed()
        compose.onNodeWithTag("delivery-place-order").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Confirm STK Push").assertDoesNotExist()
        assertEquals(mapOf(7L to 2),cart.selection.cart)
    }
}
