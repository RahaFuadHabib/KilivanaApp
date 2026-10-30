// Integrated from brianmuigai2-stack/Kilivana-buyer master 8f335e2; see docs/BRIAN_BUYER_INTEGRATION.md.
package com.example.farmapp.buyers.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Green600,
    onPrimary = NeutralSurface,
    secondary = Green600,
    onSecondary = NeutralSurface,
    tertiary = Green600,
    onTertiary = NeutralSurface,
    background = NeutralBackground,
    onBackground = TextPrimary,
    surface = NeutralSurface,
    onSurface = TextPrimary,
)

@Composable
fun KilivanabuyerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
