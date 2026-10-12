package com.example.farmapp.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class FarmerMapStateTest {
    @Test fun deniedWithoutEitherPermission() { assertEquals(MapPermission.DENIED, mapPermission(false, false)) }
    @Test fun coarseOnlyIsAccepted() { assertEquals(MapPermission.APPROXIMATE, mapPermission(true, false)) }
    @Test fun fineIsAcceptedWithOrWithoutCoarse() {
        assertEquals(MapPermission.PRECISE, mapPermission(true, true))
        assertEquals(MapPermission.PRECISE, mapPermission(false, true))
    }
    @Test fun denialRequiresPermissionEvenWithCachedFix() {
        assertEquals(RecenterAction.REQUEST_PERMISSION, recenterAction(MapPermission.DENIED, true, true))
    }
    @Test fun disabledServicesNeverUseCachedFix() {
        assertEquals(RecenterAction.OPEN_LOCATION_SETTINGS, recenterAction(MapPermission.APPROXIMATE, false, true))
    }
    @Test fun grantedPermissionWaitsForFix() {
        for (permission in listOf(MapPermission.APPROXIMATE, MapPermission.PRECISE)) {
            assertEquals(RecenterAction.WAIT_FOR_FIX, recenterAction(permission, true, false))
            assertEquals(RecenterAction.CENTER, recenterAction(permission, true, true))
        }
    }
}
