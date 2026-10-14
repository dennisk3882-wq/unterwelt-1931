from pathlib import Path
import sys
root=Path(sys.argv[1])
folder=root/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"
p=folder/"GameDeckActivity.kt"
s=p.read_text(encoding="utf-8")
old='        root.addView(sectionTitle("Spielinfos & Bilder"))'
new='''        val advisor = GameDeckPerformanceAdvisor.analyze(game)
        root.addView(infoPanel("Automatische Leistungsanalyse", advisor.details))
        val candidate = advisor.recommended
        if (candidate != null && candidate != selectedMode) {
            root.addView(secondaryButton("Gemessenes Profil übernehmen: " + candidate.label) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Profil aus Messwerten wählen")
                    .setMessage(advisor.details + "\\n\\nDie Empfehlung stammt aus deinen aufgezeichneten Spielsitzungen. Ein anderes Spiel oder Update kann abweichend reagieren. Das Profil wird erst beim nächsten Start angewendet.")
                    .setPositiveButton("Profil übernehmen") { _, _ ->
                        GameDeckProfileEngine.saveSelectedMode(this, game, candidate)
                        renderGameDetail(game)
                    }.setNegativeButton("Abbrechen", null).show()
            }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) } })
        }
        root.addView(sectionTitle("Spielinfos & Bilder"))'''
if s.count(old)!=1:raise RuntimeError("v20 activity anchor")
s=s.replace(old,new,1)
p.write_text(s,encoding="utf-8")
(folder/"GameDeckPerformanceAdvisor.kt").write_text(r'''// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.gamedeck

import org.json.JSONObject
import org.yuzu.yuzu_emu.model.Game
import kotlin.math.roundToInt

/**
 * Works only with real NativeLibrary.getPerfStats() data collected by
 * GameDeckRuntime during gameplay. No synthetic frames or imaginary tests.
 * Different profile comparisons require real sessions for both profiles.
 */
object GameDeckPerformanceAdvisor {
    data class Result(val details: String, val recommended: GameDeckProfileEngine.Mode?)
    private data class Recorded(
        val mode: GameDeckProfileEngine.Mode,
        val median: Double,
        val low10: Double,
        val samples: Int
    )
    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size/2
        return if (sorted.size%2==1) sorted[mid] else (sorted[mid-1]+sorted[mid])/2
    }
    fun analyze(game: Game): Result {
        val records = GameDeckRuntime.reports().mapNotNull { file ->
            runCatching {
                val report = JSONObject(file.readText())
                if (report.optString("id") != game.programId ||
                    report.optString("core") != GameDeckRuntime.CORE.take(12)) return@runCatching null
                val mode = runCatching {
                    GameDeckProfileEngine.Mode.valueOf(report.optString("mode"))
                }.getOrNull() ?: return@runCatching null
                val arr = report.optJSONArray("samples") ?: return@runCatching null
                val fps = mutableListOf<Double>()
                var earliest=Long.MAX_VALUE
                var latest=0L
                for (i in 0 until arr.length()) {
                    val sample=arr.optJSONObject(i) ?: continue
                    if (sample.optBoolean("paused", false)) continue
                    val value=sample.optDouble("fps",0.0)
                    if (!value.isFinite() || value < 3.0 || value > 240.0) continue
                    fps.add(value)
                    val t=sample.optLong("t",0L)
                    earliest=minOf(earliest,t); latest=maxOf(latest,t)
                }
                if (fps.size < 12 || latest-earliest < 55000L) return@runCatching null
                val sorted=fps.sorted()
                Recorded(mode,median(sorted),sorted[(sorted.size/10).coerceAtLeast(0)],sorted.size)
            }.getOrNull()
        }
        if (records.isEmpty()) return Result(
            "Eden misst während eines laufenden Spiels automatisch die Bildrate. " +
            "Nach mindestens einer Minute mit gültigen Werten zeigt GameDeck echte Ergebnisse. " +
            "Aktuell sind noch keine ausreichend langen Sitzungen für dieses Spiel vorhanden.",
            null)
        val byMode=records.groupBy { it.mode }.mapValues { (_,v) ->
            Recorded(v.first().mode,median(v.map { it.median }),median(v.map { it.low10 }),v.sumOf { it.samples })
        }
        val current=GameDeckProfileEngine.selectedMode(GameDeckRuntime.appContext(),game)
        val descriptions=byMode.values.sortedBy { it.mode.ordinal }.joinToString("\n") {
            it.mode.label + ": Median " + it.median.roundToInt() + " FPS · " +
                "10%-Untergrenze " + it.low10.roundToInt() + " FPS · " +
                it.samples + " Messpunkte"
        }
        if (byMode.size<2) return Result(
            "Echte FPS aus Eden:\n" + descriptions +
            "\nFür einen belastbaren Profilvergleich mindestens zwei Profile mit " +
            "je mindestens einer Minute tatsächlicher Spielzeit ausprobieren. " +
            "Die 10%-Untergrenze ist keine exakte 1%-Low-Messung.",null)
        // Prefer stability first, then quality if p10 is within a small verified
        // margin. Never change the profile silently or during gameplay.
        val highest=byMode.values.maxByOrNull { it.low10 }!!
        val similar=byMode.values.filter {
            it.low10 >= highest.low10*0.9 && highest.low10-it.low10 <= 5.0
        }
        val qualityRank=mapOf(
            GameDeckProfileEngine.Mode.QUALITY to 4,
            GameDeckProfileEngine.Mode.RECOMMENDED to 3,
            GameDeckProfileEngine.Mode.PERFORMANCE to 2,
            GameDeckProfileEngine.Mode.COOL to 1
        )
        val chosen=similar.maxWithOrNull(
            compareBy<Recorded> { qualityRank[it.mode] ?: 0 }.thenBy { it.low10 }
        )?.mode ?: highest.mode
        return Result(
            "Echte FPS aus Eden:\n" + descriptions +
            "\nEmpfehlung: " + chosen.label +
            ". Vergleich nach Stabilität der unteren 10% gültiger Messwerte, bei ähnlicher " +
            "Stabilität mit Vorrang für Bildqualität.\n" +
            "Kein automatischer Spieldurchlauf; Messwerte hängen von der Szene ab." ,
            if (chosen==current) null else chosen)
    }
}
''',encoding="utf-8")
print("v20: real FPS analysis and per-title recommendation")
