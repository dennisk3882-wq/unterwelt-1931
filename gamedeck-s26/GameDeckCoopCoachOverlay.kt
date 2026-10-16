// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.gamedeck

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * On-device cooperative coach preview. It observes OCR and offers cautious
 * suggestions but never injects game input or claims a win. A true P2 agent
 * requires verified second-controller routing and game-specific validation.
 */
object GameDeckCoopCoachOverlay {
    private val handler = Handler(Looper.getMainLooper())
    private var activity = WeakReference<Activity>(null)
    private var root: FrameLayout? = null
    private var card: LinearLayout? = null
    private var chip: TextView? = null
    private var status: TextView? = null
    private var goalInput: EditText? = null
    private var savedGoal = ""
    private var minimized = false
    private var attached = false

    private val poll = object : Runnable {
        override fun run() {
            val screen = activity.get()
            if (screen == null || screen.isFinishing || screen.isDestroyed) {
                detach()
                return
            }
            GameDeckSceneObserver.captureIfDue()
            if (root == null) create(screen)
            root?.visibility = View.VISIBLE
            card?.visibility = if (minimized) View.GONE else View.VISIBLE
            chip?.visibility = if (minimized) View.VISIBLE else View.GONE
            update()
            handler.postDelayed(this, 1800L)
        }
    }

    fun attach(screen: Activity) {
        if (activity.get() === screen && attached) return
        detach()
        activity = WeakReference(screen)
        attached = true
        minimized = false
        savedGoal = screen.getSharedPreferences("gamedeck_coop_coach", Context.MODE_PRIVATE)
            .getString("current_goal", "").orEmpty()
        GameDeckSceneObserver.attach(screen)
        handler.post(poll)
    }

    fun detach() {
        handler.removeCallbacks(poll)
        val view = root
        (view?.parent as? ViewGroup)?.removeView(view)
        root = null
        card = null
        chip = null
        status = null
        goalInput = null
        activity.clear()
        attached = false
        minimized = false
    }

