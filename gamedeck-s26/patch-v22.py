from pathlib import Path
import sys
root=Path(sys.argv[1])
d=root/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"
a=d/"GameDeckActivity.kt"
s=a.read_text()
def swap(old,new,label):
 global s
 if s.count(old)!=1: raise RuntimeError(label+" match "+str(s.count(old)))
 s=s.replace(old,new,1)
swap('        root.addView(actionCard("Automatischer Bibliothekstest",',
'''        root.addView(actionCard("Autonomer Spiele-Stabilitätstest",
            if (GameDeckAutoTester.isActive()) "Test läuft · Bericht / Abbruch"
            else "Alle Spiele nacheinander starten, FPS und Abstürze protokollieren") {
            showAutomatedTestDialog()
        })
        root.addView(actionCard("Automatischer Bibliothekstest",''',"menu")
swap('    private fun runLibrarySmokeTest() {',
'''    private fun showAutomatedTestDialog() {
        if (GameDeckAutoTester.isActive()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Automatischer Testlauf")
                .setMessage(GameDeckAutoTester.status())
                .setPositiveButton("Weiterlaufen lassen",null)
                .setNeutralButton("Bericht") { _, _ -> showAutoTestReport() }
                .setNegativeButton("Test abbrechen") { _, _ ->
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Test abbrechen?")
                        .setMessage("Der laufende Emulator wird gestoppt; bisherige Ergebnisse bleiben erhalten.")
                        .setPositiveButton("Test beenden") { _, _ -> GameDeckAutoTester.cancel() }
                        .setNegativeButton("Zurück",null).show()
                }.show()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Automatischer Spiele-Testlauf")
            .setMessage("GameDeck prüft alle " + lastGames.size + " Spiele nacheinander. Eden meldet jeden tatsächlichen Spielstart. Je Titel werden bis zu 75 Sekunden echte FPS und Hardwaredaten erfasst. Bei thermischer Belastung oder niedrigem Akku wird gestoppt.\\n\\nWichtig: Keine künstlichen Controller-Eingaben, keine garantierte Spielbarkeit oder vollständiges Durchspielen. Der Test kann viel Strom und Zeit benötigen.")
            .setPositiveButton("Alle Spiele testen") { _, _ ->
                GameDeckAutoTester.start(this,lastGames)
            }
            .setNeutralButton("Letzten Bericht") { _, _ -> showAutoTestReport() }
            .setNegativeButton("Schließen",null).show()
    }

    private fun showAutoTestReport() {
        val result=GameDeckAutoTester.report(this)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("GameDeck Autotest")
            .setMessage(result)
            .setPositiveButton("OK",null)
            .setNeutralButton("Kopieren") { _, _ ->
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("GameDeck Autotest",result))
            }
            .setNegativeButton("Teilen") { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(Intent.EXTRA_TEXT,result),"Bericht teilen"))
            }.show()
    }

    private fun runLibrarySmokeTest() {''',"panel")
