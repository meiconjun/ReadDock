package com.readdock.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ReadDockLightColors = lightColorScheme(
    primary = Color(0xFF002FA7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF001452),
    secondary = Color(0xFF5D6472),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3F2),
    onSecondaryContainer = Color(0xFF101C29),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF46515D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F6FA),
    surfaceContainer = Color(0xFFEEF2F6),
    surfaceContainerHigh = Color(0xFFE8EDF2),
    surfaceContainerHighest = Color(0xFFE1E7ED),
    outline = Color(0xFF737D88),
    outlineVariant = Color(0xFFC6CED8),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val ReadDockDarkColors = darkColorScheme(
    primary = Color(0xFFB9C7FF),
    onPrimary = Color(0xFF001452),
    primaryContainer = Color(0xFF1F3F9E),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFFBBC7D6),
    onSecondary = Color(0xFF25313E),
    secondaryContainer = Color(0xFF3B4857),
    onSecondaryContainer = Color(0xFFD7E3F2),
    background = Color(0xFF101417),
    onBackground = Color(0xFFE1E5E9),
    surface = Color(0xFF101417),
    onSurface = Color(0xFFE1E5E9),
    surfaceVariant = Color(0xFF414A54),
    onSurfaceVariant = Color(0xFFC1C9D2),
    surfaceContainerLowest = Color(0xFF0B0F12),
    surfaceContainerLow = Color(0xFF151A1F),
    surfaceContainer = Color(0xFF1A2025),
    surfaceContainerHigh = Color(0xFF242B31),
    surfaceContainerHighest = Color(0xFF2E363D),
    outline = Color(0xFF8B96A1),
    outlineVariant = Color(0xFF414A54),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun ReadDockTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) ReadDockDarkColors else ReadDockLightColors
    MaterialTheme(colorScheme = colors, content = content)
}
