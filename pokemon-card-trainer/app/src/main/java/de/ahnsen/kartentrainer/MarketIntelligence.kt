package de.ahnsen.kartentrainer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

data class MarketSignal(
    val label: String,
    val detail: String,
    val spreadPercent: Int?,
    val trendPercent: Int?
)

fun CollectionEntry.marketSignal(): MarketSignal {
    val avg = card.priceAvg
    val trend = card.priceTrend
    val low = card.priceLow
    val trendPercent = if (avg != null && avg > 0 && trend != null) {
        (((trend - avg) / avg) * 100).toInt()
    } else null
    val spreadPercent = if (avg != null && avg > 0 && low != null) {
        (((avg - low) / avg) * 100).toInt().coerceAtLeast(0)
    } else null

    val label = when {
        trendPercent == null -> "Zu wenig Marktdaten"
        trendPercent >= 12 -> "Trend deutlich über Durchschnitt"
        trendPercent >= 4 -> "Trend leicht positiv"
        trendPercent <= -12 -> "Trend deutlich unter Durchschnitt"
        trendPercent <= -4 -> "Trend leicht negativ"
        else -> "Trend weitgehend stabil"
    }

    val detail = buildString {
        if (trendPercent != null) {
            append("Trend ")
            append(if (trendPercent >= 0) "+" else "")
            append(trendPercent)
            append("% gegenüber Durchschnitt")
        }
        if (spreadPercent != null) {
            if (isNotEmpty()) append(" · ")
            append("Low/Ø-Spanne ")
            append(spreadPercent)
            append("%")
        }
        val adjusted = adjustedUnitValue()
        if (adjusted != null) {
            if (isNotEmpty()) append(" · ")
            append("Zustand/Sprache berücksichtigt: ")
            append(adjusted.euro())
        }
    }.ifBlank { "Keine ausreichenden Preisfelder für eine belastbare lokale Trendanalyse." }

    return MarketSignal(label, detail, spreadPercent, trendPercent)
}

@Composable
fun MarketResearchBlock(
    entry: CollectionEntry,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val signal = entry.marketSignal()
    val query = listOf(
        entry.card.name,
        entry.card.setName,
        entry.card.localId
    ).filter { it.isNotBlank() }.joinToString(" ")

    Column(modifier = modifier.fillMaxWidth()) {
        Text(signal.label, fontWeight = FontWeight.SemiBold)
        Text(
            signal.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val url = "https://www.cardmarket.com/de/Pokemon/Products/Search?searchString=" +
                        Uri.encode(query)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Text(" Cardmarket")
            }
            OutlinedButton(
                onClick = {
                    val url = "https://www.ebay.de/sch/i.html?_nkw=" +
                        Uri.encode(query) +
                        "&LH_Sold=1&LH_Complete=1"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Text(" eBay verkauft")
            }
        }
    }
}
