package de.ahnsen.kartentrainer

import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

data class CardAbility(
    val type: String,
    val name: String,
    val effect: String
)

data class CardAttack(
    val name: String,
    val damage: String,
    val effect: String,
    val cost: List<String>
) {
    val baseDamage: Int
        get() = Regex("""\d+""").find(damage)?.value?.toIntOrNull() ?: 0
}

data class CardData(
    val id: String,
    val localId: String,
    val name: String,
    val imageBase: String?,
    val category: String,
    val rarity: String,
    val setName: String,
    val setId: String,
    val hp: Int?,
    val stage: String?,
    val evolveFrom: String?,
    val regulationMark: String?,
    val standardLegal: Boolean,
    val expandedLegal: Boolean,
    val types: List<String>,
    val weaknesses: List<String>,
    val resistances: List<String>,
    val retreatCost: Int,
    val abilities: List<CardAbility>,
    val attacks: List<CardAttack>,
    val effect: String?,
    val trainerType: String?,
    val energyType: String?,
    val variantsNormal: Boolean,
    val variantsReverse: Boolean,
    val variantsHolo: Boolean,
    val priceAvg: Double?,
    val priceLow: Double?,
    val priceTrend: Double?,
    val priceAvgHolo: Double?,
    val priceLowHolo: Double?,
    val priceAvg1: Double? = null,
    val priceAvg7: Double? = null,
    val priceAvg30: Double? = null,
    val priceTrendHolo: Double? = null,
    val priceAvg1Holo: Double? = null,
    val priceAvg7Holo: Double? = null,
    val priceAvg30Holo: Double? = null,
    val priceUpdatedAt: Long? = null,
    val usMarketNormal: Double? = null,
    val usMarketHolo: Double? = null,
    val usMarketReverse: Double? = null,
    val usMarketUpdatedAt: Long? = null
) {
    val imageUrl: String?
        get() = imageBase?.let { "$it/high.webp" }

    fun normalizedCategory(): String = category.lowercase(Locale.ROOT)

    fun isPokemon(): Boolean = normalizedCategory().contains("pok")
    fun isTrainer(): Boolean = normalizedCategory().contains("trainer")
    fun isEnergy(): Boolean = normalizedCategory().contains("energ")

    fun isBasicPokemon(): Boolean {
        val s = stage.orEmpty().lowercase(Locale.ROOT)
        return isPokemon() && (s.contains("basic") || s.contains("basis") || evolveFrom.isNullOrBlank())
    }

    fun isBasicEnergy(): Boolean {
        val t = energyType.orEmpty().lowercase(Locale.ROOT)
        return isEnergy() && (t.contains("basic") || t.contains("basis"))
    }

    fun isStandardPlayable(): Boolean = standardLegal || isBasicEnergy()

    fun isAceSpec(): Boolean {
        val all = (name + " " + rarity + " " + trainerType.orEmpty() + " " + effect.orEmpty()).lowercase(Locale.ROOT)
        return "ace spec" in all || "ass-klasse" in all || "assklasse" in all
    }

    fun isRadiantPokemon(): Boolean {
        val n = name.lowercase(Locale.ROOT)
        return isPokemon() && (n.startsWith("radiant ") || n.startsWith("strahlend") || n.startsWith("strahlendes"))
    }

    fun isPokemonStar(): Boolean {
        val n = name.lowercase(Locale.ROOT)
        return isPokemon() && (name.contains("☆") || " pokémon star" in n || " pokemon star" in n)
    }

    fun isPrismStar(): Boolean {
        val all = (name + " " + rarity).lowercase(Locale.ROOT)
        return name.contains("◇") || "prism star" in all || "prisma-stern" in all
    }

    fun isVStarPokemon(): Boolean = isPokemon() && name.lowercase(Locale.ROOT).contains("vstar")
    fun isVMaxPokemon(): Boolean = isPokemon() && name.lowercase(Locale.ROOT).contains("vmax")
    fun isPokemonGX(): Boolean = isPokemon() && name.lowercase(Locale.ROOT).contains("gx")

    fun availableVariants(): List<String> = buildList {
        if (variantsNormal) add("Normal")
        if (variantsReverse) add("Reverse")
        if (variantsHolo) add("Holo")
        if (isEmpty()) add("Normal")
    }

    fun estimatedPrice(variant: String): Double? = MarketValueEngine.marketReference(this, variant)?.price

    fun friendlyExplanation(): String {
        return when {
            isPokemon() -> buildString {
                append(name)
                if (hp != null) append(" hat $hp KP. ")
                if (isBasicPokemon()) {
                    append("Es ist ein Basis-Pokémon und kann direkt als aktives Pokémon oder auf die Bank gelegt werden. ")
                } else if (!evolveFrom.isNullOrBlank()) {
                    append("Es entwickelt sich aus $evolveFrom. ")
                }
                if (attacks.isNotEmpty()) {
                    val first = attacks.first()
                    append("Die Attacke ${first.name} benötigt ${max(1, first.cost.size)} Energie")
                    if (first.baseDamage > 0) append(" und verursacht mindestens ${first.baseDamage} Schaden")
                    append(".")
                }
            }
            isTrainer() -> {
                val type = trainerType?.let { " ($it)" }.orEmpty()
                "Das ist eine Trainerkarte$type. Sie wird in deinem Zug ausgespielt und macht genau das, was im Kartentext steht. ${effect.orEmpty()}".trim()
            }
            isEnergy() -> {
                if (isBasicEnergy()) {
                    "Das ist eine Basis-Energiekarte. Energie wird an Pokémon angelegt, damit sie ihre Attacken bezahlen können."
                } else {
                    "Das ist eine Spezial-Energiekarte. Neben Energie kann sie einen zusätzlichen Effekt haben. ${effect.orEmpty()}".trim()
                }
            }
            else -> "Diese Karte gehört zu $category. Lies den Kartentext und beachte die dort genannten Regeln."
        }
    }

    fun legalityText(): String {
        return when {
            isStandardPlayable() -> "Standard: erlaubt"
            expandedLegal -> "Standard: nicht erlaubt · Erweitert: erlaubt"
            else -> "Nur für freie/casual Partien"
        }
    }
}

