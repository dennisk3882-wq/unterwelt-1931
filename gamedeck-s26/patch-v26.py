# GameDeck v26: real controller routing, feedback-gated adaptive agent,
# single-game opt-in and verifiable HDMI/controller readiness.
from pathlib import Path
import sys

d=Path(sys.argv[1])/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"
def change(s,old,new,label):
    n=s.count(old)
    if n!=1: raise RuntimeError("v26 "+label+": "+str(n)+" anchors")
    return s.replace(old,new,1)

p=d/"GameDeckAutoPilot.kt"
s=p.read_text(encoding="utf-8")
s=change(s,'import android.os.Handler',
'''import android.content.Context
import android.os.Handler''',"context")
s=change(s,'import org.yuzu.yuzu_emu.features.input.NativeInput',
'''import org.yuzu.yuzu_emu.features.input.NativeInput
import org.yuzu.yuzu_emu.utils.InputHandler''',"physical mapping import")
s=change(s,'''    private const val GUID="00000000000000000000000000000000"
    private const val PORT=100''',
'''    private const val OVERLAY_GUID="00000000000000000000000000000000"
    private const val OVERLAY_PORT=100
    private var controlGuid=OVERLAY_GUID
    private var controlPort=OVERLAY_PORT
    private var controlSource="Eden-Touch-Overlay (Zuordnung im Test nicht bestätigt)"
    private var prefs:android.content.SharedPreferences?=null
    private var gameKey=""
    private var lastActionKey=""
    private var lastEvaluatedCapture=-1
    private var learningEvidence=0''',"input mapping")
s=change(s,'''        previousMotion=0.0;previousCaptures=0''',
'''        previousMotion=0.0;previousCaptures=0
        lastActionKey=""
        lastEvaluatedCapture=-1
        learningEvidence=0''',"reset learning")
s=change(s,'''    fun setEnabled(enabled:Boolean) {
        if(enabled) running=true
        else {running=false;generation++;releaseAll()}
    }''',
'''    /**
     * Route simulated input through the same registered controller that Eden
     * uses for Player 1, rather than blindly always sending to overlay port 100.
     * The fallback still requires actual Android/Eden input configuration.
     */
    fun configure(context:Context,programId:String) {
        prefs=context.getSharedPreferences("gamedeck_ai_feedback",Context.MODE_PRIVATE)
        gameKey=programId.ifBlank { "unknown" }
        val controller=InputHandler.androidControllers.values.firstOrNull { it.getPort()==0 }
        if(controller!=null) {
            controlGuid=controller.getGUID()
            controlPort=controller.getPort()
            controlSource="Registrierter physischer Controller, Port 0: "+controller.getName()
        } else {
            controlGuid=OVERLAY_GUID
            controlPort=OVERLAY_PORT
            controlSource="Registriertes virtuelles Eden-Touch-Overlay, Port 100"
        }
    }

    fun setEnabled(enabled:Boolean) {
        if(enabled) running=true
        else {running=false;generation++;releaseAll()}
    }''',"dynamic port routing")
s=s.replace("NativeInput.onGamePadButtonEvent(GUID,PORT,","NativeInput.onGamePadButtonEvent(controlGuid,controlPort,")
s=s.replace("NativeInput.onGamePadAxisEvent(GUID,PORT,","NativeInput.onGamePadAxisEvent(controlGuid,controlPort,")
s=change(s,'''        if(stepCount>0 && captures>previousCaptures) {
            val change=totalMotion-previousMotion
            if(change>0.014) {
                responses++
                lastOutcome="Veränderung der Bildproben beobachtet"
            } else {
                noResponses++
                lastOutcome="keine sichere Bildänderung beobachtet"
            }
        }
        val choice=decide(frame)''',
'''        // Never issue a second command without a fresh frame after the last
        // command. This prevents open-loop button spamming if PixelCopy fails.
        if(stepCount>0 && captures<=previousCaptures) return
        if(stepCount>0 && captures!=lastEvaluatedCapture) {
            lastEvaluatedCapture=captures
            val change=totalMotion-previousMotion
            val reacted=change>0.014
            if(reacted) {
                responses++
                lastOutcome="Veränderung der Bildproben beobachtet"
            } else {
                noResponses++
                lastOutcome="keine sichere Bildänderung beobachtet"
            }
            learn(lastActionKey,reacted)
        }
        val choice=decide(frame)''',"strict feedback gating")
s=change(s,'''        previousCaptures=captures
        previousMotion=totalMotion
        stepCount++''',
'''        previousCaptures=captures
        previousMotion=totalMotion
        lastActionKey=choice.label
        stepCount++''',"track last choice")
