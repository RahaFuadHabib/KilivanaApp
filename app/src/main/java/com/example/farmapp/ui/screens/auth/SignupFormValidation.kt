package com.example.farmapp.ui.screens.auth

import com.example.farmapp.data.model.SignupRequest
import com.example.farmapp.network.KenyanCounties

internal enum class SignupField { ROLE, NAME, EMAIL, PHONE, USERNAME, COUNTY, BUSINESS_NAME, TOWN, ADDRESS, PASSWORD, CONFIRM_PASSWORD }

private fun locationPart(value: String) = value.trim().replace(Regex("\\s+"), " ")

/** Town/address are presentation inputs. Only their combined value enters the existing profile location field. */
internal fun supplierProfileLocation(town: String, address: String) = "${locationPart(town)}, ${locationPart(address)}"

internal fun signupFormErrors(request: SignupRequest, town: String, address: String, confirmation: String,
    recovering: Boolean): Map<SignupField, String> = buildMap {
    val supplier = request.role == "SUPPLIER"
    if (request.role !in setOf("FARMER", "BUYER", "SUPPLIER")) put(SignupField.ROLE, "Select Farmer or Supplier. Inspector signup is unavailable.")
    if (!recovering) {
        if (request.fullName.isBlank() || (supplier && request.fullName.trim().length !in 2..100))
            put(SignupField.NAME, if (supplier) "Enter your full name (2 to 100 characters)." else "Enter your full name.")
        if (request.phoneNumber.isBlank()) put(SignupField.PHONE, "Enter your phone number.")
        val username = request.username.trim()
        if (!(supplier && username.isEmpty()) && !username.matches(Regex("[a-zA-Z][a-zA-Z0-9_]{2,29}")))
            put(SignupField.USERNAME, "Use 3 to 30 letters, digits or underscores, starting with a letter.")
    }
    if (!request.email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) put(SignupField.EMAIL, "Enter a valid email address.")
    if (supplier) {
        if (KenyanCounties.normalize(request.region) == null) put(SignupField.COUNTY, "Select a supported Kenyan county/region.")
        if (request.businessName.isNullOrBlank()) put(SignupField.BUSINESS_NAME, "Enter your business name.")
        if (town.isBlank()) put(SignupField.TOWN, "Enter your town or sub-county, for example Ruiru.")
        val detail = locationPart(address)
        if (detail.length < 6 || KenyanCounties.normalize(detail) != null || detail.equals(locationPart(town), true))
            put(SignupField.ADDRESS, "Add a detailed address, for example Shop 4, Market Road, near the bus stage. County or town alone is not enough.")
    } else if (request.region.isBlank()) put(SignupField.COUNTY, "Enter your region.")
    if (recovering) {
        if (request.password.isBlank()) put(SignupField.PASSWORD, "Enter your existing account password.")
    } else if (request.password.length !in 8..72) put(SignupField.PASSWORD, "Use 8 to 72 characters.")
    if (confirmation.isEmpty()) put(SignupField.CONFIRM_PASSWORD, "Confirm your password.")
    else if (request.password != confirmation) put(SignupField.CONFIRM_PASSWORD, "Passwords do not match.")
}
