# GameDeck v15: Native quality-of-life upgrade on top of the v14 Kotlin Activity.
from pathlib import Path
import sys

root=Path(sys.argv[1])
p=root/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck/GameDeckActivity.kt"
s=p.read_text(encoding="utf-8")
def swap(before, after, reason):
    global s
    count=s.count(before)
    if count!=1:
        raise RuntimeError(f"{reason}: expected one match, found {count}")
    s=s.replace(before, after, 1)

# Preserve home filters across launches, rather than resetting on every Activity start.
swap(
    "        super.onCreate(savedInstanceState)\n        statusProvider = DeviceStatusProvider(this)",
    """        super.onCreate(savedInstanceState)
        favoritesOnly = libraryPrefs.getBoolean("filter_favorites_only", false)
        sortRecent = libraryPrefs.getBoolean("filter_sort_recent", false)
        statusProvider = DeviceStatusProvider(this)""",
    "filter restore"
)
swap(
    'filters.addView(secondaryButton(if (favoritesOnly) "★ Favoriten" else "☆ Alle Spiele") { favoritesOnly = !favoritesOnly; renderHome() }',
    'filters.addView(secondaryButton(if (favoritesOnly) "★ Favoriten" else "☆ Alle Spiele") { favoritesOnly = !favoritesOnly; libraryPrefs.edit().putBoolean("filter_favorites_only", favoritesOnly).apply(); renderHome() }',
    "favorites persistence"
)
swap(
    'filters.addView(secondaryButton(if (sortRecent) "Sortierung: Zuletzt" else "Sortierung: A–Z") { sortRecent = !sortRecent; renderHome() }',
    'filters.addView(secondaryButton(if (sortRecent) "Sortierung: Zuletzt" else "Sortierung: A–Z") { sortRecent = !sortRecent; libraryPrefs.edit().putBoolean("filter_sort_recent", sortRecent).apply(); renderHome() }',
    "sort persistence"
)
# Collapse the entire games header with the list.  ENTER_ALWAYS previously
# caused the header to slide back over the library before reaching the top.
swap(
    "AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP",
    "AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP",
    "header scroll"
)
# A console-style view means fewer, larger game covers on the phone. Large
# screen support remains responsive and preserves the native Eden game list.
swap(
    "layoutManager = GridLayoutManager(this@GameDeckActivity, if (resources.configuration.screenWidthDp >= 600) 5 else if (resources.configuration.screenWidthDp < 360) 2 else 3)",
    """layoutManager = GridLayoutManager(
                this@GameDeckActivity,
                if (resources.configuration.screenWidthDp >= 600) 5
                else if (resources.configuration.screenWidthDp < 360) 2
                else if (getSharedPreferences("gamedeck", MODE_PRIVATE).getBoolean("large_covers", false)) 2
                else 3
            )""",
    "library columns"
)
# Show real per-title launch history, without a redundant 'last played' panel
# on the start screen.
swap(
    '        root.addView(head)\n\n        root.addView(primaryButton("Spiel starten")',
    """        root.addView(head)
        val playedCount = libraryPrefs.getInt("starts_" + gameKey(game), 0)
        if (playedCount > 0) {
            val playedAt = lastPlayed(game)
            val lastDate = if (playedAt > 0L) java.text.DateFormat.getDateInstance(
                java.text.DateFormat.MEDIUM
            ).format(java.util.Date(playedAt)) else "unbekannt"
            root.addView(infoPanel("Meine Spielstatistik", "Starts: " + playedCount + " · Zuletzt: " + lastDate))
        }

        root.addView(primaryButton("Spiel starten")""",
    "detail history"
)
swap(
    "        mainHandler.postDelayed({\n            if (GameDeckRuntime.launchPending)",
    """        libraryPrefs.edit().putInt(
            "starts_" + gameKey(game),
            (libraryPrefs.getInt("starts_" + gameKey(game), 0) + 1).coerceAtMost(999999)
        ).apply()
        mainHandler.postDelayed({
            if (GameDeckRuntime.launchPending)""",
    "launch count"
)
# The same existing main navigation is retained; one compact option in Mehr
# changes the card size rather than adding a second overlapping game section.
swap(
    '        root.addView(sectionTitle("Mehr"))',
    """        root.addView(sectionTitle("Mehr"))
        root.addView(actionCard(
            "Bibliotheksansicht",
            if (getSharedPreferences("gamedeck", MODE_PRIVATE).getBoolean("large_covers", false))
                "Große Spielcover (2 pro Reihe)"
            else "Standard-Spielcover (3 pro Reihe)"
        ) {
            val prefs = getSharedPreferences("gamedeck", MODE_PRIVATE)
            prefs.edit().putBoolean("large_covers", !prefs.getBoolean("large_covers", false)).apply()
            renderMore()
        })""",
    "library view toggle"
)
# Offer native document-based backup under the existing maintenance entry,
# instead of adding a second duplicate backup menu elsewhere.
swap(
    """        root.addView(actionCard("Sicherung & Version", "Konfiguration sichern, wiederherstellen und Core-Stand prüfen") {
            startActivity(Intent(this, GameDeckTechnicalActivity::class.java).putExtra("section", "maintenance"))
        })""",
    """        root.addView(actionCard("Sicherung & Version", "Lokale GameDeck-Daten und technische Wartung") {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Sicherung & Version")
                .setItems(arrayOf("GameDeck-Daten exportieren", "GameDeck-Daten importieren", "Core & Wartung")) { _, choice ->
                    when (choice) {
                        0 -> backupCreate.launch("GameDeck-S26-Einstellungen.json")
                        1 -> backupOpen.launch(arrayOf("application/json"))
                        2 -> startActivity(Intent(this, GameDeckTechnicalActivity::class.java).putExtra("section", "maintenance"))
                    }
                }.show()
        })""",
    "maintenance consolidation"
)
swap(
    "    override fun onCreate(savedInstanceState: Bundle?) {",
    """    private val backupCreate = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) exportLocalSettings(uri) }

    private val backupOpen = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importLocalSettings(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {""",
    "backup picker"
)

