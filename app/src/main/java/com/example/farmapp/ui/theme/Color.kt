package com.example.farmapp.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// "Foodcourt" & Prototype High-Finish Palette Colors
val ForestGreen = Color(0xFF0D4A2D)          // Primary: Deep Earthy Forest Green (#0D4A2D)
val ForestDeep = Color(0xFF0F2E25)           // Deep Forest Shade
val LeafGreen = Color(0xFF2E9B42)            // Accent / Status: Vibrant Leaf Green
val HarvestAmber = Color(0xFFF49D0C)         // Accent: Warm Harvest Amber (#F49D0C)
val HarvestGold = HarvestAmber               // Backward-compatible alias
val EarthBrown = Color(0xFF6B4226)           // Supplier Theme: Rich Agronomic Earth Brown
val InspectorPurple = Color(0xFF7E22CE)      // Inspector Theme: Royal Governance Purple

// Warm Cream Background & Surface Tokens (Foodcourt Case Study)
val WarmCreamBackground = Color(0xFFFAF6EF)  // Warm cream background
val WarmCreamSurface = Color(0xFFFDF9F2)     // Warm cream elevated surface

// Soft Pastel "Category Card" Colors
val PastelMint = Color(0xFFE6F0E6)           // Mint pastel category card
val PastelPeach = Color(0xFFFBEAE0)          // Peach pastel category card
val PastelButter = Color(0xFFFBF3D9)         // Butter pastel category card
val PastelSage = Color(0xFFEAF0E4)           // Sage pastel category card

// Vertical Gradient Brushes for Hero Sections
val FarmerHeroGradient = Brush.verticalGradient(
    colors = listOf(ForestGreen, ForestDeep)
)
val SupplierHeroGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF8D5838), EarthBrown)
)
val InspectorHeroGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF9333EA), InspectorPurple)
)

// Backward-compatible alias tokens
val KilivanaGreen = ForestGreen
val KilivanaAmber = HarvestAmber
val KilivanaLeaf = LeafGreen
val KilivanaBackground = WarmCreamBackground
val KilivanaSurface = WarmCreamSurface
val KilivanaOnPrimary = Color(0xFFFFFFFF)
val KilivanaOnBackground = Color(0xFF1A1C19)

// Dark Palette Colors
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkOnBackground = Color(0xFFECEFF1)
val DarkOnSurface = Color(0xFFECEFF1)
val DarkPrimary = LeafGreen
val DarkOnPrimary = Color(0xFF00391C)
val DarkOutline = Color(0xFF81C784)

// Legacy tokens for backward compatibility
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)