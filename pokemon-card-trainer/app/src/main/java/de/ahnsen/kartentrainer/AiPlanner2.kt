package de.ahnsen.kartentrainer

import android.content.Context
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class AiTuning(
    val aggression: Double = 1.0,
    val survival: Double = 1.0,
    val setup: Double = 1.0,
    val control: Double = 1.0,
    val exploration: Double = 0.08,
    val gamesLearned: Int = 0
)

class LocalAiLearningStore(context: Context) {
    private val prefs = context.getSharedPreferences("tcg_ai_learning", Context.MODE_PRIVATE)

    fun load(): AiTuning = AiTuning(
        aggression = prefs.getFloat("aggression", 1f).toDouble().coerceIn(0.7, 1.4),
        survival = prefs.getFloat("survival", 1f).toDouble().coerceIn(0.7, 1.4),
        setup = prefs.getFloat("setup", 1f).toDouble().coerceIn(0.7, 1.4),
        control = prefs.getFloat("control", 1f).toDouble().coerceIn(0.7, 1.4),
        exploration = prefs.getFloat("exploration", 0.08f).toDouble().coerceIn(0.01, 0.25),
        gamesLearned = prefs.getInt("games", 0)
    )

    fun recordResult(
        aiWon: Boolean,
        turns: Int,
        aiPrizesLeft: Int,
        opponentPrizesLeft: Int
    ): AiTuning {
        val old = load()
        val reward = if (aiWon) 1.0 else -1.0
        val closeGame = kotlin.math.abs(aiPrizesLeft - opponentPrizesLeft) <= 1
        val rate = if (closeGame) 0.012 else 0.022

        val aggressionDelta = when {
            !aiWon && opponentPrizesLeft <= 2 -> 1.0
            aiWon && turns <= 8 -> 0.35
            else -> -0.2
        } * rate

        val survivalDelta = when {
            !aiWon && aiPrizesLeft >= 3 -> 1.0
            aiWon && aiPrizesLeft <= 2 -> 0.35
            else -> -0.1
        } * rate

        val setupDelta = when {
            !aiWon && turns <= 7 -> 0.8
            aiWon && turns >= 10 -> 0.3
            else -> 0.0
        } * rate

        val controlDelta = reward * if (turns >= 10) 0.012 else 0.004

        val next = old.copy(
            aggression = (old.aggression + aggressionDelta).coerceIn(0.7, 1.4),
            survival = (old.survival + survivalDelta).coerceIn(0.7, 1.4),
            setup = (old.setup + setupDelta).coerceIn(0.7, 1.4),
            control = (old.control + controlDelta).coerceIn(0.7, 1.4),
            exploration = (0.12 / (1.0 + old.gamesLearned / 35.0)).coerceIn(0.02, 0.12),
            gamesLearned = old.gamesLearned + 1
        )
        prefs.edit()
            .putFloat("aggression", next.aggression.toFloat())
            .putFloat("survival", next.survival.toFloat())
            .putFloat("setup", next.setup.toFloat())
            .putFloat("control", next.control.toFloat())
            .putFloat("exploration", next.exploration.toFloat())
            .putInt("games", next.gamesLearned)
            .apply()
        return next
    }

    fun reset() {
        prefs.edit().clear().apply()
    }
}

data class AiAttackCandidate(
    val index: Int,
    val attack: CardAttack,
    val expectedDamage: Int,
    val effectValue: Double,
    val cost: Int
)

data class AiRolloutContext(
    val attackerHp: Int,
    val attackerMaxHp: Int,
    val defenderHp: Int,
    val defenderMaxHp: Int,
    val aiPrizesLeft: Int,
    val opponentPrizesLeft: Int,
    val opponentBestResponseDamage: Int,
    val opponentHiddenThreat: Double,
    val aiBenchStrength: Double,
    val opponentBenchStrength: Double,
    val tuning: AiTuning
)

data class AiAttackDecision(
    val candidateIndex: Int,
    val score: Double,
    val rolloutCount: Int,
    val explanation: String
)

object DeepAiPlanner {
    fun chooseAttack(
        candidates: List<AiAttackCandidate>,
        context: AiRolloutContext,
        difficulty: AiDifficulty,
        seed: Int
    ): AiAttackDecision {
        require(candidates.isNotEmpty())
        val rollouts = when (difficulty) {
            AiDifficulty.EASY -> 1
            AiDifficulty.NORMAL -> 12
            AiDifficulty.HARD -> 96
            AiDifficulty.EXPERT -> 240
        }

        if (difficulty == AiDifficulty.EASY) {
            val picked = candidates[Random(seed).nextInt(candidates.size)]
            return AiAttackDecision(
                candidateIndex = picked.index,
                score = picked.expectedDamage.toDouble(),
                rolloutCount = 1,
                explanation = "Einfach-Modus: Die KI wählt bewusst nicht immer die mathematisch beste Aktion."
            )
        }

        val random = Random(seed)
        var best: AiAttackCandidate = candidates.first()
        var bestScore = Double.NEGATIVE_INFINITY

        for (candidate in candidates) {
            var total = 0.0
            repeat(rollouts) {
                total += rollout(candidate, context, random, difficulty)
            }
            val average = total / rollouts
            if (average > bestScore) {
                bestScore = average
                best = candidate
            }
        }

        val ko = best.expectedDamage >= context.defenderHp
        val explanation = buildString {
            append("Monte-Carlo-Planung mit ")
            append(rollouts)
            append(" Simulationen je Attacke. ")
            if (ko) append("Der gewählte Zug hat eine unmittelbare K.-o.-Linie. ")
            append("Bewertet wurden Gegenschaden, Preisrennen, Bankstärke, Effektwert und unbekannte Handgefahr.")
        }

        return AiAttackDecision(
            candidateIndex = best.index,
            score = bestScore,
            rolloutCount = rollouts,
            explanation = explanation
        )
    }

