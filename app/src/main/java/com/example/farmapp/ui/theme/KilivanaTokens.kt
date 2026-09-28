package com.example.farmapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

object KilivanaSpacing {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val content = 16.dp
    val section = 24.dp
    val large = 32.dp
    val touchTarget = 48.dp
}

/** Opt-in Farmer presentation shapes; other role defaults are unchanged. */
object KilivanaFarmerShapes {
    val overview = RoundedCornerShape(28.dp)
    val card = RoundedCornerShape(24.dp)
    val inset = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(50)
}

@Immutable
data class KilivanaAccent(val container: Color, val content: Color)

/** Presentation colors only; these do not encode domain status values. */
object KilivanaAccents {
    val supplierContent: Color
        @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFE6B98E) else EarthBrown
    val inspectorContent: Color
        @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFD6B7FF) else InspectorPurple
    val success: KilivanaAccent
        @Composable get() = KilivanaAccent(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    val warning: KilivanaAccent
        @Composable get() = KilivanaAccent(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    val error: KilivanaAccent
        @Composable get() = KilivanaAccent(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    val information: KilivanaAccent
        @Composable get() = KilivanaAccent(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
}
