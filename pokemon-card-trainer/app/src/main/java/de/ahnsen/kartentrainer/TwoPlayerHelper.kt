package de.ahnsen.kartentrainer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max

private data class TablePokemon2(
    val card: CardData,
    val hp: Int = card.hp ?: 100,
    val energy: Int = 0,
    val energyTypes: List<String> = emptyList(),
    val status: FullStatus = FullStatus.NONE
)

private data class TableSide2(
    val active: TablePokemon2? = null,
    val bench: List<TablePokemon2> = emptyList(),
    val prizesLeft: Int = 6
)

@Composable
fun TwoPlayerHelperScreen(
    collection: List<CollectionEntry>,
    modifier: Modifier = Modifier
) {
    var sideA by remember { mutableStateOf(TableSide2()) }
    var sideB by remember { mutableStateOf(TableSide2()) }
    var currentPlayer by rememberSaveable { mutableStateOf("A") }
    var selectedTarget by rememberSaveable { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Spieler A scannt zuerst sein aktives Basis-Pokémon.") }
    val log = remember { mutableListOf<String>() }

    fun side(): TableSide2 = if (currentPlayer == "A") sideA else sideB
    fun opponent(): TableSide2 = if (currentPlayer == "A") sideB else sideA
    fun setSide(value: TableSide2) {
        if (currentPlayer == "A") sideA = value else sideB = value
    }
    fun setOpponent(value: TableSide2) {
        if (currentPlayer == "A") sideB = value else sideA = value
    }

    fun energyType(card: CardData): String {
        val raw = card.energyType ?: card.types.firstOrNull() ?: card.name
        val s = raw.lowercase()
        return when {
            "feuer" in s || "fire" in s -> "Fire"
            "wasser" in s || "water" in s -> "Water"
            "pflanze" in s || "grass" in s -> "Grass"
            "elektro" in s || "lightning" in s -> "Lightning"
            "psycho" in s || "psychic" in s -> "Psychic"
            "kampf" in s || "fighting" in s -> "Fighting"
            "finstern" in s || "dark" in s -> "Darkness"
            "stahl" in s || "metal" in s -> "Metal"
            else -> "Colorless"
        }
    }

    fun updateTarget(transform: (TablePokemon2) -> TablePokemon2) {
        val s = side()
        if (selectedTarget == 0) {
            val active = s.active ?: return
            setSide(s.copy(active = transform(active)))
        } else {
            val index = selectedTarget - 1
            val p = s.bench.getOrNull(index) ?: return
            val bench = s.bench.toMutableList()
            bench[index] = transform(p)
            setSide(s.copy(bench = bench))
        }
    }

    fun handleCard(card: CardData) {
        val s = side()
        when {
            card.isEnergy() -> {
                updateTarget { p ->
                    p.copy(
                        energy = p.energy + 1,
                        energyTypes = p.energyTypes + energyType(card)
                    )
                }
                status = "Spieler $currentPlayer: " + card.name + " als Energie verbucht."
                log += status
            }
            card.isPokemon() && card.isBasicPokemon() -> {
                if (s.active == null) {
                    setSide(s.copy(active = TablePokemon2(card)))
                    status = "Spieler $currentPlayer: " + card.name + " ist aktiv."
                } else if (s.bench.size < 5) {
                    setSide(s.copy(bench = s.bench + TablePokemon2(card)))
                    status = "Spieler $currentPlayer: " + card.name + " auf die Bank gelegt."
                } else {
                    status = "Bank ist voll."
                }
                log += status
            }
            card.isPokemon() -> {
                var evolved = false
                if (selectedTarget == 0 && s.active != null &&
                    card.evolveFrom.equals(s.active.card.name, ignoreCase = true)
                ) {
                    val damage = max(0, (s.active.card.hp ?: 100) - s.active.hp)
                    setSide(
                        s.copy(
                            active = s.active.copy(
                                card = card,
                                hp = max(1, (card.hp ?: 100) - damage),
                                status = FullStatus.NONE
                            )
                        )
                    )
                    evolved = true
                } else {
                    val index = selectedTarget - 1
                    val old = s.bench.getOrNull(index)
                    if (old != null && card.evolveFrom.equals(old.card.name, ignoreCase = true)) {
                        val damage = max(0, (old.card.hp ?: 100) - old.hp)
                        val bench = s.bench.toMutableList()
                        bench[index] = old.copy(
                            card = card,
                            hp = max(1, (card.hp ?: 100) - damage),
                            status = FullStatus.NONE
                        )
                        setSide(s.copy(bench = bench))
                        evolved = true
                    }
                }
                status = if (evolved) {
                    "Spieler $currentPlayer entwickelt zu " + card.name + "."
                } else {
                    "Entwicklung passt nicht zum gewählten Pokémon."
                }
                log += status
            }
            card.isTrainer() -> {
                val parsed = EffectParser.parse(card.effect.orEmpty(), EffectSourceKind.TRAINER)
                status = "Trainer " + card.name + ": " + parsed.summary +
                    " · Automatik-Abdeckung " + parsed.coveragePercent + "%."
                log += "Spieler $currentPlayer: " + status
            }
        }
    }

    fun applyAttack(attack: CardAttack) {
        val attackerSide = side()
        val defenderSide = opponent()
        val attacker = attackerSide.active ?: return
        val defender = defenderSide.active ?: return
        val cost = attack.cost.size
        if (attacker.energy < cost) {
            status = "Nicht genug Energie für " + attack.name + "."
            return
        }
        var damage = attack.baseDamage
        val attackTypes = attacker.card.types.map { it.lowercase() }
        if (defender.card.weaknesses.any { w -> attackTypes.contains(w.lowercase()) }) damage *= 2
        if (defender.card.resistances.any { r -> attackTypes.contains(r.lowercase()) }) damage = max(0, damage - 30)

        val nextDefender = defender.copy(hp = max(0, defender.hp - damage))
        var nextOpponent = defenderSide.copy(active = nextDefender)
        status = "Spieler $currentPlayer: " + attacker.card.name + " – " + attack.name + " macht " + damage + " Schaden."
        log += status

        if (nextDefender.hp <= 0) {
            val nextPrizes = max(0, attackerSide.prizesLeft - 1)
            setSide(attackerSide.copy(prizesLeft = nextPrizes))
            if (nextOpponent.bench.isNotEmpty()) {
                nextOpponent = nextOpponent.copy(
                    active = nextOpponent.bench.first(),
                    bench = nextOpponent.bench.drop(1)
                )
            } else {
                nextOpponent = nextOpponent.copy(active = null)
            }
            setOpponent(nextOpponent)
            log += "K. o. – Spieler $currentPlayer nimmt eine Preiskarte."
        } else {
            setOpponent(nextOpponent)
        }

        currentPlayer = if (currentPlayer == "A") "B" else "A"
        selectedTarget = 0
        log += "Zugwechsel: Spieler $currentPlayer."
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = KidPalette.SoftBlue
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Icon(Icons.Default.Groups, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "🎮 2-Spieler-Abenteuer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = KidPalette.Ocean
                        )
                    }
                    Text("Lokaler Schiedsrichter für zwei echte Spieler: Karten scannen, KP, Energie, Bank, Preise und Züge verfolgen.")
                }
            }
        }

        item {
            Text(
                "Am Zug: Spieler " + currentPlayer,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            TwoPlayerSideCard("Spieler A", sideA)
        }
        item {
            TwoPlayerSideCard("Spieler B", sideB)
        }

        if (side().active != null) {
            item {
                Text("Ziel für Scan/Korrektur", fontWeight = FontWeight.Bold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTarget == 0,
                        onClick = { selectedTarget = 0 },
                        label = { Text("Aktiv") }
                    )
                    side().bench.forEachIndexed { index, p ->
                        FilterChip(
                            selected = selectedTarget == index + 1,
                            onClick = { selectedTarget = index + 1 },
                            label = { Text("Bank: " + p.card.name) }
                        )
                    }
                }
            }
        }

        item {
            SmartCameraScanner(
                batchMode = true,
                modifier = Modifier.fillMaxWidth(),
                onResult = { scan ->
                    val card = bestCollectionMatch(scan.text, collection)
                    if (card == null) {
                        status = "Karte nicht eindeutig in der Sammlung erkannt."
                    } else {
                        handleCard(card)
                    }
                },
                onError = { status = it }
            )
        }

        side().active?.let { active ->
            item {
                Text("Attacken von " + active.card.name, fontWeight = FontWeight.Bold)
            }
            items(active.card.attacks) { attack ->
                Button(
                    onClick = { applyAttack(attack) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(attack.name + " · " + attack.damage.ifBlank { "Effekt" })
                }
            }
        }

        item {
            Text("Manuelle Korrektur", fontWeight = FontWeight.Bold)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = {
                    updateTarget { it.copy(hp = max(0, it.hp - 10)) }
                }) { Text("−10 KP") }
                OutlinedButton(onClick = {
                    updateTarget { p -> p.copy(hp = minOf(p.card.hp ?: 100, p.hp + 10)) }
                }) { Text("+10 KP") }
                OutlinedButton(onClick = {
                    updateTarget { p -> p.copy(energy = p.energy + 1, energyTypes = p.energyTypes + "Any") }
                }) { Text("+Energie") }
                OutlinedButton(onClick = {
                    updateTarget { p -> p.copy(energy = max(0, p.energy - 1), energyTypes = p.energyTypes.dropLast(1)) }
                }) { Text("−Energie") }
            }
        }

        item {
            OutlinedCard {
                Text(status, modifier = Modifier.padding(12.dp))
            }
        }

        item {
            Text("Protokoll", fontWeight = FontWeight.Bold)
            log.takeLast(15).reversed().forEach {
                Text("• " + it, style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    sideA = TableSide2()
                    sideB = TableSide2()
                    currentPlayer = "A"
                    selectedTarget = 0
                    status = "Spieler A scannt zuerst sein aktives Basis-Pokémon."
                    log.clear()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Text(" Zurücksetzen")
            }
        }
    }
}

@Composable
private fun TwoPlayerSideCard(label: String, side: TableSide2) {
    OutlinedCard {
        Column(Modifier.padding(12.dp)) {
            Text(label + " · Preise übrig " + side.prizesLeft, fontWeight = FontWeight.Bold)
            val active = side.active
            if (active == null) {
                Text("Kein aktives Pokémon")
            } else {
                Text("Aktiv: " + active.card.name + " · KP " + active.hp + "/" + (active.card.hp ?: 100) + " · E" + active.energy)
            }
            if (side.bench.isNotEmpty()) {
                Text(
                    "Bank: " + side.bench.joinToString { it.card.name + " (" + it.hp + " KP)" },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