    private fun create(screen: Activity) {
        val decor = screen.window.decorView as? ViewGroup ?: return
        val panel = LinearLayout(screen).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(screen, 11), dp(screen, 8), dp(screen, 11), dp(screen, 8))
            background = GradientDrawable().apply {
                setColor(0xE91B242E.toInt())
                cornerRadius = dp(screen, 12).toFloat()
                setStroke(dp(screen, 1), 0xFF6D8DA8.toInt())
            }
            elevation = dp(screen, 12).toFloat()
        }
        val header = LinearLayout(screen).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(screen).apply {
            text = "GAMEDECK · CO-OP BEGLEITER"
            setTextColor(0xFFB6DCFF.toInt())
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val hide = Button(screen).apply {
            text = "−"
            minWidth = dp(screen, 36)
            minimumWidth = dp(screen, 36)
            minHeight = dp(screen, 30)
            minimumHeight = dp(screen, 30)
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                minimized = true
                card?.visibility = View.GONE
                chip?.visibility = View.VISIBLE
            }
        }
        header.addView(hide, LinearLayout.LayoutParams(dp(screen, 38), dp(screen, 32)))
        panel.addView(header)

        val goalLabel = TextView(screen).apply {
            text = "Dein aktuelles Ziel (bleibt lokal gespeichert)"
            setTextColor(0xFFCAD7E4.toInt())
            textSize = 10f
            setPadding(0, dp(screen, 5), 0, dp(screen, 3))
        }
        panel.addView(goalLabel)
        val inputRow = LinearLayout(screen).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val input = EditText(screen).apply {
            setSingleLine(true)
            textSize = 11f
            hint = "z. B. Boss besiegen / Rätsel lösen"
            setText(savedGoal)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9EADBC.toInt())
            setPadding(dp(screen, 7), dp(screen, 4), dp(screen, 7), dp(screen, 4))
            background = GradientDrawable().apply {
                setColor(0xFF303B47.toInt())
                cornerRadius = dp(screen, 7).toFloat()
            }
        }
        goalInput = input
        inputRow.addView(input, LinearLayout.LayoutParams(0, dp(screen, 38), 1f))
        val save = Button(screen).apply {
            text = "Setzen"
            textSize = 10f
            minWidth = dp(screen, 58)
            minimumWidth = dp(screen, 58)
            setPadding(dp(screen, 3), 0, dp(screen, 3), 0)
            setOnClickListener {
                savedGoal = input.text?.toString()?.trim().orEmpty().take(140)
                screen.getSharedPreferences("gamedeck_coop_coach", Context.MODE_PRIVATE).edit()
                    .putString("current_goal", savedGoal).apply()
                input.setText(savedGoal)
                update()
            }
        }
        inputRow.addView(save, LinearLayout.LayoutParams(dp(screen, 66), dp(screen, 38)))
        panel.addView(inputRow)
        val body = TextView(screen).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            setLineSpacing(dp(screen, 2).toFloat(), 1f)
            maxLines = 7
        }
        panel.addView(body)
        val note = TextView(screen).apply {
            text = "Coach-Modus: beobachtet und berät; steuert keinen zweiten Spieler."
            setTextColor(0xFFB9C5D1.toInt())
            textSize = 9f
            setPadding(0, dp(screen, 4), 0, 0)
        }
        panel.addView(note)
        val show = TextView(screen).apply {
            text = "CO-OP"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(screen, 10), dp(screen, 8), dp(screen, 10), dp(screen, 8))
            background = GradientDrawable().apply {
                setColor(0xE91B242E.toInt())
                cornerRadius = dp(screen, 18).toFloat()
                setStroke(dp(screen, 1), 0xFF6D8DA8.toInt())
            }
            visibility = View.GONE
            setOnClickListener {
                minimized = false
                card?.visibility = View.VISIBLE
                chip?.visibility = View.GONE
            }
        }
        val wrapper = FrameLayout(screen)
        wrapper.addView(panel, FrameLayout.LayoutParams(dp(screen, 300), -2, Gravity.TOP or Gravity.END))
        wrapper.addView(show, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        val params = FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(screen, 8)
            marginEnd = dp(screen, 8)
        }
        decor.addView(wrapper, params)
        root = wrapper
        card = panel
        chip = show
        status = body
    }

    private fun goalHint(goal: String): String {
        val g = goal.lowercase(Locale.ROOT)
        return when {
            g.contains("boss") || g.contains("gegner") || g.contains("endgegner") ->
                "Boss-Ziel: beobachte erst ein Angriffsmuster, weiche aus und nutze kurze sichere Zeitfenster. Exakte Angriffstiming-Erkennung ist nicht verfügbar."
            g.contains("rätsel") || g.contains("puzzle") || g.contains("schalter") ->
                "Rätsel-Ziel: ändere nur ein Element pro Versuch und prüfe danach die sichtbare Reaktion."
            g.contains("springen") || g.contains("plattform") || g.contains("level") ->
                "Level-Ziel: prüfe Kanten und Lücken, arbeite Abschnitt für Abschnitt und ändere nicht mehrere Bewegungen gleichzeitig."
            g.contains("rennen") || g.contains("kart") || g.contains("strecke") ->
                "Renn-Ziel: priorisiere kontrolliertes Lenken vor maximalem Tempo und prüfe, ob die letzte Eingabe geholfen hat."
            g.contains("finden") || g.contains("schlüssel") || g.contains("tür") || g.contains("weg") ->
                "Such-Ziel: prüfe Abzweigungen, interaktive Objekte und Hinweise im Bild. Merke dir bereits untersuchte Wege."
            g.isNotBlank() -> "Arbeitsziel: $goal. Beschreibe das Hindernis möglichst konkret; teste eine kleine Änderung und prüfe danach die Reaktion."
            else -> "Trage oben dein aktuelles Ziel ein. Der Coach verbindet es mit erkannten Bildschirmtexten; er versteht noch nicht zuverlässig jedes Spielelement."
        }
    }

    private fun update() {
        val body = status ?: return
        val snapshot = runCatching { GameDeckSceneObserver.snapshot() }.getOrNull()
        val scene = snapshot?.optString("scene", "UNKNOWN") ?: "UNKNOWN"
        val excerpt = snapshot?.optString("excerpt", "").orEmpty().take(95)
        val captures = snapshot?.optInt("captures", 0) ?: 0
        val hint = when (scene) {
            "MENU" -> "Menü erkannt. Prüfe die markierte Auswahl; bestätige erst, wenn du sicher bist."
            "TUTORIAL" -> "Anleitung erkannt. Achte auf die angezeigten Tasten und probiere eine Aktion nach der anderen."
            "PAUSE" -> "Pause erkannt. Fortsetzen nur, wenn du bereit bist."
            "LOADING" -> "Ladevorgang erkannt. Warte, bis das Spiel reagiert; keine schnellen Eingabefolgen."
            "GAME_OVER" -> "Möglicher Fehlschlag. Prüfe den letzten Abschnitt, bevor du einen Neustart bestätigst."
            "POSSIBLE_SUCCESS" -> "Möglicher Abschluss-Text erkannt. Das ist noch kein Beweis für einen abgeschlossenen Level."
            "LOAD_ERROR" -> "Fehlertext erkannt. Sichere den Text und prüfe Spieldatei bzw. Emulatorkonfiguration."
            "UNCLASSIFIED_TEXT" -> "Text erkannt, aber keine sichere Szene. Nutze den Text als Hinweis, nicht als Gewissheit."
            else -> "Noch keine sichere Szene erkannt. Coach beobachtet lokal; beschreibe Ziel oder Hindernis für gezielte Hilfe."
        }
        body.text = "Ziel: " + if (savedGoal.isBlank()) "noch nicht gesetzt" else savedGoal +
            "\nSzene: $scene · OCR-Proben: $captures\n" +
            "Zielhinweis: " + goalHint(savedGoal) + "\n" +
            "Szenenhinweis: $hint" +
            if (excerpt.isNotBlank()) "\nErkannter Text: $excerpt" else ""
    }

    private fun dp(screen: Activity, value: Int): Int =
        (value * screen.resources.displayMetrics.density + 0.5f).toInt()
}
