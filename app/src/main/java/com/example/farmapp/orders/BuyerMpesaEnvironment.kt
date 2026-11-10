package com.example.farmapp.orders

import com.example.farmapp.BuildConfig

internal const val MPESA_ENVIRONMENT_NOTICE = com.example.farmapp.payments.MPESA_ENVIRONMENT_NOTICE
internal fun verifiedMpesaEnvironment(environment:String,confirmedUrl:String,activeUrl:String) =
    com.example.farmapp.payments.verifiedMpesaEnvironment(environment,confirmedUrl,activeUrl)
internal object BuyerMpesaEnvironment {
    fun ready()=verifiedMpesaEnvironment(BuildConfig.BUYER_MPESA_ENVIRONMENT,BuildConfig.BUYER_MPESA_CONFIRMED_API_BASE_URL,BuildConfig.API_BASE_URL)
}
