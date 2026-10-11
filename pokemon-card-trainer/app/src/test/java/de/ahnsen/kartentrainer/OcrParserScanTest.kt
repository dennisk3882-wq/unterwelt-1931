package de.ahnsen.kartentrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrParserScanTest {
    @Test
    fun supporterHeaderDoesNotBecomeCardName() {
        val text = """
            Unterstützer
            Janinas Geheimkunst
            TRAINER
            Wähle bis zu 2 deiner Pokémon. Durchsuche für jedes jener Pokémon dein Deck.
            059/064
        """.trimIndent()

        val guess = OcrParser.parse(text)

        assertEquals("Janinas Geheimkunst", guess.name)
        assertEquals("059", guess.localId)
        assertFalse(OcrParser.candidateNames(text).any { it.equals("Unterstützer", ignoreCase = true) })
    }

    @Test
    fun commonTrainerHeadersAreFiltered() {
        val headers = listOf("Unterstützer", "Supporter", "TRAINER", "Item", "Stadion", "Pokémon-Ausrüstung")
        headers.forEach { header ->
            val text = """
                $header
                Testkarte Alpha
                025/100
                Durchsuche dein Deck nach einer Karte.
            """.trimIndent()

            val candidates = OcrParser.candidateNames(text)
            assertTrue("Expected title candidate for $header", candidates.contains("Testkarte Alpha"))
            assertFalse("Header must not be title: $header", candidates.any { it.equals(header, ignoreCase = true) })
        }
    }
}
