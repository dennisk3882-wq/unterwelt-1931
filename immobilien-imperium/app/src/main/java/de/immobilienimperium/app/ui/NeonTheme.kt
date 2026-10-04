package de.immobilienimperium.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NeonBlue = Color(0xFF1769FF)
val NeonCyan = Color(0xFF00CFE8)
val NeonMint = Color(0xFF00D99B)
val NeonPurple = Color(0xFF8B3DFF)
val NeonOrange = Color(0xFFFF9D20)
val NeonRed = Color(0xFFFF416C)
val Ink = Color(0xFF10203A)
val Muted = Color(0xFF63718A)
val Cloud = Color(0xFFF4F9FF)
val Panel = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = NeonBlue,
    onPrimary = Color.White,
    secondary = NeonPurple,
    tertiary = NeonMint,
    background = Cloud,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    error = NeonRed
)

@Composable
fun NeonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
