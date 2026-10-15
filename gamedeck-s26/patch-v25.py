# GameDeck v25: genuine opt-in closed-loop native controller exploration.
from pathlib import Path
import sys

d=Path(sys.argv[1])/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"
def one(s,old,new,label):
 n=s.count(old)
 if n!=1: raise RuntimeError("GameDeck25 "+label+": "+str(n)+" matches")
 return s.replace(old,new,1)

(d/"GameDeckAutoPilot.kt").write_text(r'''// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.gamedeck

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import org.json.JSONArray
import org.json.JSONObject
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.features.input.NativeInput
import kotlin.math.abs

/**
 * Real closed-loop exploratory input agent, intentionally bounded and opt-in.
 *
 * Reads visual frame metrics from actual Eden emulation and selects physical
 * inputs through Eden's registered on-screen controller (port 100).
 * These are real core inputs, not cosmetic UI simulations. The input device's
 * GUID/port are from Eden's YuzuInputOverlayDevice. Mapping/observable
 * response is NOT guaranteed on every game/controller profile.
 *
 * This is an evidence-guided policy, NOT a trained general game-solving model.
 * It NEVER claims a completed level/story or that inputs caused progress.
 */
object GameDeckAutoPilot {
    private val ui=Handler(Looper.getMainLooper())
    private const val GUID="00000000000000000000000000000000"
    private const val PORT=100
    private const val MAX_ACTIONS=18
    private const val COOLDOWN_MS=3300L
    private const val BUTTON_HOLD_MS=220L
    private const val AXIS_HOLD_MS=450L
    private var generation=0
    private var running=false
    private var stepCount=0
    private var lastStep=0L
    private var inProgress=false
    private var previousMotion=0.0
    private var previousCaptures=0
    private var responses=0
    private var noResponses=0
    private var inputFailures=0
    private var lastLabel=""
    private var lastOutcome="kein Bildvergleich"
    private val log=JSONArray()

    data class Choice(val label:String,val button:Int=0,
        val axis:Int=-1,val value:Float=0f,val reason:String)

    fun reset() {
        generation++
        releaseAll()
        running=false
        stepCount=0;lastStep=0L;inProgress=false
        previousMotion=0.0;previousCaptures=0
        responses=0;noResponses=0;inputFailures=0
        lastLabel="";lastOutcome="kein Bildvergleich"
        while(log.length()>0) log.remove(0)
    }
    fun setEnabled(enabled:Boolean) {
        if(enabled) running=true
        else {running=false;generation++;releaseAll()}
    }

    private fun releaseAll() {
        runCatching {
            for(key in intArrayOf(KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_Y,KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT)) {
                NativeInput.onGamePadButtonEvent(GUID,PORT,key,
                    NativeInput.ButtonState.RELEASED)
            }
            NativeInput.onGamePadAxisEvent(GUID,PORT,MotionEvent.AXIS_X,0f)
            NativeInput.onGamePadAxisEvent(GUID,PORT,MotionEvent.AXIS_Y,0f)
        }
    }

    /** Runs only on the main thread, and only after actual native start. */
    fun tick(frame:JSONObject) {
        if(!running || inProgress || !NativeLibrary.isRunning() ||
            NativeLibrary.isPaused() || stepCount>=MAX_ACTIONS) return
        val now=SystemClock.elapsedRealtime()
        if(now-lastStep < COOLDOWN_MS) return
        if(frame.optInt("captures",0)==0) return // Blind gameplay forbidden.
        lastStep=now
        val captures=frame.optInt("captures",0)
        val totalMotion=frame.optDouble("mean_motion_ratio",0.0) *
            (captures-1).coerceAtLeast(0)
        if(stepCount>0 && captures>previousCaptures) {
            val change=totalMotion-previousMotion
            if(change>0.014) {
                responses++
                lastOutcome="Veränderung der Bildproben beobachtet"
            } else {
                noResponses++
                lastOutcome="keine sichere Bildänderung beobachtet"
            }
        }
        val choice=decide(frame)
        if(choice==null) return
        previousCaptures=captures
        previousMotion=totalMotion
        stepCount++
        lastLabel=choice.label
        log.put(JSONObject().put("index",stepCount)
            .put("action",choice.label).put("reason",choice.reason)
            .put("observed_before",frame.optString("classification"))
            .put("previous_observation",lastOutcome))
        issue(choice)
    }

    private fun decide(frame:JSONObject):Choice? {
        val state=frame.optString("classification")
        val static=state=="STATISCHE_BILDABFOLGE"
        val dynamic=state=="WECHSELNDE_BILDER"
        val stuck=noResponses>=2
        if(stepCount>=MAX_ACTIONS) return null
        // A limited cyclic exploration policy. Avoid HOME/capture/system
        // buttons and deliberately don't open emulator settings or e-shops.
        // A/Start can still change games and save states: user opted in.
        return when {
            stepCount==0 -> Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,
                reason="Startmenü vorsichtig bestätigen")
            stepCount==1 -> Choice("Start",KeyEvent.KEYCODE_BUTTON_START,
                reason="Titelmenü starten, falls vorhanden")
            static && stuck && stepCount%4==0 ->
                Choice("Weiter nach unten",KeyEvent.KEYCODE_DPAD_DOWN,
                    reason="Statische Anzeige: andere Menüauswahl versuchen")
            static && stuck ->
                Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,
                    reason="Statische Anzeige: Auswahl probeweise bestätigen")
            dynamic && stepCount%3==0 ->
                Choice("Bewegen rechts",axis=MotionEvent.AXIS_X,value=0.60f,
                    reason="Dynamische Szene: geringe Analogstick-Bewegung prüfen")
            dynamic && stepCount%3==1 ->
                Choice("Springen/Bestätigen",KeyEvent.KEYCODE_BUTTON_B,
                    reason="Reaktion auf die B-Taste beobachten")
            stepCount%5==2 -> Choice("Bewegen links",axis=MotionEvent.AXIS_X,value=-0.55f,
                reason="Bewegungsreaktion gegenprüfen")
            stepCount%5==3 -> Choice("Nach oben",KeyEvent.KEYCODE_DPAD_UP,
                reason="D-Pad- oder Menünavigation prüfen")
            stepCount%5==4 -> Choice("Taste X",KeyEvent.KEYCODE_BUTTON_X,
                reason="Alternative Spielaktion prüfen")
            else -> Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,
                reason="Bildreaktion nach Eingabe überprüfen")
        }
    }

    private fun issue(choice:Choice) {
        val token=generation
        inProgress=true
        try {
            if(choice.button!=0) {
                NativeInput.onGamePadButtonEvent(GUID,PORT,choice.button,
                    NativeInput.ButtonState.PRESSED)
            } else {
                NativeInput.onGamePadAxisEvent(GUID,PORT,choice.axis,choice.value)
            }
            ui.postDelayed({
                // Always release pressed keys even if test was canceled.
                runCatching {
                    if(choice.button!=0) NativeInput.onGamePadButtonEvent(
                        GUID,PORT,choice.button,NativeInput.ButtonState.RELEASED)
                    else NativeInput.onGamePadAxisEvent(GUID,PORT,choice.axis,0f)
                }.onFailure { inputFailures++ }
                if(generation==token) inProgress=false
            },if(choice.button!=0) BUTTON_HOLD_MS else AXIS_HOLD_MS)
        } catch (_:Throwable) {
            inputFailures++
            runCatching { releaseAll() }
            inProgress=false
        }
    }

    fun summary():JSONObject = JSONObject()
        .put("mode","opt_in_exploratory_controller_agent")
        .put("controller_guid",GUID)
        .put("controller_port",PORT)
        .put("actions_issued",stepCount)
        .put("visual_changes_after_actions",responses)
        .put("no_clear_visual_changes",noResponses)
        .put("input_api_errors",inputFailures)
        .put("last_action",lastLabel)
        .put("last_observation",lastOutcome)
        .put("actions",JSONArray(log.toString()))
        .put("gameplay_completed",false)
        .put("action_mapping_verified",false)
        .put("note","Bildveränderungen belegen keinen Fortschritt; Ursache und tatsächliche Spielwirkung bleiben unbestätigt")
}
''',encoding="utf-8")
f=d/"GameDeckAutoTester.kt";s=f.read_text()
s=one(s,"    private var results=JSONArray()",
'''    private var results=JSONArray()
    private var aiMode=false''',"track ai mode")
