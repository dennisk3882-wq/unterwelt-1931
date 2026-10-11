package de.ahnsen.kartentrainer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun AdvancedMarketScreen(
    padding: PaddingValues,
    collection: List<CollectionEntry>,
    repository: TcgDexRepository,
    onReplaceCollection: (List<CollectionEntry>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val apiContext = androidx.compose.ui.platform.LocalContext.current
    val liveApi = remember(apiContext) { CardmarketLiveApi(apiContext.applicationContext) }
    var hasLiveKey by remember { mutableStateOf(liveApi.hasKey()) }
    val valuations = collection.map { it to MarketValueEngine.estimate(it) }
    val priced = valuations.filter { it.second.unitPrice != null }
    val knownSum = priced.sumOf { (entry, valuation) -> (valuation.unitPrice ?: 0.0) * entry.quantity }
    val missing = collection.count { MarketValueEngine.estimate(it).unitPrice == null }
    val matchedCount = priced.sumOf { it.first.quantity }
    val totalCount = collection.sumOf { it.quantity }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val sorted = valuations.sortedByDescending { (it.second.unitPrice ?: -1.0) * it.first.quantity }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            KidHeroBanner(
                title = "💎 Dein Schatzwert",
                subtitle = "Marktpreise prüfen und bewerten",
                accent = KidPalette.Purple,
                artwork = R.drawable.bg_progress
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
                border = BorderStroke(1.5.dp, KidPalette.Sun.copy(alpha = 0.65f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Erfasster Markt-Richtwert", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (matchedCount > 0) knownSum.euro() else "Noch keine Preisdaten",
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                        color = KidPalette.Ink
                    )
                    Text("$matchedCount von $totalCount Karten mit Richtwert · $missing Positionen ohne belastbaren Preis")
                    Text(
                        "Kein garantierter Verkaufswert. Europaweite Preisdaten sind nicht automatisch Preise für deutsche Karten.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && collection.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val ids = collection.map { it.card.id }.distinct()
                                    val batch = ids.take(80)
                                    val refreshed = mutableMapOf<String, CardData>()
                                    var failures = 0
                                    batch.forEachIndexed { index, id ->
                                        status = "Karte ${index + 1} von ${batch.size} wird aktualisiert …"
                                        runCatching { repository.getCard(id) }
                                            .onSuccess { refreshed[id] = it }
                                            .onFailure { failures++ }
                                        delay(150)
                                    }
                                    if (refreshed.isNotEmpty()) {
                                        onReplaceCollection(collection.map { entry ->
                                            refreshed[entry.card.id]?.let { entry.copy(card = it) } ?: entry
                                        })
                                    }
                                    status = "${refreshed.size} Karten aktualisiert" +
                                        (if (failures > 0) " · $failures fehlgeschlagen" else "") +
                                        (if (ids.size > 80) " · weitere ${ids.size - 80} beim nächsten Aktualisieren" else "")
                                } finally { busy = false }
                            }
                        }
                    ) { Text(if (busy) "Marktdaten werden aktualisiert …" else "🔄 Alle Preise aktualisieren") }
                    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            CardmarketKeyPanel(
                api = liveApi,
                hasKey = hasLiveKey,
                onKeyChange = { hasLiveKey = it }
            )
        }
        if (collection.isEmpty()) item {
            KidTipCard("Noch keine Karten", "Scanne Karten, dann erscheinen die Bewertungen hier.")
        }
        items(sorted, key = { (entry, _) ->
            entry.card.id + "|" + entry.variant + "|" + entry.language + "|" + entry.condition
        }) { (entry, valuation) ->
            MarketCardPanel(entry, valuation, liveApi, hasLiveKey) { replacement ->
                onReplaceCollection(collection.map {
                    if (it.card.id == entry.card.id && it.variant == entry.variant &&
                        it.language == entry.language && it.condition == entry.condition)
                        replacement else it
                })
            }
        }
    }
}

