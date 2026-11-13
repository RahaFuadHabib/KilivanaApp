package com.example.farmapp.buyers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.farmapp.buyers.ui.theme.*
import com.example.farmapp.buyers.ui.screens.*
import com.example.farmapp.buyers.model.*
import com.example.farmapp.buyers.data.CartViewModel
import com.example.farmapp.buyer.*
import com.example.farmapp.network.TokenManager
import com.example.farmapp.orders.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Brian's Buyer NavHost/scaffold and actual screens. FarmApp owns transport/session/cart/payment state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun BrianBuyerApp(modifier:Modifier=Modifier,onLogout:()->Unit={}) {
    val session by TokenManager.session.collectAsStateWithLifecycle()
    if(session.auth?.role!="BUYER" || session.loggingOut || session.isDemo)return
    val model:BuyerShoppingModel=viewModel(key="buyer-shopping-${session.generation}",factory=BuyerShoppingModel.factory(LocalContext.current))
    val ordersModel:BuyerOrdersModel=viewModel(key="buyer-orders-${session.generation}")
    val state by model.shopping.state.collectAsStateWithLifecycle()
    val addresses by model.addresses.state.collectAsStateWithLifecycle()

    val pickups by model.pickups.state.collectAsStateWithLifecycle()
    val ordersState by ordersModel.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val checkoutModel:BrianCheckoutModel=viewModel(key="brian-checkout-${session.generation}",factory=BrianCheckoutModel.factory(context))
    val checkoutState by checkoutModel.controller.state.collectAsStateWithLifecycle()
    val addressRepo=remember(session.generation){BrianAddressRepository(context,TokenManager,session)}
    val nav=rememberNavController();val entry by nav.currentBackStackEntryAsState();val route=entry?.destination?.route ?: "home"
    val selectedTabRoute=if(route.startsWith("products"))"products" else route
    val tabs=listOf("home","products","cart","orders","dashboard")
    val snackbar=remember{SnackbarHostState()};val scope=rememberCoroutineScope()
    val products=state.products.map{brianProduct(it,state.categories)}
    val categories=state.categories.mapNotNull{it.name}.distinct()
    val known=state.known.mapValues{brianProduct(it.value,state.categories)}
    val items=state.selection.cart.mapNotNull{(id,q)->known[id]?.let{CartItem(it,q)}}
    val orders=ordersState.rows.map{brianOrder(it)}
    var query by rememberSaveable{mutableStateOf("")};var filter by rememberSaveable{mutableStateOf<String?>(null)}
    var deliveryMethod by rememberSaveable{mutableStateOf("DOOR_DELIVERY")};var pickupText by rememberSaveable{mutableStateOf("")};var pickupStationId by rememberSaveable{mutableStateOf<Long?>(null)};var pickupChoice by rememberSaveable{mutableStateOf(com.example.farmapp.buyers.ui.screens.PickupChoice.STATION)}
    val cart=CartViewModel(buyerCartSubtotal(state),{id->state.selection.cart[id.toLong()]?.let{model.shopping.setQuantity(id.toLong(),it+1)}},
        {id->state.selection.cart[id.toLong()]?.takeIf{it>1}?.let{model.shopping.setQuantity(id.toLong(),it-1)}},{id->model.shopping.remove(id.toLong())})
    fun add(product:Product):Boolean { val id=product.id.toLongOrNull() ?: return false; val before=model.shopping.state.value.selection.cart[id] ?: 0; state.known[id]?.let{model.shopping.add(it,1)}; return (model.shopping.state.value.selection.cart[id] ?: 0)>before }
    fun go(target:String){nav.navigate(target){launchSingleTop=true}}
    LaunchedEffect(query,filter){delay(350);snapshotFlow{state.busy}.first{!it};val id=filter?.let{name->state.categories.singleOrNull{it.name==name}?.id};if(query!=state.query || id!=state.category)model.shopping.refresh(query,id)}
    LaunchedEffect(route){
        if(route in setOf("cart","savedItems"))model.shopping.refreshSelections()
        if(route in setOf("checkout","addresses"))model.addresses.refresh()
        if(route=="pickup")model.pickups.refresh()
        BrianUiPresence.enter(when{route=="home"->BrianScreen.HOME;route.startsWith("products")->BrianScreen.PRODUCTS;route.startsWith("product/")->BrianScreen.PRODUCT;route=="cart"->BrianScreen.CART;route=="search"->BrianScreen.SEARCH;route=="savedItems"->BrianScreen.SAVED;route=="orders"->BrianScreen.ORDERS;route.startsWith("order/")->BrianScreen.ORDER;route.startsWith("payment/")->BrianScreen.PAYMENT;route=="addresses"->BrianScreen.ADDRESSES;route=="checkout"->BrianScreen.CHECKOUT;route=="pickup"->BrianScreen.PICKUP;route=="profile"->BrianScreen.PROFILE;else->BrianScreen.ACCOUNT})
    }
    LaunchedEffect(route, checkoutState.uncertain, checkoutState.needsRecovery){
        if(route=="checkout" && (checkoutState.uncertain || checkoutState.needsRecovery) && !checkoutState.busy) checkoutModel.controller.recover()
    }
    DisposableEffect(session.generation){onDispose{BrianUiPresence.leave()}}
    LaunchedEffect(checkoutState.order?.id){checkoutState.order?.id?.let{ordersModel.refresh();go("payment/$it")}}
    LaunchedEffect(state.notice){state.notice?.let{snackbar.showSnackbar(it);model.shopping.dismissNotice()}}
    KilivanabuyerTheme {
        Scaffold(modifier.fillMaxSize().testTag("brian-buyer-app"),containerColor=NeutralBackground,snackbarHost={SnackbarHost(snackbar)},bottomBar={
            if(selectedTabRoute in tabs)NavigationBar(containerColor=NeutralSurface){
                listOf(Triple("home","Home",Icons.Default.Home),Triple("products","Products",Icons.Default.Storefront),Triple("cart","Cart",Icons.Default.ShoppingCart),Triple("orders","Orders",Icons.AutoMirrored.Filled.ReceiptLong),Triple("dashboard","Account",Icons.Default.Person)).forEach{(destination,label,icon)->
                    NavigationBarItem(selected=selectedTabRoute==destination,onClick={nav.navigate(destination){popUpTo(nav.graph.startDestinationId){saveState=true};launchSingleTop=true;restoreState=true}},icon={BadgedBox(badge={if(destination=="cart" && state.selection.cart.isNotEmpty())Badge{Text(state.selection.cart.values.sumOf{it.toLong()}.toString())}}){Icon(icon,label)}},label={Text(label)})
                }
            }
        }){inset->
            NavHost(nav,startDestination="home",modifier=Modifier.padding(inset)){
                composable("home"){
                    PullToRefreshBox(isRefreshing=state.busy,onRefresh={model.shopping.refresh()}){
                        Column{state.error?.let{Text(it,color=ErrorRed)};HomeScreen(state.selection.cart.values.sum(),products,categories,{go("search")},{filter=it;go("products")},{go("products")},{go("product/${it.id}")},{go("cart")},{add(it)})}
                    }
                }
                composable("products"){
                    PullToRefreshBox(isRefreshing=state.busy,onRefresh={model.shopping.refresh(query,state.category)}){
                        Column{state.error?.let{Text(it,color=ErrorRed)};ProductsScreen(products,filter,{go("product/${it.id}")},{add(it)},{nav.popBackStack()},categories,
                            onFilters={q,c->query=q;filter=c},hasMore=!state.last,loading=state.busy,more={model.shopping.refresh(more=true)})}
                    }
                }
                composable("search"){SearchScreen(products,{go("product/${it.id}")},{nav.popBackStack()},onSearch={query=it;filter=null},hasMore=!state.last,loading=state.busy,more={model.shopping.refresh(more=true)})}
                composable("product/{id}",arguments=listOf(navArgument("id"){type=NavType.LongType})){e->
                    val id=e.arguments!!.getLong("id");LaunchedEffect(id){model.shopping.open(id)}
                    val product=(state.detail?.takeIf{it.productId==id.toString()}?.let{brianProduct(it,state.categories)} ?: known[id])
                    if(product==null)Column(Modifier.padding(24.dp)){Text(state.detailError ?: "Loading product...");TextButton(onClick={nav.popBackStack()}){Text("Back")}}
                    else ProductDetailScreen(product,id in state.selection.saved,{nav.popBackStack()},{model.shopping.toggleSaved(id)},{add(product)},{if(add(product))go("checkout")})
                }
                composable("cart"){
                    Column{BuyerCartDiagnosticEntry(state)
                        if(items.size!=state.selection.cart.size)Text("Some cart products could not be verified. Refresh availability before checkout.",Modifier.padding(12.dp),color=ErrorRed)
                        CartScreen(items,cart,{if(state.selection.cart.isEmpty())go("products") else if(buyerCartSubtotal(state)!=null)go("checkout") else model.shopping.refreshSelections()})}
                }
                composable("savedItems"){SavedItemsScreen(state.selection.saved.mapNotNull{known[it]},{go("product/${it.id}")},{add(it)},{nav.popBackStack()},{go("products")})}
                composable("dashboard"){AccountScreen(true,session.auth?.fullName,session.auth?.email,session.auth?.phoneNumber,orders,{},{onLogout()},{},{go("orders")},{go("savedItems")},{go("addresses")},{go("profile")})}
                composable("profile"){com.example.farmapp.profile.AccountProfilePage{nav.popBackStack()}}
                composable("addresses"){
                    Column{addresses.error?.let{Text(it,color=ErrorRed)};TextButton(onClick={model.addresses.refresh()}){Text("Refresh addresses")}
                        AddressBookScreen(addresses.rows.map(::brianAddress),session.auth?.userId,addresses.busy,
                            save={id,_,label,text,lat,lon->addressRepo.save(id,label,text,lat,lon).also{model.addresses.refresh()}},
                            delete={id->addressRepo.remove(id).also{model.addresses.refresh()}},back={nav.popBackStack()})}
                }
                composable("checkout") {
                    var deliverySelectionError by remember { mutableStateOf<String?>(null) }
                    var prepareRequest by remember { mutableStateOf<com.example.farmapp.buyers.data.DeliveryQuoteRequest?>(null) }
                    var confirmOrder by remember { mutableStateOf<Pair<String,String>?>(null) }
                    val quote=checkoutState.quote?.takeIf{checkoutModel.controller.matchesCart(state.selection.cart) && it.deliveryMethod==deliveryMethod && if(deliveryMethod=="DOOR_DELIVERY")it.addressId==addresses.selectedId else if(pickupChoice==com.example.farmapp.buyers.ui.screens.PickupChoice.STATION)it.pickupStationId==pickupStationId else it.pickupLocation==pickupText.trim().takeIf{it.isNotBlank()}}
                    fun requestPreparation(addressId:Long?=addresses.selectedId) {
                        deliverySelectionError=null
                        if(checkoutState.uncertain){checkoutModel.controller.recover();return}
                        if(state.selection.cart.isEmpty() || buyerCartSubtotal(state)==null || items.size!=state.selection.cart.size)return
                        if(deliveryMethod=="DOOR_DELIVERY") {
                            if(addresses.busy || addresses.error!=null || addresses.rows.none{it.id==addressId})return
                            deliverySelectionError=buyerDoorDeliveryIssue(addresses.rows.firstOrNull { it.id==addressId })
                            if(deliverySelectionError!=null)return
                            prepareRequest=com.example.farmapp.buyers.data.DeliveryQuoteRequest("DOOR_DELIVERY",addressId=addressId)
                        } else if(pickupChoice==com.example.farmapp.buyers.ui.screens.PickupChoice.STATION && pickupStationId!=null) {
                            prepareRequest=com.example.farmapp.buyers.data.DeliveryQuoteRequest("PICKUP_STATION",pickupStationId=pickupStationId)
                        } else if(pickupChoice==com.example.farmapp.buyers.ui.screens.PickupChoice.TYPED && pickupText.trim().length in 1..500) {
                            prepareRequest=com.example.farmapp.buyers.data.DeliveryQuoteRequest("PICKUP_STATION",pickupLocation=pickupText.trim())
                        }
                    }
                    Column {
                        TextButton(onClick=checkoutModel.controller::recover,enabled=!checkoutState.busy){Text("Check checkout status")}
                        if(checkoutState.uncertain && !checkoutState.busy && checkoutState.order==null)
                            TextButton(onClick=checkoutModel.controller::resetJournal){Text("Reset checkout")}
                        if(checkoutState.order!=null) {
                            Text("Your order has been created and verified.",Modifier.padding(16.dp))
                            Button(onClick={go("payment/${checkoutState.order!!.id}")}){Text("Continue to payment review")}
                            TextButton(onClick={go("orders")}){Text("View orders")}
                            TextButton(onClick=checkoutModel.controller::startNewBasket,enabled=!checkoutState.busy){Text("Start another checkout after this order is finished")}
                            checkoutState.notice?.let{Text(it,Modifier.padding(16.dp))}
                        } else CheckoutScreen(items,addresses.rows.map(::brianAddress),addresses.busy,addresses.error,quote,{},
                            requestQuote={requestPreparation(it)},manageAddresses={go("addresses")},
                            place={id,_,phone->confirmOrder=id to phone},back={nav.popBackStack()},submitting=checkoutState.busy,
                            initialAddressId=addresses.selectedId,onAddressSelected=model.addresses::select,feedback=deliverySelectionError ?: checkoutState.notice,
                            deliveryMethodContent={Column{
                                TabRow(selectedTabIndex=if(deliveryMethod=="DOOR_DELIVERY")0 else 1){
                                    Tab(deliveryMethod=="DOOR_DELIVERY",{deliveryMethod="DOOR_DELIVERY";pickupStationId=null;pickupText=""},text={Text("Door Delivery")})
                                    Tab(deliveryMethod=="PICKUP_STATION",{deliveryMethod="PICKUP_STATION"},text={Text("Pickup Station")})
                                }
                                if(deliveryMethod=="PICKUP_STATION") {
                                    // Sub-choice: Station or Typed
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                        FilterChip(pickupChoice==com.example.farmapp.buyers.ui.screens.PickupChoice.STATION,onClick={pickupChoice=com.example.farmapp.buyers.ui.screens.PickupChoice.STATION},label={Text("Select station")})
                                        FilterChip(pickupChoice==com.example.farmapp.buyers.ui.screens.PickupChoice.TYPED,onClick={pickupChoice=com.example.farmapp.buyers.ui.screens.PickupChoice.TYPED;pickupStationId=null},label={Text("Type location")})
                                    }
                                    if(pickupChoice==com.example.farmapp.buyers.ui.screens.PickupChoice.STATION){
                                        if(pickupStationId!=null && pickupText.isNotBlank()) Text("Selected: $pickupText",color=MaterialTheme.colorScheme.primary)
                                        else Text("No station selected.",color=MaterialTheme.colorScheme.error)
                                        Button(onClick={go("pickup")},modifier=Modifier.fillMaxWidth()){Text(if(pickupStationId!=null)"Change pickup station" else "Select pickup station")}
                                        Button(onClick={requestPreparation()},enabled=pickupStationId!=null && !checkoutState.busy,modifier=Modifier.fillMaxWidth()){Text("Calculate delivery")}
                                    } else {
                                        OutlinedTextField(pickupText,{pickupText=it.take(500)},label={Text("Pickup location (max 500 chars)")},modifier=Modifier.fillMaxWidth(),minLines=2)
                                        Text("${pickupText.length}/500",style=MaterialTheme.typography.bodySmall,color=TextSecondary)
                                        Button(onClick={requestPreparation()},enabled=pickupText.trim().length in 1..500 && !checkoutState.busy,modifier=Modifier.fillMaxWidth()){Text("Calculate delivery")}
                                    }
                                }
                                if(checkoutState.uncertain)TextButton(onClick={go("orders")}){Text("Inspect existing orders")}
                            }},deliveryContent=if(deliveryMethod=="PICKUP_STATION")({if(pickupChoice==com.example.farmapp.buyers.ui.screens.PickupChoice.STATION && pickupStationId!=null)Text("Pickup station: $pickupText") else if(pickupChoice==com.example.farmapp.buyers.ui.screens.PickupChoice.TYPED && pickupText.isNotBlank())Text("Pickup location: ${pickupText.trim()}") else Text("No pickup location set.",color=MaterialTheme.colorScheme.error)})else null)
                    }
                    prepareRequest?.let { request ->
                        AlertDialog(onDismissRequest={prepareRequest=null},title={Text("Prepare your checkout?")},
                            text={Text("Upload your selected products and quantities to your server cart, then request a delivery quote. Existing quantities for these products will be set to your selection. No order or payment is submitted at this step.")},
                            confirmButton={TextButton(onClick={prepareRequest=null;buyerCartSubtotal(model.shopping.state.value)?.let{checkoutModel.controller.prepare(model.shopping.state.value.selection.cart.toMap(),it,request)}}){Text("Upload cart and calculate")}},
                            dismissButton={TextButton(onClick={prepareRequest=null}){Text("Cancel")}})
                    }
                    confirmOrder?.let { (id,phone) ->
                        AlertDialog(onDismissRequest={confirmOrder=null},title={Text("Create this order?")},
                            text={Text("Server-confirmed total: ${com.example.farmapp.buyers.model.formatKes(quote?.total)}. M-Pesa number: ${runCatching{buyerMpesaPhone(phone)}.getOrDefault("")}. Payment will require a separate confirmation after the order is verified.")},
                            confirmButton={TextButton(onClick={confirmOrder=null;checkoutModel.controller.create(model.shopping.state.value.selection.cart.toMap(),id,phone)}){Text("Confirm order")}},
                            dismissButton={TextButton(onClick={confirmOrder=null}){Text("Cancel")}})
                    }
                }
                composable("pickup"){
                    val actions=BuyerPickupActions(model.pickups::refresh,
                        select={ id ->
                            model.pickups.select(id)
                            val station=pickups.allStations.firstOrNull{it.id==id} ?: pickups.stations.firstOrNull{it.id==id}
                            val savedRow=pickups.saved.firstOrNull{it.pickupStationId==id}
                            val name=station?.name ?: savedRow?.pickupStationName
                            val address=station?.address ?: savedRow?.pickupStationAddress
                            if(name!=null){
                                pickupStationId=id
                                pickupText=listOfNotNull(name,address).joinToString(", ")
                                deliveryMethod="PICKUP_STATION"
                                nav.popBackStack()
                            }
                        },
                        save=model.pickups::save,remove=model.pickups::remove,setDefault=model.pickups::setDefault)
                    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        item{TextButton(onClick={nav.popBackStack()}){Text("Back to checkout")};Text("Select Pickup Station",style=MaterialTheme.typography.headlineMedium);Text("Tap a station to select it and return to checkout.",style=MaterialTheme.typography.bodyMedium)}
                        pickupFeedback(pickups,actions);pickupRegionChips(pickups,actions)
                        items(pickups.saved,key={"saved-${it.id}"}){BuyerSavedPickupCard(it,pickups,actions)}
                        items(pickups.stations,key={"station-${it.id}"}){BuyerAvailablePickupCard(it,pickups,actions)}
                    }
                }
                buyerOrderDestinations(
                    ordersContent = {
                        OrdersScreen(orders, { go("order/${it.id}") }, { go("products") }, ordersState.busy, ordersState.error,
                            extraAction = { displayOrder ->
                                ordersState.rows.firstOrNull { it.id?.toString() == displayOrder.id }?.let { original ->
                                    BuyerPaymentSession(original) { payment, _, _ ->
                                        if (payment.canInitiate && !payment.busy) Button(onClick = { go("payment/${displayOrder.id}") }) { Text("Pay with M-Pesa") }
                                    }
                                }
                            })
                    },
                    orderContent = { id ->
                        val original=ordersState.rows.firstOrNull{it.id==id}
                        LaunchedEffect(id,original){original?.let(ordersModel::open)}
                        val row=ordersState.selected?.takeIf{it.id==id} ?: original
                        if(row==null)Text(ordersState.error ?: "Order unavailable. Refresh Orders.",Modifier.padding(24.dp))
                        else OrderDetailScreen(brianOrder(row,ordersState.events),reorder={row.items.orEmpty().forEach{item->state.known[item.productId]?.let{model.shopping.add(it,item.quantity!!)}};go("cart")},back={nav.popBackStack()},payment={
                            BuyerPaymentSession(row){payment,refresh,_->
                                payment.notice?.let{Text(it,color=TextSecondary)}
                                if(orderPaymentConfirmed(row,payment))Text(if(row.paymentMethod.equals("mpesa",true) || payment.payments.any{it.method.equals("mpesa",true)})"Payment completed via M-Pesa" else "Payment confirmed",color=Green600)
                                if(payment.canInitiate)Button(onClick={go("payment/$id")}){Text("Pay with M-Pesa")}
                                OutlinedButton(onClick={refresh();ordersModel.open(row)}){Text("Refresh payment status")}
                                OutlinedButton(onClick={go("tracking/$id")}){Text("Track delivery")}
                            }
                        })
                    },
                    trackingContent = { id ->
                        val row=ordersState.rows.firstOrNull{it.id==id}
                        if(row==null)Text("Refresh Orders to verify this delivery.")
                        else com.example.farmapp.tracking.DeliveryTrackingScreen(row){nav.popBackStack()}
                    },
                    paymentContent = { id ->
                        val row=checkoutState.order?.takeIf{it.id==id} ?: ordersState.rows.firstOrNull{it.id==id}
                        if(row==null)Text("Refresh Orders to verify this order.")
                        else BuyerPaymentSession(row){payment,refresh,send->BrianPaymentReview(row,payment,{nav.popBackStack()},refresh,send,checkoutState.phone.takeIf{checkoutState.order?.id==row.id}.orEmpty())}
                    }
                )
            }
        }
    }
}

/** Shared registration lets navigation regression tests exercise the production routes without HTTP. */
internal fun NavGraphBuilder.buyerOrderDestinations(
    ordersContent: @Composable () -> Unit,
    orderContent: @Composable (Long) -> Unit,
    trackingContent: @Composable (Long) -> Unit,
    paymentContent: @Composable (Long) -> Unit
) {
    composable("orders") { ordersContent() }
    composable("order/{id}", arguments=listOf(navArgument("id"){type=NavType.LongType})) { entry ->
        orderContent(entry.arguments!!.getLong("id"))
    }
    composable("tracking/{id}", arguments=listOf(navArgument("id"){type=NavType.LongType})) { entry ->
        trackingContent(entry.arguments!!.getLong("id"))
    }
    composable("payment/{id}", arguments=listOf(navArgument("id"){type=NavType.LongType})) { entry ->
        paymentContent(entry.arguments!!.getLong("id"))
    }
}



