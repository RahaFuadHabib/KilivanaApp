package com.example.farmapp.buyers

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.farmapp.buyers.data.CartViewModel
import com.example.farmapp.buyers.data.PaymentStatus
import com.example.farmapp.buyers.model.*
import com.example.farmapp.buyers.ui.screens.*
import com.example.farmapp.buyers.ui.theme.KilivanabuyerTheme
import com.example.farmapp.orders.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/** Only actual imported Brian components + synthetic state; no sessions, network, or live writes. */
class BrianBuyerPresentationTest {
    @get:Rule val compose=createComposeRule()
    private val product=Product("7","Synthetic produce","Vegetables",BigDecimal("1.07"),"kg","",null,null,StockStatus.InStock,BigDecimal.ONE)
    private val order=BuyerOrderDto(105,42,"placed",BigDecimal("153.21"),"pending",items=emptyList(),subtotal=BigDecimal("3.21"),deliveryFee=BigDecimal("150"))
    @Test fun brianHomeUsesOriginalShoppingSections(){
        compose.setContent{KilivanabuyerTheme{HomeScreen(0,listOf(product),listOf("Vegetables"),{},{},{},{},{},{})}}
        compose.onNodeWithText("Kilivana").assertIsDisplayed()
        compose.onNodeWithText("Shop products").assertIsDisplayed()
        compose.onNodeWithText("Shop by category").assertExists()
    }
    @Test fun cartChangesImmediatelyWithoutDialogAndPersistsThroughDelegate(){
        val quantity=mutableIntStateOf(1);var removes=0
        compose.setContent{KilivanabuyerTheme{Box(Modifier.widthIn(max=360.dp).fillMaxSize()){
            CartScreen(listOf(CartItem(product,quantity.intValue)),CartViewModel(product.pricePerUnit.multiply(quantity.intValue.toBigDecimal()),{quantity.intValue++},{if(quantity.intValue>1)quantity.intValue--},{removes++}),{})
        }}}
        compose.onNodeWithContentDescription("Decrease quantity").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Increase quantity").performClick()
        compose.onNodeWithText("2").assertExists()
        compose.onAllNodesWithText("KES 2.14")[0].assertExists()
        compose.onNodeWithContentDescription("Decrease quantity").performClick()
        assertEquals(1,quantity.intValue);assertEquals(0,removes)
    }
    @Test fun paymentRequiresExplicitConfirmationAndCancelNeverSends(){
        var sends=0
        compose.setContent{KilivanabuyerTheme{BrianPaymentReview(order,BuyerPaymentState(105,canInitiate=true,total=order.total),{},{},{_,_->sends++})}}
        compose.onNodeWithText("Kenyan mobile number").performTextInput("0712345678")
        compose.onNodeWithText("Send M-Pesa prompt").performScrollTo().performClick()
        assertEquals(0,sends)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0,sends)
    }
    @Test fun pendingDoesNotClaimSuccessfulPayment(){
        compose.setContent{KilivanabuyerTheme{PaymentStatusScreen(PaymentStatus("pending",BigDecimal("153.21"),null),false,null,{},{})}}
        compose.onNodeWithText("Payment pending").assertIsDisplayed()
        compose.onNodeWithText("Payment confirmed").assertDoesNotExist()
    }
    @Test fun backendConfirmedPaymentUsesOriginalStatusLayout(){
        compose.setContent{KilivanabuyerTheme{PaymentStatusScreen(PaymentStatus("held",BigDecimal("153.21"),null),false,null,{},{})}}
        compose.onNodeWithText("Payment confirmed").assertIsDisplayed()
    }
    @Test fun unknownAndCancelledStatusesNeverClaimSuccess(){
        compose.setContent{KilivanabuyerTheme{PaymentStatusScreen(PaymentStatus("cancelled",null,null),false,null,{},{})}}
        compose.onNodeWithText("Payment not confirmed").assertIsDisplayed()
        compose.onNodeWithText("Payment confirmed").assertDoesNotExist()
    }
    @Test fun originalBuyerAuthFormHonorsExternalBusyGuard(){
        var calls=0
        compose.setContent{KilivanabuyerTheme{AuthScreen(false,{calls++;null},{null},{},{},externalLoading=true)}}
        compose.onNodeWithText("Email address").performTextInput("synthetic@example.invalid")
        compose.onNodeWithText("Password").performTextInput("synthetic-secret")
        compose.onNode(hasClickAction() and hasText("Sign in")).assertDoesNotExist()
        assertEquals(0,calls)
    }
    @Test fun checkoutCannotPlaceOrderWithoutVerifiedQuote(){
        var submits=0
        compose.setContent{KilivanabuyerTheme{CheckoutScreen(listOf(CartItem(product,1)),emptyList(),false,null,null,{}, {}, {}, {_,_,_->submits++},{})}}
        compose.onNodeWithText("Create order").assertDoesNotExist()
        assertEquals(0,submits)
    }
    @Test fun quoteShowsServerTotalAndDoesNotAutomaticallyPlaceOrderOrPay(){
        var submits=0
        val quote=com.example.farmapp.buyers.data.DeliveryQuote("synthetic",8,BigDecimal("3.21"),BigDecimal("150"),BigDecimal("153.21"),"KES",java.time.Instant.now().plusSeconds(900).toString(),"DOOR_DELIVERY")
        val address=com.example.farmapp.buyers.data.DeliveryAddress(8,42,"Synthetic address",null,null,"Synthetic label")
        compose.setContent{KilivanabuyerTheme{CheckoutScreen(listOf(CartItem(product,3)),listOf(address),false,null,quote,{}, {}, {}, {_,_,_->submits++},{})}}
        compose.onNodeWithText("KES 153.21").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Create order").performScrollTo().assertIsNotEnabled()
        assertEquals(0,submits)
    }
    @Test fun unknownPaymentEnvironmentDisablesSubmissionButKeepsReview(){
        var sends=0
        compose.setContent{KilivanabuyerTheme{BrianPaymentReview(order,BuyerPaymentState(105,canInitiate=true,total=order.total,submissionEnabled=false,notice=MPESA_ENVIRONMENT_NOTICE),{},{},{_,_->sends++},"254712345678")}}
        compose.onNodeWithText("Send M-Pesa prompt").performScrollTo().assertIsNotEnabled()
        assertEquals(0,sends)
    }
    @Test fun expiredQuoteCannotCreateOrder(){
        val quote=com.example.farmapp.buyers.data.DeliveryQuote("synthetic",null,BigDecimal("3.21"),BigDecimal("50"),BigDecimal("53.21"),"KES",java.time.Instant.now().minusSeconds(60).toString(),"PICKUP_STATION","Synthetic pickup")
        compose.setContent{KilivanabuyerTheme{CheckoutScreen(listOf(CartItem(product,3)),emptyList(),false,null,quote,{}, {}, {}, {_,_,_->error("No order")},{},deliveryContent={androidx.compose.material3.Text("Synthetic pickup")})}}
        compose.onNodeWithText("Create order").assertDoesNotExist()
        compose.onNodeWithText("Delivery quote expired or selection changed. Calculate delivery again.").assertExists()
    }
    @Test fun uncertainSubmittedPaymentStaysOnStatusScreenAndCannotSendAgain(){
        compose.setContent{KilivanabuyerTheme{BrianPaymentReview(order,BuyerPaymentState(105,total=order.total,submitted=true,notice="Synthetic interrupted submission"),{},{},{_,_->error("No retry")})}}
        compose.onNodeWithText("Payment not confirmed").assertIsDisplayed()
        compose.onNodeWithText("Send M-Pesa prompt").assertDoesNotExist()
    }
}
