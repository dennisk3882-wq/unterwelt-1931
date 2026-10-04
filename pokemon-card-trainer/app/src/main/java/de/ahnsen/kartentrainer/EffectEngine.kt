package de.ahnsen.kartentrainer

import java.util.Locale

enum class EffectSourceKind { ATTACK, TRAINER, ABILITY }

enum class AbilityTiming(val label: String) {
    ACTIVATED("Im eigenen Zug aktivierbar"),
    ON_PLAY("Beim Ausspielen/Entwickeln"),
    PASSIVE("Dauerhafte Fähigkeit"),
    REACTIVE("Reaktive Fähigkeit"),
    UNKNOWN("Sonderfähigkeit")
}

fun classifyAbilityTiming(effect: String): AbilityTiming {
    val s = effect.lowercase(Locale.ROOT)
    return when {
        listOf(
            "einmal während deines zuges",
            "once during your turn",
            "du kannst diese fähigkeit",
            "you may use this ability"
        ).any { s.contains(it) } -> AbilityTiming.ACTIVATED

        listOf(
            "wenn du dieses pokémon aus deiner hand",
            "wenn du dieses pokemon aus deiner hand",
            "when you play this pokémon from your hand",
            "when you play this pokemon from your hand",
            "wenn sich dieses pokémon entwickelt",
            "when this pokémon evolves"
        ).any { s.contains(it) } -> AbilityTiming.ON_PLAY

        listOf(
            "wenn dieses pokémon schaden",
            "when this pokémon is damaged",
            "when your opponent",
            "wenn dein gegner",
            "wenn dieses pokémon kampfunfähig",
            "when this pokémon is knocked out"
        ).any { s.contains(it) } -> AbilityTiming.REACTIVE

        listOf(
            "solange dieses pokémon",
            "as long as this pokémon",
            "dieses pokémon kann nicht",
            "this pokémon can't",
            "this pokemon can't",
            "alle deine pokémon",
            "all of your pokémon"
        ).any { s.contains(it) } -> AbilityTiming.PASSIVE

        else -> AbilityTiming.UNKNOWN
    }
}
enum class EffectTarget {
    SELF_ACTIVE, OWN_ACTIVE, OWN_BENCH, OWN_ANY,
    OPPONENT_ACTIVE, OPPONENT_BENCH, OPPONENT_ANY,
    ALL_OWN, ALL_OWN_BENCH, ALL_OPPONENT, ALL_OPPONENT_BENCH
}
enum class SearchKind {
    ANY, POKEMON, BASIC_POKEMON, EVOLUTION_POKEMON,
    ENERGY, BASIC_ENERGY, TRAINER, ITEM, SUPPORTER, STADIUM
}
enum class EffectStatus { POISONED, BURNED, ASLEEP, PARALYZED, CONFUSED }

sealed interface EffectOp { val description: String }

