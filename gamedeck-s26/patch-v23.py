from pathlib import Path
import sys

root=Path(sys.argv[1])
d=root/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"
tester=d/"GameDeckAutoTester.kt"
s=tester.read_text(encoding="utf-8")
def once(a,b,name):
 global s
 if s.count(a)!=1: raise RuntimeError("v23 "+name+" expected one occurrence, got "+str(s.count(a)))
 s=s.replace(a,b,1)

once("    fun onNativeStarted() {",
"""    fun onNativeStarted(activity: android.app.Activity) {
        GameDeckVisualProbe.attach(activity)""","attach screenshot analyzer")
once("        waiting=true; coreConfirmed=false; stepRecorded=false; readings=mutableListOf()",
"""        GameDeckVisualProbe.reset()
        waiting=true; coreConfirmed=false; stepRecorded=false; readings=mutableListOf()""","reset frame evidence")
once("        val stats=runCatching { NativeLibrary.getPerfStats() }.getOrDefault(doubleArrayOf())",
"""        GameDeckVisualProbe.captureIfDue()
        val stats=runCatching { NativeLibrary.getPerfStats() }.getOrDefault(doubleArrayOf())""","take screen sample")
once('        results.put(JSONObject().put("title",games[index].title)', '''        val visual=GameDeckVisualProbe.snapshot()
        val insight=GameDeckSmartAnalyzer.assess(status,detail,coreConfirmed,sorted,visual)
        results.put(JSONObject().put("title",games[index].title)''',"start insight")
once('            .put("fps_lower_10_percent",low))',
'''            .put("fps_lower_10_percent",low)
            .put("visual",visual)
            .put("diagnosis",insight))''',"store screenshot-derived diagnosis")
once('        save("LAEUFT")\n        ui.postDelayed({ next() },800L)',
'''        save("LAEUFT")
        GameDeckVisualProbe.detach()
        ui.postDelayed({ next() },800L)''',"release camera-like capture")
once('        active=false; ui.removeCallbacks(tick); save(state)',
'''        active=false; ui.removeCallbacks(tick)
        GameDeckVisualProbe.detach()
        save(state)''',"release screenshot handler on test end")
once('                append("\\n").append("  ").append(item.optString("detail")).append("\\n")',
'''                append("\\n").append("  ").append(item.optString("detail")).append("\\n")
                val diagnosis=item.optJSONObject("diagnosis")
                if(diagnosis!=null) {
                    append("  Evidenzlage: ").append(diagnosis.optString("confidence","unbekannt"))
                    append(" · Risiko: ").append(diagnosis.optInt("risk_score",0)).append("/100\\n")
                    val notes=diagnosis.optJSONArray("findings") ?: JSONArray()
                    for(k in 0 until notes.length()) {
                        append("  - ").append(notes.optString(k)).append("\\n")
                    }
                }
                val visual=item.optJSONObject("visual")
                if(visual!=null) append("  Bildproben: ").append(visual.optInt("captures"))
                    .append(" · dunkel: ").append(visual.optInt("dark"))
                    .append(" · nahezu unverändert: ").append(visual.optInt("static"))
                    .append("\\n")''',"detailed report")
tester.write_text(s,encoding="utf-8")

