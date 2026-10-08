package de.ahnsen.kartentrainer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LocalProfile(
    val id: String,
    val name: String
)

data class SavedDeckCard(
    val cardId: String,
    val variant: String,
    val quantity: Int
)

data class SavedDeck(
    val id: String,
    val name: String,
    val standardOnly: Boolean,
    val formatName: String = if (standardOnly) DeckFormat.STANDARD.name else DeckFormat.FREE.name,
    val cards: List<SavedDeckCard>,
    val strength: Int,
    val archetype: String
)

data class PlayerStats(
    val games: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val coachHintsUsed: Int = 0,
    val scannedCards: Int = 0
) {
    val winRate: Int
        get() = if (games <= 0) 0 else ((wins * 100.0) / games).toInt()
}

class LocalAppStore(context: Context) {
    private val prefs = context.getSharedPreferences("tcg_local_2", Context.MODE_PRIVATE)

    fun profiles(): List<LocalProfile> {
        val raw = prefs.getString("profiles", null)
        if (raw.isNullOrBlank()) {
            val initial = LocalProfile("default", "Spieler 1")
            prefs.edit()
                .putString("profiles", JSONArray().put(profileJson(initial)).toString())
                .putString("active_profile", initial.id)
                .apply()
            return listOf(initial)
        }
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(LocalProfile(o.optString("id"), o.optString("name")))
                }
            }
        }.getOrDefault(listOf(LocalProfile("default", "Spieler 1")))
    }

    fun activeProfileId(): String =
        prefs.getString("active_profile", null) ?: profiles().first().id

    fun setActiveProfile(id: String) {
        if (profiles().any { it.id == id }) {
            prefs.edit().putString("active_profile", id).apply()
        }
    }

    fun addProfile(name: String): LocalProfile {
        val clean = name.trim().ifBlank { "Neues Profil" }
        val profile = LocalProfile(UUID.randomUUID().toString(), clean.take(30))
        val all = profiles().toMutableList().apply { add(profile) }
        saveProfiles(all)
        setActiveProfile(profile.id)
        return profile
    }

    fun renameProfile(id: String, name: String) {
        val all = profiles().map {
            if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }.take(30)) else it
        }
        saveProfiles(all)
    }

    fun deleteProfile(id: String) {
        val all = profiles()
        if (all.size <= 1) return
        val remaining = all.filterNot { it.id == id }
        saveProfiles(remaining)
        prefs.edit()
            .remove(collectionKey(id))
            .remove(decksKey(id))
            .remove(statsKey(id))
            .apply()
        if (activeProfileId() == id) setActiveProfile(remaining.first().id)
    }

    fun loadCollection(profileId: String): List<CollectionEntry> {
        val raw = prefs.getString(collectionKey(profileId), null)
        if (raw.isNullOrBlank()) return emptyList()
        return decodeCollection(raw)
    }

    fun saveCollection(profileId: String, entries: List<CollectionEntry>) {
        prefs.edit().putString(collectionKey(profileId), encodeCollection(entries)).apply()
    }

    fun loadDecks(profileId: String): List<SavedDeck> {
        val raw = prefs.getString(decksKey(profileId), "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val cards = o.optJSONArray("cards") ?: JSONArray()
                    add(
                        SavedDeck(
                            id = o.optString("id"),
                            name = o.optString("name"),
                            standardOnly = o.optBoolean("standardOnly", true),
                            formatName = o.optString(
                                "formatName",
                                if (o.optBoolean("standardOnly", true)) DeckFormat.STANDARD.name else DeckFormat.FREE.name
                            ),
                            cards = buildList {
                                for (j in 0 until cards.length()) {
                                    val c = cards.optJSONObject(j) ?: continue
                                    add(
                                        SavedDeckCard(
                                            cardId = c.optString("cardId"),
                                            variant = c.optString("variant", "Normal"),
                                            quantity = c.optInt("quantity", 1).coerceAtLeast(1)
                                        )
                                    )
                                }
                            },
                            strength = o.optInt("strength", 0).coerceIn(0, 100),
                            archetype = o.optString("archetype", "Ausgeglichen")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveDecks(profileId: String, decks: List<SavedDeck>) {
        val arr = JSONArray()
        decks.forEach { deck ->
            arr.put(JSONObject().apply {
                put("id", deck.id)
                put("name", deck.name)
                put("standardOnly", deck.standardOnly)
                put("formatName", deck.formatName)
                put("strength", deck.strength)
                put("archetype", deck.archetype)
                put("cards", JSONArray().apply {
                    deck.cards.forEach { card ->
                        put(JSONObject().apply {
                            put("cardId", card.cardId)
                            put("variant", card.variant)
                            put("quantity", card.quantity)
                        })
                    }
                })
            })
        }
        prefs.edit().putString(decksKey(profileId), arr.toString()).apply()
    }

    fun stats(profileId: String): PlayerStats {
        val raw = prefs.getString(statsKey(profileId), null) ?: return PlayerStats()
        return runCatching {
            val o = JSONObject(raw)
            PlayerStats(
                games = o.optInt("games"),
                wins = o.optInt("wins"),
                losses = o.optInt("losses"),
                coachHintsUsed = o.optInt("coachHintsUsed"),
                scannedCards = o.optInt("scannedCards")
            )
        }.getOrDefault(PlayerStats())
    }

    fun saveStats(profileId: String, stats: PlayerStats) {
        prefs.edit().putString(
            statsKey(profileId),
            JSONObject().apply {
                put("games", stats.games)
                put("wins", stats.wins)
                put("losses", stats.losses)
                put("coachHintsUsed", stats.coachHintsUsed)
                put("scannedCards", stats.scannedCards)
            }.toString()
        ).apply()
    }

    fun exportProfile(profileId: String): String {
        val profile = profiles().firstOrNull { it.id == profileId } ?: LocalProfile(profileId, "Spieler")
        return JSONObject().apply {
            put("format", "tcg-karten-coach-local-backup")
            put("version", 2)
            put("profile", profileJson(profile))
            put("collection", JSONArray(encodeCollection(loadCollection(profileId))))
            put("decks", JSONArray().apply {
                loadDecks(profileId).forEach { deck ->
                    put(JSONObject().apply {
                        put("id", deck.id)
                        put("name", deck.name)
                        put("standardOnly", deck.standardOnly)
                        put("formatName", deck.formatName)
                        put("strength", deck.strength)
                        put("archetype", deck.archetype)
                        put("cards", JSONArray().apply {
                            deck.cards.forEach { card ->
                                put(JSONObject().apply {
                                    put("cardId", card.cardId)
                                    put("variant", card.variant)
                                    put("quantity", card.quantity)
                                })
                            }
                        })
                    })
                }
            })
            val s = stats(profileId)
            put("stats", JSONObject().apply {
                put("games", s.games)
                put("wins", s.wins)
                put("losses", s.losses)
                put("coachHintsUsed", s.coachHintsUsed)
                put("scannedCards", s.scannedCards)
            })
        }.toString(2)
    }

    fun importIntoProfile(profileId: String, raw: String) {
        val root = JSONObject(raw)
        require(root.optString("format") == "tcg-karten-coach-local-backup") {
            "Keine gültige TCG-Karten-Coach-Sicherung."
        }
        val collectionArr = root.optJSONArray("collection") ?: JSONArray()
        saveCollection(profileId, decodeCollection(collectionArr.toString()))

        val deckArr = root.optJSONArray("decks") ?: JSONArray()
        val deckJson = JSONArray()
        for (i in 0 until deckArr.length()) deckJson.put(deckArr.get(i))
        prefs.edit().putString(decksKey(profileId), deckJson.toString()).apply()

        val s = root.optJSONObject("stats")
        if (s != null) {
            saveStats(
                profileId,
                PlayerStats(
                    games = s.optInt("games"),
                    wins = s.optInt("wins"),
                    losses = s.optInt("losses"),
                    coachHintsUsed = s.optInt("coachHintsUsed"),
                    scannedCards = s.optInt("scannedCards")
                )
            )
        }
    }

    private fun saveProfiles(profiles: List<LocalProfile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(profileJson(it)) }
        prefs.edit().putString("profiles", arr.toString()).apply()
    }

    private fun profileJson(profile: LocalProfile) = JSONObject().apply {
        put("id", profile.id)
        put("name", profile.name)
    }

    private fun encodeCollection(entries: List<CollectionEntry>): String =
        JSONArray().apply { entries.forEach { put(it.toJson()) } }.toString()

    private fun decodeCollection(raw: String): List<CollectionEntry> = runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val card = o.optJSONObject("card") ?: continue
                add(
                    CollectionEntry(
                        card = cardFromJson(card),
                        quantity = o.optInt("quantity", 1).coerceAtLeast(1),
                        variant = o.optString("variant", "Normal"),
                        language = o.optString("language", "DE"),
                        condition = o.optString("condition", "NM"),
                        scanVerified = o.optBoolean("scanVerified", true),
                        lastScannedAt = if (o.has("lastScannedAt") && !o.isNull("lastScannedAt")) {
                            o.optLong("lastScannedAt")
                        } else null
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun collectionKey(profileId: String) = "collection_$profileId"
    private fun decksKey(profileId: String) = "decks_$profileId"
    private fun statsKey(profileId: String) = "stats_$profileId"
}
