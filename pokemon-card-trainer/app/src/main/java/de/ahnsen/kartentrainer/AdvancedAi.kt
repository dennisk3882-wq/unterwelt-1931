package de.ahnsen.kartentrainer

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class AiDifficulty(val label: String, val description: String) {
    EASY("Einfach", "Spielt verständlich und macht gelegentlich suboptimale Züge."),
    NORMAL("Normal", "Achtet auf Energiekosten, Schaden und verfügbare K.-o.-Chancen."),
    HARD("Schwer", "Plant Energie voraus, priorisiert K.-o.s und bewertet Gegenangriffe."),
    EXPERT("Experte", "Nutzt eine gewichtete Zugbewertung mit Bedrohungs- und Effizienzanalyse.")
}

data class StrategicPokemonSnapshot(
    val card: CardData,
    val hp: Int,
    val energy: Int
) {
    val maxHp: Int get() = card.hp ?: 100
    val damageTaken: Int get() = max(0, maxHp - hp)
    val isKnockedOut: Boolean get() = hp <= 0
}

data class StrategicSideSnapshot(
    val active: StrategicPokemonSnapshot?,
    val bench: List<StrategicPokemonSnapshot>,
    val prizesTaken: Int
)

data class StrategicBattleSnapshot(
    val player: StrategicSideSnapshot,
    val ai: StrategicSideSnapshot,
    val turn: Int,
    val playerCanAttach: Boolean,
    val playerNeedsPromotion: Boolean,
    val finished: Boolean,
    val winner: String?,
    val difficulty: AiDifficulty,
    val lastAiReasoning: String,
    val log: List<String>
)

data class CoachAdvice(
    val headline: String,
    val detail: String,
    val priority: Int
)

private data class MutableBattlePokemon(
    val card: CardData,
    var hp: Int = card.hp ?: 100,
    var energy: Int = 0
) {
    fun snapshot() = StrategicPokemonSnapshot(card, hp, energy)
}

private data class MutableBattleSide(
    var active: MutableBattlePokemon?,
    val bench: MutableList<MutableBattlePokemon>,
    var prizesTaken: Int = 0
)

object AiBattleFactory {
    fun availablePokemon(entries: List<CollectionEntry>): List<CardData> {
        val result = mutableListOf<CardData>()
        entries
            .filter { it.card.isPokemon() && it.card.attacks.isNotEmpty() }
            .forEach { entry ->
                repeat(min(2, entry.quantity)) { result += entry.card }
            }
        return result
    }

    fun buildPlayerTeam(entries: List<CollectionEntry>, leadId: String): List<CardData> {
        val cards = availablePokemon(entries)
        if (cards.isEmpty()) return emptyList()
        val lead = cards.firstOrNull { it.id == leadId } ?: cards.first()
        val remaining = cards.toMutableList()
        remaining.removeAt(remaining.indexOfFirst { it.id == lead.id }.coerceAtLeast(0))
        return listOf(lead) + remaining.sortedByDescending(::combatCardScore).take(5)
    }

    fun buildAiTeam(entries: List<CollectionEntry>, playerLeadId: String, difficulty: AiDifficulty): List<CardData> {
        val cards = availablePokemon(entries)
        if (cards.isEmpty()) return emptyList()
        val pool = cards.filterNot { it.id == playerLeadId }.ifEmpty { cards }
        val sorted = when (difficulty) {
            AiDifficulty.EASY -> pool.shuffled(Random(playerLeadId.hashCode()))
            AiDifficulty.NORMAL -> pool.sortedByDescending { combatCardScore(it) * 0.85 + Random(it.id.hashCode()).nextDouble(0.0, 20.0) }
            AiDifficulty.HARD, AiDifficulty.EXPERT -> pool.sortedByDescending(::combatCardScore)
        }
        val team = sorted.take(6).toMutableList()
        while (team.size < min(2, cards.size)) {
            team += cards[team.size % cards.size]
        }
        return team
    }

    private fun combatCardScore(card: CardData): Double {
        val bestAttack = card.attacks.maxOfOrNull { estimatedAttackDamage(it, 4) } ?: 0
        val bestEfficiency = card.attacks.maxOfOrNull {
            val cost = max(1, it.cost.size)
            estimatedAttackDamage(it, cost).toDouble() / cost
        } ?: 0.0
        return (card.hp ?: 100) * 0.35 + bestAttack * 0.9 + bestEfficiency * 0.4
    }
}

