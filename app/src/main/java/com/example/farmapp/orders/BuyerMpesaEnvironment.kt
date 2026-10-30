package com.example.farmapp.orders

import com.example.farmapp.BuildConfig

internal const val MPESA_ENVIRONMENT_NOTICE = "M-Pesa submission is disabled until the payment service environment is confirmed. Your order can still be reviewed and its status refreshed."
internal fun verifiedMpesaEnvironment(environment:String,confirmedUrl:String,activeUrl:String) =
    environment in setOf("SANDBOX","PRODUCTION") && confirmedUrl.isNotBlank() && confirmedUrl==activeUrl
internal object BuyerMpesaEnvironment {
    fun ready()=verifiedMpesaEnvironment(BuildConfig.BUYER_MPESA_ENVIRONMENT,BuildConfig.BUYER_MPESA_CONFIRMED_API_BASE_URL,BuildConfig.API_BASE_URL)
}
