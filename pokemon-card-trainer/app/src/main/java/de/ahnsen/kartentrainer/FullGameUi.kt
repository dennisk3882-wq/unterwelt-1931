package de.ahnsen.kartentrainer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.Locale
import kotlin.math.max

@Composable
fun FullGameScreen(
    collection: List<CollectionEntry>,
    modifier: Modifier = Modifier,
    onGameFinished: (String) -> Unit = {}
) {
    var difficultyName by rememberSaveable { mutableStateOf(AiDifficulty.NORMAL.name) }
    var startModeName by rememberSaveable { mutableStateOf(FullStartMode.COIN_FLIP.name) }
    var standardOnly by rememberSaveable { mutableStateOf(true) }
    var engine by remember { mutableStateOf<FullGameEngine?>(null) }
    var snapshot by remember { mutableStateOf<FullGameSnapshot?>(null) }
    var selectedTarget by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val learningStore = remember { LocalAiLearningStore(context.applicationContext) }
    var aiTuning by remember { mutableStateOf(learningStore.load()) }
    var recordedResultKey by remember { mutableStateOf("") }

    val difficulty = AiDifficulty.valueOf(difficultyName)
    val startMode = FullStartMode.valueOf(startModeName)
    val basics = collection.filter { it.card.isBasicPokemon() }.sumOf { it.quantity }

    LaunchedEffect(snapshot?.finished, snapshot?.winner) {
        val state = snapshot
        if (state?.finished == true && !state.winner.isNullOrBlank()) {
            val key = state.winner + "|" + state.turnNumber + "|" + state.player.prizesLeft + "|" + state.ai.prizesLeft
            if (key != recordedResultKey) {
                aiTuning = learningStore.recordResult(
                    aiWon = state.winner == "KI",
                    turns = state.turnNumber,
                    aiPrizesLeft = state.ai.prizesLeft,
                    opponentPrizesLeft = state.player.prizesLeft
                )
                onGameFinished(state.winner.orEmpty())
                recordedResultKey = key
            }
        }
    }

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
                        Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Vollspiel-Training",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "60 Karten, 7er-Starthand, 6 Preiskarten, Hand, Deck, Ablage, Bank, Entwicklung, Trainer, Rückzug, Sonderzustände und eine planende KI."
                            )
                        }
                    }
                }
            }
        }

        if (snapshot == null) {
            item {
                Text("Format", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = standardOnly,
                        onClick = { standardOnly = true },
                        label = { Text("Standard") }
                    )
                    FilterChip(
                        selected = !standardOnly,
                        onClick = { standardOnly = false },
                        label = { Text("Freies Training") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Für das Training werden fehlende Basis-Energien automatisch ergänzt. Andere Pokémon und Trainerkarten stammen aus eurer eingescannten Sammlung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Text("KI-Stufe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                Text(difficulty.description, style = MaterialTheme.typography.bodySmall)
            }

            item {
                Text("Wer beginnt?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                Text(
                    "Beim Münzwurf entscheidet die Engine zufällig. Erstzug-Sperren gelten nur für den tatsächlichen Startspieler.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            item {
                OutlinedCard {
                    Column(Modifier.padding(12.dp)) {
                        Text("Lokales KI-Lernen", fontWeight = FontWeight.Bold)
                        Text(
                            "Gelernte Partien: " + aiTuning.gamesLearned +
                                " · Aggression " + String.format("%.2f", aiTuning.aggression) +
                                " · Defensive " + String.format("%.2f", aiTuning.survival),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Die Gewichte werden nach Partien lokal angepasst und verlassen das Gerät nicht.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(
                            onClick = {
                                learningStore.reset()
                                aiTuning = learningStore.load()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("KI-Lernen zurücksetzen")
                        }
                    }
                }
            }

            item {
                OutlinedCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("Deck-Prüfung", fontWeight = FontWeight.Bold)
                        Text("Basis-Pokémon in deiner Sammlung: " + basics)
                        val deck = FullDeckFactory.build(collection, standardOnly, preferStrong = false)
                        Text("Trainingsdeck: " + deck.size + " Karten")
                        val ownCards = deck.count { !it.virtualEnergy }
                        val trainingEnergy = deck.count { it.virtualEnergy }
                        Text("Eigene Karten: " + ownCards + " · ergänzte Trainingsenergien: " + trainingEnergy)
                    }
                }
            }

            if (basics <= 0) {
                item {
                    FullInfoCard("Für eine Partie fehlt mindestens ein eingescanntes Basis-Pokémon. Scanne zuerst ein Basis-Pokémon in die Sammlung.")
                }
            } else {
                item {
                    Button(
                        onClick = {
                            val newEngine = FullGameEngine(
                                entries = collection,
                                difficulty = difficulty,
                                standardOnly = standardOnly,
                                startMode = startMode,
                                aiTuning = aiTuning
                            )
                            engine = newEngine
                            snapshot = newEngine.snapshot()
                            selectedTarget = 0
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("60-Karten-Partie starten")
                    }
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
                Spacer(Modifier.height(6.dp))
                FullBench(state.ai.bench, "KI-Bank")
            }

            item {
                Text("Dein Feld", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FullActivePokemon(state.player.active, "Du aktiv")
                Spacer(Modifier.height(6.dp))
                FullBench(state.player.bench, "Deine Bank")
            }

            if (!state.finished) {
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
                    Text("Ziel für Energie / Entwicklung", fontWeight = FontWeight.Bold)
                    val targets = buildList {
                        state.player.active?.let { add(0 to it) }
                        state.player.bench.forEachIndexed { index, p -> add((index + 1) to p) }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        targets.forEach { (index, p) ->
                            FilterChip(
                                selected = selectedTarget == index,
                                onClick = { selectedTarget = index },
                                label = {
                                    Text(
                                        p.card.name + " · E" + p.energy,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }

                val selectedPokemon = if (selectedTarget == 0) {
                    state.player.active
                } else {
                    state.player.bench.getOrNull(selectedTarget - 1)
                }
                if (selectedPokemon != null && selectedPokemon.card.abilities.isNotEmpty()) {
                    item {
                        Text(
                            "Fähigkeiten von " + selectedPokemon.card.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(selectedPokemon.card.abilities.indices.toList()) { abilityIndex ->
                        val ability = selectedPokemon.card.abilities[abilityIndex]
                        val timing = classifyAbilityTiming(ability.effect)
                        val parsed = EffectParser.parse(ability.effect, EffectSourceKind.ABILITY)
                        OutlinedCard {
                            Column(Modifier.padding(12.dp)) {
                                Text(ability.name, fontWeight = FontWeight.Bold)
                                Text(
                                    timing.label + " · Automatik-Abdeckung " + parsed.coveragePercent + "%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(ability.effect, style = MaterialTheme.typography.bodySmall)
                                if (parsed.operations.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Engine: " + parsed.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                if (timing == AbilityTiming.ACTIVATED || timing == AbilityTiming.UNKNOWN) {
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            snapshot = engine?.usePlayerAbility(selectedTarget, abilityIndex)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Fähigkeit benutzen")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Deine Hand · " + state.player.hand.size + " Karten",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.player.hand.indices.toList(), key = { index ->
                    state.player.hand.getOrNull(index)?.uid ?: index
                }) { handIndex ->
                    val gameCard = state.player.hand.getOrNull(handIndex)
                    if (gameCard != null) {
                        HandCardRow(
                            gameCard = gameCard,
                            handIndex = handIndex,
                            targetIndex = selectedTarget,
                            onPlayBasic = {
                                snapshot = engine?.playBasicFromHand(handIndex)
                            },
                            onAttach = {
                                snapshot = engine?.attachEnergyFromHand(handIndex, selectedTarget)
                            },
                            onEvolve = {
                                snapshot = engine?.evolveFromHand(handIndex, selectedTarget)
                            },
                            onTrainer = {
                                snapshot = engine?.playTrainerFromHand(handIndex, selectedTarget)
                            }
                        )
                    }
                }

                state.player.active?.let { active ->
                    item {
                        HorizontalDivider()
                        Text("Aktionsphase", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Energie: " + if (state.playerEnergyAttached) "bereits angelegt" else "noch möglich" +
                                " · Unterstützer: " + if (state.playerSupporterUsed) "benutzt" else "frei" +
                                " · Rückzug: " + if (state.playerRetreated) "benutzt" else "frei",
                            style = MaterialTheme.typography.bodySmall
                        )
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
                                        }
                                    ) {
                                        Text(
                                            bench.card.name + " rein · Kosten " + active.card.retreatCost
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text("Attacken", fontWeight = FontWeight.Bold)
                        if (state.firstPlayerTurn) {
                            Text(
                                "Du beginnst: Im ersten Zug darfst du noch nicht angreifen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    items(active.card.attacks.indices.toList()) { attackIndex ->
                        val attack = active.card.attacks[attackIndex]
                        val cost = max(1, attack.cost.size)
                        Button(
                            onClick = {
                                snapshot = engine?.playerAttack(attackIndex)
                                selectedTarget = 0
                            },
                            enabled = !state.firstPlayerTurn && active.energy >= cost,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    attack.name + " · " + attack.damage.ifBlank { "Effekt" } +
                                        " · Kosten " + cost,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (attack.effect.isNotBlank()) {
                                    val parsed = EffectParser.parse(attack.effect, EffectSourceKind.ATTACK)
                                    Text(
                                        attack.effect,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Effekt-Engine " + parsed.coveragePercent + "% · " + parsed.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (parsed.fullySupported) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.secondary
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = {
                                snapshot = engine?.endTurnWithoutAttack()
                                selectedTarget = 0
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Zug ohne Angriff beenden")
                        }
                    }
                }
            }

            if (state.lastAiReasoning.isNotBlank() && state.lastAiReasoning != "Die KI hat noch keinen Zug gemacht.") {
                item {
                    OutlinedCard {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Psychology, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("KI-Plan dieses Zuges", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(5.dp))
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
                Text("Spielprotokoll", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                state.log.takeLast(18).reversed().forEach {
                    Text("• " + it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        engine = null
                        snapshot = null
                        selectedTarget = 0
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Neue Vollspiel-Partie")
                }
            }
        }
    }
}

@Composable
fun FullStatusHeader(state: FullGameSnapshot) {
    OutlinedCard {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Zug " + state.turnNumber, fontWeight = FontWeight.Bold)
                    Text(state.difficulty.label, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Preise: Du " + state.player.prizesLeft + " · KI " + state.ai.prizesLeft)
                    Text(
                        "Start: " + (if (state.playerStarted) "Du" else "KI") +
                            " · Mulligans Du " + state.playerMulligans + " / KI " + state.aiMulligans,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Deck: Du " + state.player.deckCount + " · KI " + state.ai.deckCount,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    ((6 - state.player.prizesLeft).coerceIn(0, 6)).toFloat() / 6f
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Ablage: Du " + state.player.discardCount + " · KI " + state.ai.discardCount +
                    " · Lost Zone Du " + state.player.lostZoneCount + " / KI " + state.ai.lostZoneCount +
                    " · KI-Hand " + state.ai.hand.size,
                style = MaterialTheme.typography.bodySmall
            )
            if (!state.stadiumName.isNullOrBlank()) {
                Text(
                    "Stadion: " + state.stadiumName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
fun FullActivePokemon(pokemon: FullPokemonView?, label: String) {
    if (pokemon == null) {
        FullInfoCard(label + ": kein Pokémon")
        return
    }
    OutlinedCard {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = pokemon.card.imageUrl,
                contentDescription = pokemon.card.name,
                modifier = Modifier.size(width = 82.dp, height = 114.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(pokemon.card.name, fontWeight = FontWeight.Bold)
                Text("KP " + pokemon.hp + "/" + pokemon.maxHp + " · Energie " + pokemon.energy)
                if (pokemon.energyTypes.isNotEmpty()) {
                    Text(
                        pokemon.energyTypes.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Text(
                    "Rückzug " + pokemon.card.retreatCost + " · Status " + pokemon.status.label,
                    style = MaterialTheme.typography.bodySmall
                )
                if (!pokemon.toolName.isNullOrBlank()) {
                    Text(
                        "Ausrüstung: " + pokemon.toolName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { pokemon.hp.toFloat() / max(1, pokemon.maxHp).toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun FullBench(bench: List<FullPokemonView>, title: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        if (bench.isEmpty()) {
            Text("Bank leer", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bench.forEach { pokemon ->
                    OutlinedCard(modifier = Modifier.width(160.dp)) {
                        Column(Modifier.padding(8.dp)) {
                            AsyncImage(
                                model = pokemon.card.imageUrl,
                                contentDescription = pokemon.card.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                pokemon.card.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("KP " + pokemon.hp + "/" + pokemon.maxHp)
                            Text(
                                "E" + pokemon.energy +
                                    (if (pokemon.energyTypes.isNotEmpty()) " [" + pokemon.energyTypes.joinToString("/") + "]" else "") +
                                    " · " + pokemon.status.label,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandCardRow(
    gameCard: FullGameCard,
    handIndex: Int,
    targetIndex: Int,
    onPlayBasic: () -> Unit,
    onAttach: () -> Unit,
    onEvolve: () -> Unit,
    onTrainer: () -> Unit
) {
    OutlinedCard {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val art = gameCard.card?.imageUrl
                if (!art.isNullOrBlank()) {
                    AsyncImage(
                        model = art,
                        contentDescription = gameCard.name,
                        modifier = Modifier.size(width = 58.dp, height = 82.dp)
                    )
                } else {
                    Icon(
                        when {
                            gameCard.isEnergy() -> Icons.Default.Bolt
                            gameCard.isTrainer() -> Icons.Default.Style
                            else -> Icons.Default.Layers
                        },
                        contentDescription = null
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(gameCard.name, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            gameCard.virtualEnergy -> "Automatisch ergänzte Basis-Energie"
                            gameCard.isBasicPokemon() -> "Basis-Pokémon"
                            gameCard.isPokemon() -> "Entwicklung"
                            gameCard.isTrainer() -> gameCard.card?.trainerType ?: "Trainerkarte"
                            gameCard.isEnergy() -> "Energie"
                            else -> "Karte"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                gameCard.isBasicPokemon() -> {
                    Button(onClick = onPlayBasic, modifier = Modifier.fillMaxWidth()) {
                        Text("Auf die Bank legen")
                    }
                }
                gameCard.isEnergy() -> {
                    Button(onClick = onAttach, modifier = Modifier.fillMaxWidth()) {
                        Text("An gewähltes Pokémon anlegen")
                    }
                }
                gameCard.isTrainer() -> {
                    Button(onClick = onTrainer, modifier = Modifier.fillMaxWidth()) {
                        Text("Trainerkarte spielen")
                    }
                    val effect = gameCard.card?.effect.orEmpty()
                    if (effect.isNotBlank()) {
                        val parsed = EffectParser.parse(effect, EffectSourceKind.TRAINER)
                        Text(
                            effect,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Automatik " + parsed.coveragePercent + "% · " + parsed.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (parsed.fullySupported) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                gameCard.isPokemon() -> {
                    Button(onClick = onEvolve, modifier = Modifier.fillMaxWidth()) {
                        Text("Gewähltes Pokémon entwickeln")
                    }
                    gameCard.card?.evolveFrom?.let {
                        Text("Entwickelt sich aus: " + it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun FullInfoCard(text: String) {
    OutlinedCard {
        Text(text, modifier = Modifier.padding(12.dp))
    }
}
