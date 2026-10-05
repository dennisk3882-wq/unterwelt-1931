package de.ahnsen.kartentrainer

import kotlin.math.max
import kotlin.math.min

data class DeckCardChoice(
    val card: CardData,
    val variant: String,
    val quantity: Int
)

data class DeckAnalysis(
    val totalCards: Int,
    val pokemonCount: Int,
    val trainerCount: Int,
    val energyCount: Int,
    val basicPokemonCount: Int,
    val evolutionPairs: Int,
    val typeFocus: List<String>,
    val strength: Int,
    val consistency: Int,
    val legality: Int,
    val energyFit: Int,
    val synergy: Int,
    val archetype: String,
    val issues: List<String>,
    val suggestions: List<String>
)

object AdvancedDeckEngine {
    fun autoBuild(
        entries: List<CollectionEntry>,
        standardOnly: Boolean
    ): List<DeckCardChoice> {
        val eligible = entries
            .filter { !standardOnly || it.card.isStandardPlayable() }
            .flatMap { entry ->
                List(entry.quantity.coerceAtMost(if (entry.card.isBasicEnergy()) 20 else 4)) {
                    entry.card to entry.variant
                }
            }

        if (eligible.isEmpty()) return emptyList()

        val pokemon = eligible.filter { it.first.isPokemon() }
        val trainers = eligible.filter { it.first.isTrainer() }
        val energies = eligible.filter { it.first.isEnergy() }

        val typeWeights = pokemon
            .flatMap { it.first.types }
            .groupingBy { it }
            .eachCount()
        val focusTypes = typeWeights.entries.sortedByDescending { it.value }.take(2).map { it.key }

        fun pokemonScore(card: CardData): Double {
            val typeBonus = if (card.types.any { it in focusTypes }) 28.0 else 0.0
            val hp = card.hp ?: 100
            val bestAttack = card.attacks.maxOfOrNull {
                val cost = max(1, it.cost.size)
                estimatedAttackDamage(it, cost).toDouble() / cost + it.baseDamage * 0.45
            } ?: 0.0
            val ability = card.abilities.sumOf {
                EffectAiEvaluator.score(EffectParser.parse(it.effect, EffectSourceKind.ABILITY))
            } * 0.12
            val evolutionBonus = if (!card.evolveFrom.isNullOrBlank()) 18.0 else 12.0
            return hp * 0.18 + bestAttack + typeBonus + ability + evolutionBonus
        }

        fun trainerScore(card: CardData): Double {
            val parsed = EffectParser.parse(card.effect.orEmpty(), EffectSourceKind.TRAINER)
            var score = EffectAiEvaluator.score(parsed)
            val t = card.trainerType.orEmpty().lowercase()
            if ("support" in t || "unterstüt" in t) score += 12
            if ("item" in t) score += 10
            if ("stad" in t) score += 5
            return score
        }

        val chosen = mutableListOf<Pair<CardData, String>>()
        val byName = mutableMapOf<String, Int>()

        fun add(card: CardData, variant: String): Boolean {
            val limit = if (card.isBasicEnergy()) 60 else 4
            val used = byName[card.name] ?: 0
            if (used >= limit || chosen.size >= 60) return false
            chosen += card to variant
            byName[card.name] = used + 1
            return true
        }

        val basics = pokemon
            .filter { it.first.isBasicPokemon() }
            .sortedByDescending { pokemonScore(it.first) }
        basics.take(10).forEach { add(it.first, it.second) }

        val evolutions = pokemon
            .filterNot { it.first.isBasicPokemon() }
            .sortedByDescending { pokemonScore(it.first) }
        evolutions.forEach { pair ->
            if (chosen.count { it.first.isPokemon() } >= 20) return@forEach
            val parent = pair.first.evolveFrom
            if (parent.isNullOrBlank() || chosen.any { it.first.name.equals(parent, true) }) {
                add(pair.first, pair.second)
            }
        }

        pokemon
            .sortedByDescending { pokemonScore(it.first) }
            .forEach { pair ->
                if (chosen.count { it.first.isPokemon() } < 20) add(pair.first, pair.second)
            }

        trainers
            .sortedByDescending { trainerScore(it.first) }
            .forEach { pair ->
                if (chosen.count { it.first.isTrainer() } < 28) add(pair.first, pair.second)
            }

        energies
            .sortedByDescending { if (it.first.isBasicEnergy()) 2 else 1 }
            .forEach { pair ->
                if (chosen.count { it.first.isEnergy() } < 12) add(pair.first, pair.second)
            }

        val remaining = eligible.sortedByDescending { pair ->
            when {
                pair.first.isTrainer() -> trainerScore(pair.first)
                pair.first.isPokemon() -> pokemonScore(pair.first)
                else -> 10.0
            }
        }
        remaining.forEach { if (chosen.size < 60) add(it.first, it.second) }

        return chosen
            .groupBy { Triple(it.first.id, it.second, it.first.name) }
            .map { (_, list) ->
                DeckCardChoice(list.first().first, list.first().second, list.size)
            }
            .sortedWith(
                compareBy<DeckCardChoice> {
                    when {
                        it.card.isPokemon() -> 0
                        it.card.isTrainer() -> 1
                        else -> 2
                    }
                }.thenBy { it.card.name }
            )
    }

