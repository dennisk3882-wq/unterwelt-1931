package de.ahnsen.kartentrainer

import org.junit.Assert.*
import org.junit.Test

class MarketValueEngineTest {
    private fun card() = CardData(
        id = "sv07-066", localId = "066", name = "Hefel",
        imageBase = null, category = "Pokemon", rarity = "Common",
        setName = "Stellarkrone", setId = "sv07",
        hp = 60, stage = null, evolveFrom = null, regulationMark = null,
        standardLegal = true, expandedLegal = true,
        types = emptyList(), weaknesses = emptyList(), resistances = emptyList(),
        retreatCost = 1, abilities = emptyList(), attacks = emptyList(),
        effect = null, trainerType = null, energyType = null,
        variantsNormal = true, variantsReverse = true, variantsHolo = true,
        priceAvg = 1.20, priceLow = 0.25, priceTrend = 1.40,
        priceAvgHolo = 2.20, priceLowHolo = 0.70,
        priceAvg7 = 1.35, priceAvg30 = 1.28, priceAvg7Holo = 2.45,
        priceUpdatedAt = 1_760_000_000L
    )
    @Test fun holoAndNormalUseDifferentMarketFeeds() {
        assertEquals(1.35, MarketValueEngine.marketReference(card(), "Normal")!!.price, 0.0001)
        assertEquals(2.45, MarketValueEngine.marketReference(card(), "Holo")!!.price, 0.0001)
    }
    @Test fun reverseNeverBorrowsHoloPrice() {
        assertNull(MarketValueEngine.marketReference(card(), "Reverse"))
        assertNull(MarketValueEngine.estimate(CollectionEntry(card(), 1, "Reverse")).unitPrice)
    }
    @Test fun germanDoesNotGetInventedPremium() {
        val a = MarketValueEngine.estimate(CollectionEntry(card(), 1, "Normal", language = "DE"))
        val b = MarketValueEngine.estimate(CollectionEntry(card(), 1, "Normal", language = "EN"))
        assertEquals(a.unitPrice, b.unitPrice)
        assertTrue(a.note.contains("nicht separat nach Sprache"))
    }
    @Test fun damageGradeDoesNotInventExactConditionPrice() {
        val e = CollectionEntry(card(), 1, "Normal", condition = "LP")
        assertNull(MarketValueEngine.estimate(e).unitPrice)
    }
    @Test fun userSoldComparableOverridesOnlyExactEntry() {
        val e = CollectionEntry(card(), 2, "Reverse", "DE", "EX",
            referenceSalePriceEur = 9.75,
            referenceSaleSource = "eBay verkauft",
            referenceSaleAt = System.currentTimeMillis())
        val result = MarketValueEngine.estimate(e)
        assertEquals(9.75, result.unitPrice!!, 0.0001)
        assertTrue(result.isManualSale)
        assertEquals(19.50, e.totalEstimatedValue!!, 0.001)
    }
    @Test fun rejectBogusPricesAndNormalizeTimestamps() {
        assertNull(MarketValueEngine.clean(-10.0))
        assertNull(MarketValueEngine.clean(Double.NaN))
        assertEquals(1_760_000_000_000L, MarketValueEngine.epochMs(1_760_000_000L))
    }
}
