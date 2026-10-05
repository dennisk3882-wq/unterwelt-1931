package de.ahnsen.kartentrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FullGameEngineTest {
    private fun basicPokemon(
        id: String,
        name: String,
        type: String = "Fire",
        attackCost: List<String> = listOf(type)
    ) = CardData(
        id = id,
        localId = id,
        name = name,
        imageBase = null,
        category = "Pokemon",
        rarity = "Common",
        setName = "Test",
        setId = "test",
        hp = 100,
        stage = "Basic",
        evolveFrom = null,
        regulationMark = "J",
        standardLegal = true,
        expandedLegal = true,
        types = listOf(type),
        weaknesses = emptyList(),
        resistances = emptyList(),
        retreatCost = 1,
        abilities = emptyList(),
        attacks = listOf(
            CardAttack(
                name = "Testangriff",
                damage = "30",
                effect = "",
                cost = attackCost
            )
        ),
        effect = null,
        trainerType = null,
        energyType = null,
        variantsNormal = true,
        variantsReverse = false,
        variantsHolo = false,
        priceAvg = null,
        priceLow = null,
        priceTrend = null,
        priceAvgHolo = null,
        priceLowHolo = null
    )

    private fun energy(id: String, type: String) = CardData(
        id = id,
        localId = id,
        name = "$type Energy",
        imageBase = null,
        category = "Energy",
        rarity = "Common",
        setName = "Energy",
        setId = "energy",
        hp = null,
        stage = null,
        evolveFrom = null,
        regulationMark = "J",
        standardLegal = true,
        expandedLegal = true,
        types = emptyList(),
        weaknesses = emptyList(),
        resistances = emptyList(),
        retreatCost = 0,
        abilities = emptyList(),
        attacks = emptyList(),
        effect = null,
        trainerType = null,
        energyType = "Basic $type",
        variantsNormal = true,
        variantsReverse = false,
        variantsHolo = false,
        priceAvg = null,
        priceLow = null,
        priceTrend = null,
        priceAvgHolo = null,
        priceLowHolo = null
    )

    private fun collection(): List<CollectionEntry> = buildList {
        add(CollectionEntry(basicPokemon("p1", "Feuer A"), 4, "Normal"))
        add(CollectionEntry(basicPokemon("p2", "Feuer B"), 4, "Normal"))
        add(CollectionEntry(energy("e1", "Fire"), 20, "Normal"))
    }

    @Test
    fun fullGameStartsWithValidSixtyCardState() {
        val engine = FullGameEngine(
            entries = collection(),
            difficulty = AiDifficulty.NORMAL,
            standardOnly = true,
            startMode = FullStartMode.PLAYER_FIRST,
            seed = 42
        )
        val state = engine.snapshot()
        assertNotNull(state.player.active)
        assertEquals(6, state.player.prizesLeft)
        val totalVisibleAndHidden =
            state.player.deckCount +
                state.player.hand.size +
                state.player.prizesLeft +
                state.player.bench.size +
                (if (state.player.active != null) 1 else 0) +
                state.player.discardCount +
                state.player.lostZoneCount
        assertEquals(60, totalVisibleAndHidden)
    }

    @Test
    fun explicitStartModesAreRespected() {
        val playerFirst = FullGameEngine(
            collection(), AiDifficulty.NORMAL, true, FullStartMode.PLAYER_FIRST, seed = 7
        ).snapshot()
        val aiFirst = FullGameEngine(
            collection(), AiDifficulty.NORMAL, true, FullStartMode.AI_FIRST, seed = 7
        ).snapshot()
        assertTrue(playerFirst.playerStarted)
        assertTrue(!aiFirst.playerStarted)
    }

    @Test
    fun physicalSynchronizationCanAttachTypedEnergy() {
        val fire = basicPokemon("physical", "Physisch", "Fire", listOf("Fire"))
        val fireEnergy = energy("fire-energy", "Fire")
        val entries = listOf(
            CollectionEntry(fire, 4, "Normal"),
            CollectionEntry(fireEnergy, 20, "Normal")
        )
        val engine = FullGameEngine(
            entries,
            AiDifficulty.NORMAL,
            standardOnly = false,
            startMode = FullStartMode.PLAYER_FIRST,
            seed = 99
        )
        engine.setPhysicalActive(fire)
        val after = engine.synchronizePhysicalCard(fireEnergy, 0)
        assertTrue(after.player.active!!.energy >= 1)
        assertTrue(after.player.active!!.energyTypes.any { it.equals("Fire", true) })
    }
}
