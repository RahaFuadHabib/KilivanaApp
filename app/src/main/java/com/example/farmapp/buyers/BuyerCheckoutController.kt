package com.example.farmapp.buyers

import android.content.Context
import androidx.lifecycle.*
import com.example.farmapp.buyers.data.*
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.*
import com.example.farmapp.orders.*
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.time.Instant

// Existing Kotlin callers keep their names; persisted fields and phase values do not change.
internal typealias CheckoutJournal = com.example.farmapp.checkout.CheckoutJournal
internal typealias CheckoutJournalStore = com.example.farmapp.checkout.CheckoutJournalStore
internal typealias BrianCheckoutState = com.example.farmapp.checkout.CheckoutState<BuyerOrderDto>
internal fun verifiedCheckoutOrderId(data:JsonElement):Long {
    // The deployed schema is ApiResponse<Long>. Reject invented wrapper fallbacks.
    check(data.isJsonPrimitive && data.asJsonPrimitive.isNumber){"Order response did not contain the documented numeric ID."}
    return data.asBigDecimal.longValueExact().also{check(it>0)}
}
internal fun verifyPurchasedOrder(row:BuyerOrderDto,buyer:Long,items:Map<Long,Int>,quote:DeliveryQuote):BuyerOrderDto {
    validateBuyerOrder(row,buyer)
    val lines=row.items!!
    check(lines.map{it.productId}.distinct().size==lines.size && lines.associate{it.productId!! to it.quantity!!}==items){"The created order does not match your intended products and quantities. Do not pay; contact support."}
    check(row.total!!.compareTo(quote.total)==0 && row.subtotal?.compareTo(quote.subtotal)==0 && row.deliveryFee?.compareTo(quote.deliveryFee)==0){"The order amount differs from the reviewed quote. Do not pay; refresh Orders."}
    val locationMatches = when (quote.deliveryMethod) {
        "DOOR_DELIVERY" -> row.addressId==quote.addressId && row.pickupStationId==null && row.pickupLocation==null
        "PICKUP_STATION" -> row.addressId==null && if(quote.pickupStationId!=null)
            row.pickupStationId==quote.pickupStationId
        else row.pickupStationId==null && row.pickupLocation==quote.pickupLocation
        else -> false
    }
    check(locationMatches){"The order delivery location differs from your selection. Do not pay; contact support."}
    check(lines.all{it.subtotal==null || it.subtotal.signum()>=0} && lines.fold(BigDecimal.ZERO){sum,line->sum+(line.subtotal ?: line.unitPrice!!.multiply(line.quantity!!.toBigDecimal()))}.compareTo(row.subtotal)==0){"Order line amounts could not be verified. Do not pay; contact support."}
    return row
}
internal class BrianCheckoutController(api:BrianCheckoutApi,orders:BuyerOrdersApi,sessions:SessionStore,
    expected:SessionSnapshot,disk:CheckoutJournalStore,scope:CoroutineScope,marketplace:com.example.farmapp.marketplace.MarketplaceRepository?=null,now:()->Instant={Instant.now()}) {
    private val delegate=com.example.farmapp.checkout.CheckoutController(
        BuyerCheckoutRepository(api,orders,sessions,expected,marketplace),BuyerCheckoutContract,sessions,expected,
        com.example.farmapp.checkout.BuyerPurchaserPolicy,disk,scope,now)
    val state=delegate.state
    fun matchesCart(items:Map<Long,Int>)=delegate.matchesCart(items)
    fun prepare(items:Map<Long,Int>,subtotal:BigDecimal,request:DeliveryQuoteRequest)=delegate.prepare(items,subtotal,request)
    fun create(items:Map<Long,Int>,quoteId:String,phone:String)=delegate.create(items,quoteId,phone)
    fun startNewBasket()=delegate.startNewBasket()
    fun recover()=delegate.recover()
}
internal class BrianCheckoutModel(context:Context):ViewModel(){
    private val expected=TokenManager.session.value
    val controller=BrianCheckoutController(ApiClient.authenticatedService(BrianCheckoutApi::class.java),ApiClient.buyerOrdersApi,TokenManager,expected,CheckoutPreferences(context,expected.auth!!.userId),viewModelScope,marketplace=com.example.farmapp.marketplace.MarketplaceRepository(ApiClient.marketplaceApi,TokenManager))
    companion object{fun factory(context:Context)=object:ViewModelProvider.Factory{override fun<T:ViewModel>create(modelClass:Class<T>):T{@Suppress("UNCHECKED_CAST")return BrianCheckoutModel(context.applicationContext) as T}}}
}
