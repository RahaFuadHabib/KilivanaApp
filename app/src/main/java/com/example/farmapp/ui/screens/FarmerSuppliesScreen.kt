package com.example.farmapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun FarmerSuppliesScreen(modifier: Modifier, onTracking: () -> Unit) {
    com.example.farmapp.marketplace.MarketplaceScreen(modifier)
}
