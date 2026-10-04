package de.ahnsen.kartentrainer

import kotlin.math.max

data class DeckLine(
    val name: String,
    val quantity: Int,
    val category: String,
    val setNames: String
)

data class DeckResult(
    val lines: List<DeckLine>,
    val totalCards: Int,
    val pokemonCount: Int,
    val trainerCount: Int,
    val energyCount: Int,
    val missingTo60: Int,
    val notes: List<String>
)

object DeckBuilder {
    fun build(entries: List<CollectionEntry>, standardOnly: Boolean): DeckResult {
        val eligible = entries.filter { !standardOnly || it.card.isStandardPlayable() }
        val expanded = mutableListOf<Pair<CardData, Int>>()
        eligible.forEach { entry ->
            val maxAllowed = if (entry.card.isBasicEnergy()) entry.quantity else minOf(4, entry.quantity)
            if (maxAllowed > 0) expanded += entry.card to maxAllowed
        }

        val pokemon = expanded.filter { it.first.isPokemon() }
            .sortedWith(compareByDescending<Pair<CardData, Int>> { it.first.isBasicPokemon() }.thenByDescending { it.second })
        val trainers = expanded.filter { it.first.isTrainer() }
            .sortedByDescending { it.second }
        val energies = expanded.filter { it.first.isEnergy() }
            .sortedWith(compareByDescending<Pair<CardData, Int>> { it.first.isBasicEnergy() }.thenByDescending { it.second })

        val chosen = mutableMapOf<String, MutableList<Pair<CardData, Int>>>()

        fun takeFrom(source: List<Pair<CardData, Int>>, target: Int, bucket: String): Int {
            var remaining = target
            val out = mutableListOf<Pair<CardData, Int>>()
            for ((card, qty) in source) {
                if (remaining <= 0) break
                val take = minOf(qty, remaining)
                if (take > 0) {
                    out += card to take
                    remaining -= take
                }
            }
            chosen[bucket] = out
            return target - remaining
        }

        var pCount = takeFrom(pokemon, 18, "Pokemon")
        var tCount = takeFrom(trainers, 30, "Trainer")
        var eCount = takeFrom(energies, 12, "Energy")

        var total = pCount + tCount + eCount

        if (total < 60) {
            val already = chosen.values.flatten().associate { it.first.id to it.second }.toMutableMap()
            val rest = expanded.sortedByDescending { it.second }
            for ((card, qty) in rest) {
                if (total >= 60) break
                val used = already[card.id] ?: 0
                val canTake = qty - used
                if (canTake <= 0) continue
                val add = minOf(canTake, 60 - total)
                val key = when {
                    card.isPokemon() -> "Pokemon"
                    card.isTrainer() -> "Trainer"
                    else -> "Energy"
                }
                chosen.getOrPut(key) { mutableListOf() }.add(card to add)
                already[card.id] = used + add
                total += add
                when (key) {
                    "Pokemon" -> pCount += add
                    "Trainer" -> tCount += add
                    else -> eCount += add
                }
            }
        }

        val grouped = chosen.values.flatten()
            .groupBy { it.first.name }
            .map { (name, list) ->
                val qty = list.sumOf { it.second }
                val card = list.first().first
                DeckLine(
                    name = name,
                    quantity = qty,
                    category = card.category,
                    setNames = list.map { it.first.setName }.distinct().joinToString(", ")
                )
            }
            .sortedWith(compareBy<DeckLine> { it.category }.thenBy { it.name })

        val notes = buildList {
            if (pokemon.none { it.first.isBasicPokemon() }) add("Es wurde kein Basis-Pokémon gefunden. Ein offizielles Deck braucht mindestens eines.")
            if (total < 60) add("Mit deiner aktuellen Sammlung fehlen noch ${60 - total} Karten für ein vollständiges 60-Karten-Deck.")
            if (eCount < 10) add("Du hast wenig Energie im Vorschlag. Für Einsteiger sind ungefähr 12–15 Energien meist angenehmer.")
            if (standardOnly && entries.any { !it.card.isStandardPlayable() }) add("Nicht standard-legale Karten wurden für diesen Vorschlag automatisch ausgelassen.")
            add("Karten aus unterschiedlichen Sets dürfen gemischt werden. Entscheidend sind Deckregeln und Format-Zulässigkeit, nicht ein gemeinsames Set.")
        }

        return DeckResult(
            lines = grouped,
            totalCards = total,
            pokemonCount = pCount,
            trainerCount = tCount,
            energyCount = eCount,
            missingTo60 = max(0, 60 - total),
            notes = notes
        )
    }
}

data class BattleSnapshot(
    val playerCard: CardData,
    val aiCard: CardData,
    val playerHp: Int,
    val aiHp: Int,
    val playerEnergy: Int,
    val aiEnergy: Int,
    val playerCanAttach: Boolean,
    val finished: Boolean,
    val winner: String?,
    val log: List<String>
)

