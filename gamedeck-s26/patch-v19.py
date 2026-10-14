from pathlib import Path
import sys
root=Path(sys.argv[1])
folder=root/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"
p=folder/"GameDeckActivity.kt"
s=p.read_text(encoding="utf-8")
def swap(old,new,label):
 global s
 n=s.count(old)
 if n!=1: raise RuntimeError("v19 "+label+" expected once got "+str(n))
 s=s.replace(old,new,1)

swap("    private var activeGame: Game? = null","    private var activeGame: Game? = null\n    private var tvPresentation: GameDeckTvPresentation? = null","presentation member")
swap("    override fun onDestroy() {\n        mainHandler.removeCallbacks(statusTicker)","    override fun onDestroy() {\n        tvPresentation?.dismiss()\n        tvPresentation = null\n        mainHandler.removeCallbacks(statusTicker)","close presentation")
swap('        root.addView(infoPanel("▣ HDMI / Beamer", status.displayText))',
'''        root.addView(infoPanel("▣ HDMI / Beamer", status.displayText))
        root.addView(actionCard(
            if (tvPresentation?.isShowing == true) "Beamer-Bibliothek schließen" else "Beamer-Bibliothek",
            if (status.externalDisplay != null) "Spiele auf einem separaten Bildschirm anzeigen"
            else "Nur mit einem von Android erkannten zweiten Bildschirm"
        ) {
            if (tvPresentation?.isShowing == true) {
                tvPresentation?.dismiss(); tvPresentation = null; renderDevices()
            } else {
                val external = statusProvider.snapshot().externalDisplay
                if (external == null) {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Kein separater Bildschirm erkannt")
                        .setMessage("HDMI-Spiegelung allein stellt Android keinen zweiten Bildschirm bereit. Für eine eigene TV-Oberfläche benötigt GameDeck eine separate Anzeige, etwa über DeX.")
                        .setPositiveButton("OK", null).show()
                } else {
                    runCatching {
                        val panel = GameDeckTvPresentation(this, external, lastGames) { selected ->
                            tvPresentation?.dismiss(); tvPresentation = null
                            launchGame(selected, GameDeckProfileEngine.selectedMode(this, selected))
                        }
                        panel.setOnDismissListener { if (tvPresentation === panel) tvPresentation = null }
                        panel.show()
                        tvPresentation = panel
                    }.onFailure {
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("TV-Oberfläche nicht verfügbar")
                            .setMessage(it.localizedMessage ?: "Das externe Display konnte nicht geöffnet werden.")
                            .setPositiveButton("OK", null).show()
                    }
                }
            }
        })''',"tv menu")
swap('        root.addView(actionCard("Diagnose", "Sitzungsberichte, Logs und Leistungswerte") {\n            startActivity(Intent(this, GameDeckTechnicalActivity::class.java).putExtra("section", "diagnosis"))\n        })',
'''        root.addView(actionCard("Diagnose", "Sitzungsberichte, Logs und Leistungswerte") {
            startActivity(Intent(this, GameDeckTechnicalActivity::class.java).putExtra("section", "diagnosis"))
        })
        root.addView(actionCard("Automatischer Bibliothekstest", "Spieldateien und Startvoraussetzungen prüfen, ohne Spiele zu starten") {
            runLibrarySmokeTest()
        })''',"smoke menu")
swap("    private fun externalModeText(): String {",'''
    private fun runLibrarySmokeTest() {
        val games = lastGames.toList()
        if (games.isEmpty()) {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Bibliothekstest")
                .setMessage("Keine Spiele gefunden. Bitte zuerst einen Spieleordner hinzufügen.")
                .setPositiveButton("OK", null).show()
            return
        }
        val progress = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Vorabprüfung").setMessage("Dateizugriff, Schlüssel, Firmware und Treiber prüfen …")
            .setCancelable(false).create()
        progress.show()
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                val entries = games.take(150).map { game ->
                    game.title to runCatching { GameDeckRuntime.preflight(game) }
                        .getOrElse { listOf("Prüfung fehlgeschlagen: " + it.localizedMessage) }
                }
                buildString {
                    append("GAMEDECK – AUTOMATISCHER BIBLIOTHEKSTEST\\n")
                    append("Spiele insgesamt: ").append(games.size)
                    append("\\nGeprüft: ").append(entries.size)
                    append("\\nMit Hinweisen: ").append(entries.count { it.second.isNotEmpty() })
                    append("\\n\\n")
                    entries.filter { it.second.isNotEmpty() }.forEach { (name, issues) ->
                        append("• ").append(name).append(": ")
                        append(issues.joinToString("; ")).append("\\n")
                    }
                    if (entries.none { it.second.isNotEmpty() })
                        append("Keine Startvoraussetzungsprobleme erkannt.\\n")
                    append("\\nEs wurden keine Spiele gestartet. Spielbild, Ton, FPS und Spiellogik sind damit nicht getestet.")
                }
            }
            if (!isFinishing && !isDestroyed) {
                progress.dismiss()
                androidx.appcompat.app.AlertDialog.Builder(this@GameDeckActivity)
                    .setTitle("Automatischer Bibliothekstest")
                    .setMessage(report).setPositiveButton("Schließen", null)
                    .setNeutralButton("Kopieren") { _, _ ->
                        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .setPrimaryClip(android.content.ClipData.newPlainText("Bibliothekstest", report))
                    }.show()
            }
        }
    }

    private fun externalModeText(): String {''',"smoke method")
