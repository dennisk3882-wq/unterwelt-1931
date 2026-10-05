package de.ahnsen.kartentrainer

import android.content.Context
import java.security.MessageDigest

data class FamilySettings(
    val childMode: Boolean = false,
    val priceGateEnabled: Boolean = false,
    val hasPin: Boolean = false
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
    val progress: Int,
    val target: Int
)

class FamilyLocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("tcg_family_local", Context.MODE_PRIVATE)

    fun settings(): FamilySettings = FamilySettings(
        childMode = prefs.getBoolean("child_mode", false),
        priceGateEnabled = prefs.getBoolean("price_gate", false),
        hasPin = !prefs.getString("pin_hash", "").isNullOrBlank()
    )

    fun setChildMode(enabled: Boolean) {
        prefs.edit().putBoolean("child_mode", enabled).apply()
    }

    fun setPriceGate(enabled: Boolean) {
        prefs.edit().putBoolean("price_gate", enabled).apply()
    }

    fun setPin(pin: String): Boolean {
        val clean = pin.filter { it.isDigit() }
        if (clean.length !in 4..8) return false
        prefs.edit().putString("pin_hash", hash(clean)).apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val expected = prefs.getString("pin_hash", "") ?: ""
        return expected.isNotBlank() && expected == hash(pin.filter { it.isDigit() })
    }

    fun clearPin() {
        prefs.edit().remove("pin_hash").putBoolean("price_gate", false).apply()
    }

    private fun hash(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(("tcg-karten-coach-local|" + value).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

object AchievementEngine {
    fun evaluate(
        collection: List<CollectionEntry>,
        stats: PlayerStats,
        savedDecks: List<SavedDeck>,
        learnedAiGames: Int
    ): List<Achievement> {
        val totalCards = collection.sumOf { it.quantity }
        val uniqueCards = collection.size
        val sets = collection.map { it.card.setName }.filter { it.isNotBlank() }.distinct().size
        val holo = collection.filter { it.variant == "Holo" || it.variant == "Reverse" }.sumOf { it.quantity }
        val standard = collection.filter { it.card.isStandardPlayable() }.sumOf { it.quantity }

        fun a(id: String, title: String, description: String, progress: Int, target: Int) =
            Achievement(
                id = id,
                title = title,
                description = description,
                unlocked = progress >= target,
                progress = progress.coerceAtMost(target),
                target = target
            )

        return listOf(
            a("first_scan", "Erste Karte", "Die erste Karte wurde eingescannt.", totalCards, 1),
            a("collector_25", "Kleine Sammlung", "25 Karten gesammelt.", totalCards, 25),
            a("collector_100", "Kartensammler", "100 Karten gesammelt.", totalCards, 100),
            a("unique_50", "Viele verschiedene", "50 unterschiedliche Karten besitzen.", uniqueCards, 50),
            a("sets_5", "Set-Entdecker", "Karten aus 5 verschiedenen Sets besitzen.", sets, 5),
            a("holo_10", "Glanzsammler", "10 Holo/Reverse-Karten besitzen.", holo, 10),
            a("standard_60", "Turnierbasis", "60 standard-legale Karten besitzen.", standard, 60),
            a("deck_1", "Deckbauer", "Das erste Deck speichern.", savedDecks.size, 1),
            a("deck_3", "Decklabor", "3 unterschiedliche Decks speichern.", savedDecks.size, 3),
            a("game_1", "Erste Partie", "Eine Partie vollständig spielen.", stats.games, 1),
            a("win_1", "Erster Sieg", "Eine Partie gegen die KI gewinnen.", stats.wins, 1),
            a("win_10", "Trainer", "10 Partien gegen die KI gewinnen.", stats.wins, 10),
            a("ai_10", "KI-Rivale", "Die lernende KI über 10 Partien trainieren.", learnedAiGames, 10)
        )
    }
}
