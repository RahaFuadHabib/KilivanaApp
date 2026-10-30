package com.example.farmapp.buyers

/** Debug probe contains only static route enums, never account data, identifiers or API responses. */
internal enum class BrianScreen { HOME,PRODUCTS,PRODUCT,SEARCH,CART,SAVED,CHECKOUT,ORDERS,ORDER,PAYMENT,ACCOUNT,ADDRESSES,PROFILE,PICKUP }
internal object BrianUiPresence {
    @Volatile private var screen:BrianScreen?=null
    fun enter(value:BrianScreen){if(com.example.farmapp.BuildConfig.DEBUG)screen=value}
    fun leave(){screen=null}
    fun report()="Buyer implementation: Brian master 8f335e2\nIntegrated Buyer active: ${screen!=null}\nScreen: ${screen?.name ?: "NONE"}"
}
