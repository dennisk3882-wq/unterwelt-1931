package de.ahnsen.kartentrainer

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class FullStatus(val label: String) {
    NONE("Normal"),
    POISONED("Vergiftet"),
    BURNED("Verbrannt"),
    ASLEEP("Schläft"),
    PARALYZED("Paralysiert"),
    CONFUSED("Verwirrt")
}

data class FullGameCard(
    val uid: Int,
    val card: CardData?,
    val virtualEnergy: Boolean = false
) {
    val name: String
        get() = card?.name ?: "Basis-Energie (Training)"

    fun isPokemon(): Boolean = card?.isPokemon() == true
    fun isTrainer(): Boolean = card?.isTrainer() == true
    fun isEnergy(): Boolean = virtualEnergy || card?.isEnergy() == true
    fun isBasicPokemon(): Boolean = card?.isBasicPokemon() == true
}

data class FullPokemonView(
    val card: CardData,
    val hp: Int,
    val energy: Int,
    val turnsInPlay: Int,
    val status: FullStatus
) {
    val maxHp: Int get() = card.hp ?: 100
}

data class FullSideView(
    val active: FullPokemonView?,
    val bench: List<FullPokemonView>,
    val hand: List<FullGameCard>,
    val deckCount: Int,
    val discardCount: Int,
    val prizesLeft: Int
)

data class FullGameSnapshot(
    val player: FullSideView,
    val ai: FullSideView,
    val turnNumber: Int,
    val firstPlayerTurn: Boolean,
    val playerEnergyAttached: Boolean,
    val playerSupporterUsed: Boolean,
    val playerRetreated: Boolean,
    val finished: Boolean,
    val winner: String?,
    val difficulty: AiDifficulty,
    val lastAiReasoning: String,
    val log: List<String>
)

data class FullCoachAdvice(
    val headline: String,
    val detail: String
)

private data class FullPokemonState(
    var card: CardData,
    var hp: Int = card.hp ?: 100,
    var energy: Int = 0,
    var turnsInPlay: Int = 0,
    var status: FullStatus = FullStatus.NONE,
    var preventAllDamageNext: Boolean = false,
    var damageReductionNext: Int = 0,
    var attackLocked: Boolean = false,
    var retreatLocked: Boolean = false
) {
    fun view() = FullPokemonView(card, hp, energy, turnsInPlay, status)
}

private data class FullSideState(
    val deck: MutableList<FullGameCard>,
    val hand: MutableList<FullGameCard> = mutableListOf(),
    val prizes: MutableList<FullGameCard> = mutableListOf(),
    val discard: MutableList<FullGameCard> = mutableListOf(),
    var active: FullPokemonState? = null,
    val bench: MutableList<FullPokemonState> = mutableListOf(),
    var energyAttached: Boolean = false,
    var supporterUsed: Boolean = false,
    var stadiumUsed: Boolean = false,
    var retreated: Boolean = false,
    val usedAbilities: MutableSet<String> = mutableSetOf()
)

object FullDeckFactory {
    fun build(
        entries: List<CollectionEntry>,
        standardOnly: Boolean,
        preferStrong: Boolean
    ): List<FullGameCard> {
        val eligible = entries.filter { !standardOnly || it.card.isStandardPlayable() }
        val namesAvailable = eligible.map { it.card.name }.toSet()
        val pokemon = mutableListOf<CardData>()
        val trainers = mutableListOf<CardData>()
        val energies = mutableListOf<CardData>()

        val usedByName = mutableMapOf<String, Int>()
        eligible.forEach { entry ->
            val card = entry.card
            val old = usedByName[card.name] ?: 0
            val limit = if (card.isBasicEnergy()) entry.quantity else min(entry.quantity, max(0, 4 - old))
            repeat(max(0, limit)) {
                when {
                    card.isPokemon() -> {
                        val evolutionOk = card.isBasicPokemon() || card.evolveFrom.isNullOrBlank() || namesAvailable.contains(card.evolveFrom)
                        if (evolutionOk) pokemon += card
                    }
                    card.isTrainer() -> trainers += card
                    card.isEnergy() -> energies += card
                }
            }
            usedByName[card.name] = old + max(0, limit)
        }

        val scoredPokemon = if (preferStrong) {
            pokemon.sortedByDescending(::fullCombatScore)
        } else {
            pokemon.sortedWith(
                compareByDescending<CardData> { it.isBasicPokemon() }
                    .thenBy { it.name }
            )
        }

        val chosenPokemon = scoredPokemon.take(20).toMutableList()
        if (chosenPokemon.none { it.isBasicPokemon() }) {
            val fallback = pokemon.firstOrNull { it.isBasicPokemon() }
            if (fallback != null) {
                if (chosenPokemon.size >= 20) chosenPokemon.removeLast()
                chosenPokemon += fallback
            }
        }

        val trainerSorted = if (preferStrong) {
            trainers.sortedByDescending(::trainerUtilityScore)
        } else trainers

        val chosenTrainers = trainerSorted.take(28)
        val chosenEnergies = energies.take(16)

        val result = mutableListOf<FullGameCard>()
        var uid = 1
        fun addCard(card: CardData) {
            result += FullGameCard(uid++, card, false)
        }
        chosenPokemon.forEach(::addCard)
        chosenTrainers.forEach(::addCard)
        chosenEnergies.forEach(::addCard)

        while (result.size < 60) {
            result += FullGameCard(uid++, null, true)
        }

        if (result.size > 60) {
            while (result.size > 60) result.removeLast()
        }

        return result
    }