insert= r'''
    // Android Storage Access Framework: user-chosen JSON document, no cloud.
    // Only the GameDeck library and launcher preferences are included:
    // NOT NAND, game ROMs, product keys or emulator save files.
    private fun exportLocalSettings(uri: Uri) {
        runCatching {
            val data = org.json.JSONObject()
            data.put("format", 1)
            data.put("app", "GameDeck S26")
            data.put("library", org.json.JSONObject(libraryPrefs.all))
            data.put("launcher", org.json.JSONObject(getSharedPreferences("gamedeck", MODE_PRIVATE).all))
            contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(data.toString(2).toByteArray(Charsets.UTF_8))
            } ?: error("Zieldatei konnte nicht geöffnet werden")
        }.onSuccess {
            Toast.makeText(this, "GameDeck-Sicherung gespeichert", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "Sicherung fehlgeschlagen: " + it.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun importLocalSettings(uri: Uri) {
        runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Sicherungsdatei ist nicht lesbar")
            require(bytes.size in 2..262144) { "Ungültige Dateigröße" }
            val json = org.json.JSONObject(String(bytes, Charsets.UTF_8))
            require(json.optInt("format", -1) == 1 && json.optString("app") == "GameDeck S26") {
                "Kein unterstütztes GameDeck-Backup"
            }
            val library = json.optJSONObject("library") ?: error("Bibliotheksdaten fehlen")
            val launcher = json.optJSONObject("launcher") ?: error("App-Einstellungen fehlen")
            // Never clear existing data or blindly import arbitrary preference keys.
            val libEdit = libraryPrefs.edit()
            val itLib = library.keys()
            while (itLib.hasNext()) {
                val key = itLib.next()
                if (key != "known_files" && key != "filter_favorites_only" &&
                    key != "filter_sort_recent" && !key.startsWith("favorite_") &&
                    !key.startsWith("played_") && !key.startsWith("starts_")) continue
                when (val value = library.get(key)) {
                    is Boolean -> libEdit.putBoolean(key, value)
                    is String -> libEdit.putString(key, value)
                    is Int -> if (key.startsWith("starts_")) libEdit.putInt(key, value)
                        else libEdit.putLong(key, value.toLong())
                    is Long -> if (key.startsWith("starts_")) libEdit.putInt(key, value.coerceIn(0L, 999999L).toInt())
                        else libEdit.putLong(key, value)
                }
            }
            libEdit.apply()
            val appPrefs = getSharedPreferences("gamedeck", MODE_PRIVATE)
            val appEdit = appPrefs.edit()
            val itApp = launcher.keys()
            while (itApp.hasNext()) {
                val key = itApp.next()
                if (key == "external_display" || key == "large_covers") {
                    val value = launcher.opt(key)
                    if (value is Boolean) appEdit.putBoolean(key, value)
                }
            }
            appEdit.apply()
            favoritesOnly = libraryPrefs.getBoolean("filter_favorites_only", false)
            sortRecent = libraryPrefs.getBoolean("filter_sort_recent", false)
        }.onSuccess {
            renderHome()
            Toast.makeText(this, "GameDeck-Einstellungen wiederhergestellt", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "Import fehlgeschlagen: " + it.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }

'''
swap("    private fun externalModeText(): String {", insert + "    private fun externalModeText(): String {", "backup methods")
p.write_text(s,encoding="utf-8")
print("GameDeck v15 Kotlin patch applied; source size:",len(s))