data class CardBrief(
    val id: String,
    val localId: String,
    val name: String,
    val imageBase: String?
) {
    val imageUrl: String?
        get() = imageBase?.let { "$it/low.webp" }
}

data class CollectionEntry(
    val card: CardData,
    val quantity: Int,
    val variant: String,
    val language: String = "DE",
    val condition: String = "NM",
    val scanVerified: Boolean = true,
    val lastScannedAt: Long? = null,
    val referenceSalePriceEur: Double? = null,
    val referenceSaleSource: String? = null,
    val referenceSaleAt: Long? = null,
    val apiLivePriceEur: Double? = null,
    val apiLiveProductId: String? = null,
    val apiLiveLanguage: String? = null,
    val apiLiveCondition: String? = null,
    val apiLiveVariant: String? = null,
    val apiLiveUpdatedAt: Long? = null,
    val apiLiveExactGrade: Boolean = false,
    val apiLiveOffers: Int = 0
) {
    fun adjustedUnitValue(): Double? = MarketValueEngine.estimate(this).unitPrice

    val totalEstimatedValue: Double?
        get() = adjustedUnitValue()?.times(quantity)
}

data class OcrGuess(
    val name: String,
    val localId: String?,
    val rawText: String
)

object OcrParser {
    private val numberPattern = Regex("""\b([A-Za-z]*\d{1,3}[A-Za-z]?)[ ]*/[ ]*(\d{1,3})\b""")

    private val typeOrHeaderWords = listOf(
        "trainer", "unterstützer", "unterstuetzer", "supporter", "item",
        "stadion", "stadium", "ausrüstung", "ausruestung", "tool",
        "pokémon-ausrüstung", "pokemon-ausrüstung", "pokémon-ausruestung", "pokemon-ausruestung",
        "pokémon tool", "pokemon tool",
        "energie", "energy", "basis", "basic", "stufe", "stage",
        "illustration", "schwäche", "weakness", "resistenz", "retreat", "rückzug",
        "pokemon", "pokémon", "kp", "hp"
    )

    private val effectLeadWords = listOf(
        "wähle", "waehle", "durchsuche", "lege", "mische", "wenn ", "falls ",
        "du kannst", "während", "waehrend", "dein ", "deiner ", "deines ",
        "ziehe", "fügt", "fuegt", "schaden", "spiele ", "nimmt ", "nimm ",
        "wirf ", "heile ", "entferne ", "verhindere "
    )

    fun parse(text: String): OcrGuess {
        val number = numberPattern.find(text)?.groupValues?.getOrNull(1)
        val candidate = candidateNames(text).firstOrNull().orEmpty()
        return OcrGuess(candidate, number, text)
    }

    fun candidateNames(text: String): List<String> {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.length in 2..45 }

        fun normalized(line: String): String = line.lowercase(Locale.ROOT)

