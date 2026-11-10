package com.example.farmapp.buyers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.farmapp.buyer.BuyerAddressDto
import com.example.farmapp.buyers.data.DeliveryAddress
import com.example.farmapp.buyers.model.*
import com.example.farmapp.buyers.ui.screens.CheckoutScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class BuyerOptionalCoordinatesTest {
    @get:Rule val compose = createComposeRule()
    @Test fun savedAddressWithoutCoordinatesCanCalculateDelivery() {
        val address = BuyerAddressDto(id=8,userId=42,addressText="Synthetic delivery address")
        var requested: Long? = null
        val product = Product("7","Synthetic produce","Produce",BigDecimal("100"),"bag","",null,null,StockStatus.InStock,BigDecimal.ONE)
        compose.setContent { MaterialTheme {
            CheckoutScreen(listOf(CartItem(product,1)),listOf(DeliveryAddress(8,42,"Synthetic delivery address",null,null,"Saved address")),false,null,null,{},
                requestQuote={ id -> assertNull(buyerDoorDeliveryIssue(address)); requested=id },manageAddresses={},
                place={ _,_,_ -> error("No order may be submitted") },back={})
        } }
        compose.onNodeWithText("Kenyan mobile number").performTextInput("0712345678")
        compose.onNodeWithText("Calculate delivery").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(8L,requested) }
        compose.onAllNodesWithText("This address needs valid map coordinates",substring=true).assertCountEquals(0)
    }
}
