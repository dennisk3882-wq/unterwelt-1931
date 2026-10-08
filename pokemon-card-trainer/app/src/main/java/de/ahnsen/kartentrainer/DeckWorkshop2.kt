package de.ahnsen.kartentrainer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.UUID

@Composable
fun AdvancedDeckWorkshopScreen(
    padding: PaddingValues,
    collection: List<CollectionEntry>,
    store: LocalAppStore,
    profileId: String
) {
    var formatName by rememberSaveable { mutableStateOf(DeckFormat.STANDARD.name) }
    val format = DeckFormat.valueOf(formatName)
    var deckName by rememberSaveable { mutableStateOf("Mein Deck") }
    var editorQuery by rememberSaveable { mutableStateOf("") }
    var deck by remember(collection, formatName) {
        mutableStateOf(AdvancedDeckEngine.autoBuild(collection, format))
    }
    var savedDecks by remember(profileId) { mutableStateOf(store.loadDecks(profileId)) }
    var message by remember { mutableStateOf("") }

    fun updateCard(entry: CollectionEntry, delta: Int) {
        val current = deck.toMutableList()
        val index = current.indexOfFirst { it.card.id == entry.card.id && it.variant == entry.variant }
        val sameNameCount = current.filter { it.card.name == entry.card.name }.sumOf { it.quantity }
        val owned = entry.quantity
        if (delta > 0) {
            val currentQty = current.getOrNull(index)?.quantity ?: 0
            val limit = if (entry.card.isBasicEnergy()) owned else minOf(4, owned)
            if (currentQty >= limit) {
                message = "Von ${entry.card.name} kann keine weitere Karte dieses Exemplars hinzugefügt werden."
                return
            }
            if (!entry.card.isBasicEnergy() && sameNameCount >= 4) {
                message = "Viererlimit für ${entry.card.name} erreicht."
                return
            }
            if (deck.sumOf { it.quantity } >= 60) {
                message = "Deck enthält bereits 60 Karten. Entferne zuerst eine Karte."
                return
            }
            if (index >= 0) {
                current[index] = current[index].copy(quantity = current[index].quantity + 1)
            } else {
                current += DeckCardChoice(entry.card, entry.variant, 1)
            }
        } else if (index >= 0) {
            val next = current[index].quantity - 1
            if (next <= 0) current.removeAt(index)
            else current[index] = current[index].copy(quantity = next)
        }
        deck = current
        message = ""
    }

    fun saveCurrent() {
        val analysis = AdvancedDeckEngine.analyze(deck, format)
        val existingId = savedDecks.firstOrNull { it.name.equals(deckName, true) }?.id
        val saved = SavedDeck(
            id = existingId ?: UUID.randomUUID().toString(),
            name = deckName.trim().ifBlank { "Mein Deck" },
            standardOnly = format == DeckFormat.STANDARD,
            formatName = format.name,
            cards = deck.map {
                SavedDeckCard(
                    cardId = it.card.id,
                    variant = it.variant,
                    quantity = it.quantity
                )
            },
            strength = analysis.strength,
            archetype = analysis.archetype
        )
        savedDecks = savedDecks.filterNot { it.id == saved.id } + saved
        store.saveDecks(profileId, savedDecks)
        message = "Deck '${saved.name}' lokal gespeichert."
    }

    fun loadSaved(saved: SavedDeck) {
        val resolved = saved.cards.mapNotNull { ref ->
            val entry = collection.firstOrNull {
                it.card.id == ref.cardId && it.variant == ref.variant
            } ?: collection.firstOrNull { it.card.id == ref.cardId }
            entry?.let {
                DeckCardChoice(
                    card = it.card,
                    variant = it.variant,
                    quantity = ref.quantity.coerceAtMost(it.quantity)
                )
            }
        }
        deckName = saved.name
        formatName = saved.formatName
        deck = resolved
        message = "Deck '${saved.name}' geladen."
    }

    val analysis = remember(deck, formatName) {
        AdvancedDeckEngine.analyze(deck, format)
    }
    val missing = remember(collection, deck, formatName) {
        AdvancedDeckEngine.missingCardSuggestions(collection, deck, format)
    }

    val editorCards = remember(collection, editorQuery, formatName) {
        val q = editorQuery.trim().lowercase()
        collection
            .filter { it.card.isPlayableIn(format) }
            .filter {
                q.isBlank() ||
                    it.card.name.lowercase().contains(q) ||
                    it.card.setName.lowercase().contains(q) ||
                    it.card.types.any { type -> type.lowercase().contains(q) }
            }
            .sortedWith(
                compareBy<CollectionEntry> {
                    when {
                        it.card.isPokemon() -> 0
                        it.card.isTrainer() -> 1
                        else -> 2
                    }
                }.thenBy { it.card.name }
            )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            KidHeroBanner(
                title = "🧩 Bau dein Traum-Deck",
                subtitle = "Nimm deine gescannten Karten, probiere Kombinationen aus und lass dir zeigen, was noch besser zusammenpasst.",
                accent = KidPalette.Pink
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = KidPalette.SoftPink.copy(alpha = 0.90f)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Style, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "🧩 Deck-Werkstatt",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = KidPalette.Pink
                        )
                    }
                    Text("Automatisch optimieren, manuell bearbeiten, analysieren und mehrere Decks lokal speichern.")
                }
            }
        }

        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeckFormat.entries.forEach { option ->
                    FilterChip(
                        selected = format == option,
                        onClick = {
                            formatName = option.name
                            deck = AdvancedDeckEngine.autoBuild(collection, option)
                        },
                        label = { Text(option.label) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    deck = AdvancedDeckEngine.autoBuild(collection, format)
                    message = "Deck automatisch neu optimiert."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Bestes Deck aus Sammlung neu berechnen")
            }
        }

        item {
            AnalysisCard(analysis)
        }

        if (analysis.issues.isNotEmpty()) {
            item {
                OutlinedCard {
                    Column(Modifier.padding(12.dp)) {
                        Text("Probleme", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        analysis.issues.forEach { Text("• $it") }
                    }
                }
            }
        }

        item {
            OutlinedCard {
                Column(Modifier.padding(12.dp)) {
                    Text("Verbesserungen", fontWeight = FontWeight.Bold)
                    (analysis.suggestions + missing).distinct().take(12).forEach {
                        Text("• $it")
                    }
                }
            }
        }

        item {
            Text("Aktuelles Deck · ${analysis.totalCards}/60", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(deck, key = { it.card.id + "|" + it.variant }) { choice ->
            OutlinedCard {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = choice.card.imageUrl,
                        contentDescription = choice.card.name,
                        modifier = Modifier.width(54.dp).height(76.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(choice.quantity.toString() + "×", fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                    Column(Modifier.weight(1f)) {
                        Text(choice.card.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            choice.card.category + " · " + choice.card.setName,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    val source = collection.firstOrNull {
                        it.card.id == choice.card.id && it.variant == choice.variant
                    }
                    if (source != null) {
                        OutlinedButton(
                            onClick = { updateCard(source, -1) },
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp)
                        ) { Text("−") }
                        Spacer(Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = { updateCard(source, 1) },
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp)
                        ) { Text("+") }
                    }
                }
            }
        }

        item {
            Text("Karten hinzufügen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = editorQuery,
                onValueChange = { editorQuery = it },
                label = { Text("Sammlung durchsuchen") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        items(editorCards.take(60), key = { "add|" + it.card.id + "|" + it.variant }) { entry ->
            val inDeck = deck.filter { it.card.id == entry.card.id && it.variant == entry.variant }.sumOf { it.quantity }
            OutlinedCard {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = entry.card.imageUrl,
                        contentDescription = entry.card.name,
                        modifier = Modifier.width(48.dp).height(68.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.card.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Sammlung ${entry.quantity}× · im Deck ${inDeck}×",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(onClick = { updateCard(entry, 1) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(" Hinzu")
                    }
                }
            }
        }

        item {
            Text("Deck speichern", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = deckName,
                onValueChange = { deckName = it },
                label = { Text("Deckname") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { saveCurrent() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Lokal speichern")
            }
        }

        if (savedDecks.isNotEmpty()) {
            item {
                Text("Gespeicherte Decks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(savedDecks, key = { it.id }) { saved ->
                OutlinedCard {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(saved.name, fontWeight = FontWeight.Bold)
                            Text(
                                saved.archetype + " · Stärke " + saved.strength + "/100",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(onClick = { loadSaved(saved) }) {
                            Text("Laden")
                        }
                        Spacer(Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = {
                                savedDecks = savedDecks.filterNot { it.id == saved.id }
                                store.saveDecks(profileId, savedDecks)
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Löschen")
                        }
                    }
                }
            }
        }

        if (message.isNotBlank()) {
            item {
                OutlinedCard {
                    Text(message, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun AnalysisCard(analysis: DeckAnalysis) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (analysis.legality >= 100 && analysis.totalCards == 60) {
                KidPalette.SoftGreen
            } else {
                KidPalette.SoftYellow
            }
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Stärke " + analysis.strength + "/100 · " + analysis.archetype,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            AnalysisMeter("Legalität", analysis.legality)
            AnalysisMeter("Konsistenz", analysis.consistency)
            AnalysisMeter("Synergie", analysis.synergy)
            AnalysisMeter("Energiepassung", analysis.energyFit)
            Spacer(Modifier.height(6.dp))
            Text(
                "Pokémon ${analysis.pokemonCount} · Trainer ${analysis.trainerCount} · Energie ${analysis.energyCount} · Basis ${analysis.basicPokemonCount}",
                style = MaterialTheme.typography.bodySmall
            )
            if (analysis.typeFocus.isNotEmpty()) {
                Text("Typfokus: " + analysis.typeFocus.joinToString(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AnalysisMeter(label: String, value: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value.toString() + "%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
    LinearProgressIndicator(
        progress = { value.coerceIn(0, 100) / 100f },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(5.dp))
}
