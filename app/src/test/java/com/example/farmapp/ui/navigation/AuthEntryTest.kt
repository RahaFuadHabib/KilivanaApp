package com.example.farmapp.ui.navigation

import com.example.farmapp.ui.screens.AuthStep
import com.example.farmapp.ui.screens.initialAuthStep
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthEntryTest {
    @Test fun freshUnauthenticatedEntryKeepsOnboarding() {
        assertEquals(AuthStep.START, initialAuthStep(afterExplicitLogout = false))
    }

    @Test fun explicitLogoutEntersSignIn() {
        assertEquals(AuthStep.SIGN_IN, initialAuthStep(afterExplicitLogout = true))
    }
}
