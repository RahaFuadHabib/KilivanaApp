package com.example.farmapp.ui.navigation

import com.example.farmapp.data.model.AuthResponse

internal enum class AuthDestination { FARMER, BUYER, SUPPLIER, INSPECTOR, AWAITING_VERIFICATION, UNSUPPORTED }

internal fun authDestination(auth: AuthResponse): AuthDestination = when (auth.role) {
    "FARMER" -> AuthDestination.FARMER
    "BUYER" -> AuthDestination.BUYER
    "SUPPLIER" -> AuthDestination.SUPPLIER
    "INSPECTOR" -> if (auth.verificationStatus == "VERIFIED") AuthDestination.INSPECTOR else AuthDestination.AWAITING_VERIFICATION
    else -> AuthDestination.UNSUPPORTED
}
