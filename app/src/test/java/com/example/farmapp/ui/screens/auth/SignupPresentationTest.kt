package com.example.farmapp.ui.screens.auth

import com.example.farmapp.network.SignupValidation
import com.example.farmapp.network.signupInput
import org.junit.Assert.*
import org.junit.Test

class SignupPresentationTest {
    @Test fun farmerSignupKeepsRequiredWireFieldWithoutAnExtraInput() {
        val farmName = signupFarmName(SignupRole.FARMER, " Amara Osei ")
        assertEquals("Amara Osei's farm", farmName)
        val prepared = SignupValidation.prepare(signupInput("FARMER").copy(fullName = " Amara Osei ", farmName = farmName))
        assertEquals(farmName, prepared.farmName)
        assertEquals("Amara Osei", prepared.fullName)
    }
    @Test fun otherRolesNeverReceiveADerivedFarmName() {
        SignupRole.entries.filter { it != SignupRole.FARMER }.forEach {
            assertNull(signupFarmName(it, "Amara Osei"))
        }
        assertNull(signupFarmName(null, "Amara Osei"))
        assertNull(signupFarmName(SignupRole.FARMER, " "))
    }
}
