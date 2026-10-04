package de.ahnsen.kartentrainer

import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

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
    val priceLowHolo: Double?
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

    fun availableVariants(): List<String> = buildList {
        if (variantsNormal) add("Normal")
        if (variantsReverse) add("Reverse")
        if (variantsHolo) add("Holo")
        if (isEmpty()) add("Normal")
    }

    fun estimatedPrice(variant: String): Double? = when (variant) {
        "Holo" -> priceAvgHolo ?: priceAvg ?: priceTrend
        "Reverse" -> priceAvgHolo ?: priceAvg ?: priceTrend
        else -> priceAvg ?: priceTrend ?: priceLow
    }

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
    val variant: String
) {
    val totalEstimatedValue: Double?
        get() = card.estimatedPrice(variant)?.times(quantity)
}

data class OcrGuess(
    val name: String,
    val localId: String?,
    val rawText: String
)

object OcrParser {
    private val numberPattern = Regex("""\b([A-Za-z]*\d{1,3}[A-Za-z]?)[ ]*/[ ]*(\d{1,3})\b""")

    fun parse(text: String): OcrGuess {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.length in 2..45 }

        val number = numberPattern.find(text)?.groupValues?.getOrNull(1)

        val blockedWords = listOf(
            "trainer", "energie", "energy", "basis", "basic", "stufe", "stage",
            "illustration", "schwäche", "weakness", "resistenz", "retreat", "rückzug",
            "pokemon", "pokémon", "kp", "hp"
        )

        val candidate = lines
            .asSequence()
            .filterNot { line -> blockedWords.any { line.lowercase(Locale.ROOT).contains(it) } }
            .filterNot { line -> numberPattern.containsMatchIn(line) }
            .filter { line -> line.count { it.isLetter() } >= 3 }
            .sortedBy { line ->
                var score = 0
                if (line.any { it.isDigit() }) score += 4
                if (line.length > 26) score += 2
                if (line.contains(":")) score += 2
                score
            }
            .firstOrNull()
            .orEmpty()

        return OcrGuess(candidate, number, text)
    }
}

fun Double.euro(): String = NumberFormat.getCurrencyInstance(Locale.GERMANY).format(this)

fun CollectionEntry.toJson(): JSONObject = JSONObject().apply {
    put("quantity", quantity)
    put("variant", variant)
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
        priceLowHolo = nullableDouble("priceLowHolo")
    )
}
