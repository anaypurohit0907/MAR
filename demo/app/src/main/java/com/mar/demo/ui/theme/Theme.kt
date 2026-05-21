package com.mar.demo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MarColorScheme = darkColorScheme(
    primary = MarColors.Blue,
    onPrimary = Color.White,
    primaryContainer = MarColors.Blue.copy(alpha = 0.15f),
    secondary = MarColors.Green,
    onSecondary = Color.White,
    tertiary = MarColors.Orange,
    background = MarColors.BackgroundDark,
    surface = MarColors.SurfaceElevated,
    surfaceVariant = MarColors.GlassWhite,
    onBackground = MarColors.TextPrimary,
    onSurface = MarColors.TextPrimary,
    onSurfaceVariant = MarColors.TextSecondary,
    outline = MarColors.GlassBorder,
    outlineVariant = MarColors.StepLine,
    error = MarColors.Red,
    onError = Color.White
)

@Composable
fun MarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MarColorScheme,
        typography = MarTypography,
        content = content
    )
}
