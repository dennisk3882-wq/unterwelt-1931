package de.ahnsen.kartentrainer

import org.junit.Assert.assertTrue
import org.junit.Test

class EffectCoverageCorpusTest {
    @Test
    fun parsesLargeRepresentativeCorpus() {
        val samples = mutableListOf<Pair<String, Class<out EffectOp>>>()

        for (n in 1..10) {
            samples += "Ziehe $n Karten." to DrawCards::class.java
            samples += "Draw $n cards." to DrawCards::class.java
            samples += "Heile ${n * 10} Schadenspunkte bei einem deiner Pokémon." to HealDamage::class.java
            samples += "Heal ${n * 10} damage from 1 of your Pokémon." to HealDamage::class.java
        }

        for (n in 1..5) {
            samples += "Durchsuche dein Deck nach bis zu $n Basis-Energiekarten und nimm sie auf deine Hand." to SearchDeck::class.java
            samples += "Search your deck for up to $n Basic Energy cards and put them into your hand." to SearchDeck::class.java
            samples += "Lege bis zu $n Energiekarten aus deinem Ablagestapel an eines deiner Pokémon an." to AttachEnergy::class.java
            samples += "Attach up to $n Energy cards from your discard pile to 1 of your Pokémon." to AttachEnergy::class.java
            samples += "Lege $n Energie vom Aktiven Pokémon deines Gegners auf seinen Ablagestapel." to DiscardEnergy::class.java
            samples += "Discard $n Energy from your opponent's Active Pokémon." to DiscardEnergy::class.java
            samples += "Lege die obersten $n Karten des Decks deines Gegners auf seinen Ablagestapel." to DiscardTopDeck::class.java
            samples += "Discard the top $n cards of your opponent's deck." to DiscardTopDeck::class.java
        }

        samples += listOf(
            "Das Aktive Pokémon deines Gegners ist jetzt vergiftet." to ApplyCondition::class.java,
            "Das Aktive Pokémon deines Gegners ist jetzt verbrannt." to ApplyCondition::class.java,
            "Das Aktive Pokémon deines Gegners schläft jetzt." to ApplyCondition::class.java,
            "Das Aktive Pokémon deines Gegners ist jetzt paralysiert." to ApplyCondition::class.java,
            "Das Aktive Pokémon deines Gegners ist jetzt verwirrt." to ApplyCondition::class.java,
            "Your opponent's Active Pokémon is now Poisoned." to ApplyCondition::class.java,
            "Your opponent's Active Pokémon is now Burned." to ApplyCondition::class.java,
            "Your opponent's Active Pokémon is now Asleep." to ApplyCondition::class.java,
            "Your opponent's Active Pokémon is now Paralyzed." to ApplyCondition::class.java,
            "Your opponent's Active Pokémon is now Confused." to ApplyCondition::class.java,
            "Wechsle das Aktive Pokémon deines Gegners gegen 1 Pokémon auf seiner Bank aus." to SwitchActive::class.java,
            "Switch your opponent's Active Pokémon with 1 of their Benched Pokémon." to SwitchActive::class.java,
            "Verhindere allen Schaden, der diesem Pokémon im nächsten Zug durch Attacken zugefügt wird." to PreventDamage::class.java,
            "Prevent all damage done to this Pokémon by attacks during your opponent's next turn." to PreventDamage::class.java,
            "Diese Attacke fügt jedem Pokémon auf der Bank deines Gegners 20 Schadenspunkte zu." to DirectDamage::class.java,
            "This attack does 20 damage to each of your opponent's Benched Pokémon." to DirectDamage::class.java,
            "Dieses Pokémon fügt sich selbst 30 Schaden zu." to SelfDamage::class.java,
            "This Pokémon does 30 damage to itself." to SelfDamage::class.java,
            "Lege 2 Karten aus deiner Hand auf deinen Ablagestapel." to DiscardHandCards::class.java,
            "Discard 2 cards from your hand." to DiscardHandCards::class.java,
            "Nimm bis zu 2 Energiekarten aus deinem Ablagestapel auf deine Hand." to RecoverFromDiscard::class.java,
            "Put up to 2 Energy cards from your discard pile into your hand." to RecoverFromDiscard::class.java,
            "Entferne alle Sonderzustände von diesem Pokémon." to ClearSpecialConditions::class.java,
            "Remove all Special Conditions from this Pokémon." to ClearSpecialConditions::class.java,
            "Lege die obersten 3 Karten deines Decks ins Nirgendwo." to SendToLostZone::class.java,
            "Put the top 3 cards of your deck in the Lost Zone." to SendToLostZone::class.java,
            "Wirf 1 Münze. Bei Zahl hat diese Attacke keine Auswirkungen." to CoinRule::class.java,
            "Flip a coin. If tails, this attack does nothing." to CoinRule::class.java,
            "Diese Attacke fügt für jede Energie 20 mehr Schaden zu." to DamageBonus::class.java,
            "This attack does 20 more damage for each Energy." to DamageBonus::class.java
        )

        var recognized = 0
        samples.forEach { (text, expectedClass) ->
            val parsed = EffectParser.parse(text, EffectSourceKind.ATTACK)
            if (parsed.operations.any { expectedClass.isInstance(it) }) recognized += 1
        }

        assertTrue("Expected at least 100 recognized corpus entries, got $recognized", recognized >= 100)
        assertTrue("Coverage corpus unexpectedly small: ${samples.size}", samples.size >= 100)
    }

    @Test
    fun complexUnknownConditionsRemainSafe() {
        val texts = listOf(
            "Wenn dein Gegner genau 3 Preiskarten übrig hat, ziehe 4 Karten.",
            "Falls dein Gegner in seinem letzten Zug eine bestimmte Karte gespielt hat, fügt diese Attacke 120 mehr Schaden zu.",
            "Solange ein unbekannter globaler Zustand erfüllt ist, gelten besondere Regeln."
        )
        texts.forEach { text ->
            val parsed = EffectParser.parse(text, EffectSourceKind.ABILITY)
            assertTrue(parsed.unsupportedParts.isNotEmpty() || parsed.operations.isEmpty())
        }
    }
}