        return lines.mapIndexedNotNull { index, line ->
            val lower = normalized(line)
            if (typeOrHeaderWords.any { lower == it || lower.startsWith(it + " ") }) return@mapIndexedNotNull null
            if (numberPattern.containsMatchIn(line)) return@mapIndexedNotNull null
            if (line.count { it.isLetter() } < 3) return@mapIndexedNotNull null

            var score = 0
            val words = line.split(Regex("""\s+""")).filter { it.isNotBlank() }
            if (line.length in 4..26) score += 5
            if (words.size in 1..4) score += 5
            if (line.firstOrNull()?.isUpperCase() == true) score += 2
            if (line.none { it.isDigit() }) score += 2
            if (line.none { it in ".,:;!?()[]" }) score += 2

            val previous = lines.getOrNull(index - 1)?.let(::normalized).orEmpty()
            if (typeOrHeaderWords.any { previous == it || previous.startsWith(it + " ") }) score += 10

            if (effectLeadWords.any { lower.startsWith(it) || lower.contains(" " + it) }) score -= 14
            if (line.length > 30) score -= 8
            if (words.size > 6) score -= 8

            line to score
        }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }
}


fun Double.euro(): String = NumberFormat.getCurrencyInstance(Locale.GERMANY).format(this)

fun CollectionEntry.toJson(): JSONObject = JSONObject().apply {
    put("quantity", quantity)
    put("variant", variant)
    put("language", language)
    put("condition", condition)
    if (referenceSalePriceEur != null) put("referenceSalePriceEur", referenceSalePriceEur)
    if (referenceSaleSource != null) put("referenceSaleSource", referenceSaleSource)
    if (referenceSaleAt != null) put("referenceSaleAt", referenceSaleAt)
    if (apiLivePriceEur != null) put("apiLivePriceEur", apiLivePriceEur)
    if (apiLiveProductId != null) put("apiLiveProductId", apiLiveProductId)
    if (apiLiveLanguage != null) put("apiLiveLanguage", apiLiveLanguage)
    if (apiLiveCondition != null) put("apiLiveCondition", apiLiveCondition)
    if (apiLiveVariant != null) put("apiLiveVariant", apiLiveVariant)
    if (apiLiveUpdatedAt != null) put("apiLiveUpdatedAt", apiLiveUpdatedAt)
    put("apiLiveExactGrade", apiLiveExactGrade)
    put("apiLiveOffers", apiLiveOffers)
    put("scanVerified", scanVerified)
    put("lastScannedAt", lastScannedAt)
    put("card", card.toJson())
}

fun CardData.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("localId", localId)
    put("name", name)
    put("imageBase", imageBase)
    put("category", category)
    put("rarity", rarity)
    put("setName", setName)
    put("setId", setId)
    put("hp", hp)
    put("stage", stage)
    put("evolveFrom", evolveFrom)
    put("regulationMark", regulationMark)
    put("standardLegal", standardLegal)
    put("expandedLegal", expandedLegal)
    put("types", JSONArray(types))
    put("weaknesses", JSONArray(weaknesses))
    put("resistances", JSONArray(resistances))
    put("retreatCost", retreatCost)
    put("abilities", JSONArray().apply {
        abilities.forEach { a ->
            put(JSONObject().apply {
                put("type", a.type)
                put("name", a.name)
                put("effect", a.effect)
            })
        }
    })
    put("attacks", JSONArray().apply {
        attacks.forEach { a ->
            put(JSONObject().apply {
                put("name", a.name)
                put("damage", a.damage)
                put("effect", a.effect)
                put("cost", JSONArray(a.cost))
            })
        }
    })
    put("effect", effect)
    put("trainerType", trainerType)
    put("energyType", energyType)
    put("variantsNormal", variantsNormal)
    put("variantsReverse", variantsReverse)
    put("variantsHolo", variantsHolo)
    put("priceAvg", priceAvg)
    put("priceLow", priceLow)
    put("priceTrend", priceTrend)
    put("priceAvgHolo", priceAvgHolo)
    put("priceLowHolo", priceLowHolo)
    put("priceAvg1", priceAvg1)
    put("priceAvg7", priceAvg7)
    put("priceAvg30", priceAvg30)
    put("priceTrendHolo", priceTrendHolo)
    put("priceAvg1Holo", priceAvg1Holo)
    put("priceAvg7Holo", priceAvg7Holo)
    put("priceAvg30Holo", priceAvg30Holo)
    put("priceUpdatedAt", priceUpdatedAt)
    put("usMarketNormal", usMarketNormal)
    put("usMarketHolo", usMarketHolo)
    put("usMarketReverse", usMarketReverse)
    put("usMarketUpdatedAt", usMarketUpdatedAt)
}

