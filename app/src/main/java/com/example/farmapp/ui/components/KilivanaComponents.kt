package com.example.farmapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.farmapp.ui.theme.KilivanaSpacing

@Composable
fun KilivanaStatusBadge(label: String, containerColor: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = containerColor, contentColor = contentColor) {
        Text(label, modifier = Modifier.padding(horizontal = KilivanaSpacing.small, vertical = KilivanaSpacing.extraSmall),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Compact metric on a branded header, retaining the caller's label and value. */
@Composable
fun KilivanaHeaderMetric(title: String, value: String, modifier: Modifier = Modifier,
    valueColor: Color = Color.White, indicatorColor: Color? = null) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = 0.12f), contentColor = Color.White) {
        Column(Modifier.padding(KilivanaSpacing.small), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
            Spacer(Modifier.height(KilivanaSpacing.extraSmall))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KilivanaSpacing.extraSmall)) {
                if (indicatorColor != null) Box(Modifier.size(KilivanaSpacing.small).clip(CircleShape).background(indicatorColor))
                Text(title, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
