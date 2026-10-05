package de.ahnsen.kartentrainer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun AdvancedCollectionScreen(
    padding: PaddingValues,
    collection: List<CollectionEntry>,
    repository: TcgDexRepository,
    onChangeQuantity: (CollectionEntry, Int) -> Unit,
    onReplaceCollection: (List<CollectionEntry>) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Alle") }
    var standardOnly by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("Name") }
    var refreshing by remember { mutableStateOf(false) }
    var refreshStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val filtered = remember(collection, query, category, standardOnly, sort) {
        collection.filter { entry ->
            val q = query.trim().lowercase()
            val matchesQuery = q.isBlank() ||
                entry.card.name.lowercase().contains(q) ||
                entry.card.setName.lowercase().contains(q) ||
                entry.card.rarity.lowercase().contains(q) ||
                entry.card.types.any { it.lowercase().contains(q) }
            val matchesCategory = when (category) {
                "Pokémon" -> entry.card.isPokemon()
                "Trainer" -> entry.card.isTrainer()
                "Energie" -> entry.card.isEnergy()
                else -> true
            }
            val matchesLegal = !standardOnly || entry.card.isStandardPlayable()
            matchesQuery && matchesCategory && matchesLegal
        }.let { list ->
            when (sort) {
                "Wert" -> list.sortedByDescending { it.totalEstimatedValue ?: -1.0 }
                "Set" -> list.sortedWith(compareBy<CollectionEntry> { it.card.setName }.thenBy { it.card.name })
                "Seltenheit" -> list.sortedWith(compareBy<CollectionEntry> { it.card.rarity }.thenBy { it.card.name })
                else -> list.sortedBy { it.card.name }
            }
        }
    }

    val totalCards = collection.sumOf { it.quantity }
    val uniqueCards = collection.size
    val sets = collection.map { it.card.setName }.filter { it.isNotBlank() }.distinct().size
    val totalValue = collection.mapNotNull { it.totalEstimatedValue }.sum()
    val standardCards = collection.filter { it.card.isStandardPlayable() }.sumOf { it.quantity }
    val holoCards = collection.filter { it.variant == "Holo" || it.variant == "Reverse" }.sumOf { it.quantity }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sammlungsanalyse", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MiniStat("Karten", totalCards.toString())
                        MiniStat("Einzigartig", uniqueCards.toString())
                        MiniStat("Sets", sets.toString())
                        MiniStat("Wert", if (totalValue > 0) totalValue.euro() else "–")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Standard: $standardCards · Holo/Reverse: $holoCards",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Name, Set, Seltenheit oder Typ suchen") },
                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Alle", "Pokémon", "Trainer", "Energie").forEach { value ->
                    FilterChip(
                        selected = category == value,
                        onClick = { category = value },
                        label = { Text(value) }
                    )
                }
                FilterChip(
                    selected = standardOnly,
                    onClick = { standardOnly = !standardOnly },
                    label = { Text("Nur Standard") }
                )
            }
        }

        item {
            Text("Sortierung", fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Name", "Wert", "Set", "Seltenheit").forEach { value ->
                    FilterChip(
                        selected = sort == value,
                        onClick = { sort = value },
                        label = { Text(value) }
                    )
                }
            }
        }

        item {
            Button(
                enabled = !refreshing && collection.isNotEmpty(),
                onClick = {
                    scope.launch {
                        refreshing = true
                        refreshStatus = "Preise und Kartendaten werden aktualisiert …"
                        val cache = mutableMapOf<String, CardData>()
                        var ok = 0
                        val updated = collection.map { entry ->
                            val fresh = cache[entry.card.id] ?: runCatching {
                                repository.getCard(entry.card.id)
                            }.getOrNull()?.also {
                                cache[entry.card.id] = it
                                ok += 1
                            }
                            if (fresh != null) entry.copy(card = fresh) else entry
                        }
                        onReplaceCollection(updated)
                        refreshStatus = "$ok Kartendatensätze aktualisiert."
                        refreshing = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Alle Preise & Kartendaten aktualisieren")
            }
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (refreshStatus.isNotBlank()) {
                Text(refreshStatus, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Text(
                "Treffer: " + filtered.size,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(filtered, key = { it.card.id + "|" + it.variant }) { entry ->
            OutlinedCard {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = entry.card.imageUrl,
                        contentDescription = entry.card.name,
                        modifier = Modifier.size(width = 66.dp, height = 92.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.card.name, fontWeight = FontWeight.Bold)
                        Text(
                            entry.card.setName + " · " + entry.variant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            entry.card.legalityText(),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            entry.card.estimatedPrice(entry.variant)?.let { "ca. " + it.euro() } ?: "kein Preis",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(entry.quantity.toString() + "×", fontWeight = FontWeight.Bold)
                        Row {
                            OutlinedButton(
                                onClick = { onChangeQuantity(entry, -1) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) { Text("−") }
                            Spacer(Modifier.width(4.dp))
                            OutlinedButton(
                                onClick = { onChangeQuantity(entry, 1) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) { Text("+") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun LocalSettingsScreen(
    padding: PaddingValues,
    store: LocalAppStore,
    profiles: List<LocalProfile>,
    activeProfileId: String,
    collection: List<CollectionEntry>,
    stats: PlayerStats,
    onProfilesChanged: () -> Unit,
    onActiveProfileChanged: (String) -> Unit,
    onImported: () -> Unit
) {
    val context = LocalContext.current
    var newProfileName by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(store.exportProfile(activeProfileId).toByteArray())
                }
            }.onSuccess {
                message = "Lokale Sicherung wurde gespeichert."
            }.onFailure {
                message = "Export fehlgeschlagen: " + (it.message ?: "")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Datei konnte nicht gelesen werden.")
                store.importIntoProfile(activeProfileId, raw)
            }.onSuccess {
                message = "Sicherung wurde importiert."
                onImported()
            }.onFailure {
                message = "Import fehlgeschlagen: " + (it.message ?: "")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Lokal & ohne Cloud", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Profile, Sammlung, Decks, Statistiken und Backups bleiben auf dem Gerät. Es entstehen keine Cloudkosten.")
                }
            }
        }

        item {
            Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(profiles, key = { it.id }) { profile ->
            OutlinedCard {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, fontWeight = FontWeight.SemiBold)
                        if (profile.id == activeProfileId) {
                            Text("Aktiv", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    if (profile.id != activeProfileId) {
                        OutlinedButton(onClick = { onActiveProfileChanged(profile.id) }) {
                            Text("Wechseln")
                        }
                    }
                    if (profiles.size > 1) {
                        OutlinedButton(
                            onClick = {
                                store.deleteProfile(profile.id)
                                onProfilesChanged()
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Profil löschen")
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = newProfileName,
                onValueChange = { newProfileName = it },
                label = { Text("Neues Profil") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    val p = store.addProfile(newProfileName)
                    newProfileName = ""
                    onProfilesChanged()
                    onActiveProfileChanged(p.id)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Profil anlegen")
            }
        }

        item {
            Text("Lokales Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Enthält Sammlung, gespeicherte Decks und Spielstatistiken des aktiven Profils.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    exportLauncher.launch("TCG-Karten-Coach-Backup.json")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Backup, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Backup exportieren")
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Backup importieren")
            }
        }

        item {
            OutlinedCard {
                Column(Modifier.padding(14.dp)) {
                    Text("Lokale Statistik", fontWeight = FontWeight.Bold)
                    Text("Sammlung: " + collection.sumOf { it.quantity } + " Karten")
                    Text("Partien: " + stats.games)
                    Text("Siege: " + stats.wins + " · Niederlagen: " + stats.losses)
                    Text("Siegquote: " + stats.winRate + "%")
                    Text("Coach-Hinweise genutzt: " + stats.coachHintsUsed)
                    Text("Gescannt: " + stats.scannedCards)
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