@Composable
private fun MarketCardPanel(
    entry: CollectionEntry,
    valuation: MarketValuation,
    api: CardmarketLiveApi,
    hasKey: Boolean,
    onChange: (CollectionEntry) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var inputPrice by remember(entry.card.id, entry.variant, entry.language, entry.condition, entry.referenceSalePriceEur) {
        mutableStateOf(entry.referenceSalePriceEur?.let { String.format(Locale.GERMANY, "%.2f", it) } ?: "")
    }
    var source by remember(entry.card.id, entry.variant, entry.language, entry.condition) {
        mutableStateOf(entry.referenceSaleSource ?: "eBay · verkauft")
    }
    var message by remember { mutableStateOf("") }
    val card = entry.card

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, KidPalette.Purple.copy(alpha = 0.38f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(model = card.imageUrl, contentDescription = card.name,
                    modifier = Modifier.width(76.dp).height(107.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.name, fontWeight = FontWeight.ExtraBold)
                    Text(card.setName + " · " + card.localId + " · " + entry.variant,
                        style = MaterialTheme.typography.bodySmall)
                    Text(entry.language + " · Zustand " + entry.condition + " · " + entry.quantity + "×",
                        style = MaterialTheme.typography.bodySmall)
                    Text(valuation.unitPrice?.let { it.euro() + " je Karte" } ?: "Kein belastbarer Preis",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    if (valuation.unitPrice != null && entry.quantity > 1)
                        Text("Gesamt: " + (valuation.unitPrice * entry.quantity).euro())
                }
            }
            Text(valuation.confidence + " · " + valuation.source, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "Preisdetails schließen ▲" else "🔎 Preisquellen & Genauigkeit ▼")
            }
            if (expanded) {
                HorizontalDivider()
                Text(valuation.note, style = MaterialTheme.typography.bodySmall)
                Text(valuation.ageDays?.let { "Quelldatum: vor $it Tagen" }
                    ?: "Kein verlässliches Aktualisierungsdatum", style = MaterialTheme.typography.bodySmall)
                if (valuation.rangeLow != null && valuation.rangeHigh != null)
                    Text("Vergleichsspanne der EU-Kennzahlen: " + valuation.rangeLow.euro() +
                        " bis " + valuation.rangeHigh.euro(), style = MaterialTheme.typography.bodySmall)
                MarketTimeline(card, entry.variant)
                CardmarketPricePanel(entry, hasKey, api, onChange)
                val usd = when (entry.variant) {
                    "Holo" -> card.usMarketHolo
                    "Reverse" -> card.usMarketReverse
                    else -> card.usMarketNormal
                }
                if (MarketValueEngine.clean(usd) != null) {
                    Text(
                        "TCGplayer (US-Markt): USD " + String.format(Locale.US, "%.2f", usd) +
                        " · getrennte Währung; nicht in den €-Wert eingerechnet.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("Sprache und Zustand", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("DE", "EN", "JP").forEach { language ->
                        FilterChip(selected = entry.language == language,
                            onClick = { onChange(entry.copy(language = language,
                                referenceSalePriceEur = null, referenceSaleSource = null, referenceSaleAt = null)) },
                            label = { Text(language) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NM", "EX", "GD", "LP").forEach { grade ->
                        FilterChip(selected = entry.condition == grade,
                            onClick = { onChange(entry.copy(condition = grade,
                                referenceSalePriceEur = null, referenceSaleSource = null, referenceSaleAt = null)) },
                            label = { Text(grade) })
                    }
                }
                HorizontalDivider()
                Text("Tatsächlichen Vergleichsverkauf erfassen", fontWeight = FontWeight.Bold)
                Text(
                    "Nur gleiche Kartennummer, Set, Sprache, Variante und vergleichbarer Zustand. Eingaben werden nicht automatisch überprüft.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(value = inputPrice,
                    onValueChange = { inputPrice = it.take(14); message = "" },
                    label = { Text("Verkaufspreis in €") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("eBay · verkauft", "Cardmarket · Verkauf").forEach { option ->
                        FilterChip(selected = source == option, onClick = { source = option },
                            label = { Text(option) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(onClick = {
                        val price = inputPrice.trim().replace(",", ".").toDoubleOrNull()
                        if (MarketValueEngine.clean(price) != null) {
                            onChange(entry.copy(referenceSalePriceEur = price,
                                referenceSaleSource = source,
                                referenceSaleAt = System.currentTimeMillis()))
                            message = "Vergleichspreis gespeichert."
                        } else message = "Bitte einen gültigen positiven Eurobetrag eingeben."
                    }) { Text("Speichern") }
                    if (entry.referenceSalePriceEur != null) {
                        OutlinedButton(onClick = {
                            inputPrice = ""
                            onChange(entry.copy(referenceSalePriceEur = null,
                                referenceSaleSource = null, referenceSaleAt = null))
                        }) { Text("Löschen") }
                    }
                }
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                MarketResearchBlock(entry)
            }
        }
    }
}

@Composable
private fun MarketTimeline(card: CardData, variant: String) {
    if (variant == "Reverse") return
    val holo = variant == "Holo"
    val values = listOf(
        "24-Stunden-Ø" to (if (holo) card.priceAvg1Holo else card.priceAvg1),
        "7-Tage-Ø" to (if (holo) card.priceAvg7Holo else card.priceAvg7),
        "30-Tage-Ø" to (if (holo) card.priceAvg30Holo else card.priceAvg30),
        "Trend" to (if (holo) card.priceTrendHolo else card.priceTrend)
    )
    Text("Cardmarket – europäische Marktdaten", fontWeight = FontWeight.SemiBold)
    values.forEach { (name, price) ->
        MarketValueEngine.clean(price)?.let {
            Text(name + ": " + it.euro(), style = MaterialTheme.typography.bodySmall)
        }
    }
}
