package com.recondrive.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ReconCyan,
    secondary = ReconGreen,
    tertiary = TacticalAmber,
    background = TacticalObsidian,
    surface = TacticalSurface,
    onPrimary = TacticalObsidian,
    onSecondary = TacticalObsidian,
    onTertiary = TacticalObsidian,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun ReconDriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
