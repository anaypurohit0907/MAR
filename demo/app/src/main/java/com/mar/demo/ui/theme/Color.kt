package com.mar.demo.ui.theme

import androidx.compose.ui.graphics.Color

object MarColors {
    val GlassWhite = Color.White.copy(alpha = 0.08f)
    val GlassBorder = Color.White.copy(alpha = 0.15f)
    val GlassSurface = Color(0xFF1A1A2E).copy(alpha = 0.85f)
    val SurfaceElevated = Color(0xFF1E2A4A).copy(alpha = 0.7f)

    val Blue = Color(0xFF3485FF)
    val BlueDark = Color(0xFF1A5BBF)
    val BlueGlow = Color(0xFF3485FF).copy(alpha = 0.3f)

    val Green = Color(0xFF4CAF50)
    val GreenGlow = Color(0xFF4CAF50).copy(alpha = 0.3f)

    val Red = Color(0xFFDF514C)
    val RedGlow = Color(0xFFDF514C).copy(alpha = 0.3f)

    val Orange = Color(0xFFFF9800)
    val OrangeGlow = Color(0xFFFF9800).copy(alpha = 0.3f)

    val TextPrimary = Color.White.copy(alpha = 0.92f)
    val TextSecondary = Color.White.copy(alpha = 0.6f)
    val TextTertiary = Color.White.copy(alpha = 0.38f)

    val BackgroundDark = Color(0xFF0A0A14)
    val BackgroundGradientStart = Color(0xFF0F0F1A)
    val BackgroundGradientEnd = Color(0xFF1A1A2E)

    val StepLine = Color.White.copy(alpha = 0.1f)
    val StepLineActive = Blue.copy(alpha = 0.5f)
}
