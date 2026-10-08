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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
    PRICES("Sammlerwert"),
    TWO_PLAYER("2-Spieler-Helfer"),
    SETTINGS("Profile & Backup")
}

@Composable
private fun KartenCoachTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = KidPalette.Ocean,
        onPrimary = Color.White,
        primaryContainer = KidPalette.SoftBlue,
        onPrimaryContainer = KidPalette.Ink,
        secondary = KidPalette.Purple,
        onSecondary = Color.White,
        secondaryContainer = KidPalette.SoftPurple,
        onSecondaryContainer = KidPalette.Ink,
        tertiary = KidPalette.Leaf,
        onTertiary = Color.White,
        tertiaryContainer = KidPalette.SoftGreen,
        onTertiaryContainer = KidPalette.Ink,
        background = Color.Transparent,
        onBackground = KidPalette.Ink,
        surface = Color(0xFFFDFDFF),
        onSurface = KidPalette.Ink,
        surfaceVariant = Color(0xFFF0F3FA),
        onSurfaceVariant = Color(0xFF4D5870),
        error = Color(0xFFD73A49)
    )
    MaterialTheme(
        colorScheme = scheme,
        shapes = KidShapes,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KartenCoachApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val legacyStore = remember { CollectionStore(context.applicationContext) }
    val localStore = remember { LocalAppStore(context.applicationContext) }
    val repository = remember { TcgDexRepository(context.applicationContext) }
    var profiles by remember { mutableStateOf(localStore.profiles()) }
    var activeProfileId by rememberSaveable { mutableStateOf(localStore.activeProfileId()) }

    fun loadCollectionForProfile(id: String): List<CollectionEntry> {
        val saved = localStore.loadCollection(id)
        if (saved.isNotEmpty()) return saved
        if (id == "default") {
            val legacy = legacyStore.load()
            if (legacy.isNotEmpty()) {
                localStore.saveCollection(id, legacy)
                return legacy
            }
        }
        return emptyList()
    }

    var collection by remember(activeProfileId) {
        mutableStateOf(loadCollectionForProfile(activeProfileId))
    }
    var playerStats by remember(activeProfileId) {
        mutableStateOf(localStore.stats(activeProfileId))
    }

    LaunchedEffect(activeProfileId, collection.map { it.card.id }) {
        CardImageCache.prefetch(context.applicationContext, collection.map { it.card })
    }
    var screenName by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    val screen = AppScreen.valueOf(screenName)

    fun replaceCollection(entries: List<CollectionEntry>) {
        collection = entries
        localStore.saveCollection(activeProfileId, entries)
    }

    fun changeProfile(id: String) {
        localStore.setActiveProfile(id)
        activeProfileId = id
        collection = loadCollectionForProfile(id)
        playerStats = localStore.stats(id)
    }

    KidScreenBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            screen.title,
                            fontWeight = FontWeight.ExtraBold,
                            color = KidPalette.Ink
                        )
                    },
                    navigationIcon = {
                        if (screen != AppScreen.HOME) {
                            IconButton(onClick = { screenName = AppScreen.HOME.name }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
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
                onAdd = { card, variant, scannedAt ->
                    val index = collection.indexOfFirst {
                        it.card.id == card.id &&
                            it.variant == variant &&
                            it.language == "DE" &&
                            it.condition == "NM"
                    }
                    val updated = collection.toMutableList()
                    if (index >= 0) {
                        updated[index] = updated[index].copy(
                            quantity = updated[index].quantity + 1,
                            scanVerified = true,
                            lastScannedAt = scannedAt
                        )
                    } else {
                        updated += CollectionEntry(
                            card = card,
                            quantity = 1,
                            variant = variant,
                            language = "DE",
                            condition = "NM",
                            scanVerified = true,
                            lastScannedAt = scannedAt
                        )
                    }
                    replaceCollection(updated)
                    playerStats = playerStats.copy(scannedCards = playerStats.scannedCards + 1)
                    localStore.saveStats(activeProfileId, playerStats)
                }
            )
            AppScreen.COLLECTION -> AdvancedCollectionScreen(
                padding = padding,
                collection = collection,
                repository = repository,
                onChangeQuantity = { entry, delta ->
                    val updated = collection.toMutableList()
                    val index = updated.indexOfFirst {
                        it.card.id == entry.card.id &&
                            it.variant == entry.variant &&
                            it.language == entry.language &&
                            it.condition == entry.condition
                    }
                    if (index >= 0) {
                        val next = updated[index].quantity + delta
                        if (next <= 0) updated.removeAt(index)
                        else updated[index] = updated[index].copy(quantity = next)
                    }
                    replaceCollection(updated)
                },
                onReplaceCollection = { replaceCollection(it) }
            )
            AppScreen.LEARN -> AdvancedLearnScreen(padding)
            AppScreen.DECK -> AdvancedDeckWorkshopScreen(
                padding = padding,
                collection = collection,
                store = localStore,
                profileId = activeProfileId
            )
            AppScreen.AI -> AiTrainerScreen(
                padding = padding,
                collection = collection,
                onGameFinished = { winner ->
                    val next = playerStats.copy(
                        games = playerStats.games + 1,
                        wins = playerStats.wins + if (winner == "Du") 1 else 0,
                        losses = playerStats.losses + if (winner == "KI") 1 else 0
                    )
                    playerStats = next
                    localStore.saveStats(activeProfileId, next)
                }
            )
            AppScreen.PRICES -> PricesScreen(padding, collection)
            AppScreen.TWO_PLAYER -> TwoPlayerHelperScreen(collection, Modifier.padding(padding))
            AppScreen.SETTINGS -> LocalSettingsScreen(
                padding = padding,
                store = localStore,
                profiles = profiles,
                activeProfileId = activeProfileId,
                collection = collection,
                stats = playerStats,
                onProfilesChanged = {
                    profiles = localStore.profiles()
                },
                onActiveProfileChanged = { id ->
                    profiles = localStore.profiles()
                    changeProfile(id)
                },
                onImported = {
                    collection = loadCollectionForProfile(activeProfileId)
                    playerStats = localStore.stats(activeProfileId)
                }
            )
            }
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
    val homeContext = androidx.compose.ui.platform.LocalContext.current
    val familySettings = remember { FamilyLocalStore(homeContext.applicationContext).settings() }
    val hideValues = familySettings.childMode && familySettings.priceGateEnabled
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(KidPalette.HeroA, KidPalette.HeroB, KidPalette.HeroC)
                        )
                    )
                    .padding(22.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.20f)
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(12.dp).size(30.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Dein Karten-Abenteuer",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "Scannen · Sammeln · Lernen · Spielen",
                                color = Color.White.copy(alpha = 0.90f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Nur deine wirklich gescannten Karten kommen ins Spiel. Danach zeigt die App automatisch das saubere Originalkartenbild.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FunStat("🃏", "Karten", cardCount.toString(), Modifier.weight(1f))
                        FunStat("⚡", "Standard", standardCount.toString(), Modifier.weight(1f))
                        FunStat("⭐", "Wert", if (hideValues) "🔒" else if (totalValue > 0.0) totalValue.euro() else "–", Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Text(
                "Was möchtest du machen?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = KidPalette.Ink
            )
        }

        item {
            HomeTile(
                icon = Icons.Default.CameraAlt,
                title = "Karten scannen",
                subtitle = "Foto aufnehmen, Karte erkennen, Variante wählen und Sammlung aufbauen.",
                accent = KidPalette.Ocean,
                onClick = { onOpen(AppScreen.SCAN) }
            )
        }
        item {
            HomeTile(
                icon = Icons.Default.Collections,
                title = "Meine Karten",
                subtitle = "Alle Karten, Sets, Mengen, Spielbarkeit und Einzelwerte ansehen.",
                accent = KidPalette.Purple,
                onClick = { onOpen(AppScreen.COLLECTION) }
            )
        }
        item {
            HomeTile(
                icon = Icons.Default.School,
                title = "Spielen lernen",
                subtitle = "Kindgerechter Kurs von der ersten Hand bis zu Preisen und Sieg.",
                accent = KidPalette.Sun,
                onClick = { onOpen(AppScreen.LEARN) }
            )
        }
        item {
            HomeTile(
                icon = Icons.Default.Style,
                title = "Deck-Werkstatt",
                subtitle = "Prüft gemischte Sets, 60-Karten-Regel, Viererlimit und aktuelle Zulässigkeit.",
                accent = KidPalette.Pink,
                onClick = { onOpen(AppScreen.DECK) }
            )
        }
        item {
            HomeTile(
                icon = Icons.Default.SmartToy,
                title = "KI-Trainer",
                subtitle = "60-Karten-Vollspiel, strategische Arena, vier KI-Stufen, Zugerklärung, KI-Coach und echte Karten mit Kamera.",
                accent = KidPalette.Fire,
                onClick = { onOpen(AppScreen.AI) }
            )
        }
        item {
            HomeTile(
                icon = Icons.Default.Euro,
                title = "Sammlerwert",
                subtitle = "Cardmarket-Schätzwerte nach Normal, Reverse und Holo im Überblick.",
                accent = KidPalette.Leaf,
                onClick = { onOpen(AppScreen.PRICES) }
            )
        }

        item {
            HomeTile(
                icon = Icons.Default.Groups,
                title = "2-Spieler-Tischhelfer",
                subtitle = "Zwei echte Spieler: Live-Scan, KP, Energie, Bank, Preise und Zugwechsel lokal verfolgen.",
                accent = Color(0xFF28B6C8),
                onClick = { onOpen(AppScreen.TWO_PLAYER) }
            )
        }

        item {
            HomeTile(
                icon = Icons.Default.Settings,
                title = "Profile & lokales Backup",
                subtitle = "Mehrere Spielerprofile, Statistiken sowie JSON-Export und Restore – ohne Cloud.",
                accent = Color(0xFF67748D),
                onClick = { onOpen(AppScreen.SETTINGS) }
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
private fun FunStat(emoji: String, label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.18f)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(label, color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HomeTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
        border = BorderStroke(2.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = accent
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(14.dp).size(30.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = KidPalette.Ink
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f)) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
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
    onAdd: (CardData, String, Long) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var localId by rememberSaveable { mutableStateOf("") }
    var rawOcr by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CardBrief>>(emptyList()) }
    var selected by remember { mutableStateOf<CardData?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Fotografiere möglichst nur eine Karte und fülle den Bildausschnitt gut aus.") }
    var batchMode by rememberSaveable { mutableStateOf(false) }
    var lastConfidence by rememberSaveable { mutableIntStateOf(0) }
    var cameraCandidateIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastOwnershipScanAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val scanContext = androidx.compose.ui.platform.LocalContext.current

    fun runSearch() {
        cameraCandidateIds = emptySet()
        lastOwnershipScanAt = null
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
                    Text("1. Karten scannen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Live-Kamera mit Autofokus. Im Batchmodus einfach nacheinander Karten in den Rahmen legen.")
                    Spacer(Modifier.height(8.dp))
                    FilterChip(
                        selected = batchMode,
                        onClick = { batchMode = !batchMode },
                        label = { Text(if (batchMode) "Batchmodus aktiv" else "Einzelscan") }
                    )
                    Spacer(Modifier.height(8.dp))
                    SmartCameraScanner(
                        batchMode = batchMode,
                        modifier = Modifier.fillMaxWidth(),
                        onResult = { scan ->
                            rawOcr = scan.text
                            lastConfidence = scan.confidence
                            val primary = scan.guess
                            if (primary.name.isNotBlank()) query = primary.name
                            if (!primary.localId.isNullOrBlank()) localId = primary.localId
                            scope.launch {
                                loading = true
                                val merged = linkedMapOf<String, CardBrief>()
                                val guesses = if (batchMode) scan.guesses.take(5) else listOf(primary)
                                guesses.forEach { guess ->
                                    runCatching {
                                        repository.searchCards(
                                            guess.name,
                                            guess.localId.takeIf { !it.isNullOrBlank() }
                                        )
                                    }.getOrDefault(emptyList()).forEach { brief ->
                                        merged[brief.id] = brief
                                    }
                                }
                                val candidates = merged.values.toList()
                                results = candidates
                                cameraCandidateIds = candidates.map { it.id }.toSet()
                                lastOwnershipScanAt = System.currentTimeMillis()
                                if (batchMode && scan.confidence >= 75 && candidates.size == 1) {
                                    val card = runCatching { repository.getCard(candidates.first().id) }.getOrNull()
                                    if (card != null) {
                                        val variant = card.availableVariants().firstOrNull() ?: "Normal"
                                        CardImageCache.prefetch(scanContext, card)
                                        onAdd(card, variant, lastOwnershipScanAt ?: System.currentTimeMillis())
                                        message = card.name + " automatisch als eigene Karte bestätigt · Sicherheit " + scan.confidence +
                                            "%. Originalbild wurde für Sammlung und Spiel vorgeladen. Nächste Karte einlegen."
                                        results = emptyList()
                                    } else {
                                        message = "Kartendetails konnten nicht geladen werden."
                                    }
                                } else {
                                    message = if (candidates.isEmpty()) {
                                        "Keine eindeutige Karte gefunden · Sicherheit " + scan.confidence + "%."
                                    } else {
                                        candidates.size.toString() + " Treffer · Sicherheit " + scan.confidence + "%. Bitte passende Karte bestätigen."
                                    }
                                }
                                loading = false
                            }
                        },
                        onError = { message = it }
                    )
                    Spacer(Modifier.height(8.dp))
                    PrecisionCardScannerButton(
                        modifier = Modifier.fillMaxWidth(),
                        onText = { text ->
                            rawOcr = text
                            val guess = OcrParser.parse(text)
                            if (guess.name.isNotBlank()) query = guess.name
                            if (!guess.localId.isNullOrBlank()) localId = guess.localId
                            scope.launch {
                                loading = true
                                val candidates = runCatching {
                                    repository.searchCards(
                                        guess.name,
                                        guess.localId.takeIf { !it.isNullOrBlank() }
                                    )
                                }.getOrDefault(emptyList())
                                results = candidates
                                cameraCandidateIds = candidates.map { it.id }.toSet()
                                lastOwnershipScanAt = System.currentTimeMillis()
                                if (candidates.size == 1) {
                                    val card = runCatching { repository.getCard(candidates.first().id) }.getOrNull()
                                    if (card != null) {
                                        CardImageCache.prefetch(scanContext, card)
                                        onAdd(
                                            card,
                                            card.availableVariants().firstOrNull() ?: "Normal",
                                            lastOwnershipScanAt ?: System.currentTimeMillis()
                                        )
                                        message = card.name + " aus Präzisionsscan als eigene Karte bestätigt; Originalbild vorgeladen."
                                        results = emptyList()
                                    } else {
                                        message = "Kartendetails konnten nicht geladen werden."
                                    }
                                } else {
                                    message = if (candidates.isEmpty()) {
                                        "Präzisionsscan: kein eindeutiger Treffer."
                                    } else {
                                        "Präzisionsscan: " + candidates.size + " Treffer – bitte bestätigen."
                                    }
                                }
                                loading = false
                            }
                        },
                        onError = { message = it }
                    )
                    if (lastConfidence > 0) {
                        Text(
                            "Letzte OCR-Sicherheit: " + lastConfidence + "%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Text("Oder manuell nachschlagen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                Text("Karte nur nachschlagen")
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
        val scanVerified = card.id in cameraCandidateIds && lastOwnershipScanAt != null
        CardDetailDialog(
            card = card,
            canAddToCollection = scanVerified,
            onDismiss = { selected = null },
            onAdd = { variant ->
                val scannedAt = lastOwnershipScanAt
                if (scannedAt == null) {
                    message = "Diese Karte muss zuerst mit der Handykamera gescannt werden."
                } else {
                    scope.launch {
                        CardImageCache.prefetch(scanContext, card)
                        onAdd(card, variant, scannedAt)
                        message = card.name + " (" + variant + ") wurde als gescannte eigene Karte zur Sammlung hinzugefügt."
                        selected = null
                    }
                }
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
    canAddToCollection: Boolean,
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

                if (card.abilities.isNotEmpty()) {
                    Text("Fähigkeiten", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    card.abilities.forEach { ability ->
                        val timing = classifyAbilityTiming(ability.effect)
                        val parsed = EffectParser.parse(ability.effect, EffectSourceKind.ABILITY)
                        Text("• " + ability.name + " · " + timing.label, fontWeight = FontWeight.SemiBold)
                        Text(ability.effect, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Effekt-Engine: " + parsed.coveragePercent + "% · " + parsed.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (parsed.fullySupported) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                if (card.attacks.isNotEmpty()) {
                    Text("Attacken", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    card.attacks.forEach { attack ->
                        Text(
                            "• " + attack.name + " · Energie " + attack.cost.size.toString() +
                                (if (attack.damage.isNotBlank()) " · Schaden " + attack.damage else "")
                        )
                        if (attack.effect.isNotBlank()) {
                            val parsed = EffectParser.parse(attack.effect, EffectSourceKind.ATTACK)
                            Text(attack.effect, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Effekt-Engine: " + parsed.coveragePercent + "% · " + parsed.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (parsed.fullySupported) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                            )
                            if (parsed.unsupportedParts.isNotEmpty()) {
                                Text(
                                    "Manuell: " + parsed.unsupportedParts.joinToString(" | "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                if (card.isTrainer() && !card.effect.isNullOrBlank()) {
                    val parsed = EffectParser.parse(card.effect, EffectSourceKind.TRAINER)
                    Text("Trainer-Effekt", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(card.effect)
                    Text(
                        "Effekt-Engine: " + parsed.coveragePercent + "% · " + parsed.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (parsed.fullySupported) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                    )
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
                    if (canAddToCollection) {
                        "✓ Besitz durch Kamerascan bestätigt. Für die App wird das saubere Originalkartenbild verwendet."
                    } else {
                        "Nur nachgeschlagen: Zum Hinzufügen muss diese Karte zuerst mit der Handykamera gescannt werden."
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = if (canAddToCollection) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                )
                Text(
                    "Preis ist eine Markt-Schätzung und kein garantierter Verkaufswert. Zustand, Sprache, Auflage und Variante können den echten Wert stark verändern.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canAddToCollection,
                onClick = { onAdd(variant) }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (canAddToCollection) "Gescannt → zur Sammlung" else "Erst scannen")
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
                "2 · Sammlung oder 60-Karten-Deck?",
                "Deine Sammlung darf beliebig viele Karten aus vielen verschiedenen Sets enthalten. Für eine normale Partie stellt aber jeder Spieler aus seiner Sammlung ein eigenes Deck mit exakt 60 Karten zusammen. Es wird also nicht mit allen gesammelten Karten gleichzeitig gespielt. Das Deck braucht mindestens ein Basis-Pokémon; außer Basis-Energien darf eine Karte mit demselben Namen grundsätzlich höchstens viermal enthalten sein. Du kannst aus einer großen Sammlung natürlich mehrere verschiedene 60-Karten-Decks bauen."
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
private fun AiTrainerScreen(
    padding: PaddingValues,
    collection: List<CollectionEntry>,
    onGameFinished: (String) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Vollspiel") },
                icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) }
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Arena") },
                icon = { Icon(Icons.Default.SmartToy, contentDescription = null) }
            )
            Tab(
                selected = tab == 2,
                onClick = { tab = 2 },
                text = { Text("Echte") },
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) }
            )
        }
        when (tab) {
            0 -> FullGameScreen(
                collection = collection,
                modifier = Modifier.weight(1f),
                onGameFinished = onGameFinished
            )
            1 -> AdvancedDigitalBattleScreen(collection, Modifier.weight(1f))
            else -> PhysicalFullGameScreen(
                collection = collection,
                modifier = Modifier.weight(1f),
                onGameFinished = onGameFinished
            )
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
    val priceContext = androidx.compose.ui.platform.LocalContext.current
    val familyStore = remember { FamilyLocalStore(priceContext.applicationContext) }
    val settings = remember { familyStore.settings() }
    var unlocked by rememberSaveable {
        mutableStateOf(!(settings.childMode && settings.priceGateEnabled && settings.hasPin))
    }
    var pin by rememberSaveable { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    if (!unlocked) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Elternbereich", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Die Sammlerwerte sind im Kindermodus geschützt.")
                }
            }
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(8) },
                label = { Text("Eltern-PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    if (familyStore.verifyPin(pin)) {
                        unlocked = true
                        pinError = ""
                    } else {
                        pinError = "PIN ist nicht korrekt."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sammlerwerte öffnen")
            }
            if (pinError.isNotBlank()) Text(pinError, color = MaterialTheme.colorScheme.error)
        }
        return
    }

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
                            entry.adjustedUnitValue()?.let {
                                "ca. " + it.euro() + " je Karte · " + entry.language + " · " + entry.condition
                            } ?: "Kein Marktpreis verfügbar",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        MarketResearchBlock(entry)
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