    fun analyze(
        cards: List<DeckCardChoice>,
        standardOnly: Boolean
    ): DeckAnalysis {
        val total = cards.sumOf { it.quantity }
        val pokemon = cards.filter { it.card.isPokemon() }
        val trainers = cards.filter { it.card.isTrainer() }
        val energies = cards.filter { it.card.isEnergy() }
        val pCount = pokemon.sumOf { it.quantity }
        val tCount = trainers.sumOf { it.quantity }
        val eCount = energies.sumOf { it.quantity }
        val basicCount = pokemon.filter { it.card.isBasicPokemon() }.sumOf { it.quantity }

        val allPokemon = pokemon.flatMap { choice -> List(choice.quantity) { choice.card } }
        val typeCounts = allPokemon.flatMap { it.types }.groupingBy { it }.eachCount()
        val focusTypes = typeCounts.entries.sortedByDescending { it.value }.take(2).map { it.key }

        val evolutionPairs = pokemon.sumOf { choice ->
            if (choice.card.evolveFrom.isNullOrBlank()) 0
            else min(
                choice.quantity,
                pokemon.firstOrNull { it.card.name.equals(choice.card.evolveFrom, true) }?.quantity ?: 0
            )
        }

        val nameViolations = cards
            .groupBy { it.card.name }
            .filter { (_, same) ->
                val card = same.first().card
                !card.isBasicEnergy() && same.sumOf { it.quantity } > 4
            }
            .keys

        val legalityIssues = cards.filter { standardOnly && !it.card.isStandardPlayable() }
        val legality = when {
            nameViolations.isNotEmpty() || legalityIssues.isNotEmpty() -> 30
            total != 60 -> 60
            basicCount <= 0 -> 35
            else -> 100
        }

        val energyDemand = allPokemon.sumOf { p ->
            p.attacks.minOfOrNull { max(1, it.cost.size) } ?: 0
        }
        val averageDemand = if (allPokemon.isEmpty()) 0.0 else energyDemand.toDouble() / allPokemon.size
        val desiredEnergy = when {
            averageDemand >= 3.0 -> 14
            averageDemand >= 2.0 -> 12
            else -> 10
        }
        val energyFit = (100 - kotlin.math.abs(eCount - desiredEnergy) * 8).coerceIn(0, 100)

        val drawSearchScore = trainers.sumOf { choice ->
            val parsed = EffectParser.parse(choice.card.effect.orEmpty(), EffectSourceKind.TRAINER)
            val useful = parsed.operations.any {
                it is DrawCards ||
                    it is DrawUntilHandSize ||
                    it is SearchDeck ||
                    it is RecoverFromDiscard
            }
            if (useful) choice.quantity else 0
        }
        val consistency = (
            35 +
                min(30, basicCount * 3) +
                min(25, drawSearchScore * 3) +
                min(10, evolutionPairs * 2)
            ).coerceIn(0, 100)

        val focusCount = allPokemon.count { p -> p.types.any { it in focusTypes } }
        val focusRatio = if (allPokemon.isEmpty()) 0 else focusCount * 100 / allPokemon.size
        val synergy = (
            focusRatio * 0.55 +
                min(25, evolutionPairs * 5) +
                min(20, trainers.sumOf { it.quantity } / 2)
            ).toInt().coerceIn(0, 100)

        val archetype = detectArchetype(cards, focusTypes)
        val strength = (
            legality * 0.22 +
                consistency * 0.27 +
                energyFit * 0.18 +
                synergy * 0.25 +
                if (total == 60) 8 else 0
            ).toInt().coerceIn(0, 100)

        val issues = buildList {
            if (total != 60) add("Deck hat $total statt exakt 60 Karten.")
            if (basicCount == 0) add("Kein Basis-Pokémon vorhanden.")
            if (nameViolations.isNotEmpty()) add("Viererlimit überschritten: " + nameViolations.joinToString())
            if (legalityIssues.isNotEmpty()) add("${legalityIssues.size} Karte(n) sind im gewählten Standardformat nicht legal.")
            if (eCount < 8) add("Sehr wenig Energie: nur $eCount Karten.")
            if (eCount > 18) add("Sehr hoher Energieanteil: $eCount Karten.")
            val orphan = pokemon.filter {
                !it.card.isBasicPokemon() &&
                    !it.card.evolveFrom.isNullOrBlank() &&
                    pokemon.none { parent -> parent.card.name.equals(it.card.evolveFrom, true) }
            }
            if (orphan.isNotEmpty()) add("Entwicklungen ohne passende Vorstufe: " + orphan.joinToString { it.card.name })
        }

        val suggestions = buildList {
            if (total < 60) add("Noch ${60 - total} Karte(n) ergänzen.")
            if (basicCount < 6) add("Für stabilere Starthände mehr Basis-Pokémon einplanen.")
            if (drawSearchScore < 6) add("Mehr Zieh-/Such-Trainer erhöhen die Konstanz deutlich.")
            if (focusRatio < 65 && focusTypes.isNotEmpty()) add("Auf weniger Pokémon-Typen fokussieren: ${focusTypes.joinToString()}.")
            if (evolutionPairs < pokemon.count { !it.card.isBasicPokemon() }) add("Entwicklungslinien vollständiger machen.")
            if (energyFit < 75) add("Energieanzahl näher an den tatsächlichen Angriffskosten ausrichten.")
            if (issues.isEmpty()) add("Deck ist formal sauber; als Nächstes Matchups und Karten-Synergien testen.")
        }

        return DeckAnalysis(
            totalCards = total,
            pokemonCount = pCount,
            trainerCount = tCount,
            energyCount = eCount,
            basicPokemonCount = basicCount,
            evolutionPairs = evolutionPairs,
            typeFocus = focusTypes,
            strength = strength,
            consistency = consistency,
            legality = legality,
            energyFit = energyFit,
            synergy = synergy,
            archetype = archetype,
            issues = issues,
            suggestions = suggestions
        )
    }