(d/"GameDeckVisualProbe.kt").write_text(r'''// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.gamedeck

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Lightweight on-device observation of actual rendered emulator WINDOW frames.
 * Never saves screenshots. No internet, no gameplay input injection. A frozen
 * title menu and a frozen game look similar; visual findings are indicators,
 * never proofs of a functioning or broken game.
 */
object GameDeckVisualProbe {
    private val ui=Handler(Looper.getMainLooper())
    private var activity=WeakReference<Activity>(null)
    private var generation=0
    private var busy=false
    private var lastAt=0L
    private var captures=0
    private var dark=0
    private var unchanged=0
    private var copyFailures=0
    private var motionTotal=0.0
    private var prev:IntArray?=null
    private const val MIN_GAP_MS=12000L

    fun attach(screen:Activity) {
        activity=WeakReference(screen)
    }

    fun reset() {
        generation++
        activity.clear()
        busy=false
        lastAt=0L
        captures=0;dark=0;unchanged=0;copyFailures=0;motionTotal=0.0;prev=null
    }

    fun detach() {
        generation++
        activity.clear()
        busy=false
    }

    fun captureIfDue() {
        if (busy) return
        val target=activity.get() ?:return
        if (target.isFinishing || target.isDestroyed) return
        val now=SystemClock.elapsedRealtime()
        if (now-lastAt<MIN_GAP_MS) return
        if (!target.window.decorView.isAttachedToWindow) return
        val token=generation
        lastAt=now
        busy=true
        val bmp=Bitmap.createBitmap(96,54,Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(target.window,bmp,{ result ->
                try {
                    if(token!=generation) return@request
                    if(result==PixelCopy.SUCCESS) readFrame(bmp)
                    else copyFailures++
                } finally {
                    bmp.recycle()
                    if(token==generation) busy=false
                }
            },ui)
        } catch (_:Throwable) {
            copyFailures++
            bmp.recycle()
            busy=false
        }
    }

    private fun readFrame(bitmap:Bitmap) {
        val cells=IntArray(64)
        var sum=0.0
        var almostBlack=0
        for (y in 0 until 8) for (x in 0 until 8) {
            val p=bitmap.getPixel(6+x*12,3+y*6)
            val r=(p ushr 16) and 255
            val g=(p ushr 8) and 255
            val b=p and 255
            val l=(r*299+g*587+b*114)/1000
            cells[y*8+x]=l
            sum+=l
            if(l<14) almostBlack++
        }
        captures++
        if(almostBlack>=60 && sum/64.0<19.0) dark++
        val before=prev
        if(before!=null) {
            var total=0
            for(i in cells.indices) total+=kotlin.math.abs(cells[i]-before[i])
            val diff=total/(64.0*255.0)
            motionTotal+=diff
            if(diff<0.007) unchanged++
        }
        prev=cells
    }

    fun snapshot():JSONObject {
        return JSONObject()
            .put("captures",captures)
            .put("dark",dark)
            .put("static",unchanged)
            .put("copy_failures",copyFailures)
            .put("mean_motion_ratio",if(captures>1) motionTotal/(captures-1) else 0.0)
            .put("source","Android PixelCopy window miniatures; no saved pictures")
    }
}
''',encoding="utf-8")