    private fun rollout(
        candidate: AiAttackCandidate,
        context: AiRolloutContext,
        random: Random,
        difficulty: AiDifficulty
    ): Double {
        val tuning = context.tuning
        val variance = when (difficulty) {
            AiDifficulty.NORMAL -> random.nextDouble(0.88, 1.12)
            AiDifficulty.HARD -> random.nextDouble(0.94, 1.06)
            else -> random.nextDouble(0.97, 1.03)
        }

        val dealt = (candidate.expectedDamage * variance).toInt().coerceAtLeast(0)
        val defenderRemaining = max(0, context.defenderHp - dealt)
        val ko = defenderRemaining <= 0

        var score = dealt * 1.15 * tuning.aggression
        score += candidate.effectValue * tuning.control
        score -= candidate.cost * 2.4

        if (ko) {
            val prizeUrgency = if (context.aiPrizesLeft <= 2) 1.35 else 1.0
            score += 460.0 * prizeUrgency * tuning.aggression
        }

        val hiddenThreatSample = context.opponentHiddenThreat *
            random.nextDouble(0.55, 1.35)
        val response = if (ko) {
            context.opponentBestResponseDamage * 0.45
        } else {
            context.opponentBestResponseDamage.toDouble()
        } + hiddenThreatSample

        val survivalRatio = context.attackerHp.toDouble() / max(1, context.attackerMaxHp)
        val likelyKoBack = response >= context.attackerHp
        score -= response * 0.32 * tuning.survival
        if (likelyKoBack) score -= 165.0 * tuning.survival
        score += survivalRatio * 34.0 * tuning.survival

        val benchDelta = context.aiBenchStrength - context.opponentBenchStrength
        score += benchDelta * 0.11 * tuning.setup

        val prizeRace = context.opponentPrizesLeft - context.aiPrizesLeft
        score += prizeRace * 18.0 * tuning.aggression

        if (random.nextDouble() < tuning.exploration) {
            score += random.nextDouble(-22.0, 22.0)
        }

        return score
    }

    fun estimateHiddenThreat(
        opponentHandCount: Int,
        opponentDeckCount: Int,
        drawSearchDensity: Double,
        knownEnergyPressure: Double
    ): Double {
        val hand = min(12, opponentHandCount) * 2.8
        val search = drawSearchDensity.coerceIn(0.0, 1.0) * 42.0
        val energy = knownEnergyPressure.coerceIn(0.0, 3.0) * 9.0
        val deckUncertainty = if (opponentDeckCount > 0) min(18.0, opponentDeckCount / 3.0) else 0.0
        return hand + search + energy + deckUncertainty
    }
}

data class AiArchetypeProfile(
    val name: String,
    val aggression: Double,
    val control: Double,
    val setup: Double
)

object AiArchetypeDetector {
    fun detect(cards: List<CardData>): AiArchetypeProfile {
        if (cards.isEmpty()) return AiArchetypeProfile("Unbekannt", 1.0, 1.0, 1.0)
        val pokemon = cards.filter { it.isPokemon() }
        val trainers = cards.filter { it.isTrainer() }
        val fast = pokemon.count { p ->
            p.isBasicPokemon() && p.attacks.any { it.cost.size <= 1 && it.baseDamage >= 20 }
        }
        val control = trainers.count { t ->
            val parsed = EffectParser.parse(t.effect.orEmpty(), EffectSourceKind.TRAINER)
            parsed.operations.any {
                it is DiscardEnergy ||
                    it is LockAction ||
                    it is SwitchActive ||
                    it is DiscardTopDeck ||
                    it is SendToLostZone
            }
        }
        val evolutions = pokemon.count { !it.isBasicPokemon() }

        return when {
            control >= 5 -> AiArchetypeProfile("Kontrolle", 0.9, 1.25, 1.05)
            fast >= max(4, pokemon.size / 2) -> AiArchetypeProfile("Tempo", 1.25, 0.9, 0.95)
            evolutions >= max(4, pokemon.size / 3) -> AiArchetypeProfile("Entwicklung", 0.95, 1.0, 1.25)
            else -> AiArchetypeProfile("Ausgeglichen", 1.0, 1.0, 1.0)
        }
    }
}
