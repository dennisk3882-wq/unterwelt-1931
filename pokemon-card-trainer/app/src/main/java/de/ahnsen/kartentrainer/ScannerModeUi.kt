package de.ahnsen.kartentrainer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Four mutually exclusive ways to scan. Never start the live camera in other modes. */
internal enum class ScannerMode(
    val title: String,
    val subtitle: String,
    val emoji: String,
    val accent: Color,
    val softColor: Color
) {
    ONE(
        "Eine Karte scannen",
        "Schnell scannen mit automatischer Randkorrektur",
        "📷", KidPalette.Ocean, KidPalette.SoftBlue
    ),
    PRECISE(
        "Eine Karte genau scannen",
        "Zusätzliche Bildverbesserung und Trefferkontrolle",
        "✨", KidPalette.Purple, KidPalette.SoftPurple
    ),
    SEQUENTIAL(
        "Viele Karten nacheinander",
        "Karte wechseln – die Kamera erkennt laufend",
        "🔁", KidPalette.Leaf, KidPalette.SoftGreen
    ),
    MULTI_PHOTO(
        "Viele Karten auf einem Foto",
        "Mehrere Karten gleichzeitig fotografieren",
        "🗂️", KidPalette.Fire, KidPalette.SoftYellow
    )
}

@Composable
internal fun ScannerModePicker(
    selected: ScannerMode,
    onSelect: (ScannerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Wie möchtest du scannen?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = KidPalette.Ink
        )
        ScannerMode.entries.forEach { mode ->
            val active = mode == selected
            OutlinedCard(
                onClick = { onSelect(mode) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(if (active) 2.5.dp else 1.dp,
                    if (active) mode.accent else Color(0xFFD4DCE6)),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (active) mode.softColor else Color.White.copy(alpha = 0.94f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(mode.emoji, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mode.title, fontWeight = FontWeight.Bold, color = KidPalette.Ink)
                        Text(mode.subtitle, style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF465268))
                    }
                    if (active) {
                        Spacer(Modifier.width(6.dp))
                        Text("✓", fontWeight = FontWeight.ExtraBold, color = mode.accent,
                            style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}
