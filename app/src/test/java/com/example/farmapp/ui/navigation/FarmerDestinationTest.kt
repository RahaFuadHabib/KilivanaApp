package com.example.farmapp.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class FarmerDestinationTest {
    @Test fun actionsHaveExactlyTheRequiredOrder() {
        assertEquals(listOf("Home", "Orders", "Add", "Supplies", "Profile"), FarmerDestination.bottomTabs.map { it.label })
        assertEquals(FarmerDestination.ADD, FarmerDestination.bottomTabs[2])
        assertFalse(FarmerDestination.MAP in FarmerDestination.bottomTabs)
    }
    @Test fun backReturnsEverySecondaryDestinationToOneHome() {
        FarmerDestination.entries.filter { it != FarmerDestination.HOME }.forEach {
            assertEquals(FarmerDestination.HOME, it.backDestination())
            assertNull(it.backDestination()!!.backDestination())
        }
        assertNull(FarmerDestination.HOME.backDestination())
    }
}