data class DrawCards(val count: Int) : EffectOp {
    override val description = "Ziehe " + count + " Karte(n)"
}
data class HealDamage(val amount: Int, val target: EffectTarget) : EffectOp {
    override val description = "Heile " + amount + " Schaden bei " + targetLabel(target)
}
data class DirectDamage(
    val amount: Int,
    val target: EffectTarget,
    val ignoreWeaknessResistance: Boolean = true
) : EffectOp {
    override val description = amount.toString() + " Schaden an " + targetLabel(target)
}
data class SelfDamage(val amount: Int) : EffectOp {
    override val description = amount.toString() + " Rückstoßschaden an diesem Pokémon"
}
data class DrawUntilHandSize(val size: Int) : EffectOp {
    override val description = "Ziehe Karten, bis du " + size + " Karten auf der Hand hast"
}
data class ApplyCondition(val status: EffectStatus, val target: EffectTarget) : EffectOp {
    override val description = statusLabel(status) + " auf " + targetLabel(target)
}
data class SearchDeck(
    val kind: SearchKind,
    val count: Int,
    val destinationBench: Boolean = false
) : EffectOp {
    override val description =
        "Suche " + count + " " + searchLabel(kind) + " aus dem Deck" +
            if (destinationBench) " und lege sie auf die Bank" else " auf die Hand"
}
data class AttachEnergy(
    val count: Int,
    val fromDiscard: Boolean,
    val target: EffectTarget
) : EffectOp {
    override val description =
        "Lege " + count + " Energie aus " +
            (if (fromDiscard) "dem Ablagestapel" else "Hand/Deck") +
            " an " + targetLabel(target)
}
data class DiscardEnergy(val count: Int, val target: EffectTarget) : EffectOp {
    override val description = "Lege " + count + " Energie von " + targetLabel(target) + " ab"
}
data class SwitchActive(val opponent: Boolean) : EffectOp {
    override val description =
        if (opponent) "Wechsle das aktive Pokémon des Gegners" else "Wechsle dein aktives Pokémon"
}
data class ShuffleHandAndDraw(val drawCount: Int) : EffectOp {
    override val description = "Mische die Hand ins Deck und ziehe " + drawCount + " Karte(n)"
}
data class DiscardHandAndDraw(val drawCount: Int) : EffectOp {
    override val description = "Lege die Hand ab und ziehe " + drawCount + " Karte(n)"
}
data class DiscardHandCards(val count: Int) : EffectOp {
    override val description = "Lege " + count + " Karte(n) aus deiner Hand ab"
}
data class RecoverFromDiscard(val kind: SearchKind, val count: Int) : EffectOp {
    override val description = "Nimm " + count + " " + searchLabel(kind) + " aus dem Ablagestapel auf die Hand"
}
data class ClearSpecialConditions(val target: EffectTarget) : EffectOp {
    override val description = "Entferne alle Sonderzustände von " + targetLabel(target)
}
data class DiscardTopDeck(val count: Int, val opponent: Boolean) : EffectOp {
    override val description =
        "Lege die obersten " + count + " Karte(n) " +
            (if (opponent) "des gegnerischen" else "deines") + " Decks ab"
}
data class ReturnToHand(val target: EffectTarget) : EffectOp {
    override val description = "Nimm " + targetLabel(target) + " auf die Hand zurück"
}
data class DamageBonus(
    val amount: Int,
    val onHeads: Boolean = false,
    val perEnergy: Boolean = false,
    val perCounter: Boolean = false
) : EffectOp {
    override val description = when {
        onHeads -> "+" + amount + " Schaden bei Kopf"
        perEnergy -> "+" + amount + " Schaden pro Energie"
        perCounter -> "+" + amount + " Schaden pro Schadensmarke"
        else -> "+" + amount + " Schaden"
    }
}
data class CoinRule(
    val flips: Int,
    val cancelOnTails: Boolean = false,
    val bonusPerHeads: Int = 0
) : EffectOp {
    override val description = when {
        cancelOnTails -> "Wirf " + flips + " Münze(n); bei Zahl misslingt der Effekt"
        bonusPerHeads > 0 -> "Wirf " + flips + " Münze(n); +" + bonusPerHeads + " Schaden pro Kopf"
        else -> "Wirf " + flips + " Münze(n)"
    }
}
data class PreventDamage(val amount: Int?, val allDamage: Boolean) : EffectOp {
    override val description =
        if (allDamage) "Verhindere allen Schaden im nächsten gegnerischen Zug"
        else "Reduziere den nächsten Schaden um " + (amount ?: 0)
}
data class LockAction(
    val attack: Boolean = false,
    val retreat: Boolean = false,
    val target: EffectTarget = EffectTarget.SELF_ACTIVE
) : EffectOp {
    override val description = when {
        attack -> "Kann im nächsten Zug nicht angreifen"
        retreat -> "Kann im nächsten Zug nicht zurückziehen"
        else -> "Aktion gesperrt"
    }
}
data class ExtraPrize(val count: Int) : EffectOp {
    override val description = "Nimm " + count + " zusätzliche Preiskarte(n)"
}
data class UnsupportedEffect(val raw: String) : EffectOp {
    override val description = "Noch nicht vollautomatisch: " + raw
}

data class ParsedEffect(
    val sourceText: String,
    val sourceKind: EffectSourceKind,
    val operations: List<EffectOp>,
    val unsupportedParts: List<String>,
    val coveragePercent: Int
) {
    val fullySupported: Boolean get() = unsupportedParts.isEmpty()
    val summary: String
        get() = if (operations.isEmpty()) "Kein automatischer Effekt erkannt."
        else operations.joinToString(" · ") { it.description }
}

object EffectParser {
    private val numberRegex = Regex("""\d+""")

