package de.ahnsen.kartentrainer

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class FullStartMode(val label: String) {
    PLAYER_FIRST("Ich beginne"),
    AI_FIRST("KI beginnt"),
    COIN_FLIP("Münzwurf")
}

private enum class ReactiveEvent {
    DAMAGED,
    KNOCKED_OUT
}

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
    val virtualEnergy: Boolean = false,
    val virtualEnergyType: String? = null
) {
    val name: String
        get() = card?.name ?: ((virtualEnergyType ?: "Farblos") + "-Energie (Training)")

    fun isPokemon(): Boolean = card?.isPokemon() == true
    fun isTrainer(): Boolean = card?.isTrainer() == true
    fun isEnergy(): Boolean = virtualEnergy || card?.isEnergy() == true
    fun isBasicPokemon(): Boolean = card?.isBasicPokemon() == true
}

data class FullPokemonView(
    val card: CardData,
    val hp: Int,
    val energy: Int,
    val energyTypes: List<String>,
    val turnsInPlay: Int,
    val status: FullStatus,
    val toolName: String?,
    val maxHp: Int,
    val effectiveRetreatCost: Int
)

data class FullSideView(
    val active: FullPokemonView?,
    val bench: List<FullPokemonView>,
    val hand: List<FullGameCard>,
    val deckCount: Int,
    val discardCount: Int,
    val lostZoneCount: Int,
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
    val playerStarted: Boolean,
    val playerMulligans: Int,
    val aiMulligans: Int,
    val stadiumName: String?,
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
    val energyTypes: MutableList<String> = mutableListOf(),
    val attachedSpecialEnergy: MutableList<CardData> = mutableListOf(),
    var turnsInPlay: Int = 0,
    var status: FullStatus = FullStatus.NONE,
    var preventAllDamageNext: Boolean = false,
    var damageReductionNext: Int = 0,
    var attackLocked: Boolean = false,
    var retreatLocked: Boolean = false,
    var tool: CardData? = null
) {
    fun view(maxHp: Int, retreatCost: Int) =
        FullPokemonView(card, hp, energy, energyTypes.toList(), turnsInPlay, status, tool?.name, maxHp, retreatCost)
}

