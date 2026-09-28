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
        assertEquals(setOf("email", "password"), login.keySet())
        assertEquals(setOf("refreshToken"), JsonParser.parseString(gson.toJson(RefreshRequest("refresh"))).asJsonObject.keySet())
        assertEquals(setOf("refreshToken"), JsonParser.parseString(gson.toJson(LogoutRequest("refresh"))).asJsonObject.keySet())
        listOf("login", "refresh", "logout").forEach { name ->
            val method = ApiService::class.java.methods.single { it.name == name }
            assertEquals("auth/$name", method.getAnnotation(POST::class.java)!!.value)
        }
    }

    @Test fun responseRoundTripPreservesAllSevenFields() {
        val auth = testAuth()
        val json = gson.toJson(auth)
        assertEquals(setOf("userId", "email", "fullName", "role", "accessToken", "refreshToken", "refreshTokenExpiresAt"),
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
