package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF001E28),
    primaryContainer = Color(0xFF004D5C),
    onPrimaryContainer = Color(0xFFBCE9FF),
    secondary = EmeraldAccent,
    onSecondary = Color(0xFF003822),
    secondaryContainer = Color(0xFF005234),
    onSecondaryContainer = Color(0xFF6FF7B3),
    tertiary = AmberWarning,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceBorder,
    error = CrimsonError
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = Typography,
        content = content
    )
}

