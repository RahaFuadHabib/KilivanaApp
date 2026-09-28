package com.example.farmapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun textStyle(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight,
    fontSize = size.sp, lineHeight = height.sp
)

val Typography = Typography(
    headlineLarge = textStyle(32, 40, FontWeight.Bold),
    headlineMedium = textStyle(28, 36, FontWeight.Bold),
    headlineSmall = textStyle(24, 32, FontWeight.Bold),
    titleLarge = textStyle(22, 28, FontWeight.SemiBold),
    titleMedium = textStyle(16, 24, FontWeight.SemiBold),
    titleSmall = textStyle(14, 20, FontWeight.SemiBold),
    bodyLarge = textStyle(16, 24),
    bodyMedium = textStyle(14, 20),
    bodySmall = textStyle(12, 18),
    labelLarge = textStyle(14, 20, FontWeight.SemiBold),
    labelMedium = textStyle(12, 18, FontWeight.Medium),
    labelSmall = textStyle(12, 16, FontWeight.Medium)
)
