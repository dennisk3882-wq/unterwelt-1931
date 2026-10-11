package de.ahnsen.kartentrainer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun CardmarketKeyPanel(api: CardmarketLiveApi, hasKey: Boolean, onKeyChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var usage by remember { mutableStateOf<CardmarketUsage?>(null) }
    var loading by remember { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, KidPalette.Ocean.copy(alpha = 0.55f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔐 Live-Marktpreise (optional)", fontWeight = FontWeight.ExtraBold)
            Text(if (hasKey) "API-Schlüssel ist lokal verschlüsselt gespeichert."
                else "Zusätzliche Preisangebote für deutsche Karten über eigenen API-Schlüssel.",
                style = MaterialTheme.typography.bodySmall)
            Text("Inoffizielle Drittanbieter-API cardmarketapi.com (nicht Cardmarket selbst). Testzugang laut Anbieter: 3 Tage, bis zu 10 Abfragen/Tag; danach möglicherweise kostenpflichtig.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
                Text(if (open) "API-Einstellungen schließen ▲" else "🔑 API-Zugang einrichten ▼")
            }
            if (open) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.cardmarketapi.com/panel")))
                }) { Text("Testschlüssel beim Anbieter holen ↗") }
                OutlinedTextField(value = draft, onValueChange = { draft = it.take(512) },
                    label = { Text("API-Schlüssel (verdeckt)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = draft.isNotBlank() && !loading, onClick = {
                    try {
                        api.storeKey(draft)
                        draft = ""
                        status = "Schlüssel verschlüsselt gespeichert."
                        onKeyChange(true)
                        usage = null
                    } catch (e: Exception) {
                        status = e.message ?: "Speichern nicht möglich."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("🔒 Schlüssel sicher speichern") }
                if (hasKey) {
                    OutlinedButton(enabled = !loading, onClick = {
                        scope.launch {
                            loading = true
                            try {
                                usage = api.usage()
                                status = "Verbindung erfolgreich."
                            } catch (e: Exception) {
                                status = e.message ?: "API-Verbindung fehlgeschlagen."
                            } finally { loading = false }
                        }
                    }) { Text(if (loading) "Prüfe Zugang …" else "Kontingent prüfen (kostenlose Abfrage)") }
                    usage?.let { u ->
                        Text("Tarif: " + u.plan + " · Heute " + u.used + "/" + u.limit +
                            (if (u.remaining >= 0) " · " + u.remaining + " übrig" else ""),
                            style = MaterialTheme.typography.bodySmall)
                        u.expiry?.let { expires ->
                            Text("Tarifende: " + java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY)
                                .format(java.util.Date(expires)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(enabled = !loading, onClick = {
                        api.removeKey()
                        onKeyChange(false)
                        usage = null
                        draft = ""
                        status = "API-Schlüssel entfernt."
                    }) { Text("Schlüssel vom Gerät entfernen") }
                }
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun CardmarketPricePanel(
    entry: CollectionEntry, hasKey: Boolean,
    api: CardmarketLiveApi, onChange: (CollectionEntry) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember(entry.card.id) {
        mutableStateOf(listOf(entry.card.name, entry.card.setName, entry.card.localId)
            .filter { it.isNotBlank() }.joinToString(" "))
    }
    var productId by remember(entry.card.id, entry.variant, entry.language, entry.condition) {
        mutableStateOf(entry.apiLiveProductId ?: "")
    }
    var matches by remember(entry.card.id, entry.language, entry.condition) {
        mutableStateOf<List<CardmarketCandidate>>(emptyList())
    }
    var approved by remember(entry.card.id, entry.variant, productId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    OutlinedCard(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = KidPalette.SoftBlue),
        border = BorderStroke(1.dp, KidPalette.Ocean.copy(alpha = 0.55f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("🇩🇪 Gefilterte Live-Preise", fontWeight = FontWeight.ExtraBold)
            Text("Angebote für " + entry.language + " · Zustand " + entry.condition +
                " oder besser · " + entry.variant + ". Genaue Kartenausgabe prüfen.",
                style = MaterialTheme.typography.bodySmall)
            if (!hasKey) {
                Text("Zum Abrufen zuerst den optionalen Schlüssel oben hinterlegen.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                OutlinedTextField(value = query, onValueChange = { query = it.take(110) },
                    label = { Text("Karte, Set, Nummer") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedButton(enabled = !busy && query.length >= 2, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            matches = api.search(query, "", "")
                            status = if (matches.isEmpty())
                                "Kein Treffer. Alternativ eine Cardmarket-Produkt-ID eintragen."
                                else matches.size.toString() + " Produkte gefunden. Ausgabe und Variante prüfen."
                        } catch (e: Exception) {
                            status = e.message ?: "Suche fehlgeschlagen."
                        } finally { busy = false }
                    }
                }) { Text("🔎 Produkt suchen (verbraucht eventuell Kontingent)") }
                matches.take(12).forEach { candidate ->
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        productId = candidate.id
                        approved = false
                        status = "Produkt " + candidate.id + " ausgewählt. Bitte kontrollieren."
                    }) {
                        Text((candidate.name + " · " + candidate.code + " · " + candidate.expansion)
                            .take(110), maxLines = 3)
                    }
                }
                OutlinedTextField(value = productId,
                    onValueChange = {
                        productId = it.filter { ch -> ch.isDigit() }.take(12)
                        approved = false
                    },
                    label = { Text("Cardmarket-Produkt-ID") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = approved, onCheckedChange = { approved = it })
                    Text("Set, Nummer und Druckvariante stimmen überein.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    enabled = !busy && approved && productId.matches(Regex("[0-9]{1,12}")),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val quote = api.quote(productId, entry.language, entry.condition)
                                onChange(entry.copy(
                                    apiLivePriceEur = quote.price,
                                    apiLiveProductId = quote.productId,
                                    apiLiveLanguage = entry.language,
                                    apiLiveCondition = entry.condition,
                                    apiLiveVariant = entry.variant,
                                    apiLiveUpdatedAt = quote.timestamp,
                                    apiLiveExactGrade = quote.exactGrade,
                                    apiLiveOffers = quote.exactOffers
                                ))
                                status = "Angebots-Richtwert " + quote.price.euro() + " gespeichert. " +
                                    (if (quote.exactGrade) "Exakte Zustandsangebote." else "Mindestzustand oder besser.")
                            } catch (e: Exception) {
                                status = e.message ?: "Preisabfrage fehlgeschlagen."
                            } finally { busy = false }
                        }
                    }
                ) { Text(if (busy) "Lade Angebote …" else "📈 Preis abrufen (1 Abfrage)") }
            }
            if (entry.apiLivePriceEur != null) {
                Text("Live-Richtwert: " + entry.apiLivePriceEur.euro() + " · " +
                    (if (entry.apiLiveExactGrade) "exakter Zustand" else "Mindestzustand oder besser"),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val age = MarketValueEngine.ageDays(entry.apiLiveUpdatedAt)
                Text(if (age != null) "Stand: vor " + age + " Tagen · nach 7 Tagen kein bevorzugter Wert mehr"
                    else "Datenalter unklar", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    onChange(entry.copy(
                        apiLivePriceEur = null, apiLiveProductId = null,
                        apiLiveLanguage = null, apiLiveCondition = null,
                        apiLiveVariant = null, apiLiveUpdatedAt = null,
                        apiLiveExactGrade = false, apiLiveOffers = 0
                    ))
                    productId = ""
                    approved = false
                    status = "Gespeicherter Live-Richtwert entfernt."
                }) { Text("Live-Preis entfernen") }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
