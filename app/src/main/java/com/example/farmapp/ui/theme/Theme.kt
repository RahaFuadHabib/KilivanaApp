package com.example.farmapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = Color(0xFF00522B),
    onPrimaryContainer = Color(0xFF8CF5B5),
    secondary = Color(0xFFFFB77C),
    onSecondary = Color(0xFF4D2600),
    secondaryContainer = Color(0xFF6E3900),
    onSecondaryContainer = Color(0xFFFFDCC1),
    tertiary = Color(0xFF81C784),
    onTertiary = Color(0xFF003A0B),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = Color(0xFF282828),
    onSurfaceVariant = Color(0xFFD0D0D0),
    outline = DarkOutline
)

val LightColorScheme = lightColorScheme(
    primary = KilivanaGreen,
    onPrimary = KilivanaOnPrimary,
    primaryContainer = Color(0xFFD4E8DC),
    onPrimaryContainer = Color(0xFF052016),
    secondary = KilivanaAmber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC1),
    onSecondaryContainer = Color(0xFF2E1500),
    tertiary = KilivanaLeaf,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAD7),
    onTertiaryContainer = Color(0xFF002206),
    background = KilivanaBackground,
    onBackground = KilivanaOnBackground,
    surface = KilivanaSurface,
    onSurface = KilivanaOnBackground,
    surfaceVariant = Color(0xFFF0F4EF),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973)
)

@Composable
fun FarmAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}