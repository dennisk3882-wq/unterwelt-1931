package de.ahnsen.kartentrainer

import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = R.drawable.cardworld_bg,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.34f
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xDDF3FAFF),
                            Color(0xDDF7F2FF),
                            Color(0xE8FFF7EE)
                        )
                    )
                )
        )
        content()
    }
}

@Composable
fun KidHeroBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accent: Color = KidPalette.Ocean
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(154.dp)
            .clip(RoundedCornerShape(28.dp))
    ) {
        AsyncImage(
            model = R.drawable.cardworld_bg,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.88f),
                            accent.copy(alpha = 0.48f),
                            Color.Transparent
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(18.dp)
                .fillMaxWidth(0.76f)
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.95f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun KidTipCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    container: Color = Color.White.copy(alpha = 0.90f)
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, color = KidPalette.Ink)
            Spacer(Modifier.height(3.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
