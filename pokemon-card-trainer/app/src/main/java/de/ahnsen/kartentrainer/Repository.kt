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

class TcgDexRepository {
    private val baseUrl = "https://api.tcgdex.net/v2/de"

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

    suspend fun getCard(id: String): CardData = withContext(Dispatchers.IO) {
        parseCard(JSONObject(request("$baseUrl/cards/" + encodePath(id))))
    }

    private fun parseCard(o: JSONObject): CardData {
        val set = o.optJSONObject("set") ?: JSONObject()
        val legal = o.optJSONObject("legal") ?: JSONObject()
        val variants = o.optJSONObject("variants") ?: JSONObject()
        val pricing = o.optJSONObject("pricing") ?: JSONObject()
        val market = pricing.optJSONObject("cardmarket") ?: JSONObject()

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
            priceLowHolo = nullableMarketDouble("low-holo")
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
                            condition = o.optString("condition", "NM")
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
