// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.gamedeck

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference

/**
 * Compact on-screen HUD shown only while the autonomous GameDeck agent is active.
 * It reports real local agent/OCR state; it does not claim task completion.
 */
object GameDeckAiLiveOverlay {
    private val handler = Handler(Looper.getMainLooper())
    private var activity = WeakReference<Activity>(null)
    private var panel: FrameLayout? = null
    private var cardView: LinearLayout? = null
    private var minimized: TextView? = null
    private var status: TextView? = null
    private var hidden = false
    private var attached = false

    private val poll = object : Runnable {
        override fun run() {
            val screen = activity.get()
            if (screen == null || screen.isFinishing || screen.isDestroyed) {
                detach()
                return
            }
            val active = runCatching { GameDeckAutoPilot.isRunning() }.getOrDefault(false)
            if (active && panel == null) createPanel(screen)
            panel?.visibility = if (active) View.VISIBLE else View.GONE
            cardView?.visibility = if (hidden) View.GONE else View.VISIBLE
            minimized?.visibility = if (hidden) View.VISIBLE else View.GONE
            if (active) updateStatus()
            handler.postDelayed(this, 700L)
        }
    }

    fun attach(screen: Activity) {
        if (activity.get() === screen && attached) return
        detach()
        activity = WeakReference(screen)
        attached = true
        hidden = false
        handler.post(poll)
    }

    fun detach() {
        handler.removeCallbacks(poll)
        val view = panel
        val parent = view?.parent as? ViewGroup
        if (parent != null) parent.removeView(view)
        panel = null
        cardView = null
        minimized = null
        status = null
        activity.clear()
        attached = false
        hidden = false
    }

    private fun createPanel(screen: Activity) {
        val decor = screen.window.decorView as? ViewGroup ?: return
        val card = LinearLayout(screen).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(screen, 12), dp(screen, 8), dp(screen, 12), dp(screen, 8))
            background = GradientDrawable().apply {
                setColor(0xE820252D.toInt())
                cornerRadius = dp(screen, 12).toFloat()
                setStroke(dp(screen, 1), 0xFF5E7186.toInt())
            }
            elevation = dp(screen, 12).toFloat()
            isClickable = true
        }
        val header = LinearLayout(screen).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(screen).apply {
            text = "GAMEDECK · KI-TEST LIVE"
            setTextColor(0xFF9ED0FF.toInt())
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val hide = Button(screen).apply {
            text = "×"
            textSize = 15f
            minWidth = dp(screen, 34)
            minimumWidth = dp(screen, 34)
            minHeight = dp(screen, 30)
            minimumHeight = dp(screen, 30)
            setPadding(0, 0, 0, 0)
            setOnClickListener { hidden = true; card.visibility = View.GONE }
        }
        header.addView(hide, LinearLayout.LayoutParams(dp(screen, 38), dp(screen, 32)))
        card.addView(header)
        val body = TextView(screen).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            setLineSpacing(dp(screen, 2).toFloat(), 1f)
            maxLines = 8
        }
        card.addView(body)
        val show = TextView(screen).apply {
            text = "KI •"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(screen, 10), dp(screen, 8), dp(screen, 10), dp(screen, 8))
            background = GradientDrawable().apply {
                setColor(0xE820252D.toInt())
                cornerRadius = dp(screen, 18).toFloat()
                setStroke(dp(screen, 1), 0xFF5E7186.toInt())
            }
            visibility = View.GONE
            setOnClickListener { hidden = false; cardView?.visibility = View.VISIBLE; minimized?.visibility = View.GONE; panel?.visibility = View.VISIBLE }
        }
        // Use a single full-width overlay container so the minimized chip remains tappable.
        val wrapper = FrameLayout(screen)
        wrapper.addView(card, FrameLayout.LayoutParams(dp(screen, 292), -2, Gravity.TOP or Gravity.END))
        wrapper.addView(show, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        card.tag = show
        val params = FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(screen, 8)
            marginEnd = dp(screen, 8)
        }
        decor.addView(wrapper, params)
        // Store wrapper as the removable root; card remains the status target.
        wrapper.tag = "gamedeck-ai-live-overlay"
        panel = wrapper
        cardView = card
        minimized = show
        status = body
        updateStatus()
    }

    private fun updateStatus() {
        val body = status ?: return
        val agent = runCatching { GameDeckAutoPilot.snapshot() }.getOrNull()
        val scene = runCatching { GameDeckSceneObserver.snapshot() }.getOrNull()
        val strategy = agent?.optString("strategy", "unbekannt") ?: "unbekannt"
        val goal = agent?.optString("goal", "unbekannt") ?: "unbekannt"
        val action = agent?.optString("live_last_action", "warte auf Aktion") ?: "warte auf Aktion"
        val outcome = agent?.optString("live_last_outcome", "noch keine Rückmeldung") ?: "noch keine Rückmeldung"
        val step = agent?.optInt("live_step_count", 0) ?: 0
        val sceneName = scene?.optString("scene", "UNKNOWN") ?: "UNKNOWN"
        val excerpt = scene?.optString("excerpt", "").orEmpty().take(70)
        body.text = "Status: AKTIV  ·  Aktion $step\n" +
            "Strategie: $strategy\nZiel: $goal\nAktion: $action\n" +
            "Bildreaktion: $outcome\nSzene: $sceneName" +
            if (excerpt.isNotBlank()) "\nText: $excerpt" else ""
    }

    private fun dp(screen: Activity, value: Int): Int =
        (value * screen.resources.displayMetrics.density + 0.5f).toInt()
}
