package de.ahnsen.kartentrainer

import java.util.Locale

/**
 * Card IDs are NOT Pokédex numbers. Extract only a collector fraction (066/142)
 * from camera OCR and resolve a set using its stamped expansion code if available.
 */
internal data class CardScanIdentity(
    val localId: String?,
    val total: Int?,
    val printedSetCode: String?,
    val setId: String?
) {
    fun numberMatches(other: String): Boolean {
        if (localId == null) return false
        return other.trimStart('0').ifEmpty { "0" } ==
            localId.trimStart('0').ifEmpty { "0" }
    }
}

internal object CardScanIdentityParser {
    private val collectorPattern = Regex("""(?i)(?<![\p{Alnum}])([A-Z]{0,3}\d{1,3}[A-Z]?)\s*[/\\]\s*(\d{2,3})(?!\d)""")
    private val codePattern = Regex("""(?i)(?<![A-Z])(SCR|SFA|TWM|TEF|PAF|PAR|OBF|PAL|SVI|MEW|SSP|PRE|JTG)(?![A-Z])""")
    private val setCodes = mapOf(
        "SCR" to "sv07",
        "SFA" to "sv06.5",
        "TWM" to "sv06",
        "TEF" to "sv05",
        "PAF" to "sv04.5",
        "PAR" to "sv04",
        "OBF" to "sv03",
        "PAL" to "sv02",
        "SVI" to "sv01",
        "MEW" to "sv03.5",
        "SSP" to "sv08",
        "PRE" to "sv08.5",
        "JTG" to "sv09"
    )
    // The 142-card numbering on Stellarkrone is unique within the current
    // Scarlet/Violet catalog used by this app. Still verify the card name.
    private val confidentTotals = mapOf(142 to "sv07")

    fun parse(text: String): CardScanIdentity {
        val match = collectorPattern.findAll(text).firstOrNull { m ->
            val local = m.groupValues[1]
            val total = m.groupValues[2].toIntOrNull() ?: return@firstOrNull false
            val number = local.filter { it.isDigit() }.toIntOrNull() ?: return@firstOrNull false
            number > 0 && total >= number && total >= 20
        }
        val setCode = codePattern.find(text)?.value?.uppercase(Locale.ROOT)
        val total = match?.groupValues?.get(2)?.toIntOrNull()
        return CardScanIdentity(
            localId = match?.groupValues?.get(1),
            total = total,
            printedSetCode = setCode,
            setId = setCode?.let(setCodes::get) ?: total?.let(confidentTotals::get)
        )
    }

    fun normalizeName(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .filter { it.isLetterOrDigit() }

    fun matchesPrintedName(cardName: String, ocrText: String): Boolean {
        val norm = normalizeName(cardName)
        if (norm.length < 3) return false
        val names = OcrParser.candidateNames(ocrText).take(8).map(::normalizeName)
        return names.contains(norm) ||
            normalizeName(ocrText).contains(norm)
    }

    fun candidateIds(identity: CardScanIdentity): List<String> {
        val set = identity.setId ?: return emptyList()
        val number = identity.localId ?: return emptyList()
        return listOf("$set-$number", "$set-${number.padStart(3, '0')}",
            "$set-${number.toIntOrNull() ?: number}").distinct()
    }
}
