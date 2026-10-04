package de.ahnsen.kartentrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectEngineTest {
    @Test
    fun parsesDraw() {
        val p = EffectParser.parse("Ziehe 3 Karten.", EffectSourceKind.ATTACK)
        assertTrue(p.fullySupported)
        assertTrue(p.operations.any { it is DrawCards && it.count == 3 })
    }

    @Test
    fun parsesCoinCancel() {
        val p = EffectParser.parse(
            "Wirf 1 Münze. Bei Zahl hat diese Attacke keine Auswirkungen.",
            EffectSourceKind.ATTACK
        )
        assertTrue(p.operations.any { it is CoinRule && it.cancelOnTails })
    }

    @Test
    fun parsesEnergySearch() {
        val p = EffectParser.parse(
            "Durchsuche dein Deck nach bis zu 2 Basis-Energiekarten und nimm sie auf deine Hand.",
            EffectSourceKind.TRAINER
        )
        assertTrue(p.operations.any {
            it is SearchDeck && it.kind == SearchKind.BASIC_ENERGY && it.count == 2
        })
    }

    @Test
    fun parsesHealAndStatus() {
        val heal = EffectParser.parse(
            "Heile 30 Schadenspunkte bei einem deiner Pokémon.",
            EffectSourceKind.TRAINER
        )
        assertTrue(heal.operations.any { it is HealDamage && it.amount == 30 })

        val poison = EffectParser.parse(
            "Das Aktive Pokémon deines Gegners ist jetzt vergiftet.",
            EffectSourceKind.ATTACK
        )
        assertTrue(poison.operations.any {
            it is ApplyCondition && it.status == EffectStatus.POISONED
        })
    }

    @Test
    fun parsesBenchDamage() {
        val p = EffectParser.parse(
            "Diese Attacke fügt jedem Pokémon auf der Bank deines Gegners 20 Schadenspunkte zu.",
            EffectSourceKind.ATTACK
        )
        assertTrue(p.operations.any {
            it is DirectDamage &&
                it.amount == 20 &&
                it.target == EffectTarget.ALL_OPPONENT_BENCH
        })
    }

    @Test
    fun parsesEnergyManipulation() {
        val attach = EffectParser.parse(
            "Lege bis zu 2 Energiekarten aus deinem Ablagestapel an eines deiner Pokémon an.",
            EffectSourceKind.ATTACK
        )
        assertTrue(attach.operations.any {
            it is AttachEnergy && it.count == 2 && it.fromDiscard
        })

        val discard = EffectParser.parse(
            "Lege 1 Energie vom Aktiven Pokémon deines Gegners auf seinen Ablagestapel.",
            EffectSourceKind.ATTACK
        )
        assertTrue(discard.operations.any {
            it is DiscardEnergy && it.target == EffectTarget.OPPONENT_ACTIVE
        })
    }

    @Test
    fun parsesSwitchAndProtection() {
        val switch = EffectParser.parse(
            "Wechsle das Aktive Pokémon deines Gegners gegen 1 Pokémon auf seiner Bank aus.",
            EffectSourceKind.TRAINER
        )
        assertTrue(switch.operations.any { it is SwitchActive && it.opponent })

        val shield = EffectParser.parse(
            "Verhindere allen Schaden, der diesem Pokémon während des nächsten Zuges deines Gegners durch Attacken zugefügt wird.",
            EffectSourceKind.ATTACK
        )
        assertTrue(shield.operations.any { it is PreventDamage && it.allDamage })
    }

    @Test
    fun parsesHandCostsAndRecovery() {
        val cost = EffectParser.parse(
            "Lege 2 Karten aus deiner Hand auf deinen Ablagestapel.",
            EffectSourceKind.TRAINER
        )
        assertTrue(cost.operations.any { it is DiscardHandCards && it.count == 2 })

        val recover = EffectParser.parse(
            "Nimm bis zu 2 Energiekarten aus deinem Ablagestapel auf deine Hand.",
            EffectSourceKind.TRAINER
        )
        assertTrue(recover.operations.any {
            it is RecoverFromDiscard && it.kind == SearchKind.ENERGY && it.count == 2
        })
    }

    @Test
    fun unresolvedComplexConditionIsNotAutoExecuted() {
        val p = EffectParser.parse(
            "Wenn dein Gegner genau 3 Preiskarten übrig hat, ziehe 4 Karten.",
            EffectSourceKind.ABILITY
        )
        assertFalse(p.fullySupported)
        assertTrue(p.operations.isEmpty())
        assertEquals(0, p.coveragePercent)
    }

    @Test
    fun classifiesAbilityTiming() {
        assertEquals(
            AbilityTiming.ACTIVATED,
            classifyAbilityTiming("Einmal während deines Zuges kannst du 1 Karte ziehen.")
        )
        assertEquals(
            AbilityTiming.PASSIVE,
            classifyAbilityTiming("Solange dieses Pokémon dein Aktives Pokémon ist, hat es keine Rückzugskosten.")
        )
        assertEquals(
            AbilityTiming.ON_PLAY,
            classifyAbilityTiming("Wenn du dieses Pokémon aus deiner Hand spielst, ziehe 2 Karten.")
        )
    }
}
