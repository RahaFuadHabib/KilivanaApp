package com.example.farmapp.payments

import java.util.Locale

internal const val MPESA_ENVIRONMENT_NOTICE = "M-Pesa submission is disabled until the payment service environment is confirmed. Your order can still be reviewed and its status refreshed."

/** Same accepted Kenyan mobile formats; no provider credentials or network calls. */
internal fun mpesaPhone(value: String): String {
    val text = value.trim()
    require(Regex("[+0-9 ()-]+").matches(text))
    val digits = text.filter { it in '0'..'9' }
    val phone = if (digits.startsWith("0")) "254" + digits.drop(1) else digits
    require(Regex("254[71][0-9]{8}").matches(phone))
    return phone
}

internal fun paymentOutcome(status: String?): String = when (status?.lowercase(Locale.ROOT)) {
    "paid", "held", "settled" -> "confirmed"
    "pending" -> "pending"
    "failed" -> "failed"
    "refunded" -> "refunded"
    else -> "unverified"
}

/** Exact configured origin, without inferring readiness from reachability or URL similarity. */
internal fun verifiedMpesaEnvironment(environment: String, confirmedUrl: String, activeUrl: String) =
    environment in setOf("SANDBOX", "PRODUCTION") && confirmedUrl.isNotBlank() && confirmedUrl == activeUrl
