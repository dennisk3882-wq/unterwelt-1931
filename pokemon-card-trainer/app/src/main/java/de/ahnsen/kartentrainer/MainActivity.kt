package de.ahnsen.kartentrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KartenCoachTheme {
                KartenCoachApp()
            }
        }
    }
}

private enum class AppScreen(val title: String) {
    HOME("TCG Karten-Coach"),
    SCAN("Karten scannen"),
    COLLECTION("Meine Karten"),
    LEARN("Pokémon TCG lernen"),
    DECK("Deck-Werkstatt"),
    AI("KI-Trainer"),
    PRICES("Sammlerwert")
}

@Composable
private fun KartenCoachTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Color(0xFF2C5F8A),
        onPrimary = Color.White,
        secondary = Color(0xFFB7791F),
        tertiary = Color(0xFF2F855A),
        background = Color(0xFFF7F8FC),
        surface = Color.White,
        surfaceVariant = Color(0xFFE9EEF5),
        error = Color(0xFFB3261E)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KartenCoachApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { CollectionStore(context.applicationContext) }
    val repository = remember { TcgDexRepository() }
    var collection by remember { mutableStateOf(store.load()) }
    var screenName by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    val screen = AppScreen.valueOf(screenName)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (screen != AppScreen.HOME) {
                        IconButton(onClick = { screenName = AppScreen.HOME.name }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        when (screen) {
            AppScreen.HOME -> HomeScreen(
                padding = padding,
                collection = collection,
                onOpen = { screenName = it.name }
            )
            AppScreen.SCAN -> ScanScreen(
                padding = padding,
                repository = repository,
                onAdd = { card, variant ->
                    collection = store.add(collection, card, variant)
                }
            )
            AppScreen.COLLECTION -> CollectionScreen(
                padding = padding,
                collection = collection,
                onChangeQuantity = { entry, delta ->
                    collection = store.changeQuantity(collection, entry, delta)
                }
            )
            AppScreen.LEARN -> LearnScreen(padding)
            AppScreen.DECK -> DeckScreen(padding, collection)
            AppScreen.AI -> AiTrainerScreen(padding, collection)
            AppScreen.PRICES -> PricesScreen(padding, collection)
        }
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    collection: List<CollectionEntry>,
    onOpen: (AppScreen) -> Unit
) {
    val cardCount = collection.sumOf { it.quantity }
    val totalValue = collection.mapNotNull { it.totalEstimatedValue }.sum()
    val standardCount = collection.filter { it.card.isStandardPlayable() }.sumOf { it.quantity }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Mit echten Karten lernen und spielen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Scanne eure Karten, erfahre Set, Regeln und Marktwert, baue daraus ein Deck und trainiere gegen die KI.")
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Stat("Karten", cardCount.toString())
                        Stat("Standard", standardCount.toString())
                        Stat("Wert", if (totalValue > 0.0) totalValue.euro() else "–")
                    }
                }
            }
        }

        item {
            HomeTile(
                icon = Icons.Default.CameraAlt,
                title = "Karten scannen",
                subtitle = "Foto aufnehmen, Karte erkennen, Variante wählen und Sammlung aufbauen.",
                onClick = { onOpen(AppScreen.SCAN) }
            )
        }
        item {
            HomeTile(
                icon = Icons.Default.Collections,
                title = "Meine Karten",
                subtitle = "Alle Karten, Sets, Mengen, Spielbarkeit und Einzelwerte ansehen.",
                onClick = { onOpen(AppScreen.COLLECTION) }
            )
        }
        item {
            HomeTile(
                icon = Icons.Default.School,
                title = "Spielen lernen",
                subtitle = "Kindgerechter Kurs von der ersten Hand bis zu Preisen und Sieg.",
                onClick = { onOpen(AppScreen.LEARN) }
            )
        }
        item {
            HomeTile(
                icon = Icons.Default.Style,
                title = "Deck-Werkstatt",
                subtitle = "Prüft gemischte Sets, 60-Karten-Regel, Viererlimit und aktuelle Zulässigkeit.",
                onClick = { onOpen(AppScreen.DECK) }
            )
        }
        item {
            HomeTile(
                icon = Icons.Default.SmartToy,
                title = "KI-Trainer",
                subtitle = "Strategische KI mit vier Stufen, Zugerklärung, KI-Coach sowie echte Karten gegen den digitalen Gegner.",
                onClick = { onOpen(AppScreen.AI) }
            )
        }
        item {
            HomeTile(
                icon = Icons.Default.Euro,
                title = "Sammlerwert",
                subtitle = "Cardmarket-Schätzwerte nach Normal, Reverse und Holo im Überblick.",
                onClick = { onOpen(AppScreen.PRICES) }
            )
        }

        item {
            Text(
                "Inoffizielle Lern- und Sammlungs-App. Pokémon und zugehörige Marken gehören ihren jeweiligen Rechteinhabern.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HomeTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(12.dp).size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CameraOcrButton(
    label: String,
    modifier: Modifier = Modifier,
    onText: (String) -> Unit,
    onError: (String) -> Unit
) {
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(Unit) {
        onDispose { recognizer.close() }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null) {
            onError("Kein Foto aufgenommen.")
        } else {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (result.text.isBlank()) onError("Auf dem Foto wurde kein lesbarer Kartentext gefunden.")
                    else onText(result.text)
                }
                .addOnFailureListener { error ->
                    onError("Texterkennung fehlgeschlagen: " + (error.message ?: "unbekannter Fehler"))
                }
        }
    }

    Button(
        modifier = modifier,
        onClick = { launcher.launch(null) }
    ) {
        Icon(Icons.Default.CameraAlt, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ScanScreen(
    padding: PaddingValues,
    repository: TcgDexRepository,
    onAdd: (CardData, String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var localId by rememberSaveable { mutableStateOf("") }
    var rawOcr by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CardBrief>>(emptyList()) }
    var selected by remember { mutableStateOf<CardData?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Fotografiere möglichst nur eine Karte und fülle den Bildausschnitt gut aus.") }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        if (query.isBlank() && localId.isBlank()) {
            message = "Gib einen Kartennamen ein oder scanne eine Karte."
            return
        }
        scope.launch {
            loading = true
            message = "Karte wird gesucht …"
            runCatching {
                repository.searchCards(query, localId.takeIf { it.isNotBlank() })
            }.onSuccess {
                results = it
                message = if (it.isEmpty()) {
                    "Keine eindeutige Karte gefunden. Prüfe Name oder Kartennummer und suche erneut."
                } else {
                    it.size.toString() + " mögliche Karte(n) gefunden. Wähle das passende Bild/Set."
                }
            }.onFailure {
                message = "Kartensuche fehlgeschlagen: " + (it.message ?: "Netzwerkfehler")
            }
            loading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("1. Karte fotografieren", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Die App liest Namen und Kartennummer. Danach bestätigst du anhand von Bild und Set die richtige Karte.")
                    Spacer(Modifier.height(12.dp))
                    CameraOcrButton(
                        label = "Kamera öffnen",
                        modifier = Modifier.fillMaxWidth(),
                        onText = { text ->
                            rawOcr = text
                            val guess = OcrParser.parse(text)
                            if (guess.name.isNotBlank()) query = guess.name
                            if (!guess.localId.isNullOrBlank()) localId = guess.localId
                            message = "Text erkannt. Suche nach " + (guess.name.ifBlank { "Kartennummer" }) + " …"
                            runSearch()
                        },
                        onError = { message = it }
                    )
                }
            }
        }

        item {
            Text("Oder manuell suchen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Kartenname, z. B. Pikachu") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = localId,
                onValueChange = { localId = it },
                label = { Text("Kartennummer, z. B. 025 (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { runSearch() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Karte suchen")
            }
        }

        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

        item {
            InfoCard(message)
        }

        if (rawOcr.isNotBlank()) {
            item {
                OutlinedCard {
                    Column(Modifier.padding(12.dp)) {
                        Text("Erkannter Text", fontWeight = FontWeight.SemiBold)
                        Text(
                            rawOcr.take(600),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(results, key = { it.id }) { brief ->
            OutlinedCard(
                onClick = {
                    scope.launch {
                        loading = true
                        runCatching { repository.getCard(brief.id) }
                            .onSuccess { selected = it }
                            .onFailure { message = "Kartendetails konnten nicht geladen werden: " + (it.message ?: "") }
                        loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = brief.imageUrl,
                        contentDescription = brief.name,
                        modifier = Modifier.size(width = 72.dp, height = 100.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(brief.name, fontWeight = FontWeight.Bold)
                        Text("Kartennummer " + brief.localId, style = MaterialTheme.typography.bodyMedium)
                        Text("Antippen für Set, Regeln und Preis", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    selected?.let { card ->
        CardDetailDialog(
            card = card,
            onDismiss = { selected = null },
            onAdd = { variant ->
                onAdd(card, variant)
                message = card.name + " (" + variant + ") wurde zur Sammlung hinzugefügt."
                selected = null
            }
        )
    }
}

@Composable
private fun InfoCard(text: String) {
    OutlinedCard {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CardDetailDialog(
    card: CardData,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var variant by remember(card.id) { mutableStateOf(card.availableVariants().first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(card.name) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
                Text(card.setName + " · #" + card.localId, fontWeight = FontWeight.SemiBold)
                if (card.rarity.isNotBlank()) Text("Seltenheit: " + card.rarity)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (card.isStandardPlayable()) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (card.isStandardPlayable()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(card.legalityText())
                }
                if (!card.regulationMark.isNullOrBlank()) {
                    Text("Regelzeichen: " + card.regulationMark)
                }

                HorizontalDivider()
                Text("Einfach erklärt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(card.friendlyExplanation())

                if (card.attacks.isNotEmpty()) {
                    Text("Attacken", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    card.attacks.forEach { attack ->
                        Text(
                            "• " + attack.name + " · Energie " + attack.cost.size.toString() +
                                (if (attack.damage.isNotBlank()) " · Schaden " + attack.damage else "")
                        )
                        if (attack.effect.isNotBlank()) {
                            Text(attack.effect, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                HorizontalDivider()
                Text("Variante auswählen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    card.availableVariants().forEach { v ->
                        FilterChip(
                            selected = variant == v,
                            onClick = { variant = v },
                            label = { Text(v) }
                        )
                    }
                }

                val price = card.estimatedPrice(variant)
                Text(
                    if (price != null) "Geschätzter Marktwert: " + price.euro() else "Für diese Variante ist aktuell kein Marktpreis verfügbar.",
                    fontWeight = FontWeight.SemiBold
                )
                card.priceLow?.let { Text("Niedriger Cardmarket-Preis: " + it.euro(), style = MaterialTheme.typography.bodySmall) }
                card.priceTrend?.let { Text("Cardmarket-Trend: " + it.euro(), style = MaterialTheme.typography.bodySmall) }
                Text(
                    "Preis ist eine Markt-Schätzung und kein garantierter Verkaufswert. Zustand, Sprache, Auflage und Variante können den echten Wert stark verändern.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(variant) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Zur Sammlung")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}

@Composable
private fun CollectionScreen(
    padding: PaddingValues,
    collection: List<CollectionEntry>,
    onChangeQuantity: (CollectionEntry, Int) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = collection.filter {
        search.isBlank() ||
            it.card.name.contains(search, ignoreCase = true) ||
            it.card.setName.contains(search, ignoreCase = true)
    }
    val total = collection.sumOf { it.quantity }
    val totalValue = collection.mapNotNull { it.totalEstimatedValue }.sum()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryBox("Gesamt", total.toString(), Modifier.weight(1f))
                SummaryBox("Schätzwert", if (totalValue > 0) totalValue.euro() else "–", Modifier.weight(1f))
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Sammlung durchsuchen") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        if (collection.isEmpty()) {
            item {
                InfoCard("Noch keine Karten gespeichert. Öffne „Karten scannen“ und füge zuerst eure Karten hinzu.")
            }
        }
        items(filtered, key = { it.card.id + "-" + it.variant }) { entry ->
            OutlinedCard {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = entry.card.imageUrl,
                        contentDescription = entry.card.name,
                        modifier = Modifier.size(width = 64.dp, height = 90.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.card.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.card.setName + " · #" + entry.card.localId, style = MaterialTheme.typography.bodySmall)
                        Text(entry.variant + " · " + entry.card.legalityText(), style = MaterialTheme.typography.bodySmall)
                        entry.totalEstimatedValue?.let {
                            Text("ca. " + it.euro() + " gesamt", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { onChangeQuantity(entry, 1) }) {
                            Icon(Icons.Default.Add, contentDescription = "Mehr")
                        }
                        Text(entry.quantity.toString(), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onChangeQuantity(entry, -1) }) {
                            Icon(Icons.Default.Remove, contentDescription = "Weniger")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private data class LearnStep(val title: String, val text: String)

@Composable
private fun LearnScreen(padding: PaddingValues) {
    val steps = remember {
        listOf(
            LearnStep(
                "1 · Was ist das Ziel?",
                "Jeder Spieler versucht, die Pokémon des Gegners kampfunfähig zu machen und seine Preiskarten zu nehmen. Normalerweise liegen 6 Preiskarten bereit. Wer alle eigenen Preiskarten genommen hat, gewinnt. Es gibt zusätzlich weitere Siegbedingungen, zum Beispiel wenn der Gegner zu Beginn seines Zuges keine Karte mehr ziehen kann."
            ),
            LearnStep(
                "2 · Was gehört in ein Deck?",
                "Ein normales Pokémon-TCG-Deck hat genau 60 Karten und mindestens ein Basis-Pokémon. Außer Basis-Energien darf eine Karte mit demselben Namen höchstens viermal im Deck sein. Für den Einstieg sind Pokémon, Trainerkarten und ungefähr 12–15 Energien ein gut verständlicher Ausgangspunkt."
            ),
            LearnStep(
                "3 · Dürfen verschiedene Sets zusammen?",
                "Ja. Du musst kein Deck aus nur einer Erweiterung bauen. Karten aus verschiedenen Sets dürfen gemischt werden. Für Turniere ist wichtig, ob die einzelne Karte im gewählten Format zugelassen ist. Im Standardformat 2026 sind seit dem 10. April 2026 Karten mit Regelzeichen H, I und J sowie zukünftigen Zeichen zugelassen. Wiederveröffentlichte ältere Karten können ebenfalls erlaubt sein."
            ),
            LearnStep(
                "4 · Spiel vorbereiten",
                "Mische dein 60-Karten-Deck und ziehe 7 Karten. Lege ein Basis-Pokémon verdeckt als aktives Pokémon aus. Bis zu fünf weitere Basis-Pokémon dürfen auf die Bank. Danach legst du 6 Karten verdeckt als Preiskarten zur Seite."
            ),
            LearnStep(
                "5 · Was darf ich in meinem Zug?",
                "Zu Beginn ziehst du eine Karte. Danach kannst du Basis-Pokémon auf die Bank legen, Pokémon entwickeln, normalerweise eine Energie aus deiner Hand an eines deiner Pokémon anlegen und Trainerkarten spielen. Wenn du angreifst, endet dein Zug."
            ),
            LearnStep(
                "6 · Energie und Attacken",
                "Neben jeder Attacke stehen Energiesymbole. Dein Pokémon muss mindestens diese Energiekosten angelegt haben. Die Energie wird beim normalen Angriff nicht automatisch abgelegt; sie bleibt am Pokémon, sofern der Kartentext nichts anderes sagt."
            ),
            LearnStep(
                "7 · Schaden, Schwäche und K. o.",
                "Der Angriff verursacht den angegebenen Schaden. Schwäche, Resistenz und Karteneffekte können ihn verändern. Erreicht der Schaden die KP eines Pokémon oder übersteigt sie, ist es kampfunfähig. Der Gegner nimmt dann normalerweise eine Preiskarte; besondere Pokémon können mehr Preise geben."
            ),
            LearnStep(
                "8 · Trainerkarten",
                "Items, Unterstützer, Stadien und Pokémon-Ausrüstungen helfen beim Ziehen, Suchen, Heilen oder verändern Regeln. Besonders wichtig: Unterstützer dürfen normalerweise nur einmal pro Zug gespielt werden. Der genaue Kartentext hat Vorrang."
            ),
            LearnStep(
                "9 · Standard, Erweitert und freie Partie",
                "Für Zuhause könnt ihr praktisch jede gemeinsam vereinbarte Karte benutzen. Das Standardformat ist die übliche aktuelle Turnierform und rotiert regelmäßig. Das Erweiterte Format erlaubt deutlich ältere Karten. Die App zeigt deshalb bei jeder Karte die bekannte Zulässigkeit an."
            ),
            LearnStep(
                "10 · Sammlerwert ist nicht Spielstärke",
                "Eine seltene alte Karte kann sehr wertvoll sein, aber im Standardformat nicht mehr gespielt werden. Umgekehrt kann eine günstige Trainerkarte spielerisch extrem wichtig sein. Preis und Spielstärke werden in der App deshalb getrennt angezeigt."
            )
        )
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / steps.size.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(12.dp))
                Text(step.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(step.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { if (index > 0) index-- },
                enabled = index > 0,
                modifier = Modifier.weight(1f)
            ) { Text("Zurück") }
            Button(
                onClick = { if (index < steps.lastIndex) index++ else index = 0 },
                modifier = Modifier.weight(1f)
            ) { Text(if (index < steps.lastIndex) "Weiter" else "Nochmal") }
        }
    }
}

@Composable
private fun DeckScreen(padding: PaddingValues, collection: List<CollectionEntry>) {
    var standardOnly by rememberSaveable { mutableStateOf(true) }
    val result = remember(collection, standardOnly) { DeckBuilder.build(collection, standardOnly) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Gemischte Sets sind erlaubt", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Die App prüft Karten einzeln. Entscheidend ist die Format-Zulässigkeit, nicht ob alle Karten aus derselben Erweiterung stammen.")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = standardOnly,
                    onClick = { standardOnly = true },
                    label = { Text("Standard 2026") }
                )
                FilterChip(
                    selected = !standardOnly,
                    onClick = { standardOnly = false },
                    label = { Text("Freie Partie") }
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryBox("Gesamt", result.totalCards.toString(), Modifier.weight(1f))
                SummaryBox("Pokémon", result.pokemonCount.toString(), Modifier.weight(1f))
                SummaryBox("Trainer", result.trainerCount.toString(), Modifier.weight(1f))
                SummaryBox("Energie", result.energyCount.toString(), Modifier.weight(1f))
            }
        }
        if (result.missingTo60 > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "Es fehlen noch " + result.missingTo60.toString() + " geeignete Karten bis zu einem vollständigen 60-Karten-Deck.",
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(
                        "Aus der Sammlung lässt sich ein 60-Karten-Vorschlag zusammenstellen.",
                        modifier = Modifier.padding(14.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        item {
            Text("Automatischer Vorschlag", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(result.lines, key = { it.category + it.name }) { line ->
            OutlinedCard {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(line.quantity.toString() + "×", fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(line.name, fontWeight = FontWeight.SemiBold)
                        Text(line.category + " · " + line.setNames, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Text("Hinweise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            result.notes.forEach { note ->
                Text("• " + note, modifier = Modifier.padding(vertical = 3.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AiTrainerScreen(padding: PaddingValues, collection: List<CollectionEntry>) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Digital") },
                icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) }
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Echte Karten") },
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) }
            )
        }
        if (tab == 0) {
            AdvancedDigitalBattleScreen(collection, Modifier.weight(1f))
        } else {
            PhysicalBattleScreen(collection, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DigitalBattleScreen(collection: List<CollectionEntry>, modifier: Modifier = Modifier) {
    val pokemon = collection.map { it.card }.filter { it.isPokemon() && it.attacks.isNotEmpty() }.distinctBy { it.id }
    var selectedId by rememberSaveable(pokemon.size) { mutableStateOf(pokemon.firstOrNull()?.id.orEmpty()) }
    var battle by remember { mutableStateOf<TrainingBattle?>(null) }
    var snapshot by remember { mutableStateOf<BattleSnapshot?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Lernkampf mit deinen Karten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Die KI verwendet echte KP, Angriffskosten und Grundschaden der gescannten Karten. Komplexe Sondertexte werden angezeigt; der Lernkampf bildet bewusst zuerst die Kernmechanik ab.")
        }

        if (pokemon.isEmpty()) {
            item { InfoCard("Scanne zuerst mindestens eine Pokémon-Karte mit einer Attacke in deine Sammlung.") }
        } else {
            item {
                Text("Dein Pokémon", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pokemon.forEach { card ->
                        FilterChip(
                            selected = selectedId == card.id,
                            onClick = {
                                selectedId = card.id
                                battle = null
                                snapshot = null
                            },
                            label = { Text(card.name) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val player = pokemon.first { it.id == selectedId }
                        val opponents = pokemon.filter { it.id != player.id }
                        val ai = if (opponents.isNotEmpty()) opponents[Random.nextInt(opponents.size)] else player
                        battle = TrainingBattle(player, ai)
                        snapshot = battle!!.snapshot()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Lernkampf starten")
                }
            }
        }

        snapshot?.let { s ->
            item {
                BattleBoard(s)
            }
            if (!s.finished) {
                item {
                    Button(
                        onClick = { snapshot = battle?.attachEnergy() },
                        enabled = s.playerCanAttach,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1 Energie anlegen")
                    }
                }
                items(s.playerCard.attacks.indices.toList()) { index ->
                    val attack = s.playerCard.attacks[index]
                    OutlinedButton(
                        onClick = { snapshot = battle?.attack(index) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Angriff: " + attack.name +
                                " · Kosten " + maxOf(1, attack.cost.size).toString() +
                                " · Schaden " + attack.baseDamage.toString()
                        )
                    }
                }
            } else {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Text("Gewinner: " + s.winner.orEmpty(), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Text("Spielprotokoll", fontWeight = FontWeight.Bold)
                s.log.takeLast(10).reversed().forEach {
                    Text("• " + it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun BattleBoard(s: BattleSnapshot) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BattlePokemonCard(
            title = "Du",
            card = s.playerCard,
            hp = s.playerHp,
            energy = s.playerEnergy,
            modifier = Modifier.weight(1f)
        )
        BattlePokemonCard(
            title = "KI",
            card = s.aiCard,
            hp = s.aiHp,
            energy = s.aiEnergy,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BattlePokemonCard(title: String, card: CardData, hp: Int, energy: Int, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold)
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                modifier = Modifier.height(150.dp).fillMaxWidth()
            )
            Text(card.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("KP " + hp.toString() + " · Energie " + energy.toString())
        }
    }
}

@Composable
private fun PhysicalBattleScreen(collection: List<CollectionEntry>, modifier: Modifier = Modifier) {
    val pokemon = collection.map { it.card }.filter { it.isPokemon() && it.attacks.isNotEmpty() }.distinctBy { it.id }
    var battle by remember { mutableStateOf<TrainingBattle?>(null) }
    var snapshot by remember { mutableStateOf<BattleSnapshot?>(null) }
    val coachLog = remember { mutableStateListOf<String>() }
    var status by remember { mutableStateOf("Scanne zuerst dein aktives Pokémon. Es muss bereits in „Meine Karten“ gespeichert sein.") }

    fun handleScanned(text: String) {
        val card = bestCollectionMatch(text, collection)
        if (card == null) {
            status = "Die Karte konnte nicht eindeutig deiner Sammlung zugeordnet werden. Scanne sie zuerst unter „Karten scannen“."
            return
        }

        val current = battle
        if (current == null) {
            if (!card.isPokemon() || card.attacks.isEmpty()) {
                status = "Für den Start brauche ich dein aktives Pokémon. Erkannt wurde: " + card.name
                return
            }
            val opponents = pokemon.filter { it.id != card.id }
            val ai = if (opponents.isNotEmpty()) opponents[Random.nextInt(opponents.size)] else card
            val newBattle = TrainingBattle(card, ai)
            battle = newBattle
            snapshot = newBattle.snapshot()
            status = card.name + " ist aktiv. Die KI tritt mit " + ai.name + " an."
            coachLog.add("Aktives echtes Pokémon erkannt: " + card.name)
            return
        }

        when {
            card.isEnergy() -> {
                snapshot = current.attachEnergy()
                status = "Energie erkannt und für diesen Lernzug verbucht."
                coachLog.add("Energie gescannt: " + card.name)
            }
            card.isTrainer() -> {
                status = "Trainerkarte erkannt: " + card.name + ". " + card.effect.orEmpty()
                coachLog.add("Trainerkarte: " + card.name + " – " + card.effect.orEmpty())
            }
            card.isPokemon() -> {
                status = card.name + " erkannt. Nutze die Karte physisch als Bank-Pokémon oder Entwicklung; der Kern-Lernkampf lässt das aktive Pokémon unverändert."
                coachLog.add("Weiteres Pokémon gescannt: " + card.name)
            }
            else -> status = card.name + " erkannt."
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Echte Karten gegen die KI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Lege deine echten Karten auf den Tisch. Die Kamera erkennt Karten aus deiner gespeicherten Sammlung; Energie wird verbucht und die KI führt nach deinem Angriff automatisch ihren Zug aus.")
                    Spacer(Modifier.height(12.dp))
                    CameraOcrButton(
                        label = if (battle == null) "Aktives Pokémon scannen" else "Ausgespielte Karte scannen",
                        modifier = Modifier.fillMaxWidth(),
                        onText = { handleScanned(it) },
                        onError = { status = it }
                    )
                }
            }
        }

        item { InfoCard(status) }

        snapshot?.let { s ->
            item { BattleBoard(s) }
            if (!s.finished) {
                item {
                    Text("Wenn du mit der echten Karte angreifst, tippe hier die verwendete Attacke an. Danach macht die KI ihren Zug.", style = MaterialTheme.typography.bodyMedium)
                }
                items(s.playerCard.attacks.indices.toList()) { index ->
                    val attack = s.playerCard.attacks[index]
                    Button(
                        onClick = { snapshot = battle?.attack(index) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(attack.name + " · " + attack.baseDamage.toString() + " Schaden")
                    }
                }
            } else {
                item {
                    Text("Kampf beendet · Gewinner: " + s.winner.orEmpty(), fontWeight = FontWeight.Bold)
                    OutlinedButton(
                        onClick = {
                            battle = null
                            snapshot = null
                            status = "Neuer Kampf: Scanne dein aktives Pokémon."
                            coachLog.clear()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Neuen Kampf starten") }
                }
            }
            item {
                Text("Letzte Hinweise", fontWeight = FontWeight.Bold)
                (coachLog.takeLast(5) + s.log.takeLast(5)).takeLast(10).reversed().forEach {
                    Text("• " + it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PricesScreen(padding: PaddingValues, collection: List<CollectionEntry>) {
    val sorted = collection.sortedByDescending { it.totalEstimatedValue ?: -1.0 }
    val total = sorted.mapNotNull { it.totalEstimatedValue }.sum()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Geschätzter Sammlungswert", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (total > 0) total.euro() else "Noch keine Preisdaten")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Die App nutzt verfügbare Cardmarket-Marktdaten. Zustand, Sprache, Druckvariante und tatsächliche Verkäufe können deutlich abweichen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        items(sorted, key = { it.card.id + it.variant }) { entry ->
            OutlinedCard {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = entry.card.imageUrl,
                        contentDescription = entry.card.name,
                        modifier = Modifier.size(width = 58.dp, height = 82.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.card.name, fontWeight = FontWeight.Bold)
                        Text(entry.card.setName + " · " + entry.variant, style = MaterialTheme.typography.bodySmall)
                        Text(
                            entry.card.estimatedPrice(entry.variant)?.let { "ca. " + it.euro() + " je Karte" }
                                ?: "Kein Marktpreis verfügbar",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        entry.totalEstimatedValue?.euro() ?: "–",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