class TrainingBattle(
    private val player: CardData,
    private val ai: CardData
) {
    private var playerHp = player.hp ?: 100
    private var aiHp = ai.hp ?: 100
    private var playerEnergy = 0
    private var aiEnergy = 0
    private var playerCanAttach = true
    private var finished = false
    private var winner: String? = null
    private val log = mutableListOf("Kampf gestartet: ${player.name} gegen ${ai.name}")

    fun snapshot(): BattleSnapshot = BattleSnapshot(
        playerCard = player,
        aiCard = ai,
        playerHp = playerHp,
        aiHp = aiHp,
        playerEnergy = playerEnergy,
        aiEnergy = aiEnergy,
        playerCanAttach = playerCanAttach,
        finished = finished,
        winner = winner,
        log = log.toList()
    )

    fun attachEnergy(): BattleSnapshot {
        if (finished) return snapshot()
        if (!playerCanAttach) {
            log += "Du hast in diesem Zug bereits eine Energie angelegt."
            return snapshot()
        }
        playerEnergy += 1
        playerCanAttach = false
        log += "Du legst 1 Energie an ${player.name}. Energie: $playerEnergy"
        return snapshot()
    }

    fun attack(index: Int): BattleSnapshot {
        if (finished) return snapshot()
        val attack = player.attacks.getOrNull(index)
        if (attack == null) {
            log += "Diese Attacke ist nicht verfügbar."
            return snapshot()
        }
        val cost = max(1, attack.cost.size)
        if (playerEnergy < cost) {
            log += "${attack.name} braucht $cost Energie. Du hast $playerEnergy."
            return snapshot()
        }

        val damage = attack.baseDamage
        aiHp = max(0, aiHp - damage)
        log += "${player.name} setzt ${attack.name} ein und macht $damage Schaden."
        if (attack.effect.isNotBlank()) log += "Kartentext: ${attack.effect}"

        if (aiHp <= 0) {
            finished = true
            winner = "Du"
            log += "${ai.name} ist kampfunfähig. Du gewinnst den Lernkampf."
            return snapshot()
        }

        aiTurn()
        return snapshot()
    }

    private fun aiTurn() {
        if (finished) return
        aiEnergy += 1
        log += "KI legt 1 Energie an ${ai.name}. Energie: $aiEnergy"

        val possible = ai.attacks
            .withIndex()
            .filter { (_, a) -> aiEnergy >= max(1, a.cost.size) }

        if (possible.isEmpty()) {
            log += "KI kann noch nicht angreifen."
        } else {
            val picked = possible.maxByOrNull { it.value.baseDamage }!!.value
            val damage = picked.baseDamage
            playerHp = max(0, playerHp - damage)
            log += "KI: ${ai.name} setzt ${picked.name} ein und macht $damage Schaden."
            if (picked.effect.isNotBlank()) log += "Kartentext: ${picked.effect}"
        }

        if (playerHp <= 0) {
            finished = true
            winner = "KI"
            log += "${player.name} ist kampfunfähig. Die KI gewinnt den Lernkampf."
        } else {
            playerCanAttach = true
            log += "Du bist wieder am Zug."
        }
    }
}

data class TableCoachState(
    val activeCard: CardData?,
    val energy: Int,
    val log: List<String>
)

class TableCoach {
    private var activeCard: CardData? = null
    private var energy = 0
    private val log = mutableListOf(
        "Tischmodus bereit. Spiele mit deinen echten Karten und scanne jede Karte, die du ausspielst."
    )

    fun snapshot() = TableCoachState(activeCard, energy, log.toList())

    fun register(card: CardData): TableCoachState {
        when {
            card.isPokemon() -> {
                if (activeCard == null) {
                    activeCard = card
                    energy = 0
                    log += "${card.name} wurde als aktives Pokémon erkannt."
                } else {
                    log += "${card.name} wurde als Pokémon erkannt. Lege es auf die Bank oder entwickle passend nach den Regeln."
                }
            }
            card.isEnergy() -> {
                if (activeCard == null) {
                    log += "Energie erkannt, aber noch kein aktives Pokémon gesetzt."
                } else {
                    energy += 1
                    log += "Energie an ${activeCard!!.name}: jetzt $energy Energie."
                }
            }
            card.isTrainer() -> {
                log += "Trainerkarte ${card.name}: ${card.effect.orEmpty()}"
            }
            else -> log += "${card.name} erkannt."
        }
        return snapshot()
    }

    fun clearActive(): TableCoachState {
        activeCard = null
        energy = 0
        log += "Aktives Pokémon zurückgesetzt."
        return snapshot()
    }
}

fun bestCollectionMatch(text: String, entries: List<CollectionEntry>): CardData? {
    val lower = text.lowercase()
    return entries
        .map { it.card }
        .distinctBy { it.id }
        .map { card ->
            var score = 0
            if (lower.contains(card.name.lowercase())) score += 100
            if (card.localId.isNotBlank() && Regex("""\b${Regex.escape(card.localId)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) score += 50
            card to score
        }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first
}
