package com.example.farmapp.orders

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/** Reference-layout inspection uses only synthetic order/payment data and no controller or API. */
class BuyerOrdersReferencePresentationTest {
    @get:Rule val compose = createComposeRule()
    private val unpaid = BuyerOrderDto(10,42,"placed",BigDecimal("1370.25"),"pending","2026-10-28T07:24:00Z",
        items = listOf(BuyerOrderItemDto(1,25,70,"Synthetic tomatoes","kg",5,BigDecimal("100.05"),BigDecimal("500.25")),
            BuyerOrderItemDto(2,26,70,"Synthetic greens","kg",3,BigDecimal("80"),BigDecimal("240")),
            BuyerOrderItemDto(3,27,70,"Synthetic carrots","kg",6,BigDecimal("85"),BigDecimal("510"))),
        subtotal = BigDecimal("1250.25"), deliveryFee = BigDecimal("120"), deliveryAddress = "Synthetic delivery location")
    private val eligible = BuyerPaymentState(10, canInitiate = true, total = unpaid.total)
    private val paid get() = unpaid.copy(paymentStatus = "held", paymentMethod = "mpesa", paidAt = "2026-10-28T07:35:00Z")
    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "buyer-order-visuals/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun fourReferenceScreensRenderAndPaymentRemainsExplicit() {
        val page = mutableStateOf(0); var sends = 0
        compose.setContent { BuyerOrdersTheme { Box(Modifier.widthIn(max = 360.dp).fillMaxSize()) {
            when(page.value) {
                0 -> Column { Box(Modifier.weight(1f)) { BuyerOrdersListDesign(listOf(unpaid,
                    paid.copy(id = 11, status = "delivered"), unpaid.copy(id = 12, status = "confirmed")), {}) { row ->
                    BuyerOrderCard(row, if(row.id == 10L) eligible else null, { page.value = 1 }, { page.value = 2 })
                } }; NavigationBar(containerColor = Color.White) { listOf("Home","Browse","Cart","Orders","Profile").forEach { label ->
                    NavigationBarItem(selected = label == "Orders", onClick = {}, colors = NavigationBarItemDefaults.colors(selectedIconColor = OrderGreen,
                        selectedTextColor = OrderGreen, indicatorColor = OrderPaleGreen), icon = { Icon(when (label) {
                            "Home" -> Icons.Default.Home; "Browse" -> Icons.Default.Storefront; "Cart" -> Icons.Default.ShoppingCart
                            "Orders" -> Icons.Default.ReceiptLong; else -> Icons.Default.Person
                        }, null) }, label = { Text(label) })
                } } }
                1 -> BuyerOrderDetailsDesign(unpaid, eligible, { page.value = 0 }, { page.value = 2 })
                2 -> BuyerPaymentPage(unpaid, eligible, { page.value = 1 }, {}, { _, _ -> sends++ })
                else -> BuyerOrderDetailsDesign(paid, null, { page.value = 0 }, { error("Paid order cannot pay") })
            }
        } } }
        compose.onNodeWithText("My Orders").assertIsDisplayed(); capture("01-orders")
        compose.onNodeWithText("Pending", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Delivered").performClick()
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("Pay with M-Pesa").performClick(); assertEquals(0,sends)
        compose.onNodeWithText("M-Pesa phone number").performScrollTo().performTextInput("0712345678")
        compose.onNodeWithText("Send M-Pesa Prompt").performScrollTo().performClick(); assertEquals(0,sends)
        compose.onNodeWithText("Cancel").performClick(); assertEquals(0,sends)
        // Reset scroll by returning to details/payment before capturing the payment top.
        compose.onNodeWithContentDescription("Back").performClick()
        capture("02-unpaid-details")
        compose.onNodeWithText("Pay with M-Pesa").performClick(); capture("03-mpesa")
        compose.onNodeWithText("Payment Security").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { page.value = 3 }
        compose.onNodeWithText("Payment confirmed").assertIsDisplayed()
        compose.onNodeWithText("Pay with M-Pesa").assertDoesNotExist(); capture("04-paid-details")
        compose.onNodeWithTag("order-details-list").performScrollToNode(hasText("Payment completed via M-Pesa"))
        compose.onNodeWithText("Payment completed via M-Pesa").assertIsDisplayed()
        assertEquals(0,sends)
    }
    @Test fun smallScreenScrollsAndMissingBackendFeesAreNotInvented() {
        compose.setContent { BuyerOrdersTheme { Box(Modifier.width(320.dp).height(480.dp)) {
            BuyerOrderDetailsDesign(unpaid.copy(deliveryFee = null, deliveryAddress = null), eligible, {}, {})
        } } }
        compose.onNodeWithText("Pay with M-Pesa").assertIsDisplayed()
        compose.onNodeWithTag("order-details-list").performScrollToNode(hasText("Delivery details not available"))
        compose.onNodeWithText("Delivery details not available").assertIsDisplayed()
        compose.onNodeWithTag("order-details-list").performScrollToNode(hasText("Not available"))
        compose.onNodeWithText("Not available").assertIsDisplayed()
        compose.onNodeWithText("KSh 120").assertDoesNotExist()
    }
}