(d/"GameDeckSmartAnalyzer.kt").write_text(r'''// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.gamedeck

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Explainable on-device evidence engine. Detects likely issues, assigns
 * qualified confidence and proposes user-safe next steps from native session
 * and frame evidence. Not a trained game-playing AI and no false successes.
 */
object GameDeckSmartAnalyzer {
    fun assess(
        status:String,
        detail:String,
        nativeStarted:Boolean,
        fps:List<Double>,
        visual:JSONObject
    ):JSONObject {
        var risk=0
        val findings=JSONArray()
        val samples=visual.optInt("captures",0)
        val dark=visual.optInt("dark",0)
        val still=visual.optInt("static",0)
        val failures=visual.optInt("copy_failures",0)
        val median=if(fps.isEmpty()) 0.0 else fps[fps.size/2]
        val low=if(fps.isEmpty()) 0.0 else fps[(fps.size/10).coerceIn(0,fps.lastIndex)]
        fun flag(points:Int,note:String) {
            risk=(risk+points).coerceAtMost(100)
            findings.put(note)
        }

        if(status=="STARTVORAUSSETZUNG_FEHLT") flag(95,
            "Dateizugriff, eigenes Firmware-/Schlüsselmaterial oder Treiberauswahl laut Startprüfung korrigieren.")
        if(status=="ANDROID_STARTFEHLER" || status=="START_TIMEOUT") flag(90,
            "Emulation hat keinen bestätigten Start. Start-Log und Firmware-/Spielkompatibilität prüfen.")
        if(status=="CORE_VORZEITIG_BEENDET" || status=="CORE_UNTERBROCHEN") flag(86,
            "Native Sitzung vor dem geplanten Messende beendet. Eden-Protokoll, Grafiktreiber und Speicherdruck prüfen.")
        if(status.startsWith("THERMISCH_")) flag(90,
            "Thermischer Sicherheitsstopp. Gerät abkühlen lassen und danach Kühl-Profil testen.")
        if(status=="AKKU_SCHUTZSTOPP") flag(70,
            "Test wegen niedrigem Akkustand gestoppt. Laden, erst dann wiederholt prüfen.")
        if(nativeStarted && fps.size<12) flag(35,
            "Eden-Start bestätigt, aber zu wenige gültige FPS-Daten; Menü-, Lade- oder Stillstand möglich.")
        if(fps.size>=12 && median<20.0) flag(45,
            "Niedrige gemessene Bildrate (Median "+median.roundToInt()+
            " FPS). Profil Performance/Kühl und niedrigere interne Auflösung vergleichen.")
        if(fps.size>=12 && median>=20.0 && low<median*0.60) flag(35,
            "Deutliche FPS-Schwankungen (untere 10%: "+low.roundToInt()+
            " FPS). Shader-Kompilierung, Treiber oder CPU/GPU-Drosselung untersuchen.")
        if(samples>=4 && dark>=samples-1) flag(52,
            "Fast alle echten Bildproben wirken schwarz/dunkel: möglicher Black-Screen oder dunkle Spielszene.")
        if(samples>=5 && still>=samples-2) flag(34,
            "Bild über mehrere Proben fast unverändert: mögliches statisches Menü, Pause oder Hänger.")
        if(samples==0 && failures>0) findings.put(
            "Bildanalyse nicht verfügbar (Android PixelCopy verweigert die Aufnahme). Keine Bildaussage möglich.")
        if(samples<3) findings.put(
            "Noch zu wenige Bildproben für eine Aussage zur Darstellung.")
        if(risk==0) findings.put(
            "Keine eindeutigen Auffälligkeiten in den vorhandenen Messwerten. Das ist kein Beweis für spielbares Gameplay.")
        val confidence=when {
            !nativeStarted -> "hoch für Startstatus, keine Gameplay-Evidenz"
            fps.size>=20 && samples>=4 -> "mittel (FPS und Bildstichproben)"
            fps.size>=12 -> "mittel für FPS, gering für Gameplay"
            else -> "gering (wenige oder keine Messwerte)"
        }
        return JSONObject()
            .put("risk_score",risk)
            .put("confidence",confidence)
            .put("findings",findings)
            .put("native_started",nativeStarted)
            .put("reliable_gameplay_proven",false)
            .put("reasoning","Transparente heuristische Evidenzanalyse, kein trainiertes autonomes Spielmodell")
    }
}
''',encoding="utf-8")

act=d/"GameDeckActivity.kt"
a=act.read_text(encoding="utf-8")
a=a.replace('"Autonomer Spiele-Stabilitätstest"','"Intelligenter KI-Spieletester"')
a=a.replace('"Alle Spiele nacheinander starten, FPS und Abstürze protokollieren"',
            '"Spiele automatisch starten · Bild, FPS, Hänger und Fehler analysieren"')
a=a.replace('Je Titel werden bis zu 75 Sekunden echte FPS und Hardwaredaten erfasst.',
            'Je Titel werden bis zu 75 Sekunden echte FPS, Bildschirmstichproben und Hardwaredaten erfasst.')
act.write_text(a,encoding="utf-8")

rt=d/"GameDeckRuntime.kt"
r=rt.read_text(encoding="utf-8")
if r.count('const val BUILD = "GameDeck v22"')!=1:raise RuntimeError("v23 build version mismatch")
rt.write_text(r.replace('const val BUILD = "GameDeck v22"','const val BUILD = "GameDeck v23"'),encoding="utf-8")
updater=d/"GameDeckUpdateChecker.kt"
u=updater.read_text(encoding="utf-8")
if "private const val LOCAL_VERSION = 22" not in u:raise RuntimeError("v23 updater version mismatch")
updater.write_text(u.replace("private const val LOCAL_VERSION = 22","private const val LOCAL_VERSION = 23"),encoding="utf-8")
print("v23 native visual evidence and explainable intelligence integrated")