p.write_text(s,encoding="utf-8")
(folder/"GameDeckTvPresentation.kt").write_text(r'''// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.gamedeck

import android.app.Activity
import android.app.Presentation
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.yuzu.yuzu_emu.model.Game
import org.yuzu.yuzu_emu.utils.GameIconUtils

/** Real secondary Android display, not HDMI mirroring or a fake preview. */
class GameDeckTvPresentation(
    private val host: Activity,
    display: Display,
    private val games: List<Game>,
    private val launchGame: (Game) -> Unit
) : Presentation(host, display) {
    private fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    private fun shape(bg: Int, line: Int = Color.rgb(43, 79, 119)) =
        GradientDrawable().apply {
            setColor(bg); cornerRadius = dp(15).toFloat(); setStroke(dp(2), line)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.decorView?.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(36), dp(26), dp(36), dp(24))
            setBackgroundColor(Color.rgb(5, 13, 25))
        }
        root.addView(TextView(context).apply {
            text = "🎮  GameDeck S26  ·  Konsolenmodus"
            textSize = 27f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        })
        val mode = display.mode
        root.addView(TextView(context).apply {
            text = "Externer Bildschirm  ·  " + mode.physicalWidth + " × " + mode.physicalHeight +
                " · " + "%.0f".format(java.util.Locale.ROOT, mode.refreshRate) + " Hz"
            textSize = 13f; setTextColor(Color.rgb(139, 173, 210))
            setPadding(0, dp(5), 0, dp(17))
        })
        root.addView(TextView(context).apply {
            text = "DEINE SPIELE"
            textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(15))
        })
        val shelf = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        var first: Button? = null
        games.take(70).forEach { game ->
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(7), dp(7), dp(7), dp(9))
                background = shape(Color.rgb(14, 31, 51))
            }
            val cover = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = game.title
            }
            GameIconUtils.loadGameIcon(game, cover)
            item.addView(cover, LinearLayout.LayoutParams(dp(157), dp(151)))
            val button = Button(context).apply {
                text = game.title; textSize = 13f; isAllCaps = false
                setTextColor(Color.WHITE)
                background = shape(Color.rgb(22, 64, 99))
                setOnClickListener { launchGame(game) }
                setOnFocusChangeListener { _, focused ->
                    background = shape(if (focused) Color.rgb(14, 130, 220)
                        else Color.rgb(22, 64, 99),
                        if (focused) Color.WHITE else Color.rgb(43, 79, 119))
                }
            }
            if (first == null) first = button
            item.addView(button, LinearLayout.LayoutParams(dp(157), dp(72)))
            shelf.addView(item, LinearLayout.LayoutParams(dp(173), dp(250)).apply {
                rightMargin = dp(12)
            })
        }
        if (games.isEmpty()) shelf.addView(TextView(context).apply {
            text = "Keine Spiele eingelesen. Ordner zuerst am Handy hinzufügen."
            textSize = 20f; setTextColor(Color.LTGRAY)
        })
        root.addView(HorizontalScrollView(context).apply { addView(shelf) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(context).apply {
            text = "A / Eingabe: starten  ·  Steuerkreuz: auswählen  ·  B / Zurück: schließen"
            textSize = 14f; setTextColor(Color.rgb(149, 176, 207))
        })
        setContentView(root)
        first?.requestFocus()
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK) {
            dismiss(); return true
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_A && event?.repeatCount == 0) {
            currentFocus?.performClick(); return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
''',encoding="utf-8")
print("GameDeck v19: native Presentation + library preflight")