    private fun fullCombatScore(card: CardData): Double {
        val hp = card.hp ?: 100
        val best = card.attacks.maxOfOrNull { attack ->
            val cost = max(1, attack.cost.size)
            estimatedAttackDamage(attack, cost).toDouble() / cost + estimatedAttackDamage(attack, cost) * 0.45
        } ?: 0.0
        val basicBonus = if (card.isBasicPokemon()) 22.0 else 0.0
        return hp * 0.25 + best + basicBonus - card.retreatCost * 3.0
    }

    private fun trainerUtilityScore(card: CardData): Double {
        val text = card.effect.orEmpty().lowercase(Locale.ROOT)
        var score = 10.0
        if ("ziehe" in text || "draw" in text) score += 35
        if ("suche" in text || "search" in text) score += 30
        if ("energie" in text || "energy" in text) score += 18
        if ("heile" in text || "heal" in text) score += 16
        if ("tausche" in text || "switch" in text) score += 14
        if (card.trainerType.orEmpty().lowercase().contains("support")) score += 8
        return score
    }
}

class FullGameEngine(
    entries: List<CollectionEntry>,
    private val difficulty: AiDifficulty,
    standardOnly: Boolean = true,
    seed: Int = 12026
) {
    private val random = Random(seed)
    private val player = FullSideState(
        FullDeckFactory.build(entries, standardOnly, preferStrong = false)
            .shuffled(random).toMutableList()
    )
    private val ai = FullSideState(
        FullDeckFactory.build(entries, standardOnly, preferStrong = true)
            .shuffled(Random(seed + 991)).toMutableList()
    )
    private val log = mutableListOf<String>()
    private var turnNumber = 1
    private var firstPlayerTurn = true
    private var finished = false
    private var winner: String? = null
    private var lastAiReasoning = "Die KI hat noch keinen Zug gemacht."

    init {
        setupSide(player, "Du")
        setupSide(ai, "KI")
        if (!finished) {
            draw(player, 1)
            resetTurnFlags(player)
            log += "Zug 1: Du beginnst. Ziehe eine Karte. Im ersten Zug darfst du keinen Unterstützer spielen und noch nicht angreifen."
        }
    }

    fun snapshot(): FullGameSnapshot = FullGameSnapshot(
        player = player.view(),
        ai = ai.view(hideHand = true),
        turnNumber = turnNumber,
        firstPlayerTurn = firstPlayerTurn,
        playerEnergyAttached = player.energyAttached,
        playerSupporterUsed = player.supporterUsed,
        playerRetreated = player.retreated,
        finished = finished,
        winner = winner,
        difficulty = difficulty,
        lastAiReasoning = lastAiReasoning,
        log = log.toList()
    )

    fun playBasicFromHand(handIndex: Int): FullGameSnapshot {
        if (finished) return snapshot()
        if (player.bench.size >= 5) {
            log += "Deine Bank ist voll."
            return snapshot()
        }
        val handCard = player.hand.getOrNull(handIndex)
        if (handCard?.isBasicPokemon() != true) {
            log += "Diese Karte ist kein Basis-Pokémon."
            return snapshot()
        }
        val card = handCard.card ?: return snapshot()
        player.hand.removeAt(handIndex)
        player.bench += FullPokemonState(card)
        log += card.name + " kommt auf deine Bank."
        return snapshot()
    }

    fun attachEnergyFromHand(handIndex: Int, targetIndex: Int): FullGameSnapshot {
        if (finished) return snapshot()
        if (player.energyAttached) {
            log += "Du hast in diesem Zug bereits eine Energie angelegt."
            return snapshot()
        }
        val handCard = player.hand.getOrNull(handIndex)
        if (handCard?.isEnergy() != true) {
            log += "Diese Karte ist keine Energie."
            return snapshot()
        }
        val target = targetPokemon(player, targetIndex)
        if (target == null) {
            log += "Dieses Pokémon ist nicht verfügbar."
            return snapshot()
        }
        player.hand.removeAt(handIndex)
        target.energy += 1
        player.energyAttached = true
        player.discard.remove(handCard)
        log += "Du legst Energie an " + target.card.name + ". Energie dort: " + target.energy + "."
        return snapshot()
    }

    fun evolveFromHand(handIndex: Int, targetIndex: Int): FullGameSnapshot {
        if (finished) return snapshot()
        val handCard = player.hand.getOrNull(handIndex)
        val evolution = handCard?.card
        val target = targetPokemon(player, targetIndex)
        if (evolution == null || target == null || !evolution.isPokemon()) {
            log += "Diese Entwicklung ist hier nicht möglich."
            return snapshot()
        }
        if (target.turnsInPlay < 1 || firstPlayerTurn) {
            log += target.card.name + " kann noch nicht entwickelt werden. Es muss seit einem früheren Zug im Spiel sein."
            return snapshot()
        }
        if (!evolution.evolveFrom.equals(target.card.name, ignoreCase = true)) {
            log += evolution.name + " entwickelt sich nicht aus " + target.card.name + "."
            return snapshot()
        }

        val oldMax = target.card.hp ?: 100
        val damage = max(0, oldMax - target.hp)
        target.card = evolution
        target.hp = max(1, (evolution.hp ?: 100) - damage)
        target.status = FullStatus.NONE
        player.hand.removeAt(handIndex)
        log += target.card.name + " wurde entwickelt. Sonderzustände wurden dabei entfernt."
        return snapshot()
    }

    fun playTrainerFromHand(handIndex: Int): FullGameSnapshot {
        if (finished) return snapshot()
        val gameCard = player.hand.getOrNull(handIndex)
        val card = gameCard?.card
        if (card == null || !card.isTrainer()) {
            log += "Diese Karte ist keine Trainerkarte."
            return snapshot()
        }
        val type = card.trainerType.orEmpty().lowercase(Locale.ROOT)
        val isSupporter = "support" in type || "unterstüt" in type
        val isStadium = "stad" in type
        if (isSupporter && firstPlayerTurn) {
            log += "Im allerersten Zug darf der startende Spieler keinen Unterstützer spielen."
            return snapshot()
        }
        if (isSupporter && player.supporterUsed) {
            log += "Du hast in diesem Zug bereits einen Unterstützer gespielt."
            return snapshot()
        }
        if (isStadium && player.stadiumUsed) {
            log += "Du hast in diesem Zug bereits ein Stadion gespielt."
            return snapshot()
        }

        player.hand.removeAt(handIndex)
        applyTrainerEffect(player, ai, card, false)
        if (isSupporter) player.supporterUsed = true
        if (isStadium) player.stadiumUsed = true
        player.discard += gameCard
        log += "Trainerkarte gespielt: " + card.name + "."
        return snapshot()
    }

    fun retreatPlayer(benchIndex: Int): FullGameSnapshot {
        if (finished) return snapshot()
        if (player.retreated) {
            log += "Du hast in diesem Zug bereits zurückgezogen."
            return snapshot()
        }
        val active = player.active ?: return snapshot()
        val replacement = player.bench.getOrNull(benchIndex)
        if (replacement == null) {
            log += "Dieses Bank-Pokémon gibt es nicht."
            return snapshot()
        }
        if (active.status == FullStatus.ASLEEP || active.status == FullStatus.PARALYZED || active.retreatLocked) {
            log += active.card.name + " kann wegen " + active.status.label + " nicht zurückziehen."
            return snapshot()
        }
        val cost = active.card.retreatCost
        if (active.energy < cost) {
            log += "Rückzug kostet " + cost + " Energie. Es liegen erst " + active.energy + " an."
            return snapshot()
        }
        active.energy -= cost
        repeat(cost) {
            player.discard += FullGameCard(-100000 - player.discard.size, null, true)
        }
        val oldActive = active
        oldActive.status = FullStatus.NONE
        replacement.status = FullStatus.NONE
        player.bench[benchIndex] = oldActive
        player.active = replacement
        player.retreated = true
        log += "Du ziehst " + oldActive.card.name + " zurück und schickst " + replacement.card.name + " nach vorn."
        return snapshot()
    }

    fun playerAttack(attackIndex: Int): FullGameSnapshot {
        if (finished) return snapshot()
        if (firstPlayerTurn) {
            log += "Der startende Spieler darf in seinem ersten Zug noch nicht angreifen."
            return snapshot()
        }
        val attacker = player.active ?: return snapshot()
        val defender = ai.active ?: return snapshot()
        val attack = attacker.card.attacks.getOrNull(attackIndex)
        if (attack == null) return snapshot()
        val cost = max(1, attack.cost.size)
        if (attacker.energy < cost) {
            log += attack.name + " braucht " + cost + " Energie."
            return snapshot()
        }
        if (!canAttack(attacker, "Du")) {
            endPlayerTurn()
            return snapshot()
        }
        resolveAttack(player, ai, attacker, defender, attack, "Du")
        resolveKnockOut(ai, player, defenderName = "KI")
        if (!finished) endPlayerTurn()
        return snapshot()
    }

    fun endTurnWithoutAttack(): FullGameSnapshot {
        if (!finished) {
            log += "Du beendest deinen Zug ohne Angriff."
            endPlayerTurn()
        }
        return snapshot()
    }

    fun coachAdvice(): FullCoachAdvice {
        if (finished) return FullCoachAdvice("Kampf beendet", "Starte eine neue Partie.")
        val active = player.active ?: return FullCoachAdvice("Kein aktives Pokémon", "Die Partie kann so nicht fortgesetzt werden.")
        val enemy = ai.active

        val koAttack = if (!firstPlayerTurn && enemy != null) {
            active.card.attacks.withIndex()
                .filter { active.energy >= max(1, it.value.cost.size) }
                .firstOrNull { expectedDamage(active, enemy, it.value) >= enemy.hp }
        } else null
        if (koAttack != null) {
            return FullCoachAdvice(
                "K. o. möglich: " + koAttack.value.name,
                "Die Attacke reicht voraussichtlich für die verbleibenden " + enemy!!.hp + " KP."
            )
        }

        val playableEvolution = player.hand.withIndex().firstOrNull { indexed ->
            val c = indexed.value.card
            c != null && c.isPokemon() && !c.isBasicPokemon() &&
                allPokemon(player).any { p ->
                    p.turnsInPlay >= 1 && c.evolveFrom.equals(p.card.name, ignoreCase = true)
                }
        }
        if (playableEvolution != null) {
            return FullCoachAdvice(
                "Entwicklung verfügbar: " + playableEvolution.value.name,
                "Eine passende Entwicklung erhöht meist KP und Angriffsmöglichkeiten."
            )
        }

        if (!player.energyAttached) {
            val target = allPokemon(player).maxByOrNull { energyNeedScore(it) }
            if (target != null && player.hand.any { it.isEnergy() }) {
                return FullCoachAdvice(
                    "Energie an " + target.card.name,
                    "Dieses Pokémon gewinnt dadurch aktuell am meisten zusätzliche Angriffsoptionen."
                )
            }
        }

        val supporter = player.hand.firstOrNull { game ->
            val c = game.card
            c != null && c.isTrainer() &&
                c.trainerType.orEmpty().lowercase().contains("support") &&
                !player.supporterUsed && !firstPlayerTurn
        }
        if (supporter != null) {
            return FullCoachAdvice(
                "Unterstützer prüfen: " + supporter.name,
                "Du hast deinen Unterstützer für diesen Zug noch nicht benutzt."
            )
        }

        if (!player.retreated && player.bench.isNotEmpty() && active.hp < (active.card.hp ?: 100) / 3) {
            val better = player.bench.maxByOrNull { boardPokemonScore(it, enemy) }
            if (better != null && active.energy >= active.card.retreatCost) {
                return FullCoachAdvice(
                    "Rückzug erwägen",
                    active.card.name + " hat nur noch " + active.hp + " KP. " + better.card.name + " könnte den Platz übernehmen."
                )
            }
        }

        return FullCoachAdvice(
            if (firstPlayerTurn) "Aufbauen statt angreifen" else "Besten Angriff wählen",
            if (firstPlayerTurn) {
                "Lege Basis-Pokémon auf die Bank und Energie an. Als Startspieler darfst du in Zug 1 noch nicht angreifen."
            } else {
                "Prüfe zuerst Entwicklung, Energie und Trainerkarten. Der Angriff beendet deinen Zug."
            }
        )
    }

    private fun endPlayerTurn() {
        ageInPlay(player)
        processBetweenTurns(player)
        if (finished) return
        firstPlayerTurn = false
        aiTurn()
        if (finished) return
        turnNumber += 1
        draw(player, 1)
        resetTurnFlags(player)
        log += "Zug " + turnNumber + ": Du ziehst eine Karte und bist wieder dran."
    }

    private fun aiTurn() {
        if (finished) return
        turnNumber += 1
        draw(ai, 1)
        resetTurnFlags(ai)
        val reasons = mutableListOf<String>()
        val aiFirstTurn = turnNumber == 2

        aiPlayBasics(reasons)
        aiEvolve(reasons)
        aiPlayTrainers(reasons, aiFirstTurn)
        aiAttachEnergy(reasons)
        aiMaybeRetreat(reasons)

        val attacker = ai.active
        val defender = player.active
        if (attacker != null && defender != null) {
            val ready = attacker.card.attacks.withIndex()
                .filter { attacker.energy >= max(1, it.value.cost.size) }
            if (ready.isNotEmpty() && canAttack(attacker, "KI")) {
                val chosen = chooseFullAiAttack(attacker, defender, ready)
                val damage = expectedDamage(attacker, defender, chosen.value)
                if (damage >= defender.hp) {
                    reasons += "Die KI priorisiert " + chosen.value.name + ", weil damit ein K. o. möglich ist."
                } else {
                    reasons += "Die KI wählt " + chosen.value.name + " wegen des besten Gesamtwerts aus Schaden, Kosten und Gegenangriffsrisiko."
                }
                resolveAttack(ai, player, attacker, defender, chosen.value, "KI")
                resolveKnockOut(player, ai, defenderName = "Du")
            } else {
                reasons += "Keine bezahlbare Attacke; die KI investiert in den nächsten Zug."
                log += "KI greift in diesem Zug nicht an."
            }
        }

        lastAiReasoning = reasons.joinToString(" ")
        ageInPlay(ai)
        processBetweenTurns(ai)
    }

    private fun aiPlayBasics(reasons: MutableList<String>) {
        while (ai.bench.size < 5) {
            val choices = ai.hand.withIndex()
                .filter { it.value.isBasicPokemon() }
            val best = choices.maxByOrNull { indexed ->
                val c = indexed.value.card
                if (c == null) 0.0 else boardCardScore(c)
            } ?: break
            val card = best.value.card ?: break
            ai.hand.removeAt(best.index)
            ai.bench += FullPokemonState(card)
            reasons += "Basis-Pokémon " + card.name + " wird auf die Bank gelegt."
        }
    }

    private fun aiEvolve(reasons: MutableList<String>) {
        var changed = true
        while (changed) {
            changed = false
            val targets = allPokemon(ai)
            val handEvolution = ai.hand.withIndex()
                .filter { it.value.card?.isPokemon() == true && it.value.card?.isBasicPokemon() == false }
                .mapNotNull { indexed ->
                    val card = indexed.value.card ?: return@mapNotNull null
                    val target = targets.firstOrNull {
                        it.turnsInPlay >= 1 && card.evolveFrom.equals(it.card.name, ignoreCase = true)
                    } ?: return@mapNotNull null
                    Triple(indexed.index, card, target)
                }
                .maxByOrNull { (_, card, _) -> boardCardScore(card) }

            if (handEvolution != null) {
                val (handIndex, card, target) = handEvolution
                val oldMax = target.card.hp ?: 100
                val damage = max(0, oldMax - target.hp)
                target.card = card
                target.hp = max(1, (card.hp ?: 100) - damage)
                target.status = FullStatus.NONE
                ai.hand.removeAt(handIndex)
                reasons += "Die KI entwickelt zu " + card.name + "."
                changed = true
            }
        }
    }

    private fun aiPlayTrainers(reasons: MutableList<String>, firstAiTurn: Boolean) {
        var guard = 0
        while (guard++ < 6) {
            val choices = ai.hand.withIndex().filter { it.value.card?.isTrainer() == true }
            if (choices.isEmpty()) break
            val best = choices.maxByOrNull { indexed ->
                trainerPlayScore(indexed.value.card!!, ai, player, firstAiTurn)
            } ?: break
            val card = best.value.card ?: break
            val score = trainerPlayScore(card, ai, player, firstAiTurn)
            if (score <= 0) break
            val type = card.trainerType.orEmpty().lowercase()
            val isSupporter = "support" in type || "unterstüt" in type
            val isStadium = "stad" in type
            if (isSupporter && (firstAiTurn || ai.supporterUsed)) {
                break
            }
            if (isStadium && ai.stadiumUsed) break
            ai.hand.removeAt(best.index)
            applyTrainerEffect(ai, player, card, true)
            ai.discard += best.value
            if (isSupporter) ai.supporterUsed = true
            if (isStadium) ai.stadiumUsed = true
            reasons += "Trainerkarte " + card.name + " wird eingesetzt."
            if (isSupporter) break
        }
    }

    private fun aiAttachEnergy(reasons: MutableList<String>) {
        if (ai.energyAttached) return
        val energyIndex = ai.hand.indexOfFirst { it.isEnergy() }
        if (energyIndex < 0) return
        val target = allPokemon(ai).maxByOrNull { energyNeedScore(it) } ?: return
        ai.hand.removeAt(energyIndex)
        target.energy += 1
        ai.energyAttached = true
        reasons += "Energie geht an " + target.card.name + ", weil sie dort den größten zusätzlichen Angriffswert erzeugt."
    }

    private fun aiMaybeRetreat(reasons: MutableList<String>) {
        val active = ai.active ?: return
        if (ai.retreated || ai.bench.isEmpty()) return
        if (active.status == FullStatus.ASLEEP || active.status == FullStatus.PARALYZED) return
        if (active.energy < active.card.retreatCost) return
        val enemy = player.active
        val current = boardPokemonScore(active, enemy)
        val bestIndex = ai.bench.indices.maxByOrNull { index ->
            boardPokemonScore(ai.bench[index], enemy)
        } ?: return
        val best = ai.bench[bestIndex]
        val danger = active.hp <= (active.card.hp ?: 100) / 3
        val switchThreshold = if (difficulty == AiDifficulty.EXPERT) 8.0 else 24.0
        if (boardPokemonScore(best, enemy) > current + switchThreshold || danger) {
            active.energy -= active.card.retreatCost
            val old = active
            old.status = FullStatus.NONE
            best.status = FullStatus.NONE
            ai.bench[bestIndex] = old
            ai.active = best
            ai.retreated = true
            reasons += "Die KI zieht " + old.card.name + " zurück und bringt " + best.card.name + ", weil das Matchup günstiger ist."
        }
    }

    private fun chooseFullAiAttack(
        attacker: FullPokemonState,
        defender: FullPokemonState,
        ready: List<IndexedValue<CardAttack>>
    ): IndexedValue<CardAttack> {
        if (difficulty == AiDifficulty.EASY && ready.size > 1) {
            return ready[random.nextInt(ready.size)]
        }
        return ready.maxByOrNull { indexed ->
            val attack = indexed.value
            val damage = expectedDamage(attacker, defender, attack)
            val cost = max(1, attack.cost.size)
            val ko = if (damage >= defender.hp) 650.0 else 0.0
            val efficiency = damage.toDouble() / cost
            val effect = strategicTextValue(attack.effect)
            val risk = if (difficulty == AiDifficulty.EXPERT) {
                val response = defender.card.attacks
                    .filter { defender.energy >= max(1, it.cost.size) }
                    .maxOfOrNull { expectedDamage(defender, attacker, it) } ?: 0
                response * 0.18
            } else 0.0
            damage * 1.25 + efficiency * 0.5 + ko + effect - risk
        } ?: ready.first()
    }

    private fun resolveAttack(
        attackingSide: FullSideState,
        defendingSide: FullSideState,
        attacker: FullPokemonState,
        defender: FullPokemonState,
        attack: CardAttack,
        actor: String
    ) {
        if (attacker.status == FullStatus.CONFUSED && !random.nextBoolean()) {
            attacker.hp = max(0, attacker.hp - 30)
            log += actor + ": " + attacker.card.name + " ist verwirrt. Der Angriff misslingt und es nimmt 30 Schaden."
            if (attacker.hp <= 0) resolveSelfKnockOut(attackingSide, defendingSide, actor)
            return
        }

        val damage = expectedDamage(attacker, defender, attack)
        defender.hp = max(0, defender.hp - damage)
        log += actor + ": " + attacker.card.name + " setzt " + attack.name + " ein und macht " + damage + " Schaden."

        val lower = attack.effect.lowercase(Locale.ROOT)
        val heal = parseNumberAfter(lower, listOf("heile", "heal"))
        if (heal > 0) {
            val before = attacker.hp
            attacker.hp = min(attacker.card.hp ?: 100, attacker.hp + heal)
            if (attacker.hp > before) log += attacker.card.name + " heilt " + (attacker.hp - before) + " KP."
        }

        if ("vergiftet" in lower || "poisoned" in lower) defender.status = FullStatus.POISONED
        if ("verbrannt" in lower || "burned" in lower) defender.status = FullStatus.BURNED
        if ("paralys" in lower) defender.status = FullStatus.PARALYZED
        if ("schläft" in lower || "asleep" in lower) defender.status = FullStatus.ASLEEP
        if ("verwirrt" in lower || "confused" in lower) defender.status = FullStatus.CONFUSED

        val recoil = if ("diesem pok" in lower || "itself" in lower || "sich selbst" in lower) {
            parseNumberAfter(lower, listOf("selbst", "itself", "diesem pok"))
        } else 0
        if (recoil > 0) {
            attacker.hp = max(0, attacker.hp - recoil)
            log += attacker.card.name + " nimmt " + recoil + " Rückstoßschaden."
        }

        if (attack.effect.isNotBlank()) {
            log += "Karteneffekt: " + attack.effect
        }
    }

    private fun resolveKnockOut(
        defending: FullSideState,
        attacking: FullSideState,
        defenderName: String
    ) {
        val active = defending.active ?: return
        if (active.hp > 0) return
        log += active.card.name + " ist kampfunfähig."
        defending.discard += FullGameCard(-200000 - defending.discard.size, active.card, false)
        defending.active = null

        if (attacking.prizes.isNotEmpty()) {
            val prize = attacking.prizes.removeAt(0)
            attacking.hand += prize
            log += (if (defenderName == "KI") "Du" else "KI") + " nimmt eine Preiskarte. Noch " + attacking.prizes.size + "."
        }

        if (attacking.prizes.isEmpty()) {
            finish(if (defenderName == "KI") "Du" else "KI")
            return
        }
        if (defending.bench.isEmpty()) {
            finish(if (defenderName == "KI") "Du" else "KI")
            return
        }

        if (defenderName == "KI") {
            val bestIndex = defending.bench.indices.maxByOrNull {
                boardPokemonScore(defending.bench[it], attacking.active)
            } ?: 0
            defending.active = defending.bench.removeAt(bestIndex)
            log += "KI schickt " + defending.active!!.card.name + " als neues aktives Pokémon nach vorn."
        } else {
            val bestIndex = defending.bench.indices.maxByOrNull {
                boardPokemonScore(defending.bench[it], attacking.active)
            } ?: 0
            defending.active = defending.bench.removeAt(bestIndex)
            log += "Für den Lernfluss wird automatisch " + defending.active!!.card.name + " als dein neues aktives Pokémon eingesetzt."
        }
    }

    private fun resolveSelfKnockOut(
        side: FullSideState,
        opponent: FullSideState,
        actor: String
    ) {
        val active = side.active ?: return
        if (active.hp > 0) return
        log += active.card.name + " ist durch eigenen Schaden kampfunfähig."
        side.discard += FullGameCard(-300000 - side.discard.size, active.card, false)
        side.active = null
        if (opponent.prizes.isNotEmpty()) {
            opponent.hand += opponent.prizes.removeAt(0)
        }
        if (side.bench.isEmpty()) {
            finish(if (actor == "Du") "KI" else "Du")
        } else {
            side.active = side.bench.removeAt(0)
        }
    }

    private fun applyTrainerEffect(
        side: FullSideState,
        opponent: FullSideState,
        card: CardData,
        aiControlled: Boolean
    ) {
        val text = card.effect.orEmpty().lowercase(Locale.ROOT)
        val drawCount = parseNumberAfter(text, listOf("ziehe", "draw"))
        if (drawCount > 0) {
            draw(side, min(drawCount, 7))
            log += card.name + ": " + min(drawCount, 7) + " Karte(n) gezogen."
        }

        if (("suche" in text || "search" in text) && ("energie" in text || "energy" in text)) {
            val index = side.deck.indexOfFirst { it.isEnergy() }
            if (index >= 0) {
                side.hand += side.deck.removeAt(index)
                log += card.name + ": Eine Energie wurde aus dem Deck auf die Hand genommen."
            }
        }

        val heal = parseNumberAfter(text, listOf("heile", "heal"))
        if (heal > 0) {
            val target = if (aiControlled) {
                allPokemon(side).minByOrNull { p -> p.hp.toDouble() / max(1, p.card.hp ?: 100) }
            } else side.active
            if (target != null) {
                val before = target.hp
                target.hp = min(target.card.hp ?: 100, target.hp + heal)
                log += card.name + ": " + target.card.name + " heilt " + (target.hp - before) + " KP."
            }
        }

        if (("tausche" in text || "switch" in text) && side.bench.isNotEmpty() && side.active != null) {
            val index = if (aiControlled) {
                side.bench.indices.maxByOrNull { boardPokemonScore(side.bench[it], opponent.active) } ?: 0
            } else 0
            val old = side.active!!
            val replacement = side.bench[index]
            old.status = FullStatus.NONE
            replacement.status = FullStatus.NONE
            side.bench[index] = old
            side.active = replacement
            log += card.name + ": Aktives Pokémon wurde gewechselt."
        }
    }

    private fun trainerPlayScore(
        card: CardData,
        side: FullSideState,
        opponent: FullSideState,
        firstTurn: Boolean
    ): Double {
        val type = card.trainerType.orEmpty().lowercase()
        if (("support" in type || "unterstüt" in type) && (firstTurn || side.supporterUsed)) return -1000.0
        if ("stad" in type && side.stadiumUsed) return -1000.0
        val text = card.effect.orEmpty().lowercase()
        var score = 4.0
        val draw = parseNumberAfter(text, listOf("ziehe", "draw"))
        score += draw * if (side.hand.size <= 4) 10 else 4
        if (("suche" in text || "search" in text) && ("energie" in text || "energy" in text)) {
            score += if (side.hand.none { it.isEnergy() }) 35 else 12
        }
        val heal = parseNumberAfter(text, listOf("heile", "heal"))
        val damaged = side.active?.let { (it.card.hp ?: 100) - it.hp } ?: 0
        score += min(heal, damaged) * 0.4
        if (("switch" in text || "tausche" in text) && side.bench.isNotEmpty()) {
            val activeScore = side.active?.let { boardPokemonScore(it, opponent.active) } ?: 0.0
            val better = side.bench.maxOfOrNull { boardPokemonScore(it, opponent.active) } ?: activeScore
            score += max(0.0, better - activeScore)
        }
        return score
    }

    private fun processBetweenTurns(sideThatJustActed: FullSideState) {
        val active = sideThatJustActed.active ?: return
        when (active.status) {
            FullStatus.POISONED -> {
                active.hp = max(0, active.hp - 10)
                log += active.card.name + " nimmt 10 Giftschaden zwischen den Zügen."
            }
            FullStatus.BURNED -> {
                active.hp = max(0, active.hp - 20)
                log += active.card.name + " nimmt 20 Verbrennungsschaden."
                if (random.nextBoolean()) {
                    active.status = FullStatus.NONE
                    log += active.card.name + " ist nicht mehr verbrannt."
                }
            }
            FullStatus.ASLEEP -> {
                if (random.nextBoolean()) {
                    active.status = FullStatus.NONE
                    log += active.card.name + " wacht auf."
                }
            }
            FullStatus.PARALYZED -> {
                active.status = FullStatus.NONE
                log += active.card.name + " ist nicht mehr paralysiert."
            }
            else -> Unit
        }
        if (active.hp <= 0) {
            if (sideThatJustActed === player) resolveSelfKnockOut(player, ai, "Du")
            else resolveSelfKnockOut(ai, player, "KI")
        }
    }

    private fun canAttack(attacker: FullPokemonState, actor: String): Boolean {
        return when (attacker.status) {
            FullStatus.ASLEEP -> {
                log += actor + ": " + attacker.card.name + " schläft und kann nicht angreifen."
                false
            }
            FullStatus.PARALYZED -> {
                log += actor + ": " + attacker.card.name + " ist paralysiert und kann nicht angreifen."
                false
            }
            else -> true
        }
    }

    private fun expectedDamage(
        attacker: FullPokemonState,
        defender: FullPokemonState,
        attack: CardAttack
    ): Int {
        var damage = estimatedAttackDamage(attack, attacker.energy)
        val attackerTypes = attacker.card.types.map { it.lowercase(Locale.ROOT) }
        if (defender.card.weaknesses.any { weak -> attackerTypes.contains(weak.lowercase(Locale.ROOT)) }) {
            damage *= 2
        }
        if (defender.card.resistances.any { resist -> attackerTypes.contains(resist.lowercase(Locale.ROOT)) }) {
            damage = max(0, damage - 30)
        }
        return damage
    }

    private fun setupSide(side: FullSideState, label: String) {
        var attempts = 0
        do {
            side.deck += side.hand
            side.hand.clear()
            side.deck.shuffle(random)
            draw(side, 7)
            attempts += 1
        } while (side.hand.none { it.isBasicPokemon() } && attempts < 20)

        if (side.hand.none { it.isBasicPokemon() }) {
            val deckIndex = side.deck.indexOfFirst { it.isBasicPokemon() }
            if (deckIndex >= 0) {
                side.hand += side.deck.removeAt(deckIndex)
            }
        }

        val activeIndex = side.hand.indexOfFirst { it.isBasicPokemon() }
        if (activeIndex < 0) {
            finish(if (label == "Du") "KI" else "Du")
            log += label + " hat kein Basis-Pokémon für den Spielstart."
            return
        }
        val activeCard = side.hand.removeAt(activeIndex).card!!
        side.active = FullPokemonState(activeCard)

        while (side.bench.size < 5) {
            val index = side.hand.indexOfFirst { it.isBasicPokemon() }
            if (index < 0) break
            val c = side.hand.removeAt(index).card ?: break
            side.bench += FullPokemonState(c)
        }

        repeat(min(6, side.deck.size)) {
            side.prizes += side.deck.removeAt(0)
        }
        log += label + " startet mit " + side.active!!.card.name + ", " + side.bench.size + " Pokémon auf der Bank und " + side.prizes.size + " Preiskarten."
    }

    private fun draw(side: FullSideState, count: Int) {
        repeat(count) {
            if (side.deck.isEmpty()) {
                if (!finished) finish(if (side === player) "KI" else "Du")
                return
            }
            side.hand += side.deck.removeAt(0)
        }
    }

    private fun resetTurnFlags(side: FullSideState) {
        side.energyAttached = false
        side.supporterUsed = false
        side.stadiumUsed = false
        side.retreated = false
        side.usedAbilities.clear()
    }

    private fun ageInPlay(side: FullSideState) {
        side.active?.let { it.turnsInPlay += 1 }
        side.bench.forEach { it.turnsInPlay += 1 }
    }

    private fun targetPokemon(side: FullSideState, targetIndex: Int): FullPokemonState? {
        return if (targetIndex == 0) side.active else side.bench.getOrNull(targetIndex - 1)
    }

    private fun allPokemon(side: FullSideState): List<FullPokemonState> = buildList {
        side.active?.let(::add)
        addAll(side.bench)
    }

    private fun energyNeedScore(pokemon: FullPokemonState): Double {
        val attacks = pokemon.card.attacks
        if (attacks.isEmpty()) return 0.0
        val before = attacks.maxOfOrNull { attack ->
            if (pokemon.energy >= max(1, attack.cost.size)) estimatedAttackDamage(attack, pokemon.energy).toDouble() else 0.0
        } ?: 0.0
        val afterEnergy = pokemon.energy + 1
        val after = attacks.maxOfOrNull { attack ->
            if (afterEnergy >= max(1, attack.cost.size)) estimatedAttackDamage(attack, afterEnergy).toDouble() else 0.0
        } ?: 0.0
        val unlockBonus = if (after > before) 55.0 else 0.0
        return (after - before) + unlockBonus + boardCardScore(pokemon.card) * 0.08
    }

    private fun boardPokemonScore(pokemon: FullPokemonState, enemy: FullPokemonState?): Double {
        val healthRatio = pokemon.hp.toDouble() / max(1, pokemon.card.hp ?: 100)
        val bestReady = pokemon.card.attacks
            .filter { pokemon.energy >= max(1, it.cost.size) }
            .maxOfOrNull { attack ->
                if (enemy == null) estimatedAttackDamage(attack, pokemon.energy)
                else expectedDamage(pokemon, enemy, attack)
            } ?: 0
        val ko = if (enemy != null && bestReady >= enemy.hp) 180 else 0
        return bestReady * 1.15 + ko + healthRatio * 45 + pokemon.energy * 9 - pokemon.card.retreatCost * 3
    }

    private fun boardCardScore(card: CardData): Double {
        val hp = card.hp ?: 100
        val damage = card.attacks.maxOfOrNull { estimatedAttackDamage(it, max(1, it.cost.size)) } ?: 0
        return hp * 0.25 + damage * 0.75 - card.retreatCost * 2
    }

    private fun strategicTextValue(text: String): Double {
        val lower = text.lowercase(Locale.ROOT)
        var score = 0.0
        if ("heile" in lower || "heal" in lower) score += 18
        if ("vergiftet" in lower || "poison" in lower) score += 16
        if ("verbrannt" in lower || "burn" in lower) score += 17
        if ("paralys" in lower) score += 24
        if ("schläft" in lower || "asleep" in lower) score += 19
        if ("verwirrt" in lower || "confused" in lower) score += 17
        if ("ziehe" in lower || "draw" in lower) score += 10
        return score
    }

    private fun parseNumberAfter(text: String, keywords: List<String>): Int {
        keywords.forEach { key ->
            val idx = text.indexOf(key)
            if (idx >= 0) {
                val tail = text.substring(idx + key.length).take(32)
                val number = Regex("""\d+""").find(tail)?.value?.toIntOrNull()
                if (number != null) return number
            }
        }
        return 0
    }

    private fun finish(winnerName: String) {
        if (finished) return
        finished = true
        winner = winnerName
        log += "Partie beendet. Gewinner: " + winnerName + "."
    }

    private fun FullSideState.view(hideHand: Boolean = false) = FullSideView(
        active = active?.view(),
        bench = bench.map { it.view() },
        hand = if (hideHand) List(hand.size) { FullGameCard(-1 - it, null, false) } else hand.toList(),
        deckCount = deck.size,
        discardCount = discard.size,
        prizesLeft = prizes.size
    )
}
