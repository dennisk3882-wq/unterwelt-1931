package de.ahnsen.kartentrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardScanIdentityTest {
    @Test fun hefelStellarCrownIsResolvedByNumberAndTotal() {
        val text = """
            BASIS
            Hefel
            Nr. 0926 Welpen-Pokémon Größe 0,3 m
            Angenehmes Aroma
            Durchsuche dein Deck nach 1 Basis-Pokémon
            Illustr. Narumi Sato
            SCR DE 066/142
        """.trimIndent()
        val id = CardScanIdentityParser.parse(text)
        assertEquals("066", id.localId)
        assertEquals(142, id.total)
        assertEquals("SCR", id.printedSetCode)
        assertEquals("sv07", id.setId)
        assertTrue(id.numberMatches("66"))
        assertTrue(CardScanIdentityParser.candidateIds(id).contains("sv07-066"))
        assertTrue(CardScanIdentityParser.matchesPrintedName("Hefel", text))
        assertFalse(CardScanIdentityParser.matchesPrintedName("Pikachu", text))
    }

    @Test fun cardNumberIsNotPokedexNumber() {
        val text = "BASIS\nHefel\nNr. 0926 Welpen-Pokémon\n066/142"
        val id = CardScanIdentityParser.parse(text)
        assertEquals("066", id.localId)
        assertTrue("sv07-066" in CardScanIdentityParser.candidateIds(id))
    }

    @Test fun commonTrainerCardsDoNotForceWrongSets() {
        val text = "Unterstützer\nJaninas Geheimkunst\nTRAINER\n059/064"
        val identity = CardScanIdentityParser.parse(text)
        assertEquals("059", identity.localId)
        assertEquals(64, identity.total)
        assertEquals(null, identity.setId)
    }

    @Test fun missingCardFractionDoesNotInventOne() {
        val identity = CardScanIdentityParser.parse("BASIS\nHefel\nNr. 0926")
        assertEquals(null, identity.localId)
        assertTrue(CardScanIdentityParser.candidateIds(identity).isEmpty())
    }
}