private data class FullSideState(
    val deck: MutableList<FullGameCard>,
    val hand: MutableList<FullGameCard> = mutableListOf(),
    val prizes: MutableList<FullGameCard> = mutableListOf(),
    val discard: MutableList<FullGameCard> = mutableListOf(),
    val lostZone: MutableList<FullGameCard> = mutableListOf(),
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

        val preferredEnergyType = chosenPokemon
            .flatMap { it.types }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "Colorless"

        while (result.size < 60) {
            result += FullGameCard(uid++, null, true, preferredEnergyType)
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
    startMode: FullStartMode = FullStartMode.COIN_FLIP,
    private val aiTuning: AiTuning = AiTuning(),
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
    private val playerStarts: Boolean = when (startMode) {
        FullStartMode.PLAYER_FIRST -> true
        FullStartMode.AI_FIRST -> false
        FullStartMode.COIN_FLIP -> random.nextBoolean()
    }
    private var playerMulligans: Int = 0
    private var aiMulligans: Int = 0
    private var stadiumCard: FullGameCard? = null
    private var stadiumOwner: FullSideState? = null
    private var turnNumber = if (playerStarts) 1 else 0
    private var firstPlayerTurn = playerStarts
    private var finished = false
    private var winner: String? = null
    private var lastAiReasoning = "Die KI hat noch keinen Zug gemacht."
    private var lastDeepPlanExplanation = ""

    init {
        playerMulligans = setupSide(player, "Du")
        aiMulligans = setupSide(ai, "KI")

        if (aiMulligans > 0) {
            draw(player, aiMulligans)
            log += "KI hatte " + aiMulligans + " Mulligan(s). Du ziehst " + aiMulligans + " zusätzliche Karte(n)."
        }
        if (playerMulligans > 0) {
            draw(ai, playerMulligans)
            log += "Du hattest " + playerMulligans + " Mulligan(s). Die KI zieht " + playerMulligans + " zusätzliche Karte(n)."
        }

        if (!finished) {
            if (playerStarts) {
                draw(player, 1)
                resetTurnFlags(player)
                log += "Startspieler: Du. Im ersten Zug darfst du keinen Unterstützer spielen und noch nicht angreifen."
            } else {
                log += "Startspieler: KI. Ihr erster Zug darf keinen Unterstützer und keinen Angriff enthalten."
                aiTurn(opening = true)
                if (!finished) {
                    turnNumber += 1
                    draw(player, 1)
                    resetTurnFlags(player)
                    firstPlayerTurn = false
                    log += "Zug " + turnNumber + ": Du bist als zweiter Spieler dran und darfst angreifen."
                }
            }
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
        playerStarted = playerStarts,
        playerMulligans = playerMulligans,
        aiMulligans = aiMulligans,
        stadiumName = stadiumCard?.name,
        lastAiReasoning = lastAiReasoning,
        log = log.toList()
    )

    fun setPhysicalActive(card: CardData): FullGameSnapshot {
        if (finished) return snapshot()
        if (!card.isBasicPokemon()) {
            log += "Für den physischen Start muss ein Basis-Pokémon gescannt werden."
            return snapshot()
        }
        removeOneCardFromHiddenZones(player, card.id)
        val old = player.active
        if (old != null && old.card.id != card.id) {
            player.deck += FullGameCard(-800000 - player.deck.size, old.card, false)
            player.deck.shuffle(random)
        }
        player.active = FullPokemonState(card)
        log += "Physischer Tisch synchronisiert: " + card.name + " ist dein aktives Pokémon."
        return snapshot()
    }

    fun synchronizePhysicalCard(card: CardData, targetIndex: Int = 0): FullGameSnapshot {
        if (finished) return snapshot()
        val handIndex = ensurePhysicalHandCard(card)
        return when {
            card.isEnergy() -> attachEnergyFromHand(handIndex, targetIndex)
            card.isTrainer() -> playTrainerFromHand(handIndex, targetIndex)
            card.isPokemon() && card.isBasicPokemon() -> playBasicFromHand(handIndex)
            card.isPokemon() -> evolveFromHand(handIndex, targetIndex)
            else -> {
                log += "Physische Karte erkannt, aber für diese Kartenkategorie gibt es noch keine Zugaktion."
                snapshot()
            }
        }
    }

    private fun ensurePhysicalHandCard(card: CardData): Int {
        val inHand = player.hand.indexOfFirst { it.card?.id == card.id }
        if (inHand >= 0) return inHand

        val inDeck = player.deck.indexOfFirst { it.card?.id == card.id }
        if (inDeck >= 0) {
            player.hand += player.deck.removeAt(inDeck)
            return player.hand.lastIndex
        }

        val inPrizes = player.prizes.indexOfFirst { it.card?.id == card.id }
        if (inPrizes >= 0) {
            player.hand += player.prizes.removeAt(inPrizes)
            log += "Physischer Abgleich: Karte lag im simulierten Preisstapel und wurde an die reale Hand angepasst."
            return player.hand.lastIndex
        }

        val inDiscard = player.discard.indexOfFirst { it.card?.id == card.id }
        if (inDiscard >= 0) {
            player.hand += player.discard.removeAt(inDiscard)
            log += "Physischer Abgleich: Karte aus simulierter Ablage an realen Tischzustand angepasst."
            return player.hand.lastIndex
        }

        player.hand += FullGameCard(-900000 - player.hand.size, card, false)
        log += "Physischer Abgleich: " + card.name + " wurde als reale Handkarte ergänzt."
        return player.hand.lastIndex
    }

    private fun removeOneCardFromHiddenZones(side: FullSideState, cardId: String) {
        val zones = listOf(side.hand, side.deck, side.prizes, side.discard)
        zones.forEach { zone ->
            val index = zone.indexOfFirst { it.card?.id == cardId }
            if (index >= 0) {
                zone.removeAt(index)
                return
            }
        }
    }

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
        val state = FullPokemonState(card)
        player.bench += state
        log += card.name + " kommt auf deine Bank."
        triggerOnPlayAbilities(player, ai, state, "Du", false)
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
        attachEnergyCard(target, handCard)
        player.energyAttached = true
        log += "Du legst " + handCard.name + " an " + target.card.name +
            ". Energie dort: " + target.energy + " (" + target.energyTypes.joinToString() + ")."
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
        triggerOnPlayAbilities(player, ai, target, "Du", false)
        return snapshot()
    }

    fun playTrainerFromHand(handIndex: Int, targetIndex: Int = 0): FullGameSnapshot {
        if (finished) return snapshot()
        val gameCard = player.hand.getOrNull(handIndex)
        val card = gameCard?.card
        if (card == null || !card.isTrainer()) {
            log += "Diese Karte ist keine Trainerkarte."
            return snapshot()
        }

        val isSupporter = isSupporterCard(card)
        val isStadium = isStadiumCard(card)
        val isTool = isToolCard(card)

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

        val parsed = EffectParser.parse(card.effect.orEmpty(), EffectSourceKind.TRAINER)
        val discardCost = parsed.operations.filterIsInstance<DiscardHandCards>().sumOf { it.count }
        if (discardCost > player.hand.size - 1) {
            log += card.name + " kann nicht gespielt werden: Für die Kosten fehlen Handkarten."
            return snapshot()
        }

        if (isTool) {
            val target = targetPokemon(player, targetIndex)
            if (target == null) {
                log += "Wähle zuerst ein Pokémon für die Ausrüstung."
                return snapshot()
            }
            if (target.tool != null) {
                log += target.card.name + " hat bereits eine Pokémon-Ausrüstung."
                return snapshot()
            }
            player.hand.removeAt(handIndex)
            target.tool = card
            log += card.name + " wurde an " + target.card.name + " angelegt."
            logEffectCoverage(card.name, parsed)
            return snapshot()
        }

        if (isStadium) {
            if (stadiumCard?.card?.name.equals(card.name, ignoreCase = true)) {
                log += "Ein Stadion mit demselben Namen liegt bereits im Spiel."
                return snapshot()
            }
            player.hand.removeAt(handIndex)
            val old = stadiumCard
            val oldOwner = stadiumOwner
            if (old != null && oldOwner != null) {
                oldOwner.discard += old
            }
            stadiumCard = gameCard
            stadiumOwner = player
            player.stadiumUsed = true
            log += "Stadion im Spiel: " + card.name + "."
            logEffectCoverage(card.name, parsed)
            return snapshot()
        }

        player.hand.removeAt(handIndex)
        applyTrainerEffect(player, ai, card, false)
        if (isSupporter) player.supporterUsed = true
        player.discard += gameCard
        log += "Trainerkarte gespielt: " + card.name + "."
        return snapshot()
    }

    fun usePlayerAbility(targetIndex: Int, abilityIndex: Int): FullGameSnapshot {
        if (finished) return snapshot()
        val pokemon = targetPokemon(player, targetIndex)
        if (pokemon == null) {
            log += "Dieses Pokémon ist nicht verfügbar."
            return snapshot()
        }
        val ability = pokemon.card.abilities.getOrNull(abilityIndex)
        if (ability == null) {
            log += "Diese Fähigkeit ist nicht verfügbar."
            return snapshot()
        }
        val timing = classifyAbilityTiming(ability.effect)
        if (timing != AbilityTiming.ACTIVATED && timing != AbilityTiming.UNKNOWN) {
            log += ability.name + " ist " + timing.label + " und wird nicht als manuelle Zugaktion behandelt."
            return snapshot()
        }

        val key = pokemon.card.id + "#" + ability.name
        if (!player.usedAbilities.add(key)) {
            log += ability.name + " wurde in diesem Zug bereits benutzt."
            return snapshot()
        }

        val parsed = EffectParser.parse(ability.effect, EffectSourceKind.ABILITY)
        executeParsedEffect(
            side = player,
            opponent = ai,
            sourcePokemon = pokemon,
            parsed = parsed,
            actor = "Du",
            aiControlled = false
        )
        log += "Fähigkeit benutzt: " + pokemon.card.name + " – " + ability.name + "."
        logEffectCoverage(ability.name, parsed)
        resolveKnockOut(ai, player, defenderName = "KI")
        resolveBenchKnockOuts(ai, player, "KI")
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
        val cost = effectiveRetreatCost(active)
        if (active.energy < cost) {
            log += "Rückzug kostet " + cost + " Energie. Es liegen erst " + active.energy + " an."
            return snapshot()
        }
        val removedForRetreat = removeEnergyUnits(active, cost)
        repeat(removedForRetreat) {
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
        val cost = attack.cost.size
        if (!canPayAttack(attacker, attack)) {
            log += attack.name + " braucht " + formatAttackCost(attack) +
                ". Angelegt: " + attacker.energyTypes.joinToString().ifBlank { "keine Energie" } + "."
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
                .filter { canPayAttack(active, it.value) }
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

    private fun aiTurn(opening: Boolean = false) {
        if (finished) return
        turnNumber += 1
        draw(ai, 1)
        resetTurnFlags(ai)
        val reasons = mutableListOf<String>()
        val aiFirstTurn = opening

        aiPlayBasics(reasons)
        aiEvolve(reasons)
        aiUseAbilities(reasons)
        aiPlayTrainers(reasons, aiFirstTurn)
        aiAttachEnergy(reasons)
        aiMaybeRetreat(reasons)

        val attacker = ai.active
        val defender = player.active
        if (!opening && attacker != null && defender != null) {
            val ready = attacker.card.attacks.withIndex()
                .filter { canPayAttack(attacker, it.value) }
            if (ready.isNotEmpty() && canAttack(attacker, "KI")) {
                val chosen = chooseFullAiAttack(attacker, defender, ready)
                val damage = expectedDamage(attacker, defender, chosen.value)
                if (damage >= defender.hp) {
                    reasons += "Die KI priorisiert " + chosen.value.name + ", weil damit ein K. o. möglich ist."
                } else {
                    reasons += "Die KI wählt " + chosen.value.name + " wegen des besten Gesamtwerts aus Schaden, Kosten und Gegenangriffsrisiko."
                }
                if (lastDeepPlanExplanation.isNotBlank()) reasons += lastDeepPlanExplanation
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
            val state = FullPokemonState(card)
            ai.bench += state
            reasons += "Basis-Pokémon " + card.name + " wird auf die Bank gelegt."
            triggerOnPlayAbilities(ai, player, state, "KI", true)
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
                triggerOnPlayAbilities(ai, player, target, "KI", true)
                changed = true
            }
        }
    }

    private fun triggerReactiveAbilities(
        side: FullSideState,
        opponent: FullSideState,
        pokemon: FullPokemonState,
        event: ReactiveEvent,
        actor: String,
        aiControlled: Boolean
    ) {
        pokemon.card.abilities.forEach { ability ->
            if (classifyAbilityTiming(ability.effect) != AbilityTiming.REACTIVE) return@forEach
            val lower = ability.effect.lowercase(Locale.ROOT)
            val matches = when (event) {
                ReactiveEvent.DAMAGED ->
                    lower.contains("schaden") || lower.contains("damaged")
                ReactiveEvent.KNOCKED_OUT ->
                    lower.contains("kampfunfähig") ||
                        lower.contains("knocked out") ||
                        lower.contains("knockout")
            }
            if (!matches) return@forEach

            val onceKey = "reactive:" + event.name + ":" + pokemon.card.id + ":" + ability.name + ":" + turnNumber
            if (!side.usedAbilities.add(onceKey)) return@forEach

            val actionText = reactiveActionText(ability.effect)
            val parsed = EffectParser.parse(actionText, EffectSourceKind.ABILITY)
            if (parsed.operations.isNotEmpty()) {
                executeParsedEffect(
                    side = side,
                    opponent = opponent,
                    sourcePokemon = pokemon,
                    parsed = parsed,
                    actor = actor,
                    aiControlled = aiControlled
                )
                log += actor + ": Reaktive Fähigkeit " + ability.name + " wurde ausgelöst."
                logEffectCoverage(ability.name, parsed)
            } else {
                log += actor + ": Reaktive Fähigkeit " + ability.name +
                    " ausgelöst; Sondertext muss manuell beachtet werden: " + ability.effect
            }
        }
    }

    private fun reactiveActionText(text: String): String {
        val comma = text.indexOf(',')
        if (comma >= 0 && comma < text.lastIndex) return text.substring(comma + 1).trim()
        val period = text.indexOf('.')
        if (period >= 0 && period < text.lastIndex) return text.substring(period + 1).trim()
        return text
    }

    private fun triggerOnPlayAbilities(
        side: FullSideState,
        opponent: FullSideState,
        pokemon: FullPokemonState,
        actor: String,
        aiControlled: Boolean
    ) {
        pokemon.card.abilities.forEach { ability ->
            if (classifyAbilityTiming(ability.effect) == AbilityTiming.ON_PLAY) {
                val parsed = EffectParser.parse(ability.effect, EffectSourceKind.ABILITY)
                executeParsedEffect(side, opponent, pokemon, parsed, actor, aiControlled)
                log += actor + ": Beim-Ausspielen-Fähigkeit " + ability.name + " ausgelöst."
                logEffectCoverage(ability.name, parsed)
            }
        }
    }

    private fun aiUseAbilities(reasons: MutableList<String>) {
        val candidates = allPokemon(ai).flatMap { pokemon ->
            pokemon.card.abilities.mapIndexedNotNull { index, ability ->
                val timing = classifyAbilityTiming(ability.effect)
                if (timing != AbilityTiming.ACTIVATED && timing != AbilityTiming.UNKNOWN) return@mapIndexedNotNull null
                val key = pokemon.card.id + "#" + ability.name
                if (ai.usedAbilities.contains(key)) return@mapIndexedNotNull null
                val parsed = EffectParser.parse(ability.effect, EffectSourceKind.ABILITY)
                val score = EffectAiEvaluator.score(parsed)
                if (score <= 3.0) return@mapIndexedNotNull null
                Triple(pokemon, index, parsed)
            }
        }.sortedByDescending { EffectAiEvaluator.score(it.third) }

        candidates.take(if (difficulty == AiDifficulty.EXPERT) 3 else 2).forEach { candidate ->
            val pokemon = candidate.first
            val index = candidate.second
            val parsed = candidate.third
            val ability = pokemon.card.abilities[index]
            val key = pokemon.card.id + "#" + ability.name
            if (ai.usedAbilities.add(key)) {
                executeParsedEffect(ai, player, pokemon, parsed, "KI", true)
                reasons += "Die KI nutzt " + pokemon.card.name + " – " + ability.name +
                    " (Effektwert " + EffectAiEvaluator.score(parsed).toInt() + ")."
                logEffectCoverage(ability.name, parsed)
            }
        }
    }

    private fun aiPlayTrainers(reasons: MutableList<String>, firstAiTurn: Boolean) {
        var guard = 0
        while (guard++ < 8) {
            val choices = ai.hand.withIndex().filter { it.value.card?.isTrainer() == true }
            if (choices.isEmpty()) break
            val best = choices.maxByOrNull { indexed ->
                trainerPlayScore(indexed.value.card!!, ai, player, firstAiTurn)
            } ?: break
            val card = best.value.card ?: break
            val score = trainerPlayScore(card, ai, player, firstAiTurn)
            if (score <= 0) break

            val isSupporter = isSupporterCard(card)
            val isStadium = isStadiumCard(card)
            val isTool = isToolCard(card)
            if (isSupporter && (firstAiTurn || ai.supporterUsed)) break
            if (isStadium && ai.stadiumUsed) break

            val parsed = EffectParser.parse(card.effect.orEmpty(), EffectSourceKind.TRAINER)
            val discardCost = parsed.operations.filterIsInstance<DiscardHandCards>().sumOf { it.count }
            if (discardCost > ai.hand.size - 1) {
                reasons += card.name + " wird zurückgehalten, weil die Zusatzkosten nicht bezahlt werden können."
                break
            }

            if (isTool) {
                val target = allPokemon(ai)
                    .filter { it.tool == null }
                    .maxByOrNull { boardPokemonScore(it, player.active) }
                if (target == null) break
                ai.hand.removeAt(best.index)
                target.tool = card
                reasons += "Die KI legt " + card.name + " an " + target.card.name + " an."
                logEffectCoverage(card.name, parsed)
                continue
            }

            if (isStadium) {
                if (stadiumCard?.card?.name.equals(card.name, ignoreCase = true)) break
                ai.hand.removeAt(best.index)
                val old = stadiumCard
                val oldOwner = stadiumOwner
                if (old != null && oldOwner != null) oldOwner.discard += old
                stadiumCard = best.value
                stadiumOwner = ai
                ai.stadiumUsed = true
                reasons += "Die KI spielt das Stadion " + card.name + "."
                logEffectCoverage(card.name, parsed)
                continue
            }

            ai.hand.removeAt(best.index)
            applyTrainerEffect(ai, player, card, true)
            ai.discard += best.value
            if (isSupporter) ai.supporterUsed = true
            reasons += "Trainerkarte " + card.name + " wird eingesetzt."
            if (isSupporter) break
        }
    }

    private fun aiAttachEnergy(reasons: MutableList<String>) {
        if (ai.energyAttached) return
        val energyChoices = ai.hand.withIndex().filter { it.value.isEnergy() }
        if (energyChoices.isEmpty()) return
        val pair = energyChoices.maxByOrNull { indexed ->
            allPokemon(ai).maxOfOrNull { p -> energyNeedScore(p, indexed.value) } ?: 0.0
        } ?: return
        val energyCard = pair.value
        val target = allPokemon(ai).maxByOrNull { energyNeedScore(it, energyCard) } ?: return
        ai.hand.removeAt(pair.index)
        attachEnergyCard(target, energyCard)
        ai.energyAttached = true
        reasons += energyCard.name + " geht an " + target.card.name +
            ", weil sie dort die beste neue Angriffsmöglichkeit freischaltet."
    }

    private fun aiMaybeRetreat(reasons: MutableList<String>) {
        val active = ai.active ?: return
        if (ai.retreated || ai.bench.isEmpty()) return
        if (active.status == FullStatus.ASLEEP || active.status == FullStatus.PARALYZED) return
        if (active.energy < effectiveRetreatCost(active)) return
        val enemy = player.active
        val current = boardPokemonScore(active, enemy)
        val bestIndex = ai.bench.indices.maxByOrNull { index ->
            boardPokemonScore(ai.bench[index], enemy)
        } ?: return
        val best = ai.bench[bestIndex]
        val danger = active.hp <= (active.card.hp ?: 100) / 3
        val switchThreshold = if (difficulty == AiDifficulty.EXPERT) 8.0 else 24.0
        if (boardPokemonScore(best, enemy) > current + switchThreshold || danger) {
            removeEnergyUnits(active, effectiveRetreatCost(active))
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
        val candidates = ready.map { indexed ->
            val parsed = EffectParser.parse(indexed.value.effect, EffectSourceKind.ATTACK)
            AiAttackCandidate(
                index = indexed.index,
                attack = indexed.value,
                expectedDamage = expectedDamage(attacker, defender, indexed.value),
                effectValue = EffectAiEvaluator.score(parsed),
                cost = indexed.value.cost.size
            )
        }

        val opponentBestResponse = defender.card.attacks
            .filter { canPayAttack(defender, it) }
            .maxOfOrNull { expectedDamage(defender, attacker, it) } ?: 0

        val knownPool = player.deck + player.discard
        val drawSearchCards = knownPool.count { gameCard ->
            val card = gameCard.card
            card?.isTrainer() == true && EffectParser.parse(
                card.effect.orEmpty(),
                EffectSourceKind.TRAINER
            ).operations.any {
                it is DrawCards || it is DrawUntilHandSize || it is SearchDeck
            }
        }
        val drawSearchDensity = if (knownPool.isEmpty()) 0.0 else drawSearchCards.toDouble() / knownPool.size

        val hiddenThreat = DeepAiPlanner.estimateHiddenThreat(
            opponentHandCount = player.hand.size,
            opponentDeckCount = player.deck.size,
            drawSearchDensity = drawSearchDensity,
            knownEnergyPressure = (player.active?.energy ?: 0).toDouble()
        )

        val opponentProfile = AiArchetypeDetector.detect(
            buildList {
                player.active?.card?.let(::add)
                addAll(player.bench.map { it.card })
                addAll(player.discard.mapNotNull { it.card })
            }
        )

        val tuned = aiTuning.copy(
            aggression = aiTuning.aggression * opponentProfile.aggression,
            control = aiTuning.control * opponentProfile.control,
            setup = aiTuning.setup * opponentProfile.setup
        )

        val context = AiRolloutContext(
            attackerHp = attacker.hp,
            attackerMaxHp = effectiveMaxHp(attacker),
            defenderHp = defender.hp,
            defenderMaxHp = effectiveMaxHp(defender),
            aiPrizesLeft = ai.prizes.size,
            opponentPrizesLeft = player.prizes.size,
            opponentBestResponseDamage = opponentBestResponse,
            opponentHiddenThreat = hiddenThreat,
            aiBenchStrength = ai.bench.sumOf { boardPokemonScore(it, defender) },
            opponentBenchStrength = player.bench.sumOf { boardPokemonScore(it, attacker) },
            tuning = tuned
        )

        val decision = DeepAiPlanner.chooseAttack(
            candidates = candidates,
            context = context,
            difficulty = difficulty,
            seed = turnNumber * 7919 + attacker.card.id.hashCode()
        )
        lastDeepPlanExplanation =
            decision.explanation + " Gegner-Archetyp: " + opponentProfile.name +
                ". Planwert: " + decision.score.toInt() + "."

        return ready.firstOrNull { it.index == decision.candidateIndex } ?: ready.first()
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

        val parsed = EffectParser.parse(attack.effect, EffectSourceKind.ATTACK)
        var totalHeads = 0
        val coinRules = parsed.operations.filterIsInstance<CoinRule>()
        for (rule in coinRules) {
            var heads = 0
            repeat(rule.flips) {
                if (random.nextBoolean()) heads += 1
            }
            totalHeads += heads
            log += actor + ": Münzwurf " + heads + "× Kopf bei " + rule.flips + " Wurf/Würfen."
            if (rule.cancelOnTails && heads < rule.flips) {
                log += actor + ": Der Kartentext lässt die Attacke wegen Zahl vollständig misslingen."
                logEffectCoverage(attack.name, parsed)
                return
            }
        }

        val damage = calculateAttackDamage(attacker, defender, attack, parsed, totalHeads, expected = false)
        defender.hp = max(0, defender.hp - damage)
        log += actor + ": " + attacker.card.name + " setzt " + attack.name + " ein und macht " + damage + " Schaden."
        if (damage > 0) {
            triggerReactiveAbilities(
                side = defendingSide,
                opponent = attackingSide,
                pokemon = defender,
                event = ReactiveEvent.DAMAGED,
                actor = if (defendingSide === player) "Du" else "KI",
                aiControlled = defendingSide === ai
            )
        }

        if (defender.preventAllDamageNext) {
            defender.preventAllDamageNext = false
            log += defender.card.name + ": Schutz vor dem nächsten Angriff wurde verbraucht."
        }
        if (defender.damageReductionNext > 0) {
            defender.damageReductionNext = 0
        }

        executeParsedEffect(
            side = attackingSide,
            opponent = defendingSide,
            sourcePokemon = attacker,
            parsed = parsed,
            actor = actor,
            aiControlled = attackingSide === ai
        )
        logEffectCoverage(attack.name, parsed)

        resolveBenchKnockOuts(
            defending = defendingSide,
            attacking = attackingSide,
            defenderName = if (defendingSide === ai) "KI" else "Du"
        )
    }

    private fun resolveKnockOut(
        defending: FullSideState,
        attacking: FullSideState,
        defenderName: String
    ) {
        val active = defending.active ?: return
        if (active.hp > 0) return
        val defeatedCard = active.card
        log += defeatedCard.name + " ist kampfunfähig."
        triggerReactiveAbilities(
            side = defending,
            opponent = attacking,
            pokemon = active,
            event = ReactiveEvent.KNOCKED_OUT,
            actor = defenderName,
            aiControlled = defending === ai
        )
        defending.discard += FullGameCard(-200000 - defending.discard.size, defeatedCard, false)
        active.tool?.let {
            defending.discard += FullGameCard(-210000 - defending.discard.size, it, false)
        }
        defending.active = null

        val attackerLabel = if (defenderName == "KI") "Du" else "KI"
        takePrizeCards(attacking, prizeValue(defeatedCard), attackerLabel)

        if (attacking.prizes.isEmpty()) {
            finish(attackerLabel)
            return
        }
        if (defending.bench.isEmpty()) {
            finish(attackerLabel)
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
        val defeatedCard = active.card
        log += defeatedCard.name + " ist durch eigenen Schaden kampfunfähig."
        side.discard += FullGameCard(-300000 - side.discard.size, defeatedCard, false)
        active.tool?.let {
            side.discard += FullGameCard(-310000 - side.discard.size, it, false)
        }
        side.active = null

        val opponentLabel = if (actor == "Du") "KI" else "Du"
        takePrizeCards(opponent, prizeValue(defeatedCard), opponentLabel)

        if (opponent.prizes.isEmpty()) {
            finish(opponentLabel)
            return
        }
        if (side.bench.isEmpty()) {
            finish(opponentLabel)
        } else {
            val bestIndex = side.bench.indices.maxByOrNull {
                boardPokemonScore(side.bench[it], opponent.active)
            } ?: 0
            side.active = side.bench.removeAt(bestIndex)
        }
    }

    private fun applyTrainerEffect(
        side: FullSideState,
        opponent: FullSideState,
        card: CardData,
        aiControlled: Boolean
    ) {
        val parsed = EffectParser.parse(card.effect.orEmpty(), EffectSourceKind.TRAINER)
        executeParsedEffect(
            side = side,
            opponent = opponent,
            sourcePokemon = side.active,
            parsed = parsed,
            actor = if (aiControlled) "KI" else "Du",
            aiControlled = aiControlled
        )
        logEffectCoverage(card.name, parsed)
    }

    private fun executeParsedEffect(
        side: FullSideState,
        opponent: FullSideState,
        sourcePokemon: FullPokemonState?,
        parsed: ParsedEffect,
        actor: String,
        aiControlled: Boolean
    ) {
        parsed.operations.forEach { op ->
            when (op) {
                is DrawCards -> draw(side, op.count)

                is DrawUntilHandSize -> {
                    val missing = max(0, op.size - side.hand.size)
                    if (missing > 0) draw(side, missing)
                }

                is HealDamage -> {
                    val targets = pokemonTargets(side, opponent, sourcePokemon, op.target, aiControlled)
                    targets.forEach { target ->
                        val before = target.hp
                        target.hp = min(effectiveMaxHp(target), target.hp + op.amount)
                        val healed = target.hp - before
                        if (healed > 0) {
                            log += actor + ": " + target.card.name + " heilt " + healed + " KP."
                        }
                    }
                }

                is DirectDamage -> {
                    val targets = pokemonTargets(side, opponent, sourcePokemon, op.target, aiControlled)
                    targets.forEach { target ->
                        target.hp = max(0, target.hp - op.amount)
                        log += actor + ": Karteneffekt macht " + op.amount + " Schaden an " + target.card.name + "."
                        if (op.amount > 0) {
                            val targetSide = ownerOf(target)
                            if (targetSide != null) {
                                triggerReactiveAbilities(
                                    side = targetSide,
                                    opponent = if (targetSide === player) ai else player,
                                    pokemon = target,
                                    event = ReactiveEvent.DAMAGED,
                                    actor = if (targetSide === player) "Du" else "KI",
                                    aiControlled = targetSide === ai
                                )
                            }
                        }
                    }
                    resolveBenchKnockOuts(opponent, side, if (side === player) "KI" else "Du")
                }

                is SelfDamage -> {
                    val target = sourcePokemon
                    if (target != null) {
                        target.hp = max(0, target.hp - op.amount)
                        log += actor + ": " + target.card.name + " nimmt " + op.amount + " Rückstoßschaden."
                    }
                }

                is ApplyCondition -> {
                    val target = pokemonTargets(side, opponent, sourcePokemon, op.target, aiControlled).firstOrNull()
                    if (target != null) {
                        if (hasSpecialConditionImmunity(target)) {
                            log += actor + ": " + target.card.name + " ist durch einen dauerhaften Effekt gegen Sonderzustände geschützt."
                            return@forEach
                        }
                        target.status = when (op.status) {
                            EffectStatus.POISONED -> FullStatus.POISONED
                            EffectStatus.BURNED -> FullStatus.BURNED
                            EffectStatus.ASLEEP -> FullStatus.ASLEEP
                            EffectStatus.PARALYZED -> FullStatus.PARALYZED
                            EffectStatus.CONFUSED -> FullStatus.CONFUSED
                        }
                        log += actor + ": " + target.card.name + " ist jetzt " + target.status.label + "."
                    }
                }

                is SearchDeck -> {
                    var moved = 0
                    repeat(op.count) {
                        val index = side.deck.indexOfFirst { matchesSearch(it, op.kind) }
                        if (index >= 0) {
                            val found = side.deck.removeAt(index)
                            if (op.destinationBench && found.isBasicPokemon() && side.bench.size < 5) {
                                val card = found.card
                                if (card != null) side.bench += FullPokemonState(card)
                            } else {
                                side.hand += found
                            }
                            moved += 1
                        }
                    }
                    if (moved > 0) {
                        side.deck.shuffle(random)
                        log += actor + ": " + moved + " Karte(n) wurden aus dem Deck gesucht."
                    }
                }

                is AttachEnergy -> {
                    repeat(op.count) {
                        val energyCard = if (op.fromDiscard) {
                            val index = side.discard.indexOfFirst { it.isEnergy() }
                            if (index >= 0) side.discard.removeAt(index) else null
                        } else {
                            val index = side.deck.indexOfFirst { it.isEnergy() }
                            if (index >= 0) side.deck.removeAt(index) else null
                        }
                        if (energyCard != null) {
                            val target = pokemonTargets(side, opponent, sourcePokemon, op.target, aiControlled).firstOrNull()
                            if (target != null) {
                                attachEnergyCard(target, energyCard)
                                log += actor + ": " + energyCard.name + " zusätzlich an " + target.card.name + "."
                            } else {
                                side.hand += energyCard
                            }
                        }
                    }
                }

                is DiscardEnergy -> {
                    val target = pokemonTargets(side, opponent, sourcePokemon, op.target, aiControlled).firstOrNull()
                    if (target != null) {
                        val targetSide = when (op.target) {
                            EffectTarget.OPPONENT_ACTIVE,
                            EffectTarget.OPPONENT_BENCH,
                            EffectTarget.OPPONENT_ANY,
                            EffectTarget.ALL_OPPONENT,
                            EffectTarget.ALL_OPPONENT_BENCH -> opponent
                            else -> side
                        }
                        val removed = removeEnergyUnits(target, op.count)
                        repeat(removed) {
                            targetSide.discard += FullGameCard(-500000 - targetSide.discard.size, null, true)
                        }
                        if (removed > 0) log += actor + ": " + removed + " Energie von " + target.card.name + " abgelegt."
                    }
                }

                is SwitchActive -> {
                    val switchSide = if (op.opponent) opponent else side
                    if (switchSide.active != null && switchSide.bench.isNotEmpty()) {
                        val index = if (op.opponent) {
                            switchSide.bench.indices.minByOrNull {
                                boardPokemonScore(switchSide.bench[it], side.active)
                            } ?: 0
                        } else {
                            switchSide.bench.indices.maxByOrNull {
                                boardPokemonScore(switchSide.bench[it], opponent.active)
                            } ?: 0
                        }
                        val old = switchSide.active!!
                        val replacement = switchSide.bench[index]
                        old.status = FullStatus.NONE
                        replacement.status = FullStatus.NONE
                        switchSide.bench[index] = old
                        switchSide.active = replacement
                        log += actor + ": Aktives Pokémon wird durch " + replacement.card.name + " ersetzt."
                    }
                }

                is ShuffleHandAndDraw -> {
                    side.deck += side.hand
                    side.hand.clear()
                    side.deck.shuffle(random)
                    draw(side, op.drawCount)
                }

                is DiscardHandAndDraw -> {
                    side.discard += side.hand
                    side.hand.clear()
                    draw(side, op.drawCount)
                }

                is DiscardHandCards -> {
                    repeat(min(op.count, side.hand.size)) {
                        val index = if (aiControlled) {
                            side.hand.indices.minByOrNull { handIndex ->
                                side.hand[handIndex].card?.let { boardCardScore(it) } ?: 1.0
                            } ?: 0
                        } else {
                            side.hand.lastIndex
                        }
                        if (index >= 0) side.discard += side.hand.removeAt(index)
                    }
                }

                is RecoverFromDiscard -> {
                    repeat(op.count) {
                        val index = side.discard.indexOfFirst { matchesSearch(it, op.kind) }
                        if (index >= 0) side.hand += side.discard.removeAt(index)
                    }
                }

                is ClearSpecialConditions -> {
                    pokemonTargets(side, opponent, sourcePokemon, op.target, aiControlled).forEach {
                        it.status = FullStatus.NONE
                    }
                }

                is DiscardTopDeck -> {
                    val targetSide = if (op.opponent) opponent else side
                    repeat(min(op.count, targetSide.deck.size)) {
                        targetSide.discard += targetSide.deck.removeAt(0)
                    }
                }

                is ReturnToHand -> {
                    returnPokemonToHand(side, opponent, sourcePokemon, op.target, aiControlled, actor)
                }

                is PreventDamage -> {
                    val target = sourcePokemon ?: side.active
                    if (target != null) {
                        if (op.allDamage) target.preventAllDamageNext = true
                        else target.damageReductionNext = max(target.damageReductionNext, op.amount ?: 0)
                    }
                }

                is LockAction -> {
                    val target = pokemonTargets(side, opponent, sourcePokemon, op.target, aiControlled).firstOrNull()
                    if (target != null) {
                        if (op.attack) target.attackLocked = true
                        if (op.retreat) target.retreatLocked = true
                    }
                }

                is ExtraPrize -> {
                    repeat(op.count) {
                        if (side.prizes.isNotEmpty()) {
                            side.hand += side.prizes.removeAt(0)
                        }
                    }
                    if (side.prizes.isEmpty()) finish(if (side === player) "Du" else "KI")
                }

                is SendToLostZone -> {
                    val targetSide = if (op.opponent) opponent else side
                    repeat(op.count) {
                        val moved = when {
                            op.fromDiscard && targetSide.discard.isNotEmpty() ->
                                targetSide.discard.removeAt(targetSide.discard.lastIndex)
                            op.fromDeckTop && targetSide.deck.isNotEmpty() ->
                                targetSide.deck.removeAt(0)
                            targetSide.hand.isNotEmpty() ->
                                targetSide.hand.removeAt(targetSide.hand.lastIndex)
                            targetSide.deck.isNotEmpty() ->
                                targetSide.deck.removeAt(0)
                            else -> null
                        }
                        if (moved != null) targetSide.lostZone += moved
                    }
                    log += actor + ": " + op.count + " Karte(n) wurden ins Nirgendwo / in die Lost Zone gelegt."
                }

                is ContinuousHpModifier,
                is ContinuousRetreatModifier,
                is ContinuousOutgoingDamageModifier,
                is ContinuousIncomingDamageModifier,
                is SpecialConditionImmunity,
                is DamageBonus,
                is CoinRule,
                is UnsupportedEffect -> Unit
            }
        }

        sourcePokemon?.let { pokemon ->
            if (pokemon.hp <= 0) {
                resolveSelfKnockOut(side, opponent, actor)
            }
        }
    }

    private fun pokemonTargets(
        side: FullSideState,
        opponent: FullSideState,
        sourcePokemon: FullPokemonState?,
        target: EffectTarget,
        aiControlled: Boolean
    ): List<FullPokemonState> {
        return when (target) {
            EffectTarget.SELF_ACTIVE -> listOfNotNull(sourcePokemon ?: side.active)
            EffectTarget.OWN_ACTIVE -> listOfNotNull(side.active)
            EffectTarget.OWN_BENCH -> listOfNotNull(
                if (aiControlled) side.bench.minByOrNull { it.hp.toDouble() / max(1, it.card.hp ?: 100) }
                else side.bench.firstOrNull()
            )
            EffectTarget.OWN_ANY -> listOfNotNull(
                allPokemon(side).minByOrNull { it.hp.toDouble() / max(1, it.card.hp ?: 100) }
            )
            EffectTarget.OPPONENT_ACTIVE -> listOfNotNull(opponent.active)
            EffectTarget.OPPONENT_BENCH -> listOfNotNull(
                opponent.bench.minByOrNull { boardPokemonScore(it, side.active) }
            )
            EffectTarget.OPPONENT_ANY -> listOfNotNull(
                allPokemon(opponent).minByOrNull { boardPokemonScore(it, side.active) }
            )
            EffectTarget.ALL_OWN -> allPokemon(side)
            EffectTarget.ALL_OWN_BENCH -> side.bench.toList()
            EffectTarget.ALL_OPPONENT -> allPokemon(opponent)
            EffectTarget.ALL_OPPONENT_BENCH -> opponent.bench.toList()
        }
    }

    private fun matchesSearch(gameCard: FullGameCard, kind: SearchKind): Boolean {
        val card = gameCard.card
        return when (kind) {
            SearchKind.ANY -> true
            SearchKind.POKEMON -> card?.isPokemon() == true
            SearchKind.BASIC_POKEMON -> gameCard.isBasicPokemon()
            SearchKind.EVOLUTION_POKEMON -> card?.isPokemon() == true && card.isBasicPokemon().not()
            SearchKind.ENERGY -> gameCard.isEnergy()
            SearchKind.BASIC_ENERGY -> gameCard.virtualEnergy || card?.isBasicEnergy() == true
            SearchKind.TRAINER -> card?.isTrainer() == true
            SearchKind.ITEM -> card?.trainerType.orEmpty().lowercase(Locale.ROOT).contains("item")
            SearchKind.SUPPORTER -> {
                val type = card?.trainerType.orEmpty().lowercase(Locale.ROOT)
                type.contains("support") || type.contains("unterstüt")
            }
            SearchKind.STADIUM -> card?.trainerType.orEmpty().lowercase(Locale.ROOT).contains("stad")
        }
    }

    private fun returnPokemonToHand(
        side: FullSideState,
        opponent: FullSideState,
        sourcePokemon: FullPokemonState?,
        target: EffectTarget,
        aiControlled: Boolean,
        actor: String
    ) {
        val targetSide = when (target) {
            EffectTarget.OPPONENT_ACTIVE,
            EffectTarget.OPPONENT_BENCH,
            EffectTarget.OPPONENT_ANY,
            EffectTarget.ALL_OPPONENT,
            EffectTarget.ALL_OPPONENT_BENCH -> opponent
            else -> side
        }

        val pokemon = pokemonTargets(side, opponent, sourcePokemon, target, aiControlled).firstOrNull() ?: return
        if (targetSide.active === pokemon) {
            if (targetSide.bench.isEmpty()) return
            targetSide.hand += FullGameCard(-600000 - targetSide.hand.size, pokemon.card, false)
            targetSide.active = targetSide.bench.removeAt(0)
        } else {
            val index = targetSide.bench.indexOfFirst { it === pokemon }
            if (index >= 0) {
                val removed = targetSide.bench.removeAt(index)
                targetSide.hand += FullGameCard(-600000 - targetSide.hand.size, removed.card, false)
            }
        }
        log += actor + ": " + pokemon.card.name + " wird auf die Hand zurückgenommen."
    }

    private fun resolveBenchKnockOuts(
        defending: FullSideState,
        attacking: FullSideState,
        defenderName: String
    ) {
        val knocked = defending.bench.filter { it.hp <= 0 }.toList()
        val attackerLabel = if (defenderName == "KI") "Du" else "KI"
        knocked.forEach { pokemon ->
            defending.bench.remove(pokemon)
            defending.discard += FullGameCard(-700000 - defending.discard.size, pokemon.card, false)
            pokemon.tool?.let {
                defending.discard += FullGameCard(-710000 - defending.discard.size, it, false)
            }
            log += pokemon.card.name + " auf der Bank ist kampfunfähig."
            takePrizeCards(attacking, prizeValue(pokemon.card), attackerLabel)
            if (attacking.prizes.isEmpty()) {
                finish(attackerLabel)
                return
            }
        }
    }

    private fun takePrizeCards(side: FullSideState, count: Int, actor: String) {
        var taken = 0
        repeat(count.coerceAtLeast(1)) {
            if (side.prizes.isNotEmpty()) {
                side.hand += side.prizes.removeAt(0)
                taken += 1
            }
        }
        if (taken > 0) {
            log += actor + " nimmt " + taken + " Preiskarte(n). Noch " + side.prizes.size + "."
        }
    }

    private fun prizeValue(card: CardData): Int {
        val name = card.name.lowercase(Locale.ROOT)
        return when {
            ("tag team" in name || "tag-team" in name) && "gx" in name -> 3
            "vmax" in name -> 3
            "vstar" in name -> 2
            Regex("""(^|\s)ex($|\s|-)""", RegexOption.IGNORE_CASE).containsMatchIn(card.name) -> 2
            Regex("""(^|\s)v($|\s|-)""", RegexOption.IGNORE_CASE).containsMatchIn(card.name) -> 2
            "gx" in name -> 2
            else -> 1
        }
    }

    private fun logEffectCoverage(sourceName: String, parsed: ParsedEffect) {
        if (parsed.sourceText.isBlank()) return
        if (parsed.fullySupported) {
            log += sourceName + ": Effekt vollständig automatisch verarbeitet (" + parsed.coveragePercent + "%)."
        } else {
            log += sourceName + ": Effektabdeckung " + parsed.coveragePercent + "%."
            parsed.unsupportedParts.take(2).forEach {
                log += "Noch manuell zu beachten: " + it
            }
        }
    }

    private fun isSupporterCard(card: CardData): Boolean {
        val type = card.trainerType.orEmpty().lowercase(Locale.ROOT)
        return type.contains("support") || type.contains("unterstüt")
    }

    private fun isStadiumCard(card: CardData): Boolean {
        val type = card.trainerType.orEmpty().lowercase(Locale.ROOT)
        return type.contains("stad")
    }

    private fun isToolCard(card: CardData): Boolean {
        val type = card.trainerType.orEmpty().lowercase(Locale.ROOT)
        return type.contains("tool") || type.contains("ausrüstung") || type.contains("werkzeug")
    }

    private fun trainerPlayScore(
        card: CardData,
        side: FullSideState,
        opponent: FullSideState,
        firstTurn: Boolean
    ): Double {
        if (isSupporterCard(card) && (firstTurn || side.supporterUsed)) return -1000.0
        if (isStadiumCard(card) && side.stadiumUsed) return -1000.0
        if (isStadiumCard(card) && stadiumCard?.card?.name.equals(card.name, ignoreCase = true)) return -1000.0
        if (isToolCard(card) && allPokemon(side).none { it.tool == null }) return -1000.0
        val parsed = EffectParser.parse(card.effect.orEmpty(), EffectSourceKind.TRAINER)
        var score = 4.0 + EffectAiEvaluator.score(parsed)
        if (isToolCard(card)) score += 16.0
        if (isStadiumCard(card)) score += 10.0
        if (side.hand.size <= 4 && parsed.operations.any { it is DrawCards || it is DrawUntilHandSize }) {
            score += 18.0
        }
        if (side.hand.none { it.isEnergy() } && parsed.operations.any { it is SearchDeck && (it.kind == SearchKind.ENERGY || it.kind == SearchKind.BASIC_ENERGY) }) {
            score += 24.0
        }
        if (parsed.operations.any { it is HealDamage }) {
            val damaged = side.active?.let { (it.card.hp ?: 100) - it.hp } ?: 0
            if (damaged <= 0) score -= 18.0
        }
        if (parsed.operations.any { it is SwitchActive } && side.bench.isNotEmpty()) {
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
        if (attacker.attackLocked) {
            log += actor + ": " + attacker.card.name + " kann in diesem Zug wegen eines Karteneffekts nicht angreifen."
            attacker.attackLocked = false
            return false
        }
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
        val parsed = EffectParser.parse(attack.effect, EffectSourceKind.ATTACK)
        return calculateAttackDamage(attacker, defender, attack, parsed, 0, expected = true)
    }

    private fun calculateAttackDamage(
        attacker: FullPokemonState,
        defender: FullPokemonState,
        attack: CardAttack,
        parsed: ParsedEffect,
        actualHeads: Int,
        expected: Boolean
    ): Int {
        var raw = estimatedAttackDamage(attack, attacker.energy).toDouble()
        val coinRules = parsed.operations.filterIsInstance<CoinRule>()
        val hasCoinBonus = coinRules.any { it.bonusPerHeads > 0 }

        parsed.operations.filterIsInstance<DamageBonus>().forEach { bonus ->
            when {
                bonus.perEnergy -> raw += bonus.amount * attacker.energy
                bonus.perCounter -> {
                    val damageTaken = max(0, (attacker.card.hp ?: 100) - attacker.hp)
                    raw += bonus.amount * (damageTaken / 10)
                }
                bonus.onHeads && !hasCoinBonus -> {
                    raw += if (expected) {
                        bonus.amount * 0.5
                    } else {
                        if (actualHeads > 0) bonus.amount.toDouble() else 0.0
                    }
                }
                !bonus.onHeads -> raw += bonus.amount
            }
        }

        coinRules.forEach { rule ->
            if (rule.bonusPerHeads > 0) {
                raw += if (expected) {
                    rule.flips * rule.bonusPerHeads * 0.5
                } else {
                    actualHeads * rule.bonusPerHeads.toDouble()
                }
            }
            if (rule.cancelOnTails && expected) {
                val successProbability = Math.pow(0.5, rule.flips.toDouble())
                raw *= successProbability
            }
        }

        raw += persistentOps(attacker).filterIsInstance<ContinuousOutgoingDamageModifier>().sumOf { it.amount }
        raw += persistentOps(defender).filterIsInstance<ContinuousIncomingDamageModifier>().sumOf { it.amount }
        var damage = raw.toInt().coerceAtLeast(0)
        val attackerTypes = attacker.card.types.map { it.lowercase(Locale.ROOT) }
        if (defender.card.weaknesses.any { weak -> attackerTypes.contains(weak.lowercase(Locale.ROOT)) }) {
            damage *= 2
        }
        if (defender.card.resistances.any { resist -> attackerTypes.contains(resist.lowercase(Locale.ROOT)) }) {
            damage = max(0, damage - 30)
        }
        if (defender.preventAllDamageNext) damage = 0
        if (defender.damageReductionNext > 0) damage = max(0, damage - defender.damageReductionNext)
        return damage
    }

    private fun setupSide(side: FullSideState, label: String): Int {
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
            return attempts.coerceAtLeast(1) - 1
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
        val mulligans = (attempts - 1).coerceAtLeast(0)
        log += label + " startet mit " + side.active!!.card.name + ", " + side.bench.size +
            " Pokémon auf der Bank und " + side.prizes.size + " Preiskarten. Mulligans: " + mulligans + "."
        return mulligans
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

    private fun ownerOf(pokemon: FullPokemonState): FullSideState? {
        if (player.active === pokemon || player.bench.any { it === pokemon }) return player
        if (ai.active === pokemon || ai.bench.any { it === pokemon }) return ai
        return null
    }

    private fun persistentOps(pokemon: FullPokemonState): List<EffectOp> {
        val ops = mutableListOf<EffectOp>()
        pokemon.card.abilities
            .filter { classifyAbilityTiming(it.effect) == AbilityTiming.PASSIVE }
            .forEach { ability ->
                ops += EffectParser.parsePersistent(ability.effect, EffectSourceKind.ABILITY).operations
            }
        pokemon.tool?.let { tool ->
            ops += EffectParser.parsePersistent(tool.effect.orEmpty(), EffectSourceKind.TRAINER).operations
        }
        pokemon.attachedSpecialEnergy.forEach { energy ->
            ops += EffectParser.parsePersistent(energy.effect.orEmpty(), EffectSourceKind.TRAINER).operations
        }
        val stadium = stadiumCard?.card
        val owner = ownerOf(pokemon)
        if (stadium != null && stadiumAppliesTo(stadium, owner)) {
            ops += EffectParser.parsePersistent(stadium.effect.orEmpty(), EffectSourceKind.TRAINER).operations
        }
        return ops
    }

    private fun stadiumAppliesTo(stadium: CardData, side: FullSideState?): Boolean {
        val text = stadium.effect.orEmpty().lowercase(Locale.ROOT)
        val ownerOnly = text.contains("deine pok") || text.contains("your pok")
        return !ownerOnly || side === stadiumOwner
    }

    private fun effectiveMaxHp(pokemon: FullPokemonState): Int {
        val bonus = persistentOps(pokemon)
            .filterIsInstance<ContinuousHpModifier>()
            .sumOf { it.amount }
        return ((pokemon.card.hp ?: 100) + bonus).coerceAtLeast(10)
    }

    private fun effectiveRetreatCost(pokemon: FullPokemonState): Int {
        val modifier = persistentOps(pokemon)
            .filterIsInstance<ContinuousRetreatModifier>()
            .sumOf { it.amount }
        return (pokemon.card.retreatCost + modifier).coerceAtLeast(0)
    }

    private fun hasSpecialConditionImmunity(pokemon: FullPokemonState): Boolean =
        persistentOps(pokemon).any { it is SpecialConditionImmunity }

    private fun normalizeEnergyType(raw: String): String {
        val s = raw.trim().lowercase(Locale.ROOT)
        return when {
            s.contains("fire") || s.contains("feuer") -> "Fire"
            s.contains("water") || s.contains("wasser") -> "Water"
            s.contains("grass") || s.contains("pflanze") -> "Grass"
            s.contains("lightning") || s.contains("elektro") -> "Lightning"
            s.contains("psychic") || s.contains("psycho") -> "Psychic"
            s.contains("fighting") || s.contains("kampf") -> "Fighting"
            s.contains("dark") || s.contains("finstern") -> "Darkness"
            s.contains("metal") || s.contains("stahl") -> "Metal"
            s.contains("fairy") || s.contains("fee") -> "Fairy"
            s.contains("dragon") || s.contains("drache") -> "Dragon"
            s.contains("colorless") || s.contains("farblos") -> "Colorless"
            s.contains("any") || s.contains("beliebig") || s.contains("jeden energietyp") -> "Any"
            else -> raw.ifBlank { "Colorless" }
        }
    }

    private fun energyUnits(card: FullGameCard): List<String> {
        if (card.virtualEnergy) {
            return listOf(normalizeEnergyType(card.virtualEnergyType ?: "Colorless"))
        }
        val data = card.card ?: return listOf("Colorless")
        val effect = data.effect.orEmpty().lowercase(Locale.ROOT)
        val anyType = effect.contains("jeden energietyp") ||
            effect.contains("beliebigen energietyp") ||
            effect.contains("every type of energy") ||
            effect.contains("any type of energy")
        val baseType = if (anyType) {
            "Any"
        } else {
            normalizeEnergyType(
                data.energyType
                    ?: data.types.firstOrNull()
                    ?: data.name
            )
        }
        val providesTwo = effect.contains("2 energie") ||
            effect.contains("2 energy") ||
            effect.contains("provides 2") ||
            effect.contains("liefert 2")
        return if (providesTwo) listOf(baseType, baseType) else listOf(baseType)
    }

    private fun attachEnergyCard(target: FullPokemonState, gameCard: FullGameCard) {
        val units = energyUnits(gameCard)
        target.energy += units.size
        target.energyTypes += units
        val data = gameCard.card
        if (data != null && data.isEnergy() && !data.isBasicEnergy()) {
            target.attachedSpecialEnergy += data
        }
    }

    private fun removeEnergyUnits(target: FullPokemonState, count: Int): Int {
        val actual = min(count, target.energy)
        repeat(actual) {
            target.energy = max(0, target.energy - 1)
            if (target.energyTypes.isNotEmpty()) target.energyTypes.removeAt(target.energyTypes.lastIndex)
        }
        while (target.attachedSpecialEnergy.size > target.energy) {
            target.attachedSpecialEnergy.removeAt(target.attachedSpecialEnergy.lastIndex)
        }
        return actual
    }

    private fun canPayAttack(pokemon: FullPokemonState, attack: CardAttack): Boolean {
        if (attack.cost.isEmpty()) return true
        val pool = pokemon.energyTypes.toMutableList().ifEmpty {
            MutableList(pokemon.energy) { "Colorless" }
        }
        val normalizedCost = attack.cost.map(::normalizeEnergyType)

        normalizedCost.filter { it != "Colorless" }.forEach { need ->
            val exact = pool.indexOfFirst { it == need }
            val any = pool.indexOfFirst { it == "Any" }
            val index = if (exact >= 0) exact else any
            if (index < 0) return false
            pool.removeAt(index)
        }

        val colorless = normalizedCost.count { it == "Colorless" }
        return pool.size >= colorless
    }

    private fun canPayAttackWithExtra(
        pokemon: FullPokemonState,
        attack: CardAttack,
        extra: FullGameCard
    ): Boolean {
        val originalEnergy = pokemon.energy
        val originalTypes = pokemon.energyTypes.toList()
        val originalSpecial = pokemon.attachedSpecialEnergy.toList()
        attachEnergyCard(pokemon, extra)
        val result = canPayAttack(pokemon, attack)
        pokemon.energy = originalEnergy
        pokemon.energyTypes.clear()
        pokemon.energyTypes += originalTypes
        pokemon.attachedSpecialEnergy.clear()
        pokemon.attachedSpecialEnergy += originalSpecial
        return result
    }

    private fun formatAttackCost(attack: CardAttack): String {
        if (attack.cost.isEmpty()) return "keine Energie"
        return attack.cost.joinToString(" + ") { normalizeEnergyType(it) }
    }

    private fun targetPokemon(side: FullSideState, targetIndex: Int): FullPokemonState? {
        return if (targetIndex == 0) side.active else side.bench.getOrNull(targetIndex - 1)
    }

    private fun allPokemon(side: FullSideState): List<FullPokemonState> = buildList {
        side.active?.let(::add)
        addAll(side.bench)
    }

    private fun energyNeedScore(
        pokemon: FullPokemonState,
        candidateEnergy: FullGameCard? = null
    ): Double {
        val attacks = pokemon.card.attacks
        if (attacks.isEmpty()) return 0.0
        val before = attacks.maxOfOrNull { attack ->
            if (canPayAttack(pokemon, attack)) {
                estimatedAttackDamage(attack, pokemon.energy).toDouble()
            } else 0.0
        } ?: 0.0

        val after = if (candidateEnergy != null) {
            attacks.maxOfOrNull { attack ->
                if (canPayAttackWithExtra(pokemon, attack, candidateEnergy)) {
                    estimatedAttackDamage(attack, pokemon.energy + energyUnits(candidateEnergy).size).toDouble()
                } else 0.0
            } ?: 0.0
        } else {
            before
        }

        val unlockBonus = if (after > before) 65.0 else 0.0
        val missingColorPenalty = attacks.minOfOrNull { attack ->
            attack.cost.count { cost ->
                val need = normalizeEnergyType(cost)
                need != "Colorless" &&
                    pokemon.energyTypes.none { it == need || it == "Any" }
            }
        } ?: 0
        return (after - before) + unlockBonus - missingColorPenalty * 8 +
            boardCardScore(pokemon.card) * 0.08
    }

    private fun boardPokemonScore(pokemon: FullPokemonState, enemy: FullPokemonState?): Double {
        val healthRatio = pokemon.hp.toDouble() / max(1, pokemon.card.hp ?: 100)
        val bestReady = pokemon.card.attacks
            .filter { canPayAttack(pokemon, it) }
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
        active = active?.let { it.view(effectiveMaxHp(it), effectiveRetreatCost(it)) },
        bench = bench.map { it.view(effectiveMaxHp(it), effectiveRetreatCost(it)) },
        hand = if (hideHand) List(hand.size) { FullGameCard(-1 - it, null, false) } else hand.toList(),
        deckCount = deck.size,
        discardCount = discard.size,
        lostZoneCount = lostZone.size,
        prizesLeft = prizes.size
    )
}
