package com.example.farmapp.ui.screens.auth

import com.example.farmapp.data.model.LoginRequest
import com.example.farmapp.network.MockAuthService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AuthPresentationTest {
    @Test
    fun everyDropdownAccountStillAuthenticatesWithItsOriginalRole() = runBlocking {
        val expected = listOf("FARMER", "BUYER", "SUPPLIER", "INSPECTOR")
        assertEquals(expected, DemoAccount.entries.map { it.name })
        DemoAccount.entries.forEach { account ->
            val response = MockAuthService().login(LoginRequest(account.email, account.password))
            assertTrue(response.isSuccessful)
            assertEquals(account.name, response.body()!!.role)
        }
    }

    @Test
    fun manualEmailEditsRecomputeOrClearTheDisplayedAccount() {
        assertEquals(DemoAccount.FARMER, DemoAccount.forEmail("farmer@kilivana.com"))
        assertNull(DemoAccount.forEmail("farmer@kilivana.com.example"))
        assertNull(DemoAccount.forEmail(""))
        assertEquals(DemoAccount.BUYER, DemoAccount.forEmail("buyer@kilivana.com"))
        assertEquals(DemoAccount.SUPPLIER, DemoAccount.forEmail(" SUPPLIER@KILIVANA.COM "))
    }

    @Test
    fun incorrectCredentialsRemainRejected() = runBlocking {
        val service = MockAuthService()
        DemoAccount.entries.forEach { account ->
            assertEquals(401, service.login(LoginRequest(account.email, "incorrect")).code())
        }
        assertEquals(401, service.login(LoginRequest("unknown@kilivana.com", "1234")).code())
    }

    @Test
    fun manuallyChangingEmailUsesTheResponseRole() = runBlocking {
        val initiallySelected = DemoAccount.FARMER
        val editedEmail = DemoAccount.INSPECTOR.email
        val response = MockAuthService().login(LoginRequest(editedEmail, initiallySelected.password))
        assertEquals("INSPECTOR", response.body()!!.role)
    }
}