class StrategicTrainingBattle(
    playerTeam: List<CardData>,
    aiTeam: List<CardData>,
    private val difficulty: AiDifficulty
) {
    private val playerSide = createSide(playerTeam)
    private val aiSide = createSide(aiTeam)
    private var turn = 1
    private var playerCanAttach = true
    private var playerNeedsPromotion = false
    private var finished = false
    private var winner: String? = null
    private var lastAiReasoning = "Die KI hat noch keinen Zug gemacht."
    private val log = mutableListOf<String>()

    init {
        require(playerSide.active != null) { "Spielerteam braucht mindestens ein Pokémon." }
        require(aiSide.active != null) { "KI-Team braucht mindestens ein Pokémon." }
        log += "Strategischer KI-Kampf gestartet: ${playerSide.active!!.card.name} gegen ${aiSide.active!!.card.name}."
        log += "Bis zu fünf Pokémon liegen auf der Bank. Für jedes K. o. wird eine Preiskarte genommen."
    }

    private fun createSide(team: List<CardData>): MutableBattleSide {
        val active = team.firstOrNull()?.let(::MutableBattlePokemon)
        val bench = team.drop(1).take(5).map(::MutableBattlePokemon).toMutableList()
        return MutableBattleSide(active, bench)
    }

    fun snapshot(): StrategicBattleSnapshot = StrategicBattleSnapshot(
        player = playerSide.snapshot(),
        ai = aiSide.snapshot(),
        turn = turn,
        playerCanAttach = playerCanAttach,
        playerNeedsPromotion = playerNeedsPromotion,
        finished = finished,
        winner = winner,
        difficulty = difficulty,
        lastAiReasoning = lastAiReasoning,
        log = log.toList()
    )

    fun attachPlayerEnergy(targetIndex: Int): StrategicBattleSnapshot {
        if (finished || playerNeedsPromotion) return snapshot()
        if (!playerCanAttach) {
            log += "Du hast in diesem Zug bereits eine Energie angelegt."
            return snapshot()
        }
        val target = playerTarget(targetIndex)
        if (target == null) {
            log += "Dieses Pokémon ist nicht mehr verfügbar."
            return snapshot()
        }
        target.energy += 1
        playerCanAttach = false
        log += "Du legst 1 Energie an ${target.card.name}. Dort liegen jetzt ${target.energy} Energien."
        return snapshot()
    }

    fun playerAttack(index: Int): StrategicBattleSnapshot {
        if (finished || playerNeedsPromotion) return snapshot()
        val attacker = playerSide.active ?: return snapshot()
        val defender = aiSide.active ?: return snapshot()
        val attack = attacker.card.attacks.getOrNull(index)
        if (attack == null) {
            log += "Diese Attacke gibt es bei ${attacker.card.name} nicht."
            return snapshot()
        }
        val cost = max(1, attack.cost.size)
        if (attacker.energy < cost) {
            log += "${attack.name} braucht $cost Energie. ${attacker.card.name} hat erst ${attacker.energy}."
            return snapshot()
        }

        resolveAttack("Du", attacker, defender, attack)
        resolveKnockOut(defenderIsAi = true)
        if (!finished) aiTurn()
        return snapshot()
    }

    fun promotePlayer(benchIndex: Int): StrategicBattleSnapshot {
        if (finished || !playerNeedsPromotion) return snapshot()
        val selected = playerSide.bench.getOrNull(benchIndex)
        if (selected == null) {
            log += "Dieses Bank-Pokémon kann nicht eingewechselt werden."
            return snapshot()
        }
        playerSide.bench.removeAt(benchIndex)
        playerSide.active = selected
        playerNeedsPromotion = false
        playerCanAttach = true
        log += "Du schickst ${selected.card.name} als neues aktives Pokémon nach vorn."
        turn += 1
        return snapshot()
    }

    fun coachAdvice(): CoachAdvice {
        if (finished) return CoachAdvice("Kampf beendet", "Starte einen neuen Kampf, um weiter zu trainieren.", 0)
        if (playerNeedsPromotion) {
            val best = playerSide.bench.withIndex().maxByOrNull { promotionScore(it.value, aiSide.active) }
            return if (best != null) {
                CoachAdvice(
                    "Wechsle ${best.value.card.name} ein",
                    "Es hat aktuell die beste Mischung aus KP, verfügbarer Energie und möglichem Gegenschaden.",
                    100
                )
            } else CoachAdvice("Kein Pokémon übrig", "Du hast kein Bank-Pokémon mehr.", 100)
        }

        val active = playerSide.active ?: return CoachAdvice("Kein aktives Pokémon", "Wähle ein Pokémon.", 100)
        val enemy = aiSide.active ?: return CoachAdvice("Gegner besiegt", "Der Kampf ist praktisch entschieden.", 100)

        val ready = active.card.attacks.withIndex()
            .filter { active.energy >= max(1, it.value.cost.size) }
        val ko = ready
            .filter { estimatedAttackDamage(it.value, active.energy) >= enemy.hp }
            .maxByOrNull { estimatedAttackDamage(it.value, active.energy) }

        if (ko != null) {
            return CoachAdvice(
                "Jetzt K. o.: ${ko.value.name}",
                "Diese Attacke reicht voraussichtlich für die verbleibenden ${enemy.hp} KP. Ein sofortiges K. o. ist meist stärker als weiterer Aufbau.",
                100
            )
        }

        if (playerCanAttach) {
            val cheapestMissing = active.card.attacks
                .map { max(1, it.cost.size) - active.energy }
                .filter { it > 0 }
                .minOrNull()
            if (cheapestMissing != null) {
                return CoachAdvice(
                    "Energie an ${active.card.name}",
                    "Damit kommst du einer weiteren Attacke näher. Es fehlen nur noch $cheapestMissing Energie bis zur nächsten neuen Angriffsoption.",
                    85
                )
            }

            val benchTarget = playerSide.bench
                .maxByOrNull { setupScore(it, enemy) }
            if (benchTarget != null && setupScore(benchTarget, enemy) > setupScore(active, enemy) + 15) {
                return CoachAdvice(
                    "Bank aufbauen: ${benchTarget.card.name}",
                    "Dein aktives Pokémon kann bereits angreifen. Eine Energie auf der Bank bereitet den nächsten Angreifer vor.",
                    75
                )
            }
        }

        val bestReady = ready.maxByOrNull { attackScore(it.value, active, enemy, expert = true) }
        if (bestReady != null) {
            return CoachAdvice(
                "Angreifen: ${bestReady.value.name}",
                "Von deinen aktuell bezahlbaren Attacken hat diese das beste Verhältnis aus Schaden, K.-o.-Druck und Energieeffizienz.",
                70
            )
        }

        return CoachAdvice(
            "Weiter aufbauen",
            "Noch keine Attacke ist bezahlbar. Lege Energie an und achte darauf, welches Bank-Pokémon als Nächstes übernehmen könnte.",
            50
        )
    }

    private fun aiTurn() {
        if (finished || playerNeedsPromotion) return
        val active = aiSide.active ?: return
        val playerActive = playerSide.active ?: return

        val reasoning = mutableListOf<String>()

        val attachTarget = chooseAiEnergyTarget()
        if (attachTarget != null) {
            attachTarget.energy += 1
            reasoning += "Energie geht an ${attachTarget.card.name}, weil dort der größte zusätzliche Angriffswert entsteht."
            log += "KI legt 1 Energie an ${attachTarget.card.name}."
        }

        val ready = active.card.attacks.withIndex()
            .filter { active.energy >= max(1, it.value.cost.size) }

        if (ready.isEmpty()) {
            reasoning += "${active.card.name} kann noch keine Attacke bezahlen; die KI baut deshalb Energie für kommende Züge auf."
            log += "KI kann mit ${active.card.name} noch nicht angreifen."
        } else {
            val picked = chooseAiAttack(ready, active, playerActive)
            val damage = estimatedAttackDamage(picked.value, active.energy)
            val wouldKo = damage >= playerActive.hp
            reasoning += if (wouldKo) {
                "${picked.value.name} wurde gewählt, weil damit ein sofortiges K. o. möglich ist."
            } else {
                "${picked.value.name} erzielt aktuell den besten gewichteten Wert aus Schaden, Energiekosten und Druck für den nächsten Zug."
            }
            resolveAttack("KI", active, playerActive, picked.value)
        }

        lastAiReasoning = reasoning.joinToString(" ")
        resolveKnockOut(defenderIsAi = false)

        if (!finished && !playerNeedsPromotion) {
            playerCanAttach = true
            turn += 1
            log += "Zug $turn: Du bist wieder dran."
        }
    }

    private fun chooseAiEnergyTarget(): MutableBattlePokemon? {
        val candidates = buildList {
            aiSide.active?.let(::add)
            addAll(aiSide.bench)
        }
        if (candidates.isEmpty()) return null
        if (difficulty == AiDifficulty.EASY) return aiSide.active

        return candidates.maxByOrNull { pokemon ->
            val activeBonus = if (pokemon === aiSide.active) 12.0 else 0.0
            val bestAttack = pokemon.card.attacks.maxOfOrNull { attack ->
                val cost = max(1, attack.cost.size)
                val afterEnergy = pokemon.energy + 1
                val readiness = if (afterEnergy >= cost) 45.0 else 0.0
                val missingPenalty = max(0, cost - afterEnergy) * 10.0
                estimatedAttackDamage(attack, afterEnergy) * 0.65 + readiness - missingPenalty
            } ?: 0.0
            activeBonus + bestAttack + (pokemon.hp.toDouble() / max(1, pokemon.card.hp ?: 100)) * 8.0
        }
    }

    private fun chooseAiAttack(
        ready: List<IndexedValue<CardAttack>>,
        attacker: MutableBattlePokemon,
        defender: MutableBattlePokemon
    ): IndexedValue<CardAttack> {
        if (difficulty == AiDifficulty.EASY && ready.size > 1) {
            return ready[Random(turn + attacker.card.id.hashCode()).nextInt(ready.size)]
        }
        val expert = difficulty == AiDifficulty.EXPERT
        val scored = ready.map { indexed ->
            indexed to attackScore(indexed.value, attacker, defender, expert)
        }.sortedByDescending { it.second }

        if (difficulty == AiDifficulty.NORMAL && scored.size > 1 && scored[0].second - scored[1].second < 18) {
            return if (Random(turn * 31 + attacker.card.id.hashCode()).nextInt(100) < 25) scored[1].first else scored[0].first
        }
        return scored.first().first
    }

    private fun attackScore(
        attack: CardAttack,
        attacker: MutableBattlePokemon,
        defender: MutableBattlePokemon,
        expert: Boolean
    ): Double {
        val cost = max(1, attack.cost.size)
        val damage = estimatedAttackDamage(attack, attacker.energy)
        val koBonus = if (damage >= defender.hp) 500.0 else 0.0
        val efficiency = damage.toDouble() / cost
        val overkillPenalty = max(0, damage - defender.hp) * 0.12
        val effectBonus = effectStrategicBonus(attack.effect)
        val futureThreat = if (expert) {
            val opponentBest = defender.card.attacks
                .filter { defender.energy >= max(1, it.cost.size) }
                .maxOfOrNull { estimatedAttackDamage(it, defender.energy) } ?: 0
            if (opponentBest >= attacker.hp) 60.0 else opponentBest * 0.08
        } else 0.0
        return damage * 1.2 + efficiency * 0.45 + koBonus + effectBonus + futureThreat - overkillPenalty
    }

    private fun effectStrategicBonus(effect: String): Double {
        val lower = effect.lowercase()
        var bonus = 0.0
        if ("heile" in lower) bonus += 22
        if ("vergiftet" in lower || "verbrannt" in lower) bonus += 18
        if ("paralys" in lower || "schläft" in lower || "verwirrt" in lower) bonus += 24
        if ("ziehe" in lower) bonus += 12
        if ("energie" in lower && ("anlegen" in lower || "suche" in lower)) bonus += 16
        if ("wirf eine münze" in lower) bonus -= 4
        return bonus
    }

    private fun resolveAttack(
        actor: String,
        attacker: MutableBattlePokemon,
        defender: MutableBattlePokemon,
        attack: CardAttack
    ) {
        val damage = estimatedAttackDamage(attack, attacker.energy)
        defender.hp = max(0, defender.hp - damage)
        log += "$actor: ${attacker.card.name} setzt ${attack.name} ein und verursacht $damage Schaden."

        val heal = parseHeal(attack.effect)
        if (heal > 0) {
            val before = attacker.hp
            attacker.hp = min(attacker.card.hp ?: 100, attacker.hp + heal)
            val actual = attacker.hp - before
            if (actual > 0) log += "${attacker.card.name} heilt $actual KP durch den Attackeneffekt."
        }

        val recoil = parseRecoil(attack.effect)
        if (recoil > 0) {
            attacker.hp = max(0, attacker.hp - recoil)
            log += "${attacker.card.name} nimmt $recoil Rückstoßschaden."
        }

        if (attack.effect.isNotBlank() && heal == 0 && recoil == 0) {
            log += "Sondereffekt-Hinweis: ${attack.effect}"
        }
    }

    private fun resolveKnockOut(defenderIsAi: Boolean) {
        val defending = if (defenderIsAi) aiSide else playerSide
        val attacking = if (defenderIsAi) playerSide else aiSide
        val knocked = defending.active ?: return
        if (knocked.hp > 0) return

        log += "${knocked.card.name} ist kampfunfähig."
        attacking.prizesTaken += 1
        log += (if (defenderIsAi) "Du" else "KI") + " nimmt eine Preiskarte (${attacking.prizesTaken}/6)."

        if (attacking.prizesTaken >= 6) {
            finish(if (defenderIsAi) "Du" else "KI")
            return
        }

        defending.active = null
        if (defending.bench.isEmpty()) {
            finish(if (defenderIsAi) "Du" else "KI")
            return
        }

        if (defenderIsAi) {
            val bestIndex = defending.bench.indices.maxByOrNull { index ->
                promotionScore(defending.bench[index], playerSide.active)
            } ?: 0
            val promoted = defending.bench.removeAt(bestIndex)
            defending.active = promoted
            log += "KI schickt ${promoted.card.name} von der Bank nach vorn."
        } else {
            playerNeedsPromotion = true
            log += "Wähle jetzt ein Pokémon von deiner Bank als neues aktives Pokémon."
        }
    }

    private fun finish(winnerName: String) {
        finished = true
        winner = winnerName
        playerNeedsPromotion = false
        log += "Kampf beendet. Gewinner: $winnerName."
    }

    private fun playerTarget(index: Int): MutableBattlePokemon? {
        if (index == 0) return playerSide.active
        return playerSide.bench.getOrNull(index - 1)
    }

    private fun promotionScore(pokemon: MutableBattlePokemon, opponent: MutableBattlePokemon?): Double {
        val bestReady = pokemon.card.attacks
            .filter { pokemon.energy >= max(1, it.cost.size) }
            .maxOfOrNull { estimatedAttackDamage(it, pokemon.energy) } ?: 0
        val bestFuture = pokemon.card.attacks.maxOfOrNull { estimatedAttackDamage(it, max(pokemon.energy, max(1, it.cost.size))) } ?: 0
        val koBonus = if (opponent != null && bestReady >= opponent.hp) 300 else 0
        return koBonus + bestReady * 1.4 + bestFuture * 0.35 + pokemon.hp * 0.25 + pokemon.energy * 10
    }

    private fun setupScore(pokemon: MutableBattlePokemon, opponent: MutableBattlePokemon): Double {
        val targetAttack = pokemon.card.attacks.maxByOrNull { estimatedAttackDamage(it, max(1, it.cost.size)) } ?: return 0.0
        val cost = max(1, targetAttack.cost.size)
        val missing = max(0, cost - pokemon.energy)
        val damage = estimatedAttackDamage(targetAttack, cost)
        val koBonus = if (damage >= opponent.hp) 80 else 0
        return damage + koBonus - missing * 16 + pokemon.hp * 0.08
    }

    private fun MutableBattleSide.snapshot() = StrategicSideSnapshot(
        active = active?.snapshot(),
        bench = bench.map { it.snapshot() },
        prizesTaken = prizesTaken
    )
}

fun estimatedAttackDamage(attack: CardAttack, energy: Int): Int {
    val base = attack.baseDamage
    if (base <= 0) return 0
    return when {
        "×" in attack.damage || "x" in attack.damage.lowercase() -> {
            val multiplier = max(1, min(energy, 5))
            base * multiplier
        }
        "+" in attack.damage -> base + 10
        else -> base
    }
}

private fun parseHeal(effect: String): Int {
    val patterns = listOf(
        Regex("""(?i)heile\s+(\d+)\s+schadenspunkte"""),
        Regex("""(?i)heile\s+(\d+)""")
    )
    return patterns.firstNotNullOfOrNull { regex ->
        regex.find(effect)?.groupValues?.getOrNull(1)?.toIntOrNull()
    } ?: 0
}

private fun parseRecoil(effect: String): Int {
    val patterns = listOf(
        Regex("""(?i)sich selbst\s+(\d+)\s+schadenspunkte"""),
        Regex("""(?i)diesem pok[eé]mon\s+(\d+)\s+schadenspunkte""")
    )
    return patterns.firstNotNullOfOrNull { regex ->
        regex.find(effect)?.groupValues?.getOrNull(1)?.toIntOrNull()
    } ?: 0
}
