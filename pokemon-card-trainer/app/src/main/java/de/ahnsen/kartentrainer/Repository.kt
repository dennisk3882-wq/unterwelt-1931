package de.ahnsen.kartentrainer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

class TcgDexRepository(context: Context? = null) {
    private val baseUrl = "https://api.tcgdex.net/v2/de"
    private val cache = context?.getSharedPreferences("tcg_card_cache_v2", Context.MODE_PRIVATE)

    suspend fun searchCards(name: String, localId: String? = null): List<CardBrief> = withContext(Dispatchers.IO) {
        if (name.isBlank() && localId.isNullOrBlank()) return@withContext emptyList()
        val params = mutableListOf<String>()
        if (name.isNotBlank()) params += "name=" + encode(name.trim())
        if (!localId.isNullOrBlank()) params += "localId=" + encode("eq:" + localId.trim())
        params += "pagination:page=1"
        params += "pagination:itemsPerPage=30"

        val json = request("$baseUrl/cards?" + params.joinToString("&"))
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    CardBrief(
                        id = o.optString("id"),
                        localId = o.optString("localId"),
                        name = o.optString("name"),
                        imageBase = o.optString("image").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    suspend fun resolveScannedCards(rawText: String): List<CardBrief> {
        val identity = CardScanIdentityParser.parse(rawText)
        val parsed = OcrParser.parse(rawText)
        val names = OcrParser.candidateNames(rawText).take(6)
        val localId = identity.localId ?: parsed.localId?.takeIf { it.isNotBlank() }

        // Exact collector number and set code/card count beat fuzzy name searches.
        // Validate the OCR name too: do not save a wrong card on a coincidental ID.
        if (identity.localId != null && identity.setId != null) {
            for (id in CardScanIdentityParser.candidateIds(identity)) {
                val card = runCatching { getCard(id) }.getOrNull() ?: continue
                if (identity.numberMatches(card.localId) &&
                    CardScanIdentityParser.matchesPrintedName(card.name, rawText)
                ) {
                    return listOf(CardBrief(card.id, card.localId, card.name, card.imageBase))
                }
            }
        }

        val merged = linkedMapOf<String, CardBrief>()
        suspend fun collect(name: String, number: String?) {
            runCatching { searchCards(name, number) }
                .getOrDefault(emptyList())
                .forEach { merged[it.id] = it }
        }

        // Try name+collector number first; never let a Pokédex number win.
        names.take(4).forEach { collect(it, localId) }
        if (merged.isEmpty() && localId != null) collect("", localId)
        if (merged.isEmpty()) names.take(3).forEach { collect(it, null) }

        val allCandidates = merged.values.toList()
        if (allCandidates.isEmpty()) return emptyList()

        // TCGdex searches may include other printings, because some endpoints
        // perform fuzzy filtering. Keep same-number results ahead of anything else.
        val numberMatching = if (localId != null) {
            allCandidates.filter { card ->
                identity.numberMatches(card.localId) ||
                    (identity.localId == null && card.localId.trimStart('0') ==
                        localId.trimStart('0'))
            }
        } else emptyList()
        val candidates = if (numberMatching.isNotEmpty()) numberMatching else allCandidates
        val rawNormalized = CardScanIdentityParser.normalizeName(rawText)

        fun dice(a: String, b: String): Double {
            val left = CardScanIdentityParser.normalizeName(a)
            val right = CardScanIdentityParser.normalizeName(b)
            if (left.isBlank() || right.isBlank()) return 0.0
            if (left == right) return 1.0
            if (left.length < 2 || right.length < 2) return 0.0
            val available = left.windowed(2).groupingBy { it }.eachCount().toMutableMap()
            var overlap = 0
            for (pair in right.windowed(2)) {
                val count = available[pair] ?: 0
                if (count > 0) {
                    overlap++
                    available[pair] = count - 1
                }
            }
            return 2.0 * overlap / ((left.length - 1) + (right.length - 1))
        }

        val scored = candidates.map { card ->
            val name = CardScanIdentityParser.normalizeName(card.name)
            val exactName = name.length >= 3 && rawNormalized.contains(name)
            val matchingNumber = localId != null &&
                card.localId.trimStart('0').equals(localId.trimStart('0'), ignoreCase = true)
            var score = 0.0
            if (exactName) score += 110.0
            if (matchingNumber) score += 130.0
            if (localId != null && !matchingNumber) score -= 180.0
            if (identity.setId != null && card.id.substringBeforeLast('-') == identity.setId) score += 280.0
            if (identity.setId != null && card.id.substringBeforeLast('-') != identity.setId) score -= 200.0
            score += names.maxOfOrNull { dice(it, card.name) * 60.0 } ?: 0.0
            card to score
        }.sortedByDescending { it.second }
        val top = scored.first()
        val runnerUp = scored.getOrNull(1)
        val clear = runnerUp == null || top.second - runnerUp.second >= 55.0
        val exactName = CardScanIdentityParser.matchesPrintedName(top.first.name, rawText)
        val numberCorrect = localId != null &&
            top.first.localId.trimStart('0').equals(localId.trimStart('0'), ignoreCase = true)
        val setCorrect = identity.setId == null ||
            top.first.id.substringBeforeLast('-') == identity.setId

        return if (exactName && numberCorrect && setCorrect && clear) {
            listOf(top.first)
        } else {
            // Never auto-pick from a weak/ambiguous name-only match.
            scored.filter { it.second > 0 }.take(12).map { it.first }
        }
    }

    suspend fun getCard(id: String): CardData = withContext(Dispatchers.IO) {
        val key = "card_" + id
        val online = runCatching {
            request("$baseUrl/cards/" + encodePath(id))
        }.getOrNull()

        if (!online.isNullOrBlank()) {
            cache?.edit()?.putString(key, online)?.apply()
            return@withContext parseCard(JSONObject(online))
        }

        val cached = cache?.getString(key, null)
            ?: error("Kartendienst nicht erreichbar und keine lokale Kopie für $id vorhanden.")
        parseCard(JSONObject(cached))
    }

    private fun parseCard(o: JSONObject): CardData {
        val set = o.optJSONObject("set") ?: JSONObject()
        val legal = o.optJSONObject("legal") ?: JSONObject()
        val variants = o.optJSONObject("variants") ?: JSONObject()
        val pricing = o.optJSONObject("pricing") ?: JSONObject()
        val market = pricing.optJSONObject("cardmarket") ?: JSONObject()
        val usPricing = pricing.optJSONObject("tcgplayer") ?: JSONObject()

        val typesArr = o.optJSONArray("types") ?: JSONArray()
        val types = buildList {
            for (i in 0 until typesArr.length()) add(typesArr.optString(i))
        }

        fun typeList(key: String): List<String> {
            val arr = o.optJSONArray(key) ?: JSONArray()
            return buildList {
                for (i in 0 until arr.length()) {
                    val entry = arr.optJSONObject(i)
                    if (entry != null) {
                        val type = entry.optString("type")
                        if (type.isNotBlank()) add(type)
                    }
                }
            }
        }

        val abilitiesArr = o.optJSONArray("abilities") ?: JSONArray()
        val abilities = buildList {
            for (i in 0 until abilitiesArr.length()) {
                val a = abilitiesArr.optJSONObject(i) ?: continue
                add(
                    CardAbility(
                        type = a.optString("type"),
                        name = a.optString("name"),
                        effect = a.optString("effect")
                    )
                )
            }
        }

        val attacksArr = o.optJSONArray("attacks") ?: JSONArray()
        val attacks = buildList {
            for (i in 0 until attacksArr.length()) {
                val a = attacksArr.optJSONObject(i) ?: continue
                val costArr = a.optJSONArray("cost") ?: JSONArray()
                val cost = buildList {
                    for (j in 0 until costArr.length()) add(costArr.optString(j))
                }
                add(
                    CardAttack(
                        name = a.optString("name"),
                        damage = if (a.has("damage") && !a.isNull("damage")) a.opt("damage").toString() else "",
                        effect = a.optString("effect"),
                        cost = cost
                    )
                )
            }
        }

        fun nullableString(key: String): String? =
            if (!o.has(key) || o.isNull(key)) null else o.optString(key).takeIf { it.isNotBlank() }

        fun nullableMarketDouble(key: String): Double? =
            if (!market.has(key) || market.isNull(key)) null else market.optDouble(key).takeUnless { it.isNaN() }

        return CardData(
            id = o.optString("id"),
            localId = o.opt("localId")?.toString().orEmpty(),
            name = o.optString("name"),
            imageBase = nullableString("image"),
            category = o.optString("category"),
            rarity = o.optString("rarity"),
            setName = set.optString("name"),
            setId = set.optString("id"),
            hp = if (o.has("hp") && !o.isNull("hp")) o.optInt("hp") else null,
            stage = nullableString("stage"),
            evolveFrom = nullableString("evolveFrom"),
            regulationMark = nullableString("regulationMark"),
            standardLegal = legal.optBoolean("standard"),
            expandedLegal = legal.optBoolean("expanded"),
            types = types,
            weaknesses = typeList("weaknesses"),
            resistances = typeList("resistances"),
            retreatCost = o.optInt("retreat", 1).coerceAtLeast(0),
            abilities = abilities,
            attacks = attacks,
            effect = nullableString("effect"),
            trainerType = nullableString("trainerType"),
            energyType = nullableString("energyType"),
            variantsNormal = variants.optBoolean("normal", true),
            variantsReverse = variants.optBoolean("reverse"),
            variantsHolo = variants.optBoolean("holo"),
            priceAvg = nullableMarketDouble("avg"),
            priceLow = nullableMarketDouble("low"),
            priceTrend = nullableMarketDouble("trend"),
            priceAvgHolo = nullableMarketDouble("avg-holo"),
            priceLowHolo = nullableMarketDouble("low-holo"),
            priceAvg1 = nullableMarketDouble("avg1"),
            priceAvg7 = nullableMarketDouble("avg7"),
            priceAvg30 = nullableMarketDouble("avg30"),
            priceTrendHolo = nullableMarketDouble("trend-holo"),
            priceAvg1Holo = nullableMarketDouble("avg1-holo"),
            priceAvg7Holo = nullableMarketDouble("avg7-holo"),
            priceAvg30Holo = nullableMarketDouble("avg30-holo"),
            priceUpdatedAt = if (market.has("updated") && !market.isNull("updated")) market.optLong("updated").takeIf { it > 0L } else null,
            usMarketNormal = usPricing.optJSONObject("normal")?.optDouble("marketPrice")?.takeIf { !it.isNaN() && it > 0 },
            usMarketHolo = usPricing.optJSONObject("holofoil")?.optDouble("marketPrice")?.takeIf { !it.isNaN() && it > 0 },
            usMarketReverse = usPricing.optJSONObject("reverse-holofoil")?.optDouble("marketPrice")?.takeIf { !it.isNaN() && it > 0 }
                ?: usPricing.optJSONObject("reverseHolofoil")?.optDouble("marketPrice")?.takeIf { !it.isNaN() && it > 0 },
            usMarketUpdatedAt = if (usPricing.has("updated") && !usPricing.isNull("updated")) usPricing.optLong("updated").takeIf { it > 0L } else null
        )
    }

    private fun request(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 16000
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("Kartendienst meldet HTTP $code")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun encodePath(value: String): String =
        value.replace(" ", "%20")
}

class CollectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("tcg_cards", Context.MODE_PRIVATE)

    fun load(): List<CollectionEntry> {
        val raw = prefs.getString("collection_v1", "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val cardObj = o.optJSONObject("card") ?: continue
                    add(
                        CollectionEntry(
                            card = cardFromJson(cardObj),
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
    }

    fun save(entries: List<CollectionEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("collection_v1", arr.toString()).apply()
    }

    fun add(entries: List<CollectionEntry>, card: CardData, variant: String): List<CollectionEntry> {
        val index = entries.indexOfFirst {
            it.card.id == card.id && it.variant == variant && it.language == "DE" && it.condition == "NM"
        }
        val updated = entries.toMutableList()
        if (index >= 0) {
            val old = updated[index]
            updated[index] = old.copy(quantity = old.quantity + 1)
        } else {
            updated += CollectionEntry(card, 1, variant)
        }
        save(updated)
        return updated
    }

    fun changeQuantity(entries: List<CollectionEntry>, target: CollectionEntry, delta: Int): List<CollectionEntry> {
        val updated = entries.toMutableList()
        val index = updated.indexOfFirst {
            it.card.id == target.card.id &&
                it.variant == target.variant &&
                it.language == target.language &&
                it.condition == target.condition
        }
        if (index < 0) return entries
        val next = updated[index].quantity + delta
        if (next <= 0) updated.removeAt(index) else updated[index] = updated[index].copy(quantity = next)
        save(updated)
        return updated
    }
}