    fun missingCardSuggestions(
        collection: List<CollectionEntry>,
        deck: List<DeckCardChoice>,
        standardOnly: Boolean
    ): List<String> {
        val analysis = analyze(deck, standardOnly)
        val result = mutableListOf<String>()
        if (analysis.basicPokemonCount < 6) result += "Mehr spielbare Basis-Pokémon aus deiner Sammlung hinzufügen."
        if (analysis.energyCount < 10) result += "Basis-Energien auf etwa 10–14 erhöhen."
        if (analysis.consistency < 75) result += "Zieh-/Suchkarten priorisieren, z. B. Trainer mit 'ziehe' oder 'durchsuche dein Deck'."

        val deckNames = deck.map { it.card.name }.toSet()
        val evolutions = deck.filter { !it.card.evolveFrom.isNullOrBlank() }
        evolutions.forEach { evo ->
            if (deckNames.none { it.equals(evo.card.evolveFrom, true) }) {
                result += "Vorentwicklung für ${evo.card.name} ergänzen: ${evo.card.evolveFrom}."
            }
        }

        val candidateNames = collection
            .filter { !standardOnly || it.card.isStandardPlayable() }
            .filter { it.card.isTrainer() }
            .sortedByDescending {
                EffectAiEvaluator.score(EffectParser.parse(it.card.effect.orEmpty(), EffectSourceKind.TRAINER))
            }
            .map { it.card.name }
            .distinct()
            .filterNot { it in deckNames }
            .take(5)

        if (candidateNames.isNotEmpty()) {
            result += "Starke noch ungenutzte Trainer aus deiner Sammlung prüfen: " + candidateNames.joinToString()
        }
        return result.distinct().take(10)
    }

    private fun detectArchetype(cards: List<DeckCardChoice>, focusTypes: List<String>): String {
        val trainers = cards.filter { it.card.isTrainer() }
        val pokemon = cards.filter { it.card.isPokemon() }
        val draw = trainers.sumOf {
            val parsed = EffectParser.parse(it.card.effect.orEmpty(), EffectSourceKind.TRAINER)
            if (parsed.operations.any { op -> op is DrawCards || op is DrawUntilHandSize }) it.quantity else 0
        }
        val control = trainers.sumOf {
            val parsed = EffectParser.parse(it.card.effect.orEmpty(), EffectSourceKind.TRAINER)
            if (parsed.operations.any { op ->
                    op is DiscardEnergy || op is SwitchActive || op is LockAction || op is DiscardTopDeck
                }) it.quantity else 0
        }
        val fastBasics = pokemon.filter { it.card.isBasicPokemon() }.sumOf { it.quantity }
        return when {
            control >= 6 -> "Kontrolle"
            fastBasics >= 12 && draw >= 6 -> "Tempo"
            pokemon.count { !it.card.isBasicPokemon() } >= 6 -> "Entwicklung"
            focusTypes.size == 1 -> focusTypes.first() + "-Fokus"
            else -> "Ausgeglichen"
        }
    }
}