fun cardFromJson(o: JSONObject): CardData {
    fun nullableString(key: String): String? =
        if (!o.has(key) || o.isNull(key)) null else o.optString(key).takeIf { it.isNotBlank() }

    fun nullableDouble(key: String): Double? =
        if (!o.has(key) || o.isNull(key)) null else o.optDouble(key).takeUnless { it.isNaN() }

    val typesArray = o.optJSONArray("types") ?: JSONArray()
    val types = buildList {
        for (i in 0 until typesArray.length()) add(typesArray.optString(i))
    }

    val weaknessesArray = o.optJSONArray("weaknesses") ?: JSONArray()
    val weaknesses = buildList {
        for (i in 0 until weaknessesArray.length()) add(weaknessesArray.optString(i))
    }

    val resistancesArray = o.optJSONArray("resistances") ?: JSONArray()
    val resistances = buildList {
        for (i in 0 until resistancesArray.length()) add(resistancesArray.optString(i))
    }

    val abilitiesArray = o.optJSONArray("abilities") ?: JSONArray()
    val abilities = buildList {
        for (i in 0 until abilitiesArray.length()) {
            val a = abilitiesArray.optJSONObject(i) ?: continue
            add(
                CardAbility(
                    type = a.optString("type"),
                    name = a.optString("name"),
                    effect = a.optString("effect")
                )
            )
        }
    }

    val attacksArray = o.optJSONArray("attacks") ?: JSONArray()
    val attacks = buildList {
        for (i in 0 until attacksArray.length()) {
            val a = attacksArray.optJSONObject(i) ?: continue
            val costArray = a.optJSONArray("cost") ?: JSONArray()
            val cost = buildList {
                for (j in 0 until costArray.length()) add(costArray.optString(j))
            }
            add(CardAttack(a.optString("name"), a.optString("damage"), a.optString("effect"), cost))
        }
    }

    return CardData(
        id = o.optString("id"),
        localId = o.optString("localId"),
        name = o.optString("name"),
        imageBase = nullableString("imageBase"),
        category = o.optString("category"),
        rarity = o.optString("rarity"),
        setName = o.optString("setName"),
        setId = o.optString("setId"),
        hp = if (o.has("hp") && !o.isNull("hp")) o.optInt("hp") else null,
        stage = nullableString("stage"),
        evolveFrom = nullableString("evolveFrom"),
        regulationMark = nullableString("regulationMark"),
        standardLegal = o.optBoolean("standardLegal"),
        expandedLegal = o.optBoolean("expandedLegal"),
        types = types,
        weaknesses = weaknesses,
        resistances = resistances,
        retreatCost = o.optInt("retreatCost", 1).coerceAtLeast(0),
        abilities = abilities,
        attacks = attacks,
        effect = nullableString("effect"),
        trainerType = nullableString("trainerType"),
        energyType = nullableString("energyType"),
        variantsNormal = o.optBoolean("variantsNormal", true),
        variantsReverse = o.optBoolean("variantsReverse"),
        variantsHolo = o.optBoolean("variantsHolo"),
        priceAvg = nullableDouble("priceAvg"),
        priceLow = nullableDouble("priceLow"),
        priceTrend = nullableDouble("priceTrend"),
        priceAvgHolo = nullableDouble("priceAvgHolo"),
        priceLowHolo = nullableDouble("priceLowHolo"),
        priceAvg1 = nullableDouble("priceAvg1"),
        priceAvg7 = nullableDouble("priceAvg7"),
        priceAvg30 = nullableDouble("priceAvg30"),
        priceTrendHolo = nullableDouble("priceTrendHolo"),
        priceAvg1Holo = nullableDouble("priceAvg1Holo"),
        priceAvg7Holo = nullableDouble("priceAvg7Holo"),
        priceAvg30Holo = nullableDouble("priceAvg30Holo"),
        priceUpdatedAt = if (o.has("priceUpdatedAt") && !o.isNull("priceUpdatedAt")) o.optLong("priceUpdatedAt") else null,
        usMarketNormal = nullableDouble("usMarketNormal"),
        usMarketHolo = nullableDouble("usMarketHolo"),
        usMarketReverse = nullableDouble("usMarketReverse"),
        usMarketUpdatedAt = if (o.has("usMarketUpdatedAt") && !o.isNull("usMarketUpdatedAt")) o.optLong("usMarketUpdatedAt") else null
    )
}