s=one(s,"    fun start(activity:GameDeckActivity,list:List<Game>) {",
'''    fun start(activity:GameDeckActivity,list:List<Game>,autoplay:Boolean=false) {''',"mode input")
s=one(s,"        host=WeakReference(activity); games=list.toList(); index=-1",
'''        aiMode=autoplay
        GameDeckAutoPilot.reset()
        host=WeakReference(activity); games=list.toList(); index=-1''',"mode init")
s=one(s,"            waiting=false; coreConfirmed=true; coreAt=SystemClock.elapsedRealtime()",
'''            waiting=false; coreConfirmed=true; coreAt=SystemClock.elapsedRealtime()
            GameDeckAutoPilot.setEnabled(aiMode)''',"enable agent on native start")
s=one(s,"        GameDeckVisualProbe.reset()",
'''        GameDeckAutoPilot.reset()
        GameDeckVisualProbe.reset()''',"agent reset per game")
s=one(s,"        GameDeckVisualProbe.captureIfDue()",
'''        GameDeckVisualProbe.captureIfDue()
        if(aiMode && coreConfirmed) GameDeckAutoPilot.tick(GameDeckVisualProbe.snapshot())''',"decision on measured frames")
s=one(s,"        val insight=GameDeckSmartAnalyzer.assess(status,detail,coreConfirmed,sorted,visual)",
'''        val insight=GameDeckSmartAnalyzer.assess(status,detail,coreConfirmed,sorted,visual)
        val actions=GameDeckAutoPilot.summary()''',"agent telemetry")
