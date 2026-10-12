package com.example.farmapp.network

import com.example.farmapp.data.model.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import retrofit2.http.POST

class AuthContractTest {
    private val gson = Gson()

    @Test fun requestFieldsAndRelativeEndpointsExactlyMatchContract() {
        val login = JsonParser.parseString(gson.toJson(LoginRequest("person@example.test", "secret"))).asJsonObject
        assertEquals(setOf("identifier", "password"), login.keySet())
        assertEquals(setOf("refreshToken"), JsonParser.parseString(gson.toJson(RefreshRequest("refresh"))).asJsonObject.keySet())
        assertEquals(setOf("refreshToken"), JsonParser.parseString(gson.toJson(LogoutRequest("refresh"))).asJsonObject.keySet())
        listOf("login", "refresh", "logout", "signup").forEach { name ->
            val method = ApiService::class.java.methods.single { it.name == name }
            assertEquals("auth/$name", method.getAnnotation(POST::class.java)!!.value)
        }
    }

    @Test fun phoneAndEmailUseIdentifierWithoutLegacyEmailKey() {
        for (identifier in listOf("person@example.test", "0712345678", "+254712345678")) {
            val json = JsonParser.parseString(gson.toJson(LoginRequest(identifier, " secret "))).asJsonObject
            assertEquals(setOf("identifier", "password"), json.keySet())
            assertEquals(identifier, json["identifier"].asString)
            assertEquals(" secret ", json["password"].asString)
        }
    }

    @Test fun legacyProfileOmissionsAreAcceptedOnlyForRestoration() {
        val json = JsonParser.parseString(gson.toJson(testAuth())).asJsonObject
        listOf("username", "region", "phoneNumber", "verificationStatus").forEach { json.remove(it) }
        val legacy = gson.fromJson(json, AuthResponse::class.java)
        assertEquals(legacy, AuthValidation.validate(legacy))
        assertNull(legacy.username)
        assertNull(legacy.region)
        assertNull(legacy.phoneNumber)
        assertNull(legacy.verificationStatus)
        try { AuthValidation.validateBackend(legacy); fail() } catch (_: InvalidAuthResponse) { }
        for (field in listOf("username", "region", "phoneNumber")) {
            val incomplete = JsonParser.parseString(gson.toJson(testAuth())).asJsonObject
            incomplete.remove(field)
            try { AuthValidation.validateBackend(gson.fromJson(incomplete, AuthResponse::class.java)); fail(field) }
            catch (_: InvalidAuthResponse) { }
        }
    }

    @Test fun responseRoundTripPreservesAllElevenFields() {
        val auth = testAuth().copy(verificationStatus = "PENDING")
        val json = gson.toJson(auth)
        assertEquals(setOf("userId", "email", "username", "region", "phoneNumber", "fullName", "role", "verificationStatus", "accessToken", "refreshToken", "refreshTokenExpiresAt"),
            JsonParser.parseString(json).asJsonObject.keySet())
        assertEquals(auth, AuthValidation.validate(gson.fromJson(json, AuthResponse::class.java)))
    }

    @Test fun missingRequiredFieldsAreRejectedInsteadOfCreatingASession() {
        try { AuthValidation.validate(gson.fromJson("""{"role":"FARMER"}""", AuthResponse::class.java)); fail() }
        catch (_: InvalidAuthResponse) { }
    }

    @Test fun unsupportedRolesNeverMapToAnExistingPersona() {
        assertFalse("DRIVER" in AuthValidation.supportedRoles)
        assertFalse("ADMIN" in AuthValidation.supportedRoles)
        assertFalse("UNKNOWN" in AuthValidation.supportedRoles)
        assertEquals(setOf("FARMER", "BUYER", "SUPPLIER", "INSPECTOR"), AuthValidation.supportedRoles)
        assertEquals("ADMIN", AuthValidation.validate(testAuth(role = "ADMIN")).role)
    }

    @Test fun dataClassStringRepresentationsDoNotLeakCredentials() {
        assertFalse(LoginRequest("email", "secret").toString().contains("secret"))
        assertFalse(testAuth().toString().contains("access-old"))
        assertFalse(RefreshRequest("secret").toString().contains("secret"))
        assertFalse(LogoutRequest("secret").toString().contains("secret"))
    }
}
