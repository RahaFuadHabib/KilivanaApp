package com.example.farmapp.buyers

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.farmapp.orders.BuyerOrderDto
import com.example.farmapp.orders.BuyerPaymentState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/** Exercises the same destination registration as BrianBuyerApp with no session/API calls. */
class BuyerOrderNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController

    private fun graph() {
        compose.setContent {
            nav = rememberNavController()
            MaterialTheme {
                NavHost(nav, startDestination = "home") {
                    composable("home") { Text("Synthetic home") }
                    buyerOrderDestinations(
                        ordersContent = { Text("Synthetic orders") },
                        orderContent = { Text("Synthetic order $it") },
                        trackingContent = { Text("Synthetic tracking $it") },
                        paymentContent = { id ->
                            BrianPaymentReview(
                                BuyerOrderDto(id=id,total=BigDecimal("153.21")),
                                BuyerPaymentState(orderId=id,canInitiate=true,total=BigDecimal("153.21"),submissionEnabled=false),
                                { nav.popBackStack() }, {}, { _, _ -> error("Navigation must never send STK") }
                            )
                        }
                    )
                }
            }
        }
    }

    @Test fun ordersDetailsTrackingAndBackResolveProductionRoutes() {
        graph()
        for ((route, label) in listOf("orders" to "Synthetic orders", "order/105" to "Synthetic order 105", "tracking/105" to "Synthetic tracking 105")) {
            compose.runOnIdle { nav.navigate(route) }
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        compose.runOnIdle { assertTrue(nav.popBackStack()) }
        compose.onNodeWithText("Synthetic order 105").assertIsDisplayed()
        compose.runOnIdle { assertTrue(nav.popBackStack()) }
        compose.onNodeWithText("Synthetic orders").assertIsDisplayed()
    }

    @Test fun verifiedOrderPaymentRouteOpensReviewWithoutEnablingSubmission() {
        graph()
        compose.runOnIdle { nav.navigate("payment/105") }
        compose.onNodeWithText("Kenyan mobile number").performTextInput("0712345678")
        compose.onNodeWithText("Send M-Pesa prompt").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(105L,nav.currentBackStackEntry!!.arguments!!.getLong("id"))
            assertTrue(nav.popBackStack())
        }
        compose.onNodeWithText("Synthetic home").assertIsDisplayed()
    }
}
