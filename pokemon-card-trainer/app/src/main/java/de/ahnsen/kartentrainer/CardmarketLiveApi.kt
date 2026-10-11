package de.ahnsen.kartentrainer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.time.Instant
import java.util.Locale

/**
 * Optional third-party cardmarketapi.com integration.
 * Not Cardmarket's official API. Never includes credentials in URLs, logs or backups.
 */
internal class CardmarketLiveApi(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cm_api_secure", Context.MODE_PRIVATE)
    private val alias = "de.ahnsen.kartentrainer.cm_key_v1"
    private val base = "https://cardmarketapi.com/api/v1"
    private fun encryptionKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    fun hasKey(): Boolean = prefs.contains("key")

    fun storeKey(input: String) {
        val candidate = input.trim()
        require(candidate.length in 8..512 && candidate.none { it.isWhitespace() }) {
            "Bitte gültigen API-Schlüssel eingeben."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encoded = cipher.doFinal(candidate.toByteArray(StandardCharsets.UTF_8))
        prefs.edit()
            .putString("key", Base64.encodeToString(encoded, Base64.NO_WRAP))
            .putString("nonce", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun removeKey() {
        prefs.edit().remove("key").remove("nonce").apply()
    }

    private fun secret(): String {
        val payload = prefs.getString("key", null) ?: error("Bitte zuerst den API-Schlüssel hinterlegen.")
        val nonce = prefs.getString("nonce", null) ?: error("API-Schlüssel bitte erneut speichern.")
        return try {
            val decipher = Cipher.getInstance("AES/GCM/NoPadding")
            decipher.init(Cipher.DECRYPT_MODE, encryptionKey(),
                GCMParameterSpec(128, Base64.decode(nonce, Base64.DEFAULT)))
            String(decipher.doFinal(Base64.decode(payload, Base64.DEFAULT)), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            throw IllegalStateException("Gespeicherter Schlüssel unlesbar. Bitte entfernen und neu eingeben.")
        }
    }

    private fun encoded(s: String) = URLEncoder.encode(s, "UTF-8")
    private suspend fun call(path: String): JSONObject = withContext(Dispatchers.IO) {
        require(path.startsWith("/") && !path.contains("://"))
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 12000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-API-Key", secret())
            val status = conn.responseCode
            if (status !in 200..299) {
                val message = when (status) {
                    401 -> "API-Schlüssel ungültig oder Testzugang abgelaufen (HTTP 401)."
                    403 -> "API-Zugriff nicht erlaubt (HTTP 403)."
                    429 -> "Tageskontingent aufgebraucht (HTTP 429)."
                    502, 503, 504 -> "Preisdienst momentan nicht erreichbar (HTTP $status)."
                    else -> "Preisdienst meldet HTTP $status."
                }
                throw IllegalStateException(message)
            }
            // Guard against an unexpectedly massive or malformed response.
            val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText().take(300_001) }
            require(body.length <= 300_000) { "Antwort des Preisdienstes ist zu groß." }
            JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun usage(): CardmarketUsage {
        val o = call("/usage")
        return CardmarketUsage(
            o.optString("plan_label").ifBlank { o.optString("plan", "Unbekannt") },
            o.optInt("daily_limit", 0),
            o.optInt("used_today", 0),
            o.optInt("remaining_today", -1),
            o.optLong("plan_expires_at", 0L).takeIf { it > 0L }
        )
    }

    suspend fun search(name: String, set: String, localId: String): List<CardmarketCandidate> {
        val text = (name.trim() + " " + set.trim() + " " + localId.trim()).trim().take(110)
        require(text.length >= 2)
        val result = call("/search?game=pokemon&limit=15&q=" + encoded(text))
        val arr = result.optJSONArray("results") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (!id.matches(Regex("[0-9]{1,12}"))) continue
                if (!item.optString("game").equals("pokemon", true)) continue
                add(CardmarketCandidate(
                    id = id,
                    name = item.optString("name"),
                    code = item.optString("code"),
                    expansion = item.optString("expansion")
                ))
            }
        }
    }

    suspend fun quote(productId: String, language: String, condition: String): CardmarketLiveQuote {
        require(productId.matches(Regex("[0-9]{1,12}"))) { "Cardmarket-Produkt-ID muss eine Zahl sein." }
        val lang = when(language.uppercase(Locale.ROOT)) {
            "DE" -> "german"; "EN" -> "english"; "FR" -> "french"; "IT" -> "italian"
            "ES" -> "spanish"; "JP" -> "japanese"; else -> error("Sprache wird vom Preisdienst nicht unterstützt.")
        }
        val grade = condition.lowercase(Locale.ROOT)
        require(grade in setOf("mt","nm","ex","gd","lp","pl","po"))
        val obj = call("/card/$productId?game=pokemon&language=$lang&condition=$grade")
        require(obj.optString("game").equals("pokemon", true)) { "Antwort ist keine Pokémon-Karte." }
        require(obj.optString("id") == productId) { "Antwort hat eine andere Produkt-ID." }
        require(obj.optString("currency").equals("EUR", true)) { "Nicht unterstützte Währung." }
        val filter = obj.optJSONObject("filter") ?: error("Antwort enthält keine Filterbestätigung.")
        require(filter.optString("language").equals(lang, true)) { "Sprache nicht bestätigt." }
        require(filter.optString("condition").equals(grade, true)) { "Zustand nicht bestätigt." }
        val prices = obj.optJSONObject("prices") ?: error("Keine Preisdaten vorhanden.")
        fun money(o: JSONObject, key: String): Double? =
            if (!o.has(key) || o.isNull(key)) null else MarketValueEngine.clean(o.optDouble(key))
        val floor = money(prices, "from")
        val mean5 = money(prices, "avg5")
        val offers = obj.optJSONArray("listings") ?: JSONArray()
        val exact = buildList {
            for (i in 0 until offers.length()) {
                val offer = offers.optJSONObject(i) ?: continue
                if (!offer.optString("cond").equals(grade, true)) continue
                if (!offer.optString("lang").equals(lang, true)) continue
                money(offer, "price")?.let { add(it) }
            }
        }.sorted()
        // A few verified same-grade offers are more relevant than "EX or better" aggregates.
        val exactMedian = if (exact.size >= 3) exact.take(5).sorted().let { it[it.size / 2] } else null
        val computed = exactMedian ?: mean5 ?: floor
        require(computed != null) { "Keine Angebote für Sprache/Zustand vorhanden." }
        val age = runCatching { Instant.parse(obj.optString("fetched_at")).toEpochMilli() }.getOrNull()
            ?: System.currentTimeMillis()
        return CardmarketLiveQuote(
            productId = productId, price = computed,
            exactGrade = exactMedian != null, exactOffers = exact.size,
            from = floor, avg5 = mean5, available = prices.optInt("available", -1),
            timestamp = age, productName = obj.optString("name"), expansion = obj.optString("expansion"),
            language = language.uppercase(Locale.ROOT), condition = condition.uppercase(Locale.ROOT)
        )
    }
}

internal data class CardmarketUsage(
    val plan: String, val limit: Int, val used: Int, val remaining: Int, val expiry: Long?
)
internal data class CardmarketCandidate(
    val id: String, val name: String, val code: String, val expansion: String
)
internal data class CardmarketLiveQuote(
    val productId: String, val price: Double, val exactGrade: Boolean, val exactOffers: Int,
    val from: Double?, val avg5: Double?, val available: Int, val timestamp: Long,
    val productName: String, val expansion: String, val language: String, val condition: String
)
