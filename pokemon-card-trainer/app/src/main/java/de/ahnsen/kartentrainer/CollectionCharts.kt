package de.ahnsen.kartentrainer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max

data class ChartSlice(
    val label: String,
    val value: Float
)

@Composable
fun CollectionCharts(
    collection: List<CollectionEntry>,
    modifier: Modifier = Modifier
) {
    val category = listOf(
        ChartSlice("Pokémon", collection.filter { it.card.isPokemon() }.sumOf { it.quantity }.toFloat()),
        ChartSlice("Trainer", collection.filter { it.card.isTrainer() }.sumOf { it.quantity }.toFloat()),
        ChartSlice("Energie", collection.filter { it.card.isEnergy() }.sumOf { it.quantity }.toFloat())
    )

    val topSets = collection
        .groupBy { it.card.setName.ifBlank { "Ohne Set" } }
        .mapValues { (_, values) -> values.sumOf { it.quantity }.toFloat() }
        .toList()
        .sortedByDescending { it.second }
        .take(6)
        .map { ChartSlice(it.first, it.second) }

    val valueBySet = collection
        .groupBy { it.card.setName.ifBlank { "Ohne Set" } }
        .mapValues { (_, values) -> values.mapNotNull { it.totalEstimatedValue }.sum().toFloat() }
        .toList()
        .filter { it.second > 0f }
        .sortedByDescending { it.second }
        .take(6)
        .map { ChartSlice(it.first, it.second) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Sammlungsdiagramme",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        HorizontalBarChartCard(
            title = "Karten nach Kategorie",
            data = category,
            valueSuffix = " Karten"
        )
        HorizontalBarChartCard(
            title = "Größte Sets",
            data = topSets,
            valueSuffix = " Karten"
        )
        if (valueBySet.isNotEmpty()) {
            HorizontalBarChartCard(
                title = "Geschätzter Wert nach Set",
                data = valueBySet,
                valueSuffix = " €",
                decimals = 2
            )
        }
    }
}

@Composable
private fun HorizontalBarChartCard(
    title: String,
    data: List<ChartSlice>,
    valueSuffix: String,
    decimals: Int = 0
) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    OutlinedCard {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (data.isEmpty() || data.all { it.value <= 0f }) {
                Text(
                    "Noch keine auswertbaren Daten.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                val maxValue = max(1f, data.maxOf { it.value })
                data.forEach { slice ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            slice.label,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        val formatted = if (decimals == 2) {
                            String.format("%.2f", slice.value)
                        } else {
                            slice.value.toInt().toString()
                        }
                        Text(
                            formatted + valueSuffix,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .padding(vertical = 2.dp)
                    ) {
                        drawRect(
                            color = trackColor,
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height)
                        )
                        drawRect(
                            color = barColor,
                            topLeft = Offset.Zero,
                            size = Size(size.width * (slice.value / maxValue), size.height)
                        )
                    }
                }
            }
        }
    }
}
