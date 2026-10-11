package de.ahnsen.kartentrainer

import java.util.Locale
import kotlin.math.abs

/** Indicative prices are not proven sold prices, and EU figures are NOT German-only. */
internal data class MarketReference(
    val price: Double,
    val label: String,
    val recent: Boolean,
    val ageDays: Long?,
    val low: Double?,
    val daily: Double?,
    val monthly: Double?,
    val updatedAt: Long?
)

internal data class MarketValuation(
    val unitPrice: Double?,
    val confidence: String,
    val source: String,
    val note: String,
    val ageDays: Long?,
    val rangeLow: Double?,
    val rangeHigh: Double?,
    val isManualSale: Boolean
)

internal object MarketValueEngine {
    private const val DAY = 86_400_000L
    fun clean(price: Double?): Double? = price?.takeIf { it.isFinite() && it > 0.0 && it <= 200_000.0 }

    fun epochMs(value: Long?): Long? {
        val n = value ?: return null
        if (n <= 0) return null
        return if (n < 10_000_000_000L) n * 1000L else n
    }

    fun ageDays(timestamp: Long?, now: Long = System.currentTimeMillis()): Long? {
        val time = epochMs(timestamp) ?: return null
        if (time > now + DAY) return null
        return ((now - time).coerceAtLeast(0L)) / DAY
    }

    fun marketReference(card: CardData, variant: String, now: Long = System.currentTimeMillis()): MarketReference? {
        val choice: List<Pair<String, Double?>> = when (variant.lowercase(Locale.ROOT)) {
            "normal" -> listOf(
                "Cardmarket · 7-Tage-Durchschnitt" to card.priceAvg7,
                "Cardmarket · 30-Tage-Durchschnitt" to card.priceAvg30,
                "Cardmarket · Trend" to card.priceTrend,
                "Cardmarket · Durchschnitt" to card.priceAvg,
                "Cardmarket · Tagesdurchschnitt" to card.priceAvg1
            )
            "holo" -> listOf(
                "Cardmarket · Holo · 7 Tage" to card.priceAvg7Holo,
                "Cardmarket · Holo · 30 Tage" to card.priceAvg30Holo,
                "Cardmarket · Holo-Trend" to card.priceTrendHolo,
                "Cardmarket · Holo-Durchschnitt" to card.priceAvgHolo
            )
            // Important: reverse has no independent Cardmarket price in this feed.
            // Do not substitute holofoil or normal and present that as reverse.
            else -> emptyList()
        }
        val selected = choice.firstNotNullOfOrNull { (label, value) ->
            clean(value)?.let { label to it }
        } ?: return null
        val days = ageDays(card.priceUpdatedAt, now)
        return MarketReference(
            price = selected.second,
            label = selected.first,
            recent = days != null && days <= 14,
            ageDays = days,
            low = clean(if (variant.equals("holo", true)) card.priceLowHolo else card.priceLow),
            daily = clean(if (variant.equals("holo", true)) card.priceAvg1Holo else card.priceAvg1),
            monthly = clean(if (variant.equals("holo", true)) card.priceAvg30Holo else card.priceAvg30),
            updatedAt = card.priceUpdatedAt
        )
    }

    fun estimate(entry: CollectionEntry, now: Long = System.currentTimeMillis()): MarketValuation {
        val days = ageDays(entry.referenceSaleAt, now)
        val comparable = clean(entry.referenceSalePriceEur)
        val exactMetadata = entry.language.uppercase(Locale.ROOT) in setOf("DE", "EN", "FR", "IT", "ES", "JP", "OTHER") &&
            entry.condition.uppercase(Locale.ROOT) in setOf("NM", "EX", "GD", "LP", "PL", "PO")
        if (comparable != null && exactMetadata) {
            return MarketValuation(
                unitPrice = comparable,
                confidence = if (days != null && days <= 30) "Vergleichsverkauf" else "Älterer Vergleich",
                source = entry.referenceSaleSource.orEmpty().ifBlank { "Manuell erfasster Verkauf" },
                note = "Vom Nutzer eingetragener Verkaufspreis für diese Ausgabe, Sprache, Variante und Zustand; nicht unabhängig verifiziert.",
                ageDays = days,
                rangeLow = null,
                rangeHigh = null,
                isManualSale = true
            )
        }
        val market = marketReference(entry.card, entry.variant, now)
        if (market == null) {
            return MarketValuation(
                null, "Keine belastbaren Daten", "Kein variantengenauer EU-Preis",
                if (entry.variant.equals("Reverse", true)) "Reverse-Preis fehlt: Holo darf nicht als Ersatz verwendet werden."
                else "Für diese Druckvariante liegen keine zuverlässigen Marktdaten vor.",
                null, null, null, false
            )
        }
        if (!entry.condition.equals("NM", true)) {
            return MarketValuation(
                null, "Zustand ungeprüft", market.label,
                "EU-Richtwert bezieht sich nicht auf den Zustand ${entry.condition}. Keine erfundenen pauschalen Zustandsrabatte.",
                market.ageDays, null, null, false
            )
        }
        val note = buildString {
            append("Europaweiter Karten-Richtwert, nicht separat nach Sprache gefiltert.")
            if (entry.language == "DE") append(" Deutsch ist als Sprache hinterlegt, aber der Marktfeed bestätigt keinen DE-Exklusivpreis.")
            if (market.ageDays == null) append(" Aktualisierungsdatum der Quelle unbekannt.")
            else if (market.ageDays > 30) append(" Marktdaten älter als 30 Tage.")
        }
        val reliable = market.ageDays != null && market.ageDays <= 14 &&
            (market.monthly != null || market.daily != null)
        val range = listOfNotNull(market.low, market.daily, market.monthly, market.price)
            .filter { it >= market.price * 0.5 && it <= market.price * 2.0 }
        val low = if (range.size >= 2) range.minOrNull() else null
        val high = if (range.size >= 2) range.maxOrNull() else null
        return MarketValuation(
            unitPrice = market.price,
            confidence = if (reliable) "EU-Richtwert" else "Eingeschränkte Daten",
            source = market.label,
            note = note,
            ageDays = market.ageDays,
            rangeLow = low,
            rangeHigh = high,
            isManualSale = false
        )
    }
}
