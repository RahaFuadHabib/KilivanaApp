package com.example.farmapp.marketplace

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.buyer.*
import com.example.farmapp.network.TokenManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List

@Composable internal fun BuyerShell(modifier: Modifier = Modifier) {
    val session by TokenManager.session.collectAsStateWithLifecycle()
    if (session.auth?.role != "BUYER" || session.loggingOut || session.isDemo) return
    val model: BuyerShoppingModel = viewModel(key = "buyer-shopping-${session.generation}", factory = BuyerShoppingModel.factory(LocalContext.current))
    val state by model.shopping.state.collectAsStateWithLifecycle()
    val addresses by model.addresses.state.collectAsStateWithLifecycle()
    val checkout by model.checkout.state.collectAsStateWithLifecycle()
    val pickups by model.pickups.state.collectAsStateWithLifecycle()
    var deliveryMethod by rememberSaveable(session.generation) { mutableStateOf(BuyerDeliveryMethod.PICKUP) }
    var destination by rememberSaveable(session.generation) { mutableStateOf(BuyerDestination.HOME) }
    fun navigate(target: BuyerDestination) { model.shopping.closeDetail(); destination = target }
    BackHandler(destination != BuyerDestination.HOME && state.selectedId == null && destination !in setOf(BuyerDestination.ORDERS, BuyerDestination.PROFILE, BuyerDestination.DELIVERY)) { navigate(BuyerDestination.HOME) }
    LaunchedEffect(destination) { if (destination == BuyerDestination.DELIVERY || destination == BuyerDestination.ADDRESSES && !addresses.loaded) model.addresses.refresh()
        if (destination == BuyerDestination.DELIVERY) model.pickups.refresh() }
    val actions = BuyerActions(::navigate, { model.shopping.refresh() }, { query, category -> model.shopping.refresh(query, category) },
        { model.shopping.refresh(more = true) }, model.shopping::open, model.shopping::closeDetail, model.shopping::toggleSaved,
        model.shopping::add, model.shopping::setQuantity, model.shopping::remove, model.shopping::refreshSelections,
        model.addresses::refresh, model.shopping::dismissNotice, { navigate(BuyerDestination.DELIVERY) })
    Column(modifier.fillMaxSize()) {
        if (destination == BuyerDestination.CART) BuyerCartDiagnosticEntry(state)
        Box(Modifier.weight(1f)) {
            when (destination) {
                BuyerDestination.DELIVERY -> BuyerDeliveryFlow(state, addresses, checkout, deliveryMethod,
                    { deliveryMethod = it }, model.addresses::select, model.addresses::create, model.addresses::refresh,
                    model.checkout::check, { navigate(BuyerDestination.CART) }, pickups = pickups,
                    pickupActions = BuyerPickupActions(model.pickups::refresh, model.pickups::select, model.pickups::save, model.pickups::remove, model.pickups::setDefault), onOrders = { navigate(BuyerDestination.ORDERS) })
                BuyerDestination.ORDERS -> com.example.farmapp.orders.BuyerOrdersScreen(Modifier.fillMaxSize(), onBack = { navigate(BuyerDestination.HOME) })
                BuyerDestination.PROFILE -> com.example.farmapp.profile.AccountProfilePage { navigate(BuyerDestination.ACCOUNT) }
                else -> BuyerShoppingContent(destination, state, addresses, session.auth!!.fullName, actions)
            }
        }
        NavigationBar(containerColor = if (destination == BuyerDestination.ORDERS) androidx.compose.ui.graphics.Color.White else NavigationBarDefaults.containerColor) {
            buyerTabs.forEach { tab ->
                NavigationBarItem(colors = if (destination == BuyerDestination.ORDERS) NavigationBarItemDefaults.colors(
                    selectedIconColor = com.example.farmapp.orders.OrderGreen, selectedTextColor = com.example.farmapp.orders.OrderGreen,
                    indicatorColor = com.example.farmapp.orders.OrderPaleGreen) else NavigationBarItemDefaults.colors(), selected = destination == tab || tab == BuyerDestination.CART && destination == BuyerDestination.DELIVERY || tab == BuyerDestination.ACCOUNT && destination in setOf(BuyerDestination.PROFILE, BuyerDestination.ADDRESSES, BuyerDestination.SAVED),
                    onClick = { navigate(tab) }, icon = { Icon(when(tab) {
                        BuyerDestination.HOME -> Icons.Default.Home
                        BuyerDestination.SHOP -> Icons.Default.Storefront
                        BuyerDestination.CART -> Icons.Default.ShoppingCart
                        BuyerDestination.ORDERS -> Icons.AutoMirrored.Filled.List
                        else -> Icons.Default.Person
                    }, contentDescription = null) }, label = { Text(if (tab == BuyerDestination.CART) "Cart (${state.selection.cart.values.sumOf { it.toLong() }})" else tab.label) })
            }
        }
    }
}
