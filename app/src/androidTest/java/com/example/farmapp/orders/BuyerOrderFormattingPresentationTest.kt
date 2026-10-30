package com.example.farmapp.orders

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class BuyerOrderFormattingPresentationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun orderCardHidesInternalIdAndRawTimestamp() {
        compose.setContent { MaterialTheme { BuyerOrderSummary(BuyerOrderDto(987654321,42,"placed",BigDecimal("125.57"),"pending","2026-10-28T12:00:00Z")) } }
        compose.onNodeWithText("987654321", substring = true).assertDoesNotExist()
        compose.onNodeWithText("2026-10-28T12:00:00Z", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Oct 2026", substring = true).assertIsDisplayed()
    }
}
