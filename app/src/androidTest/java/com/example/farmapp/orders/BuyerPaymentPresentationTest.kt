package com.example.farmapp.orders

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/** Synthetic UI only. No session, Retrofit service, controller or backend is constructed. */
class BuyerPaymentPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val order = BuyerOrderDto(10, 42, "placed", BigDecimal("125.57"), "pending", items = emptyList())
    @Test fun cancellingConfirmationSendsNothingAndConfirmIsExplicit() {
        var sends = 0
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BuyerPaymentControls(order,
            BuyerPaymentState(10, canInitiate = true, total = BigDecimal("125.57")), {}, { _, amount ->
                assertEquals(BigDecimal("125.57"), amount); sends++
            }) } } }
        compose.onNodeWithText("Pay with M-Pesa").performClick()
        compose.onNodeWithText("M-Pesa phone number").performScrollTo().performTextInput("0712345678")
        compose.onNodeWithText("Send M-Pesa Prompt").performScrollTo().performClick()
        assertEquals(0, sends)
        compose.onNodeWithText("Cancel").performClick(); assertEquals(0, sends)
        compose.onNodeWithText("Send M-Pesa Prompt").performScrollTo().performClick()
        compose.onNodeWithText("Confirm STK Push").performClick(); assertEquals(1, sends)
    }
    @Test fun pendingAndFailedDoNotOfferAnotherInitiationOrShowPaid() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BuyerPaymentControls(order,
            BuyerPaymentState(10, payments = listOf(PaymentDto(9, 10, "mpesa", BigDecimal("125.57"), "pending"))), {}, { _, _ -> error("No submission") }) } } }
        compose.onNodeWithText("Payment: pending - KSh 125.57").assertIsDisplayed()
        compose.onNodeWithText("Send M-Pesa Prompt").assertDoesNotExist()
        compose.onNodeWithText("paid", substring = true).assertDoesNotExist()
    }
    @Test fun failedAndUnknownResultsNeverClaimConfirmationAndSuccessRequiresConfirmedState() {
        val state = androidx.compose.runtime.mutableStateOf(BuyerPaymentState(10,
            payments = listOf(PaymentDto(9, 10, "mpesa", BigDecimal("125.57"), "failed"))))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { BuyerPaymentControls(order, state.value, {}, { _, _ -> error("No submission") }) } } }
        compose.onNodeWithText("Payment: failed - KSh 125.57").assertIsDisplayed()
        compose.onNodeWithText("Send M-Pesa Prompt").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(payments = state.value.payments.map { it.copy(status = "cancelled") }) }
        compose.onNodeWithText("Payment: unverified - KSh 125.57").assertIsDisplayed()
        compose.onNodeWithText("confirmed", substring = true).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(payments = state.value.payments.map { it.copy(status = "paid") }) }
        compose.onNodeWithText("Payment: confirmed - KSh 125.57").assertIsDisplayed()
    }

}
