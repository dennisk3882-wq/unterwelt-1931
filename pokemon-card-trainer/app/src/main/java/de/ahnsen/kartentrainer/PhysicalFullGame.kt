package de.ahnsen.kartentrainer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PhysicalFullGameScreen(
    collection: List<CollectionEntry>,
    modifier: Modifier = Modifier,
    onGameFinished: (String) -> Unit = {}
) {
    var difficultyName by rememberSaveable { mutableStateOf(AiDifficulty.NORMAL.name) }
    var startModeName by rememberSaveable { mutableStateOf(FullStartMode.COIN_FLIP.name) }
    var engine by remember { mutableStateOf<FullGameEngine?>(null) }
    var snapshot by remember { mutableStateOf<FullGameSnapshot?>(null) }
    var selectedTarget by rememberSaveable { mutableIntStateOf(0) }
    var activeSynced by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf("Partie konfigurieren und starten.") }
    var batchMode by rememberSaveable { mutableStateOf(true) }
    var reportedResult by remember { mutableStateOf("") }

    LaunchedEffect(snapshot?.finished, snapshot?.winner) {
        val state = snapshot
        if (state?.finished == true && !state.winner.isNullOrBlank() && reportedResult != state.winner) {
            onGameFinished(state.winner.orEmpty())
            reportedResult = state.winner.orEmpty()
        }
    }

    val difficulty = AiDifficulty.valueOf(difficultyName)
    val startMode = FullStartMode.valueOf(startModeName)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
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
                    Row {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Echte Karten · Vollspiel 2.0",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Die Kamera bleibt auf dem Tisch. Jede neu erkannte Karte wird in dieselbe 60-Karten-/Effekt-/KI-Engine wie im digitalen Vollspiel übernommen."
                    )
                }
            }
        }

        if (snapshot == null) {
            item {
                Text("KI-Stufe", fontWeight = FontWeight.Bold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiDifficulty.entries.forEach { level ->
                        FilterChip(
                            selected = difficulty == level,
                            onClick = { difficultyName = level.name },
                            label = { Text(level.label) }
                        )
                    }
                }
            }
            item {
                Text("Startspieler", fontWeight = FontWeight.Bold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FullStartMode.entries.forEach { mode ->
                        FilterChip(
                            selected = startMode == mode,
                            onClick = { startModeName = mode.name },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
            item {
                Button(
                    enabled = collection.any { it.card.isBasicPokemon() },
                    onClick = {
                        val newEngine = FullGameEngine(
                            entries = collection,
                            difficulty = difficulty,
                            standardOnly = false,
                            startMode = startMode
                        )
                        engine = newEngine
                        snapshot = newEngine.snapshot()
                        activeSynced = false
                        status = "Scanne jetzt dein reales aktives Basis-Pokémon. Danach kann die Kamera ausgespielte Karten automatisch übernehmen."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Physische Vollspiel-Partie starten")
                }
            }
        }

        snapshot?.let { state ->
            item {
                FullStatusHeader(state)
            }

            item {
                Text("KI-Feld", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FullActivePokemon(state.ai.active, "KI aktiv")
                FullBench(state.ai.bench, "KI-Bank")
            }

            item {
                Text("Dein reales Feld", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FullActivePokemon(state.player.active, "Du aktiv")
                FullBench(state.player.bench, "Deine Bank")
            }

            item {
                OutlinedCard {
                    Text(status, modifier = Modifier.padding(12.dp))
                }
            }

            if (activeSynced && !state.finished) {
                item {
                    Text("Ziel für nächste gescannte Karte", fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.player.active?.let { active ->
                            FilterChip(
                                selected = selectedTarget == 0,
                                onClick = { selectedTarget = 0 },
                                label = { Text("Aktiv: " + active.card.name) }
                            )
                        }
                        state.player.bench.forEachIndexed { index, bench ->
                            FilterChip(
                                selected = selectedTarget == index + 1,
                                onClick = { selectedTarget = index + 1 },
                                label = { Text("Bank: " + bench.card.name) }
                            )
                        }
                    }
                }

                item {
                    val advice = engine?.coachAdvice()
                    if (advice != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row {
                                    Icon(Icons.Default.Psychology, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("KI-Coach", fontWeight = FontWeight.Bold)
                                }
                                Text(advice.headline)
                                Text(advice.detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = batchMode,
                        onClick = { batchMode = true },
                        label = { Text("Live-Batch") }
                    )
                    FilterChip(
                        selected = !batchMode,
                        onClick = { batchMode = false },
                        label = { Text("Einzelscan") }
                    )
                }
                Spacer(Modifier.height(6.dp))
                SmartCameraScanner(
                    batchMode = batchMode,
                    modifier = Modifier.fillMaxWidth(),
                    onResult = { scan ->
                        val spatial = scan.trackedObjects
                            .groupingBy { it.zone }
                            .eachCount()
                            .entries
                            .joinToString { it.key + " " + it.value }
                        val card = bestCollectionMatch(scan.text, collection)
                        if (card == null) {
                            status = "Karte nicht eindeutig in deiner Sammlung gefunden. Sicherheit " + scan.confidence + "%."
                        } else {
                            val current = engine
                            if (current == null) {
                                status = "Starte zuerst die Partie."
                            } else if (!activeSynced) {
                                if (card.isBasicPokemon()) {
                                    snapshot = current.setPhysicalActive(card)
                                    activeSynced = true
                                    status = card.name + " als reales aktives Pokémon synchronisiert." +
                                        if (spatial.isNotBlank()) " · Kamera: " + spatial else ""
                                } else {
                                    status = "Für den Start brauche ich dein reales Basis-Pokémon. Erkannt: " + card.name
                                }
                            } else {
                                snapshot = current.synchronizePhysicalCard(card, selectedTarget)
                                status = card.name + " wurde in den Vollspiel-Zustand übernommen." +
                                    if (spatial.isNotBlank()) " · Kamera: " + spatial else ""
                            }
                        }
                    },
                    onError = { status = it }
                )
            }

            if (activeSynced && !state.finished) {
                state.player.active?.let { active ->
                    item {
                        Text("Reale Attacke ausführen", fontWeight = FontWeight.Bold)
                        Text(
                            "Tippe die Attacke an, nachdem du sie mit der echten Karte angesagt hast. Danach spielt die KI ihren vollständigen Zug.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    items(active.card.attacks.indices.toList()) { index ->
                        val attack = active.card.attacks[index]
                        Button(
                            onClick = {
                                snapshot = engine?.playerAttack(index)
                                status = "Attacke " + attack.name + " verarbeitet; KI-Zug wurde berechnet."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                attack.name + " · Kosten " + attack.cost.joinToString("/").ifBlank { "0" } +
                                    " · " + attack.damage.ifBlank { "Effekt" }
                            )
                        }
                    }
                }

                if (state.player.bench.isNotEmpty()) {
                    item {
                        Text("Rückzug", fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.player.bench.forEachIndexed { index, bench ->
                                OutlinedButton(
                                    onClick = {
                                        snapshot = engine?.retreatPlayer(index)
                                        selectedTarget = 0
                                        status = "Rückzug in der Vollspiel-Engine verarbeitet."
                                    }
                                ) {
                                    Text(bench.card.name + " nach vorn")
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            snapshot = engine?.endTurnWithoutAttack()
                            status = "Zug beendet; KI hat ihren Zug gespielt."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Zug ohne Angriff beenden")
                    }
                }
            }

            if (state.lastAiReasoning.isNotBlank() && state.lastAiReasoning != "Die KI hat noch keinen Zug gemacht.") {
                item {
                    OutlinedCard {
                        Column(Modifier.padding(12.dp)) {
                            Text("Warum hat die KI so gespielt?", fontWeight = FontWeight.Bold)
                            Text(state.lastAiReasoning)
                        }
                    }
                }
            }

            if (state.finished) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Partie beendet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Gewinner: " + state.winner.orEmpty())
                        }
                    }
                }
            }

            item {
                Text("Regel-/Scan-Protokoll", fontWeight = FontWeight.Bold)
                state.log.takeLast(18).reversed().forEach {
                    Text("• " + it, style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        engine = null
                        snapshot = null
                        activeSynced = false
                        selectedTarget = 0
                        status = "Partie konfigurieren und starten."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Text(" Neue physische Partie")
                }
            }
        }
    }
}
