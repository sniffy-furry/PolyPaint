package com.polypaint.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFE8A33D)
private val AmberDim = Color(0xFF9C6B1F)
private val Charcoal = Color(0xFF14161A)
private val CharcoalSurface = Color(0xFF1E2126)
private val OffWhite = Color(0xFFEDEDEF)

private val PolyPaintDarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF201400),
    secondary = AmberDim,
    background = Charcoal,
    onBackground = OffWhite,
    surface = CharcoalSurface,
    onSurface = OffWhite,
    surfaceVariant = Color(0xFF2A2D33),
)

private val PolyPaintLightColors = lightColorScheme(
    primary = AmberDim,
    onPrimary = Color.White,
    secondary = Amber,
    background = Color(0xFFF7F5F2),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
)

@Composable
fun PolyPaintTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PolyPaintDarkColors else PolyPaintLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