a.write_text(s)
(d/"GameDeckAutoTester.kt").write_text(r'''// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.gamedeck

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.model.Game
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * User-approved automated native launch and runtime smoke test.
 * Relies on Eden onEmulationStarted callbacks and built-in ROM swapping.
 * It neither pretends to finish story games nor injects risky button macros.
 */
object GameDeckAutoTester {
    private val ui=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor()
    private val tick=Runnable { sample() }
    private var host=WeakReference<GameDeckActivity>(null)
    private var games=emptyList<Game>()
    private var index=-1
    private var active=false
    private var waiting=true
    private var coreConfirmed=false
    private var startedAt=0L
    private var coreAt=0L
    private var readings=mutableListOf<Double>()
    private var results=JSONArray()
    private var logFile:File?=null
    private var firstWall=0L
    private const val SAMPLE_INTERVAL=1500L
    private const val BOOT_TIMEOUT=100000L
    private const val RUN_WINDOW=75000L

    fun isActive()=active
    fun status():String {
        val title=games.getOrNull(index)?.title ?: "Vorbereitung"
        return if (!active) "Kein Test aktiv" else "Spiel "+(index+1)+"/"+games.size+
            ": "+title+"\\nNative Emulation: "+if (waiting) "Warte auf Eden-Startbestätigung"
            else "Gestartet · FPS-Messpunkte: "+readings.size
    }

    fun start(activity:GameDeckActivity,list:List<Game>) {
        if(active || list.isEmpty() || GameDeckRuntime.hasSession() || NativeLibrary.isRunning()) {
            Toast.makeText(activity,"Bitte zuerst laufendes Spiel beenden und Bibliothek prüfen.",Toast.LENGTH_LONG).show()
            return
        }
        host=WeakReference(activity); games=list.toList(); index=-1
        active=true; waiting=true; results=JSONArray()
        firstWall=System.currentTimeMillis()
        logFile=File(activity.filesDir,"gamedeck_auto_test/latest.json")
        logFile?.parentFile?.mkdirs()
        save("GESTARTET")
        next()
    }

    /** Called from real Eden EmulationActivity callback, not guessed from FPS. */
    fun onNativeStarted() {
        if(active && index in games.indices && waiting) {
            waiting=false; coreConfirmed=true; coreAt=SystemClock.elapsedRealtime()
        }
    }

    fun onNativeStopped(code:Int) {
        if(active && index in games.indices && !waiting && coreConfirmed) {
            record("CORE_VORZEITIG_BEENDET","Eden hat den Core beendet: Status "+code)
        }
    }

    private fun next() {
        if(!active) return
        ui.removeCallbacks(tick)
        index++
        if(index >= games.size) {
            finish("ABGESCHLOSSEN")
            runCatching { NativeLibrary.stopEmulation() }
            return
        }
        val activity=host.get()
        if(activity==null || activity.isFinishing || activity.isDestroyed) {
            finish("UNTERBROCHEN_ACTIVITY_VERLOREN")
            return
        }
        val game=games[index]
        waiting=true; coreConfirmed=false; readings=mutableListOf()
        startedAt=SystemClock.elapsedRealtime(); coreAt=0L
        save("LAEUFT")
        io.execute {
            val issues=runCatching { GameDeckRuntime.preflight(game) }
                .getOrElse { listOf("Prüfung fehlgeschlagen: "+it.localizedMessage) }
            ui.post {
                if(!active || games.getOrNull(index)!=game) return@post
                if(issues.isNotEmpty()) {
                    record("STARTVORAUSSETZUNG_FEHLT",issues.joinToString("; "))
                    return@post
                }
                runCatching {
                    // SINGLE_TOP + CLEAR_TOP reuses Eden EmulationActivity and
                    // requests its existing synchronized ROM swap mechanism.
                    GameDeckRuntime.begin(game,GameDeckProfileEngine.selectedMode(activity,game))
                    activity.startActivity(game.launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION))
                }.onFailure {
                    record("ANDROID_STARTFEHLER",it.localizedMessage ?: "Start abgelehnt")
                    return@post
                }
                ui.postDelayed(tick,SAMPLE_INTERVAL)
            }
        }
    }

    private fun sample() {
        if(!active || index !in games.indices) return
        val activity=host.get()
        if(activity==null || activity.isFinishing || activity.isDestroyed) {
            finish("UNTERBROCHEN_ACTIVITY_VERLOREN")
            return
        }
        val pm=activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal=if(Build.VERSION.SDK_INT>=29) pm.currentThermalStatus else 0
        val device=DeviceStatusProvider(activity).snapshot()
        if(thermal>=PowerManager.THERMAL_STATUS_SEVERE || device.batteryTempC>=44f) {
            record("THERMISCH_ABGEBROCHEN","Temperatur "+device.batteryTempC+" °C / Android "+thermal)
            finish("THERMISCH_ABGEBROCHEN")
            runCatching { NativeLibrary.stopEmulation() }
            return
        }
        if(device.batteryPercent<15 && !device.charging) {
            record("AKKU_SCHUTZSTOPP","Akku unter 15 % und Gerät nicht am Ladegerät")
            finish("AKKU_SCHUTZSTOPP")
            runCatching { NativeLibrary.stopEmulation() }
            return
        }
        val now=SystemClock.elapsedRealtime()
        if(waiting) {
            if(now-startedAt>=BOOT_TIMEOUT) {
                record("START_TIMEOUT","Keine echte Eden-Startbestätigung in 100 Sekunden")
            } else ui.postDelayed(tick,SAMPLE_INTERVAL)
            return
        }
        if(!NativeLibrary.isRunning()) {
            record("CORE_UNTERBROCHEN","Core ist nicht mehr aktiv")
            return
        }
        val stats=runCatching { NativeLibrary.getPerfStats() }.getOrDefault(doubleArrayOf())
        val fps=stats.getOrNull(1)?.takeIf { it.isFinite() && it>0.0 && it<=240.0 }
        if(fps!=null && !NativeLibrary.isPaused()) readings.add(fps)
        if(now-coreAt>=RUN_WINDOW) {
            record(if(readings.size>=12) "START_UND_FPS_NACHGEWIESEN" else "NICHT_AUSREICHEND_FPS",
                "75 Sekunden reales Emulationsfenster")
        } else ui.postDelayed(tick,SAMPLE_INTERVAL)
    }

    private fun record(status:String,detail:String) {
        if(!active || index !in games.indices) return
        ui.removeCallbacks(tick)
        val sorted=readings.sorted()
        val median=if(sorted.isEmpty()) 0.0 else sorted[sorted.size/2]
        val low=if(sorted.isEmpty()) 0.0 else sorted[sorted.size/10]
        results.put(JSONObject().put("title",games[index].title)
            .put("status",status).put("detail",detail)
            .put("native_started",coreConfirmed)
            .put("fps_samples",sorted.size).put("median_fps",median)
            .put("fps_lower_10_percent",low))
        save("LAEUFT")
        ui.postDelayed({ next() },800L)
    }

    fun cancel() {
        if(!active) return
        finish("NUTZER_ABBRUCH")
        runCatching { NativeLibrary.stopEmulation() }
    }

    private fun finish(state:String) {
        active=false; ui.removeCallbacks(tick); save(state)
    }

    private fun save(state:String) {
        val file=logFile ?:return
        val raw=JSONObject().put("build",GameDeckRuntime.BUILD)
            .put("started",firstWall).put("state",state)
            .put("total",games.size).put("results",JSONArray(results.toString()))
            .toString(2)
        io.execute { runCatching { file.writeText(raw,Charsets.UTF_8) } }
    }

    fun report(activity:GameDeckActivity):String {
        val j=if(active) JSONObject().put("state","LAEUFT")
            .put("total",games.size).put("results",results)
        else runCatching { JSONObject(File(activity.filesDir,"gamedeck_auto_test/latest.json").readText()) }
            .getOrDefault(JSONObject())
        val entries=j.optJSONArray("results") ?: JSONArray()
        return buildString {
            append("GAMEDECK – AUTONOMER START- UND STABILITÄTSTEST\n")
            append("Status: ").append(j.optString("state","KEIN_BERICHT"))
            append("\nSpiele: ").append(entries.length()).append("/").append(j.optInt("total",0))
            append("\n\n")
            for(i in 0 until entries.length()) {
                val item=entries.optJSONObject(i) ?: continue
                append(item.optString("title")).append(" · ").append(item.optString("status"))
                if(item.optBoolean("native_started")) append(" · Eden-Start bestätigt")
                val n=item.optInt("fps_samples")
                if(n>0) append(" · ").append(item.optDouble("median_fps").roundToInt())
                    .append(" FPS (Median, ").append(n).append(" Messpunkte)")
                append("\n").append("  ").append(item.optString("detail")).append("\n")
            }
            append("\nKeine automatische Story-, Menü- oder Gameplay-Bestätigung.")
        }
    }
}
''',encoding="utf-8")
print("v22 sequential tester ready")
