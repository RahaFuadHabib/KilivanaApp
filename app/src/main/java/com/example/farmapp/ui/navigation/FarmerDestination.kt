package com.example.farmapp.ui.navigation

/** One flat Farmer tab selection, with no nested home stack. */
internal enum class FarmerDestination(val label: String) {
    HOME("Home"), ORDERS("Orders"), ADD("Add"), MAP("Map"), SUPPLIES("Supplies"), PROFILE("Profile");

    fun backDestination(): FarmerDestination? = if (this == HOME) null else HOME

    companion object {
        val bottomTabs = listOf(HOME, ORDERS, ADD, SUPPLIES, PROFILE)
    }
}
