package com.sintonia.sincronia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = darkColorScheme(
    primary = SintoniaPrimary,
    secondary = SintoniaTextMuted,
    background = SintoniaBackground,
    surface = SintoniaSurface,
    onPrimary = Color.White,
    onSecondary = SintoniaBackground,
    onBackground = SintoniaText,
    onSurface = SintoniaText
)

@Composable
fun SintoniaSincroniaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = SintoniaTypography,
        content = content
    )
}