s=change(s,'''        // A limited cyclic exploration policy. Avoid HOME/capture/system''',
'''        val repeatable=when {
            static -> listOf(
                Choice("Weiter nach unten",KeyEvent.KEYCODE_DPAD_DOWN,
                    reason="Statisches Menü: Auswahl wechseln"),
                Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,
                    reason="Menü: Bestätigen"))
            dynamic -> listOf(
                Choice("Bewegen rechts",axis=MotionEvent.AXIS_X,value=0.60f,
                    reason="Dynamische Szene: Bewegung prüfen"),
                Choice("Bewegen links",axis=MotionEvent.AXIS_X,value=-0.55f,
                    reason="Dynamische Szene: Gegenrichtung prüfen"),
                Choice("Springen/Bestätigen",KeyEvent.KEYCODE_BUTTON_B,
                    reason="Dynamische Szene: Reaktion auf B testen"))
            else -> emptyList()
        }
        // Local action-response history biases toward previously responsive
        // actions. The response is mere image motion, not task success.
        if(stepCount>=3 && repeatable.isNotEmpty() && learningEvidence>=3) {
            val best=repeatable.maxByOrNull { responseScore(it.label) }
            if(best!=null && responseScore(best.label)>0.65) {
                return best.copy(reason=best.reason+" (durch frühere Bildreaktionen bevorzugt)")
            }
        }
        // A limited cyclic exploration policy. Avoid HOME/capture/system''',"feedback learning policy")
s=change(s,'''    private fun issue(choice:Choice) {''',
'''    private fun responseScore(action:String):Double {
        val store=prefs ?:return 0.5
        val prefix="game_"+gameKey.hashCode()+"_"+action.hashCode()
        val count=store.getInt(prefix+"_n",0)
        val yes=store.getInt(prefix+"_yes",0)
        return (yes.toDouble()+1.0)/(count.toDouble()+2.0)
    }
    private fun learn(action:String,reacted:Boolean) {
        if(action.isBlank()) return
        val store=prefs ?:return
        val prefix="game_"+gameKey.hashCode()+"_"+action.hashCode()
        val count=store.getInt(prefix+"_n",0).coerceAtMost(9999)
        val yes=store.getInt(prefix+"_yes",0).coerceAtMost(count)
        store.edit()
            .putInt(prefix+"_n",count+1)
            .putInt(prefix+"_yes",yes+if(reacted) 1 else 0)
            .apply()
        learningEvidence++
    }

    private fun issue(choice:Choice) {''',"persist action-feedback")
s=change(s,'''        .put("controller_guid",GUID)
        .put("controller_port",PORT)''',
'''        .put("controller_guid",controlGuid)
        .put("controller_port",controlPort)
        .put("controller_source",controlSource)
        .put("learning_feedback_samples",learningEvidence)''',"input report")
p.write_text(s,encoding="utf-8")

f=d/"GameDeckAutoTester.kt"
s=f.read_text(encoding="utf-8")
s=change(s,'''            GameDeckAutoPilot.setEnabled(aiMode)''',
'''            if(aiMode) {
                host.get()?.let { GameDeckAutoPilot.configure(it,games[index].programId) }
            }
            GameDeckAutoPilot.setEnabled(aiMode)''',"per-game controller binding")
s=change(s,'''                    append("  Keine garantierte korrekte Tastenzuordnung oder Level-Fortschritte.\\n")''',
'''                    append("  Input: ").append(ai.optString("controller_source","unbekannt"))
                        .append("\\n  Gelernte Bildreaktionen: ")
                        .append(ai.optInt("learning_feedback_samples"))
                        .append("\\n")
                    append("  Keine garantierte korrekte Tastenzuordnung oder Level-Fortschritte.\\n")''',"input report details")
f.write_text(s,encoding="utf-8")

