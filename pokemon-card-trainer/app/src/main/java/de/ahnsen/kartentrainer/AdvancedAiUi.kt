package de.ahnsen.kartentrainer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.max

@Composable
fun AdvancedDigitalBattleScreen(
    collection: List<CollectionEntry>,
    modifier: Modifier = Modifier
) {
    val available = remember(collection) { AiBattleFactory.availablePokemon(collection) }
    var selectedLeadId by rememberSaveable(available.size) {
        mutableStateOf(available.firstOrNull()?.id.orEmpty())
    }
    var difficultyName by rememberSaveable { mutableStateOf(AiDifficulty.NORMAL.name) }
    val difficulty = AiDifficulty.valueOf(difficultyName)
    var engine by remember { mutableStateOf<StrategicTrainingBattle?>(null) }
    var snapshot by remember { mutableStateOf<StrategicBattleSnapshot?>(null) }
    var selectedEnergyTarget by rememberSaveable { mutableIntStateOf(0) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Strategische KI 1.1",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Die KI bewertet K.-o.-Chancen, Energiekurve, Rest-KP, Angriffseffizienz und den nächsten möglichen Gegenangriff.")
                        }
                    }
                }
            }
        }

        if (available.isEmpty()) {
            item {
                AiInfoCard("Scanne zuerst mindestens eine Pokémon-Karte mit einer Attacke. Je mehr unterschiedliche Pokémon in deiner Sammlung sind, desto abwechslungsreicher kann die KI spielen.")
            }
            return@LazyColumn
        }

        if (snapshot == null) {
            item {
                Text("Schwierigkeitsgrad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                Spacer(Modifier.height(6.dp))
                Text(
                    difficulty.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Text("Dein Start-Pokémon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    available.distinctBy { it.id }.forEach { card ->
                        FilterChip(
                            selected = selectedLeadId == card.id,
                            onClick = { selectedLeadId = card.id },
                            label = { Text(card.name) }
                        )
                    }
                }
            }

            item {
                val playerTeam = AiBattleFactory.buildPlayerTeam(collection, selectedLeadId)
                val aiTeam = AiBattleFactory.buildAiTeam(collection, selectedLeadId, difficulty)
                OutlinedCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("Vorschau", fontWeight = FontWeight.Bold)
                        Text("Dein Team: " + playerTeam.joinToString { it.name })
                        Text("KI-Team: " + aiTeam.joinToString { it.name })
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Es wird mit aktivem Pokémon, bis zu fünf Bank-Pokémon, Energieaufbau und sechs Preiskarten trainiert. Komplexe Kartentexte, die noch nicht vollständig maschinell auflösbar sind, werden weiterhin als Hinweis angezeigt.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val playerTeam = AiBattleFactory.buildPlayerTeam(collection, selectedLeadId)
                        val aiTeam = AiBattleFactory.buildAiTeam(collection, selectedLeadId, difficulty)
                        if (playerTeam.isNotEmpty() && aiTeam.isNotEmpty()) {
                            val newEngine = StrategicTrainingBattle(playerTeam, aiTeam, difficulty)
                            engine = newEngine
                            snapshot = newEngine.snapshot()
                            selectedEnergyTarget = 0
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Strategischen KI-Kampf starten")
                }
            }
        }

        snapshot?.let { state ->
            item {
                BattleHeader(state)
            }

            item {
                Text("KI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StrategicActiveCard(state.ai.active, "Aktiv", Modifier.fillMaxWidth())
                BenchRow(state.ai.bench, "KI-Bank")
            }

            item {
                Text("Du", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StrategicActiveCard(state.player.active, "Aktiv", Modifier.fillMaxWidth())
                BenchRow(state.player.bench, "Deine Bank")
            }

            if (state.playerNeedsPromotion && !state.finished) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Wähle dein neues aktives Pokémon", fontWeight = FontWeight.Bold)
                            Text("Dein vorheriges Pokémon wurde kampfunfähig. Jetzt entscheidest du, wer von der Bank nach vorne kommt.")
                        }
                    }
                }
                items(state.player.bench.indices.toList()) { index ->
                    val pokemon = state.player.bench[index]
                    Button(
                        onClick = {
                            snapshot = engine?.promotePlayer(index)
                            selectedEnergyTarget = 0
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(pokemon.card.name + " einwechseln · " + pokemon.hp + " KP · " + pokemon.energy + " Energie")
                    }
                }
            }

            if (!state.playerNeedsPromotion && !state.finished) {
                item {
                    val advice = engine?.coachAdvice()
                    if (advice != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("KI-Coach: " + advice.headline, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(advice.detail)
                            }
                        }
                    }
                }

                item {
                    val energyTargets = buildList {
                        state.player.active?.let { add(0 to it) }
                        state.player.bench.forEachIndexed { index, p -> add((index + 1) to p) }
                    }
                    Text("Energie anlegen", fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        energyTargets.forEach { (index, pokemon) ->
                            FilterChip(
                                selected = selectedEnergyTarget == index,
                                onClick = { selectedEnergyTarget = index },
                                label = {
                                    Text(
                                        pokemon.card.name + " (" + pokemon.energy + ")",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            snapshot = engine?.attachPlayerEnergy(selectedEnergyTarget)
                        },
                        enabled = state.playerCanAttach,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.playerCanAttach) "1 Energie an ausgewähltes Pokémon" else "Energie für diesen Zug bereits angelegt")
                    }
                }

                state.player.active?.let { active ->
                    item {
                        Text("Attacken", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Angreifen beendet deinen Zug. Danach plant die KI automatisch Energie und Attacke.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(active.card.attacks.indices.toList()) { index ->
                        val attack = active.card.attacks[index]
                        val cost = max(1, attack.cost.size)
                        val estimated = estimatedAttackDamage(attack, active.energy)
                        OutlinedButton(
                            onClick = {
                                snapshot = engine?.playerAttack(index)
                                selectedEnergyTarget = 0
                            },
                            enabled = active.energy >= cost,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    attack.name + " · Kosten " + cost + " · ca. " + estimated + " Schaden",
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (active.energy < cost) {
                                    Text(
                                        "Noch " + (cost - active.energy) + " Energie nötig",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else if (attack.effect.isNotBlank()) {
                                    Text(
                                        attack.effect,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.lastAiReasoning.isNotBlank() && state.lastAiReasoning != "Die KI hat noch keinen Zug gemacht.") {
                item {
                    OutlinedCard {
                        Column(Modifier.padding(14.dp)) {
                            Text("Warum hat die KI das gemacht?", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
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
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("Kampf beendet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Gewinner: " + state.winner.orEmpty())
                        }
                    }
                }
            }

            item {
                Text("Spielprotokoll", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                state.log.takeLast(14).reversed().forEach { entry ->
                    Text("• " + entry, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        engine = null
                        snapshot = null
                        selectedEnergyTarget = 0
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Neuen KI-Kampf konfigurieren")
                }
            }
        }
    }
}

@Composable
private fun BattleHeader(state: StrategicBattleSnapshot) {
    OutlinedCard {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Zug " + state.turn, fontWeight = FontWeight.Bold)
                Text(state.difficulty.label, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Preise Du " + state.player.prizesTaken + "/6", fontWeight = FontWeight.SemiBold)
                Text("Preise KI " + state.ai.prizesTaken + "/6", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StrategicActiveCard(
    pokemon: StrategicPokemonSnapshot?,
    label: String,
    modifier: Modifier = Modifier
) {
    if (pokemon == null) {
        AiInfoCard("Kein aktives Pokémon.")
        return
    }
    OutlinedCard(modifier) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = pokemon.card.imageUrl,
                contentDescription = pokemon.card.name,
                modifier = Modifier.size(width = 88.dp, height = 122.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(pokemon.card.name, fontWeight = FontWeight.Bold)
                Text("KP " + pokemon.hp + "/" + pokemon.maxHp + " · Energie " + pokemon.energy)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { pokemon.hp.toFloat() / max(1, pokemon.maxHp).toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                val ready = pokemon.card.attacks.count { pokemon.energy >= max(1, it.cost.size) }
                Text(
                    if (ready > 0) "$ready Attacke(n) bereit" else "Noch keine Attacke bereit",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun BenchRow(bench: List<StrategicPokemonSnapshot>, title: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        if (bench.isEmpty()) {
            Text("Bank leer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bench.forEach { pokemon ->
                OutlinedCard(modifier = Modifier.width(150.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text(pokemon.card.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("KP " + pokemon.hp + "/" + pokemon.maxHp)
                        Text("Energie " + pokemon.energy, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiInfoCard(text: String) {
    OutlinedCard {
        Text(text, modifier = Modifier.padding(12.dp))
    }
}
