package com.example.farmapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Farmer-only control; the other roles retain their existing theme actions. */
@Composable
internal fun FarmerThemeToggle(isDarkTheme: Boolean, onToggle: () -> Unit, iconOnly: Boolean = false) {
    val rotation by animateFloatAsState(if (isDarkTheme) 180f else 0f, tween(350), label = "dayNightRotation")
    val tint by animateColorAsState(if (isDarkTheme) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.primary, tween(350), label = "dayNightTint")
    val icon: @Composable () -> Unit = {
        Icon(if (isDarkTheme) Icons.Default.Bedtime else Icons.Default.WbSunny,
            contentDescription = if (iconOnly) {
                if (isDarkTheme) "Switch to light mode" else "Switch to dark mode"
            } else null,
            tint = tint, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation })
    }
    if (iconOnly) {
        FilledTonalIconButton(onClick = onToggle, modifier = Modifier.size(48.dp), shape = CircleShape) { icon() }
    } else OutlinedButton(onClick = onToggle) {
        icon()
        Text(if (isDarkTheme) "NIGHT" else "DAY", modifier = Modifier.padding(start = 6.dp))
    }
}