hardware=d/"GameDeckHardwareAudit.kt"
hardware.write_text(r'''// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.gamedeck

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.InputDevice
import org.yuzu.yuzu_emu.utils.InputHandler
import java.util.Locale

/**
 * Actual Android display and controller enumeration. No manufactured HDMI
 * handshake, guaranteed second screen, controller event, or render metrics.
 */
object GameDeckHardwareAudit {
    fun report(context:Context):String {
        val displays=(context.getSystemService(Context.DISPLAY_SERVICE)
            as DisplayManager).displays
        val controllers=InputDevice.getDeviceIds()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { InputHandler.isPhysicalGameController(it) }
        val external=displays.filter { it.displayId!=Display.DEFAULT_DISPLAY }
        val projectable=external.filter { it.flags and Display.FLAG_PRESENTATION != 0 }
        return buildString {
            append("GAMEDECK – HDMI / CONTROLLER HARDWARE-CHECK\\n")
            append("Android: ").append(Build.VERSION.RELEASE)
                .append(" · ").append(Build.MANUFACTURER).append(" ")
                .append(Build.MODEL).append("\\n")
            append("Bildschirme: ").append(displays.size)
                .append(" · externe: ").append(external.size)
                .append(" · Presentation-fähig: ").append(projectable.size).append("\\n")
            for(display in displays) {
                val mode=display.mode
                append("• Display ").append(display.displayId).append(": ")
                    .append(display.name).append(" · ")
                    .append(mode.physicalWidth).append("×").append(mode.physicalHeight)
                    .append(" · ")
                    .append(String.format(Locale.US,"%.1f",mode.refreshRate))
                    .append(" Hz")
                    .append(if(display.displayId==Display.DEFAULT_DISPLAY)
                        " (Handy)" else " (zusätzlich)").append("\\n")
            }
            if(external.isEmpty()) append(
                "Kein separates Android-Display erkannt. HDMI-Spiegelung/DeX kann nicht allein aus dieser Messung bestätigt werden.\\n")
            if(external.isNotEmpty() && projectable.isEmpty()) append(
                "Externer Bildschirm erkannt, aber keine Presentation-Freigabe. TV-Bibliothek kann deshalb eingeschränkt sein.\\n")
            append("Physische Gamepads: ").append(controllers.size).append("\\n")
            for(device in controllers) {
                val axes=device.motionRanges.map { it.axis }.distinct()
                append("• ").append(device.name)
                    .append(" · ID ").append(device.id)
                    .append(" · ").append(axes.size).append(" Achsen")
                    .append(" · Quellen 0x").append(device.sources.toString(16))
                    .append("\\n")
            }
            if(controllers.isEmpty()) append(
                "Kein physischer Controller erkannt. Bluetooth-Kopplung und Gamepad-Eingaben separat prüfen.\\n")
            append("\\nErgebnis: Hardware-Erkennung, KEIN bestätigter HDMI-Videostream, ")
            append("kein Test der Gamepad-Eingaben im Emulator oder einer vollständigen Spielsitzung.\\n")
            append("Prüfung erfolgt lokal ohne Upload.")
        }
    }
}
''',encoding="utf-8")

f=d/"GameDeckActivity.kt"
s=f.read_text(encoding="utf-8")
s=change(s,'''        root.addView(infoPanel("▣ HDMI / Beamer", status.displayText))''',
'''        root.addView(infoPanel("▣ HDMI / Beamer", status.displayText))
        root.addView(actionCard("HDMI- und Controller-Check",
            "Alle echten Android-Bildschirme und Eingabegeräte prüfen") {
            val report=GameDeckHardwareAudit.report(this)
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hardwarediagnose")
                .setMessage(report)
                .setPositiveButton("OK",null)
                .setNeutralButton("Kopieren") { _, _ ->
                    (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText(
                            "GameDeck Hardwarediagnose",report))
                }.show()
        })''',"HDMI audit UI")
s=change(s,'''        root.addView(infoPanel("Automatische Leistungsanalyse", advisor.details))''',
'''        root.addView(infoPanel("Automatische Leistungsanalyse", advisor.details))
        root.addView(secondaryButton("KI-Spieltest nur für dieses Spiel") {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("KI-Spieltest: "+game.title)
                .setMessage("Die KI sendet reale Emulator-Eingaben und beobachtet Bildveränderungen. Ein laufendes Spiel darf nicht aktiv sein. Bitte Spielstand vorher sichern; echte Eingaben können Fortschritte und Einstellungen ändern.")
                .setPositiveButton("KI-Test starten") { _, _ ->
                    GameDeckAutoTester.start(this,listOf(game),true)
                }
                .setNeutralButton("Nur Laufzeittest") { _, _ ->
                    GameDeckAutoTester.start(this,listOf(game),false)
                }
                .setNegativeButton("Abbrechen",null)
                .show()
        })''',"single game AI test button")
f.write_text(s,encoding="utf-8")

f=d/"GameDeckRuntime.kt";s=f.read_text(encoding="utf-8")
s=change(s,'const val BUILD = "GameDeck v25"','const val BUILD = "GameDeck v26"',"runtime release")
f.write_text(s,encoding="utf-8")
f=d/"GameDeckUpdateChecker.kt";s=f.read_text(encoding="utf-8")
s=change(s,'private const val LOCAL_VERSION = 25','private const val LOCAL_VERSION = 26',"update release")
f.write_text(s,encoding="utf-8")
print("GameDeck v26: controller mapping, feedback learning, single-game test, device audit")