s=one(s,'''            .put("diagnosis",insight))''',
'''            .put("diagnosis",insight)
            .put("autopilot",actions))''',"save agent record")
s=one(s,'''        GameDeckVisualProbe.detach()
        ui.postDelayed({ next() },800L)''',
'''        GameDeckAutoPilot.setEnabled(false)
        GameDeckVisualProbe.detach()
        ui.postDelayed({ next() },800L)''',"stop inputs on step end")
s=one(s,'''        GameDeckVisualProbe.detach()
        save(state)''',
'''        GameDeckAutoPilot.reset()
        GameDeckVisualProbe.detach()
        save(state)''',"stop all inputs on test end")
s=one(s,'''                val visual=item.optJSONObject("visual")''',
'''                val ai=item.optJSONObject("autopilot")
                if(ai!=null && ai.optString("mode")=="opt_in_exploratory_controller_agent" &&
                    ai.optInt("actions_issued")>0) {
                    append("  KI-Steuerung: ").append(ai.optInt("actions_issued"))
                        .append(" reale Eingaben; ")
                        .append(ai.optInt("visual_changes_after_actions"))
                        .append(" beobachtete Bildreaktionen\\n")
                    append("  Keine garantierte korrekte Tastenzuordnung oder Level-Fortschritte.\\n")
                }
                val visual=item.optJSONObject("visual")''',"ai report")
f.write_text(s)

f=d/"GameDeckActivity.kt";s=f.read_text()
s=one(s,'''            .setPositiveButton("Alle Spiele testen") { _, _ ->
                GameDeckAutoTester.start(this,lastGames)
            }
            .setNeutralButton("Letzten Bericht") { _, _ -> showAutoTestReport() }''',
'''            .setPositiveButton("KI aktiv spielen lassen") { _, _ ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("KI-Spielsteuerung aktivieren?")
                    .setMessage("GameDeck sendet echte A/B/X-, Start-, D-Pad- und Analogstick-Eingaben an Eden. Die KI beobachtet anschließend die Bildreaktion und entscheidet weitere begrenzte Schritte.\\n\\nAchtung: Spiele können Speicherstände oder Einstellungen verändern. Nur mit eigenen Spieldaten und möglichst einem separaten Testprofil verwenden. Keine Garantie für Fortschritt oder vollständiges Durchspielen.")
                    .setPositiveButton("KI-Steuerung erlauben") { _, _ ->
                        GameDeckAutoTester.start(this,lastGames,true)
                    }
                    .setNegativeButton("Abbrechen",null).show()
            }
            .setNeutralButton("Nur Start und FPS") { _, _ ->
                GameDeckAutoTester.start(this,lastGames,false)
            }''',"opt-in active game AI controls")
s=one(s,"GameDeck AutoPilot impossible placeholder","") if "GameDeck AutoPilot impossible placeholder" in s else s
s=one(s,'''Je Titel werden bis zu 75 Sekunden echte FPS, Bildschirmstichproben und Hardwaredaten erfasst.''',
'''Je Titel werden bis zu 75 Sekunden echte FPS, Bildschirmstichproben und Hardwaredaten erfasst. Optional sendet ein explorierender KI-Agent kontrollierte Tastenaktionen an Eden.''',"explainer")
f.write_text(s)

f=d/"GameDeckVisualProbe.kt";s=f.read_text()
s=one(s,"    private const val MIN_GAP_MS=12000L",
'''    private const val MIN_GAP_MS=1700L''',"fast vision feedback")
f.write_text(s)
runtime=d/"GameDeckRuntime.kt";s=runtime.read_text()
s=one(s,'const val BUILD = "GameDeck v24"','const val BUILD = "GameDeck v25"',"runtime id")
runtime.write_text(s)
updater=d/"GameDeckUpdateChecker.kt";s=updater.read_text()
s=one(s,"private const val LOCAL_VERSION = 24","private const val LOCAL_VERSION = 25","update id")
updater.write_text(s)
print("GameDeck v25: opt-in frame-driven Eden controller agent and observable results")
