package com.example.farmapp.ui.screens.auth

import com.example.farmapp.data.model.SignupRequest
import com.example.farmapp.network.KenyanCounties
import com.example.farmapp.network.SignupValidation
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class SignupFormValidationTest {
    private fun request() = SignupRequest("supplier@example.test", "synthetic-password", "Synthetic Supplier", "+254700000000",
        "SUPPLIER", "", "Kiambu", businessName = "Synthetic shop", businessLocation = supplierProfileLocation("Ruiru", "Shop 4, Market Road"))
    private fun errors(input: SignupRequest = request(), town: String = "Ruiru", address: String = "Shop 4, Market Road",
        confirmation: String = input.password, recovering: Boolean = false) = signupFormErrors(input, town, address, confirmation, recovering)

    @Test fun supportedCountiesAreCompleteUniqueAndNormalizeBackendAliases() {
        assertEquals(47, KenyanCounties.all.size)
        assertEquals(47, KenyanCounties.all.toSet().size)
        assertEquals("Nairobi City", KenyanCounties.normalize(" Nairobi "))
        assertEquals("Murang'a", KenyanCounties.normalize("Muranga"))
        assertNull(KenyanCounties.normalize("Unknown county"))
    }
    @Test fun countySelectionIsRequiredAndUnsupportedValuesAreRejected() {
        for (county in listOf("", " ", "Unknown county")) assertTrue(errors(request().copy(region = county)).containsKey(SignupField.COUNTY))
        assertTrue(errors().isEmpty())
    }
    @Test fun townAndDetailedAddressAreIndependentlyRequired() {
        assertTrue(errors(town = " ").containsKey(SignupField.TOWN))
        assertTrue(errors(address = " ").containsKey(SignupField.ADDRESS))
        assertTrue(errors(address = "Kiambu").containsKey(SignupField.ADDRESS))
        assertTrue(errors(address = "Ruiru").containsKey(SignupField.ADDRESS))
    }
    @Test fun locationUsesTownAndAddressWithoutReplacingAccountCounty() {
        val combined = supplierProfileLocation("  Ruiru \n", " Shop 4,  Market Road ")
        assertEquals("Ruiru, Shop 4, Market Road", combined)
        val prepared = SignupValidation.prepare(request().copy(businessLocation = combined))
        assertEquals("Kiambu", prepared.region)
        assertEquals(combined, prepared.businessLocation)
    }
    @Test fun missingConfirmationAndMismatchHaveSpecificErrors() {
        assertEquals("Confirm your password.", errors(confirmation = "")[SignupField.CONFIRM_PASSWORD])
        assertEquals("Passwords do not match.", errors(confirmation = "different-password")[SignupField.CONFIRM_PASSWORD])
    }
    @Test fun passwordsMustMatchExactlyWithoutTrimming() {
        val input = request().copy(password = " password ")
        assertTrue(errors(input, confirmation = "password").containsKey(SignupField.CONFIRM_PASSWORD))
        assertTrue(errors(input, confirmation = " password ").isEmpty())
        assertEquals(" password ", SignupValidation.prepare(input).password)
    }
    @Test fun recoveryKeepsExistingPasswordRulesAndStillConfirmsInput() {
        val input = request().copy(fullName = "", phoneNumber = "", username = "", password = "older6")
        assertTrue(errors(input, recovering = true).isEmpty())
        assertTrue(errors(input, confirmation = "wrong", recovering = true).containsKey(SignupField.CONFIRM_PASSWORD))
        assertEquals("older6", SignupValidation.prepareSupplierRecovery(input).password)
    }
    @Test fun farmerRetainsOriginalAccountAndFarmRequestFields() {
        val input = request().copy(role = "FARMER", username = "synthetic_farmer", region = "Original region",
            farmName = signupFarmName(SignupRole.FARMER, "Synthetic Farmer"))
        assertTrue(errors(input).isEmpty())
        val prepared = SignupValidation.prepare(input)
        assertEquals("Original region", prepared.region)
        assertEquals("Synthetic Farmer's farm", prepared.farmName)
        assertNull(prepared.businessName); assertNull(prepared.businessLocation)
    }
    @Test fun confirmationAndSeparateLocationInputsDoNotEnterRequestModel() {
        val json = JsonParser.parseString(Gson().toJson(request())).asJsonObject
        assertFalse(json.has("confirmPassword")); assertFalse(json.has("confirmation"))
        assertFalse(json.has("town")); assertFalse(json.has("address"))
        assertEquals("Ruiru, Shop 4, Market Road", json["businessLocation"].asString)
        assertEquals("SignupRequest(redacted)", request().toString())
    }
    @Test fun errorsIdentifyPersonalFieldsWithoutChangingEnteredValues() {
        val input = request().copy(fullName = "x", email = "bad", phoneNumber = "", username = "1bad")
        assertEquals(setOf(SignupField.NAME, SignupField.EMAIL, SignupField.PHONE, SignupField.USERNAME), errors(input).keys)
        assertEquals("bad", input.email); assertEquals("synthetic-password", input.password)
    }
}
