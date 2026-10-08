package de.ahnsen.kartentrainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object KidPalette {
    val Sky = Color(0xFF57B8FF)
    val Ocean = Color(0xFF176BFF)
    val Purple = Color(0xFF7A5CFF)
    val Pink = Color(0xFFFF6FB7)
    val Sun = Color(0xFFFFC94A)
    val Leaf = Color(0xFF39C98A)
    val Fire = Color(0xFFFF6B4A)
    val Ink = Color(0xFF17223B)

    val HeroA = Color(0xFF5B4BFF)
    val HeroB = Color(0xFF1AA7EC)
    val HeroC = Color(0xFF35D0A1)

    val SoftBlue = Color(0xFFE8F4FF)
    val SoftPurple = Color(0xFFF0E9FF)
    val SoftPink = Color(0xFFFFEAF4)
    val SoftGreen = Color(0xFFE8FFF4)
    val SoftYellow = Color(0xFFFFF6D8)
}

val KidShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

fun kidBackgroundBrush(): Brush = Brush.verticalGradient(
    listOf(
        Color(0xFFEAF7FF),
        Color(0xFFF4EEFF),
        Color(0xFFFFF5E8)
    )
)

@Composable
fun KidScreenBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(kidBackgroundBrush()),
        content = content
    )
}
