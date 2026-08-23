package com.readdock.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val ReadDockLightColors = lightColorScheme(
    primary = Color(0xFF25282D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E8EB),
    onPrimaryContainer = Color(0xFF25282D),
    secondary = Color(0xFF52689E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9EDF5),
    onSecondaryContainer = Color(0xFF263450),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF17191C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17191C),
    surfaceVariant = Color(0xFFECEDEF),
    onSurfaceVariant = Color(0xFF515964),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F4F5),
    surfaceContainer = Color(0xFFEEEEF0),
    surfaceContainerHigh = Color(0xFFE7E8EA),
    surfaceContainerHighest = Color(0xFFDFE1E5),
    outline = Color(0xFF737A84),
    outlineVariant = Color(0xFFD0D3D8),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val ReadDockDarkColors = darkColorScheme(
    primary = Color(0xFFD9DCE1),
    onPrimary = Color(0xFF202329),
    primaryContainer = Color(0xFF3A3D43),
    onPrimaryContainer = Color(0xFFF1F2F4),
    secondary = Color(0xFFAEBDE1),
    onSecondary = Color(0xFF1D2A47),
    secondaryContainer = Color(0xFF303A52),
    onSecondaryContainer = Color(0xFFE1E8FA),
    background = Color(0xFF111214),
    onBackground = Color(0xFFE7E8EA),
    surface = Color(0xFF111214),
    onSurface = Color(0xFFE7E8EA),
    surfaceVariant = Color(0xFF3E4248),
    onSurfaceVariant = Color(0xFFC7CAD0),
    surfaceContainerLowest = Color(0xFF0D0E10),
    surfaceContainerLow = Color(0xFF17181B),
    surfaceContainer = Color(0xFF1C1D21),
    surfaceContainerHigh = Color(0xFF26282D),
    surfaceContainerHighest = Color(0xFF303239),
    outline = Color(0xFF8D929A),
    outlineVariant = Color(0xFF43464D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val ReadDockShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun ReadDockTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) ReadDockDarkColors else ReadDockLightColors
    MaterialTheme(
        colorScheme = colors,
        shapes = ReadDockShapes,
        content = content
    )
}
