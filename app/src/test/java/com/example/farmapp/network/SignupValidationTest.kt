package com.example.farmapp.network

import com.example.farmapp.data.model.SignupRequest
import com.example.farmapp.repository.AuthFailure
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

internal fun signupInput(role: String = "BUYER") = SignupRequest("person@example.test", "password", "Person", "0700000000", role, "person", "Nairobi",
    "Farm", "Business", "Code", "Physical address")

class SignupValidationTest {
    @Test fun eachRoleSerializesExactlyItsApplicableFields() {
        val common = setOf("email", "password", "fullName", "phoneNumber", "role", "username", "region")
        mapOf("FARMER" to setOf("farmName"), "BUYER" to emptySet(),
            "SUPPLIER" to setOf("businessName", "businessLocation"), "INSPECTOR" to setOf("employeeCode")).forEach { (role, extra) ->
            val json = JsonParser.parseString(Gson().toJson(SignupValidation.prepare(signupInput(role)))).asJsonObject
            assertEquals(common + extra, json.keySet())
            assertEquals(role, json["role"].asString)
            assertEquals("password", json["password"].asString)
            extra.forEach { assertFalse(json[it].asString.isBlank()) }
        }
        assertEquals("SignupRequest(redacted)", signupInput().toString())
    }

    @Test fun usernameNormalizationAndExactSyntax() {
        assertEquals("amara_osei", SignupValidation.prepare(signupInput().copy(username = " AMARA_OSEI ")).username)
        listOf("amara", "amara_osei", "farmer123", "a".repeat(30)).forEach {
            assertEquals(it, SignupValidation.prepare(signupInput().copy(username = it)).username)
        }
        listOf("A", "123farmer", "farmer-name", "farmer name", "abcdefghijklmnopqrstuvwxyz12345", "\u00e9clair", "", "a\nb").forEach {
            rejected(signupInput().copy(username = it))
        }
    }

    @Test fun usernameLowercasingDoesNotDependOnDeviceLocale() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            assertEquals("inspector", SignupValidation.prepare(signupInput().copy(username = " INSPECTOR ")).username)
        } finally { java.util.Locale.setDefault(previous) }
    }

    @Test fun commonFieldsTrimWithoutNarrowingPhoneOrRegion() {
        for (role in listOf("FARMER", "BUYER", "SUPPLIER", "INSPECTOR")) {
            rejected(signupInput(role).copy(region = " "))
            val prepared = SignupValidation.prepare(signupInput(role).copy(region = if (role == "SUPPLIER") " Nairobi " else " Any free text region ", phoneNumber = " 0712 345 678 "))
            assertEquals(if (role == "SUPPLIER") "Nairobi City" else "Any free text region", prepared.region)
            assertEquals("0712 345 678", prepared.phoneNumber)
        }
    }

    private fun rejected(input: SignupRequest) {
        try { SignupValidation.prepare(input); fail("Expected validation failure") } catch (_: AuthFailure) { }
    }

    @Test fun employeeCodeRequiredAndBounded() {
        listOf(null, "", "   ", "x".repeat(101)).forEach { rejected(signupInput("INSPECTOR").copy(employeeCode = it)) }
        assertEquals("x".repeat(100), SignupValidation.prepare(signupInput("INSPECTOR").copy(employeeCode = "x".repeat(100))).employeeCode)
    }

    @Test fun passwordBoundariesAndWhitespacePreserved() {
        listOf("", "x".repeat(7), "x".repeat(73)).forEach { rejected(signupInput().copy(password = it)) }
        listOf("x".repeat(8), "x".repeat(72), " password ").forEach {
            assertEquals(it, SignupValidation.prepare(signupInput().copy(password = it)).password)
        }
    }

    @Test fun requiredFieldsAndPublicRolesAreValidated() {
        rejected(signupInput().copy(fullName = " "))
        rejected(signupInput().copy(email = "invalid"))
        rejected(signupInput().copy(phoneNumber = " "))
        rejected(signupInput("FARMER").copy(farmName = null))
        rejected(signupInput().copy(region = " "))
        rejected(signupInput("SUPPLIER").copy(businessName = ""))
        listOf("ADMIN", "DRIVER", "UNKNOWN", "").forEach { rejected(signupInput(it)) }
    }
}
