package com.example.farmapp.network

import com.example.farmapp.data.model.SignupRequest
import com.example.farmapp.repository.AuthFailure
import java.util.Locale

object SignupValidation {
    fun prepare(input: SignupRequest): SignupRequest {
        if (input.role !in AuthValidation.supportedRoles) throw AuthFailure("Please select an account type.")
        val username = input.username.trim().lowercase(Locale.ROOT)
        if (!(input.role == "SUPPLIER" && username.isEmpty()) && !username.matches(Regex("[a-z][a-z0-9_]{2,29}")))
            throw AuthFailure("Username must be 3 to 30 characters, start with a letter, and use only letters, digits, or underscores.")
        if (input.role != "SUPPLIER" && input.region.isBlank()) throw AuthFailure("Please enter your region.")
        if (input.role == "SUPPLIER" && KenyanCounties.normalize(input.region) == null)
            throw AuthFailure("Select a supported Kenyan county/region.")
        if (input.fullName.isBlank()) throw AuthFailure("Please enter your full name.")
        if (input.role in setOf("BUYER", "SUPPLIER") && input.fullName.trim().length !in 2..100) throw AuthFailure("Name must contain 2 to 100 characters.")
        if (input.email.isBlank() || !input.email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")))
            throw AuthFailure("Please enter a valid email address.")
        if (input.phoneNumber.isBlank()) throw AuthFailure("Please enter your phone number.")
        if (input.password.length !in 8..72) throw AuthFailure("Password must contain 8 to 72 characters.")
        if (input.role == "FARMER" && input.farmName.isNullOrBlank())
            throw AuthFailure("Please enter your farm name.")
        if (input.role == "SUPPLIER" && input.businessName.isNullOrBlank())
            throw AuthFailure("Please enter your business name.")
        if (input.role == "SUPPLIER" && input.businessLocation.isNullOrBlank())
            throw AuthFailure("Please enter your physical business location.")
        if (input.role == "INSPECTOR" && (input.employeeCode.isNullOrBlank() || input.employeeCode.length > 100))
            throw AuthFailure("Employee code is required and must be at most 100 characters.")
        return input.copy(username = username, region = if (input.role == "SUPPLIER") KenyanCounties.normalize(input.region)!! else input.region.trim(), email = input.email.trim(), fullName = input.fullName.trim(), phoneNumber = input.phoneNumber.trim(),
            farmName = input.farmName?.trim().takeIf { input.role == "FARMER" },
            businessName = input.businessName?.trim().takeIf { input.role == "SUPPLIER" },
            businessLocation = input.businessLocation?.trim().takeIf { input.role == "SUPPLIER" },
            employeeCode = input.employeeCode?.trim().takeIf { input.role == "INSPECTOR" })
    }

    fun prepareSupplierRecovery(input: SignupRequest): SignupRequest {
        // Recovery authenticates an existing account; it must never require or resubmit account creation fields.
        if (input.role != "SUPPLIER") throw AuthFailure("Select Supplier to finish business setup.")
        if (!input.email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) throw AuthFailure("Please enter a valid email address.")
        if (input.password.isBlank()) throw AuthFailure("Please enter your existing account password.")
        if (input.businessName.isNullOrBlank()) throw AuthFailure("Please enter your business name.")
        if (input.businessLocation.isNullOrBlank()) throw AuthFailure("Please enter your physical business location.")
        return input.copy(email = input.email.trim(), businessName = input.businessName.trim(), businessLocation = input.businessLocation.trim())
    }
}