    fun parse(text: String, sourceKind: EffectSourceKind): ParsedEffect {
        if (text.isBlank()) return ParsedEffect(text, sourceKind, emptyList(), emptyList(), 100)

        val normalized = text.replace("\n", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val rawSentences = normalized
            .split(Regex("""(?<=[.!?])\s+|;\s*"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val sentences = mutableListOf<String>()
        rawSentences.forEach { sentence ->
            val lower = sentence.lowercase(Locale.ROOT)
            val isCoinContinuation = lower.startsWith("bei kopf") ||
                lower.startsWith("bei zahl") ||
                lower.startsWith("if heads") ||
                lower.startsWith("if tails") ||
                lower.startsWith("for each heads")
            if (isCoinContinuation && sentences.isNotEmpty()) {
                val previous = sentences.last().lowercase(Locale.ROOT)
                if (previous.contains("münze") || previous.contains("coin")) {
                    sentences[sentences.lastIndex] = sentences.last() + " " + sentence
                } else {
                    sentences += sentence
                }
            } else {
                sentences += sentence
            }
        }

        val operations = mutableListOf<EffectOp>()
        val unsupported = mutableListOf<String>()

        sentences.forEach { sentence ->
            val parsed = parseSentence(sentence)
            if (parsed.isEmpty()) {
                unsupported += sentence
            } else if (hasUnresolvedCondition(sentence, parsed)) {
                unsupported += sentence
            } else {
                operations += parsed
            }
        }

        val total = operations.size + unsupported.size
        val coverage = if (total == 0) 100 else (operations.size * 100 / total).coerceIn(0, 100)

        return ParsedEffect(text, sourceKind, operations, unsupported, coverage)
    }

    private fun parseSentence(sentence: String): List<EffectOp> {
        val s = sentence.lowercase(Locale.ROOT)
        val ops = mutableListOf<EffectOp>()
        parseCoin(s)?.let(ops::add)
        parseShuffleDraw(s)?.let(ops::add)
        parseDiscardHandDraw(s)?.let(ops::add)
        parseDiscardHandCards(s)?.let(ops::add)
        parseRecoverFromDiscard(s)?.let(ops::add)
        parseClearConditions(s)?.let(ops::add)
        parseDrawUntil(s)?.let(ops::add)
        parseDraw(s)?.let(ops::add)
        parseSearch(s)?.let(ops::add)
        parseHeal(s)?.let(ops::add)
        parseStatus(s)?.let(ops::add)
        parseDirectDamage(s)?.let(ops::add)
        parseSelfDamage(s)?.let(ops::add)
        parseAttachEnergy(s)?.let(ops::add)
        parseDiscardEnergy(s)?.let(ops::add)
        parseSwitch(s)?.let(ops::add)
        parseDiscardTopDeck(s)?.let(ops::add)
        parseReturnToHand(s)?.let(ops::add)
        parsePrevent(s)?.let(ops::add)
        parseLock(s)?.let(ops::add)
        parseBonusDamage(s)?.let(ops::add)
        parsePrize(s)?.let(ops::add)
        return ops.distinctBy { it::class.simpleName + "|" + it.description }
    }

    private fun parseCoin(s: String): EffectOp? {
        if (!containsAny(s, "münze", "coin")) return null
        if (!containsAny(s, "wirf", "flip")) return null
        val flips = Regex("""(?:wirf|flip)\D{0,8}(\d+)""")
            .find(s)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val cancel = containsAny(
            s,
            "bei zahl hat diese attacke keine auswirkungen",
            "bei zahl passiert nichts",
            "if tails, this attack does nothing",
            "if tails, this attack does no damage",
            "if tails, this effect does nothing"
        )
        val bonus = Regex("""(?:bei kopf|für jeden kopf|for each heads?|if heads).*?(\d+)\s+(?:mehr )?(?:schadenspunkte|schaden|damage)""")
            .find(s)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return CoinRule(flips.coerceAtLeast(1), cancel, bonus)
    }

    private fun parseDiscardHandCards(s: String): EffectOp? {
        if (!containsAny(s, "deiner hand", "your hand")) return null
        if (!containsAny(s, "ablegen", "wirf", "discard")) return null
        if (containsAny(s, "lege deine hand ab", "discard your hand")) return null
        val regexes = listOf(
            Regex("""(?:lege|wirf).*?(\d+)\s+karten?.*hand"""),
            Regex("""discard\s+(\d+)\s+cards?.*hand""")
        )
        val count = regexes.firstNotNullOfOrNull {
            it.find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()
        } ?: return null
        return DiscardHandCards(count.coerceIn(1, 10))
    }

    private fun parseRecoverFromDiscard(s: String): EffectOp? {
        if (!containsAny(s, "ablagestapel", "discard pile")) return null
        if (!containsAny(s, "auf deine hand", "into your hand", "to your hand")) return null
        if (!containsAny(s, "nimm", "put", "return")) return null
        val count = Regex("""(?:bis zu|up to)?\s*(\d+)""")
            .find(s)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val kind = when {
            containsAny(s, "basis-energ", "basic energy") -> SearchKind.BASIC_ENERGY
            containsAny(s, "energie", "energy") -> SearchKind.ENERGY
            containsAny(s, "basis-pok", "basic pok") -> SearchKind.BASIC_POKEMON
            containsAny(s, "pokemon", "pokémon") -> SearchKind.POKEMON
            containsAny(s, "trainer") -> SearchKind.TRAINER
            else -> SearchKind.ANY
        }
        return RecoverFromDiscard(kind, count.coerceIn(1, 6))
    }

    private fun parseClearConditions(s: String): EffectOp? {
        if (!containsAny(
                s,
                "entferne alle sonderzustände",
                "entferne alle speziellen zustände",
                "remove all special conditions"
            )
        ) return null
        val target = if (containsAny(s, "diesem pok", "this pok")) {
            EffectTarget.SELF_ACTIVE
        } else EffectTarget.OWN_ANY
        return ClearSpecialConditions(target)
    }

    private fun parseDrawUntil(s: String): EffectOp? {
        val regexes = listOf(
            Regex("""(?:bis du|bis ihr)\s+(\d+)\s+karten?.*hand"""),
            Regex("""until you have\s+(\d+)\s+cards?.*hand""")
        )
        val size = regexes.firstNotNullOfOrNull {
            it.find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()
        } ?: return null
        return DrawUntilHandSize(size.coerceIn(1, 20))
    }

    private fun parseDraw(s: String): EffectOp? {
        if (containsAny(s, "mische deine hand", "shuffle your hand", "lege deine hand ab", "discard your hand")) return null
        val count = findDrawCount(s) ?: return null
        return DrawCards(count.coerceIn(1, 20))
    }

    private fun parseShuffleDraw(s: String): EffectOp? {
        if (!containsAny(s, "mische deine hand", "shuffle your hand")) return null
        if (!containsAny(s, "ins deck", "into your deck")) return null
        val count = findDrawCount(s) ?: return null
        return ShuffleHandAndDraw(count.coerceIn(1, 20))
    }

    private fun parseDiscardHandDraw(s: String): EffectOp? {
        if (!containsAny(s, "lege deine hand ab", "wirf deine hand ab", "discard your hand")) return null
        val count = findDrawCount(s) ?: return null
        return DiscardHandAndDraw(count.coerceIn(1, 20))
    }

    private fun parseSearch(s: String): EffectOp? {
        if (!containsAny(s, "durchsuche", "suche dein deck", "search your deck")) return null
        val count = Regex("""(?:bis zu|up to)?\s*(\d+)""")
            .find(s)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val kind = when {
            containsAny(s, "basis-energ", "basic energy") -> SearchKind.BASIC_ENERGY
            containsAny(s, "energiekarte", "energiekarten", "energy card", "energy cards") -> SearchKind.ENERGY
            containsAny(s, "basis-pok", "basic pok") -> SearchKind.BASIC_POKEMON
            containsAny(s, "entwicklungs", "evolution pok") -> SearchKind.EVOLUTION_POKEMON
            containsAny(s, "pokemon", "pokémon") -> SearchKind.POKEMON
            containsAny(s, "unterstützer", "supporter") -> SearchKind.SUPPORTER
            containsAny(s, "stadion", "stadium") -> SearchKind.STADIUM
            containsAny(s, "item") -> SearchKind.ITEM
            containsAny(s, "trainer") -> SearchKind.TRAINER
            else -> SearchKind.ANY
        }
        return SearchDeck(kind, count.coerceIn(1, 6), containsAny(s, "auf deine bank", "onto your bench"))
    }

    private fun parseHeal(s: String): EffectOp? {
        if (!containsAny(s, "heile", "heal")) return null
        val amount = numberRegex.find(s)?.value?.toIntOrNull() ?: return null
        val target = when {
            containsAny(s, "jedem deiner pok", "all of your pok", "each of your pok") -> EffectTarget.ALL_OWN
            containsAny(s, "bank-pok", "benched pok") -> EffectTarget.OWN_BENCH
            containsAny(s, "diesem pok", "this pok") -> EffectTarget.SELF_ACTIVE
            else -> EffectTarget.OWN_ANY
        }
        return HealDamage(amount, target)
    }

    private fun parseStatus(s: String): EffectOp? {
        val status = when {
            containsAny(s, "vergiftet", "poisoned") -> EffectStatus.POISONED
            containsAny(s, "verbrannt", "burned") -> EffectStatus.BURNED
            containsAny(s, "schläft", "asleep") -> EffectStatus.ASLEEP
            containsAny(s, "paralys", "paralyzed") -> EffectStatus.PARALYZED
            containsAny(s, "verwirrt", "confused") -> EffectStatus.CONFUSED
            else -> return null
        }
        val target = if (containsAny(s, "dieses pok", "this pok")) EffectTarget.SELF_ACTIVE else EffectTarget.OPPONENT_ACTIVE
        return ApplyCondition(status, target)
    }

    private fun parseDirectDamage(s: String): EffectOp? {
        if (!containsAny(s, "schadenspunkte", "schaden", "damage")) return null
        if (!containsAny(s, "bank", "bench")) return null
        val amount = Regex("""(\d+)\s+(?:schadenspunkte|schaden|damage)""")
            .find(s)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        val target = if (containsAny(s, "jedem", "each")) EffectTarget.ALL_OPPONENT_BENCH else EffectTarget.OPPONENT_BENCH
        return DirectDamage(amount, target, true)
    }

    private fun parseSelfDamage(s: String): EffectOp? {
        val selfMention = containsAny(
            s,
            "diesem pokémon", "diesem pokemon", "sich selbst",
            "this pokémon", "this pokemon", "itself"
        )
        if (!selfMention || !containsAny(s, "schaden", "damage")) return null
        val amount = Regex("""(\d+)\s+(?:schadenspunkte|schaden|damage)""")
            .find(s)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        return SelfDamage(amount)
    }

    private fun parseAttachEnergy(s: String): EffectOp? {
        if (!containsAny(s, "energie", "energy")) return null
        if (!containsAny(s, "anlegen", "lege", "attach")) return null
        val count = Regex("""(?:bis zu|up to)?\s*(\d+)""")
            .find(s)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val target = when {
            containsAny(s, "bank", "bench") -> EffectTarget.OWN_BENCH
            containsAny(s, "diesem pok", "this pok") -> EffectTarget.SELF_ACTIVE
            else -> EffectTarget.OWN_ANY
        }
        return AttachEnergy(count.coerceIn(1, 5), containsAny(s, "ablagestapel", "discard pile"), target)
    }

    private fun parseDiscardEnergy(s: String): EffectOp? {
        if (!containsAny(s, "energie", "energy")) return null
        if (!containsAny(s, "ablegen", "wirf", "discard")) return null
        if (containsAny(s, "deiner hand", "your hand")) return null
        val count = Regex("""(?:bis zu|up to)?\s*(\d+)""")
            .find(s)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val target = if (containsAny(s, "gegner", "opponent")) EffectTarget.OPPONENT_ACTIVE else EffectTarget.SELF_ACTIVE
        return DiscardEnergy(count.coerceIn(1, 5), target)
    }

    private fun parseSwitch(s: String): EffectOp? {
        if (!containsAny(s, "tausche", "wechsle", "switch")) return null
        if (!containsAny(s, "aktives", "active")) return null
        return SwitchActive(containsAny(s, "gegner", "opponent"))
    }

    private fun parseDiscardTopDeck(s: String): EffectOp? {
        if (!containsAny(s, "obersten", "top")) return null
        if (!containsAny(s, "deck")) return null
        if (!containsAny(s, "ablege", "discard")) return null
        val count = numberRegex.find(s)?.value?.toIntOrNull() ?: 1
        return DiscardTopDeck(count.coerceIn(1, 20), containsAny(s, "gegner", "opponent"))
    }

    private fun parseReturnToHand(s: String): EffectOp? {
        if (!containsAny(s, "auf die hand", "into your hand", "to your hand")) return null
        if (!containsAny(s, "nimm", "return", "put")) return null
        if (containsAny(s, "aus dem deck", "from your deck")) return null
        val target = when {
            containsAny(s, "gegner", "opponent") -> EffectTarget.OPPONENT_ACTIVE
            containsAny(s, "bank", "bench") -> EffectTarget.OWN_BENCH
            else -> EffectTarget.SELF_ACTIVE
        }
        return ReturnToHand(target)
    }

    private fun parsePrevent(s: String): EffectOp? {
        if (containsAny(s, "verhindere allen schaden", "prevent all damage")) return PreventDamage(null, true)
        if (containsAny(s, "weniger schaden", "less damage")) {
            val amount = numberRegex.find(s)?.value?.toIntOrNull() ?: return null
            return PreventDamage(amount, false)
        }
        return null
    }

    private fun parseLock(s: String): EffectOp? {
        if (containsAny(
                s,
                "kann im nächsten zug nicht angreifen",
                "can't attack during your next turn",
                "cannot attack during your next turn"
            )
        ) {
            val target = if (containsAny(s, "verteidigende", "defending pok", "gegner")) {
                EffectTarget.OPPONENT_ACTIVE
            } else EffectTarget.SELF_ACTIVE
            return LockAction(attack = true, target = target)
        }

        if (containsAny(
                s,
                "kann sich nicht zurückziehen",
                "kann nicht zurückziehen",
                "can't retreat",
                "cannot retreat"
            )
        ) {
            val target = if (containsAny(s, "verteidigende", "defending pok", "gegner")) {
                EffectTarget.OPPONENT_ACTIVE
            } else EffectTarget.SELF_ACTIVE
            return LockAction(retreat = true, target = target)
        }

        return null
    }

    private fun parseBonusDamage(s: String): EffectOp? {
        val amount = Regex("""(\d+)\s+(?:mehr )?(?:schaden|damage)""")
            .find(s)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        return when {
            containsAny(s, "für jede energie", "for each energy") -> DamageBonus(amount, perEnergy = true)
            containsAny(s, "für jede schadensmarke", "für jeden schadenszähler", "for each damage counter") ->
                DamageBonus(amount, perCounter = true)
            containsAny(s, "bei kopf", "if heads") -> DamageBonus(amount, onHeads = true)
            containsAny(s, "mehr schaden", "more damage", "additional damage") -> DamageBonus(amount)
            else -> null
        }
    }

    private fun parsePrize(s: String): EffectOp? {
        if (!containsAny(s, "preiskarte", "prize card")) return null
        if (!containsAny(s, "zusätzlich", "extra", "additional")) return null
        val count = numberRegex.find(s)?.value?.toIntOrNull() ?: 1
        return ExtraPrize(count.coerceIn(1, 3))
    }

    private fun findDrawCount(s: String): Int? {
        val regexes = listOf(
            Regex("""(?:ziehe|zieh)\s+(\d+)\s+karten?"""),
            Regex("""draw\s+(\d+)\s+cards?""")
        )
        return regexes.firstNotNullOfOrNull {
            it.find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
    }

    private fun hasUnresolvedCondition(sentence: String, parsed: List<EffectOp>): Boolean {
        val s = sentence.lowercase(Locale.ROOT)
        val conditional = listOf(
            " wenn ", "wenn ", " falls ", "falls ", " solange ", "solange ",
            " if ", "if ", " as long as ", "for each ", "für jede ", "für jeden "
        ).any { s.contains(it) }
        if (!conditional) return false

        val supportedConditional = parsed.any {
            it is CoinRule ||
                (it is DamageBonus && (it.onHeads || it.perEnergy || it.perCounter))
        }
        return !supportedConditional
    }

    private fun containsAny(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it) }
}

object EffectAiEvaluator {
    fun score(parsed: ParsedEffect): Double {
        var score = 0.0
        parsed.operations.forEach { op ->
            score += when (op) {
                is DrawCards -> op.count * 11.0
                is DrawUntilHandSize -> op.size * 5.5
                is HealDamage -> op.amount * 0.35
                is DirectDamage -> op.amount * 0.9
                is SelfDamage -> op.amount * -0.45
                is ApplyCondition -> when (op.status) {
                    EffectStatus.PARALYZED -> 36.0
                    EffectStatus.ASLEEP -> 27.0
                    EffectStatus.CONFUSED -> 24.0
                    EffectStatus.BURNED -> 22.0
                    EffectStatus.POISONED -> 20.0
                }
                is SearchDeck -> op.count * when (op.kind) {
                    SearchKind.BASIC_POKEMON -> 24.0
                    SearchKind.POKEMON -> 21.0
                    SearchKind.ENERGY, SearchKind.BASIC_ENERGY -> 19.0
                    SearchKind.TRAINER, SearchKind.ITEM, SearchKind.SUPPORTER, SearchKind.STADIUM -> 18.0
                    else -> 15.0
                }
                is AttachEnergy -> op.count * 28.0
                is DiscardEnergy -> op.count * 30.0
                is SwitchActive -> 18.0
                is ShuffleHandAndDraw -> op.drawCount * 7.0
                is DiscardHandAndDraw -> op.drawCount * 6.0
                is DiscardHandCards -> op.count * -5.0
                is RecoverFromDiscard -> op.count * 17.0
                is ClearSpecialConditions -> 18.0
                is DiscardTopDeck -> op.count * if (op.opponent) 8.0 else -4.0
                is ReturnToHand -> if (op.target == EffectTarget.OPPONENT_ACTIVE) 28.0 else 8.0
                is DamageBonus -> op.amount * 0.75
                is CoinRule -> if (op.cancelOnTails) -9.0 else op.bonusPerHeads * 0.35
                is PreventDamage -> if (op.allDamage) 42.0 else (op.amount ?: 0) * 0.45
                is LockAction -> if (op.attack) 34.0 else if (op.retreat) 17.0 else 0.0
                is ExtraPrize -> op.count * 80.0
                is UnsupportedEffect -> 0.0
            }
        }
        score -= parsed.unsupportedParts.size * 3.0
        return score
    }
}

fun targetLabel(target: EffectTarget): String = when (target) {
    EffectTarget.SELF_ACTIVE -> "diesem Pokémon"
    EffectTarget.OWN_ACTIVE -> "deinem aktiven Pokémon"
    EffectTarget.OWN_BENCH -> "einem deiner Bank-Pokémon"
    EffectTarget.OWN_ANY -> "einem deiner Pokémon"
    EffectTarget.OPPONENT_ACTIVE -> "dem aktiven Pokémon des Gegners"
    EffectTarget.OPPONENT_BENCH -> "einem gegnerischen Bank-Pokémon"
    EffectTarget.OPPONENT_ANY -> "einem Pokémon des Gegners"
    EffectTarget.ALL_OWN -> "allen deinen Pokémon"
    EffectTarget.ALL_OWN_BENCH -> "allen deinen Bank-Pokémon"
    EffectTarget.ALL_OPPONENT -> "allen gegnerischen Pokémon"
    EffectTarget.ALL_OPPONENT_BENCH -> "allen gegnerischen Bank-Pokémon"
}

private fun statusLabel(status: EffectStatus): String = when (status) {
    EffectStatus.POISONED -> "Vergiftet"
    EffectStatus.BURNED -> "Verbrannt"
    EffectStatus.ASLEEP -> "Schläft"
    EffectStatus.PARALYZED -> "Paralysiert"
    EffectStatus.CONFUSED -> "Verwirrt"
}

private fun searchLabel(kind: SearchKind): String = when (kind) {
    SearchKind.ANY -> "Karte(n)"
    SearchKind.POKEMON -> "Pokémon"
    SearchKind.BASIC_POKEMON -> "Basis-Pokémon"
    SearchKind.EVOLUTION_POKEMON -> "Entwicklungs-Pokémon"
    SearchKind.ENERGY -> "Energiekarte(n)"
    SearchKind.BASIC_ENERGY -> "Basis-Energiekarte(n)"
    SearchKind.TRAINER -> "Trainerkarte(n)"
    SearchKind.ITEM -> "Itemkarte(n)"
    SearchKind.SUPPORTER -> "Unterstützerkarte(n)"
    SearchKind.STADIUM -> "Stadionkarte(n)"
}
