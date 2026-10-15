# GameDeck v26b: safe actual native profile writes in automatic tests,
# opt-in comparable multi-profile benchmark and evidence-gated auto-selection.
from pathlib import Path
import sys
d=Path(sys.argv[1])/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"
def one(s,o,n,label):
 c=s.count(o)
 if c!=1:raise RuntimeError("GameDeck profile "+label+": "+str(c))
 return s.replace(o,n,1)

p=d/"GameDeckAutoTester.kt";s=p.read_text()
s=one(s,"    private var aiMode=false",
'''    private var aiMode=false
    private var benchmarkModes=emptyList<GameDeckProfileEngine.Mode>()
    private var benchmarkGame:Game?=null
    private var benchmarkOriginal:GameDeckProfileEngine.Mode?=null
    private var benchmarkResult=""''',"benchmark state")
s=one(s,'''    fun start(activity:GameDeckActivity,list:List<Game>,autoplay:Boolean=false) {''',
'''    /** Explicit manual benchmark, not disguised AI gameplay. */
    fun startBenchmark(activity:GameDeckActivity,game:Game) {
        val modes=listOf(
            GameDeckProfileEngine.Mode.PERFORMANCE,
            GameDeckProfileEngine.Mode.RECOMMENDED,
            GameDeckProfileEngine.Mode.QUALITY)
        start(activity,List(modes.size){game},false,modes)
    }

    fun start(activity:GameDeckActivity,list:List<Game>,autoplay:Boolean=false,
        profiles:List<GameDeckProfileEngine.Mode> = emptyList()) {''',"start benchmark")
s=one(s,'''        aiMode=autoplay
        GameDeckAutoPilot.reset()''',
'''        aiMode=autoplay
        benchmarkModes=if(profiles.size==list.size) profiles else emptyList()
        benchmarkGame=if(benchmarkModes.isNotEmpty()) list.firstOrNull() else null
        benchmarkOriginal=benchmarkGame?.let { GameDeckProfileEngine.selectedMode(activity,it) }
        benchmarkResult=""
        GameDeckAutoPilot.reset()''',"benchmark initial state")
s=one(s,'''                    GameDeckRuntime.begin(game,GameDeckProfileEngine.selectedMode(activity,game))
                    activity.startActivity(game.launchIntent.addFlags(''',
'''                    val mode=if(benchmarkModes.size==games.size)
                        benchmarkModes[index]
                    else GameDeckProfileEngine.selectedMode(activity,game)
                    GameDeckRuntime.begin(game,mode)
                    // A logged profile alone doesn't change Eden. Write and
                    // validate the genuine native per-game config before start.
                    GameDeckProfileEngine.apply(activity,game,mode).getOrThrow()
                    activity.startActivity(game.launchIntent.addFlags(''',"apply native profile")
s=one(s,'''        results.put(JSONObject().put("title",games[index].title)''',
'''        val recordedMode=if(benchmarkModes.size==games.size)
            benchmarkModes[index].name
            else host.get()?.let { GameDeckProfileEngine.selectedMode(it,games[index]).name } ?: "UNKNOWN"
        results.put(JSONObject().put("title",games[index].title)
            .put("profile_mode",recordedMode)''',"persist actual profile")
s=one(s,'''    private fun finish(state:String) {
        active=false; ui.removeCallbacks(tick)
        GameDeckAutoPilot.reset()
        GameDeckVisualProbe.detach()
        save(state)
    }''',
'''    private fun finish(state:String) {
        active=false; ui.removeCallbacks(tick)
        GameDeckAutoPilot.reset()
        GameDeckVisualProbe.detach()
        evaluateBenchmark(state)
        save(state)
    }

    private fun evaluateBenchmark(state:String) {
        val game=benchmarkGame ?:return
        val activity=host.get() ?:return
        val initial=benchmarkOriginal ?:return
        // Never leave a partially tested graphics mode selected by accident.
        val found=mutableListOf<Pair<GameDeckProfileEngine.Mode,Double>>()
        if(state=="ABGESCHLOSSEN" && results.length()==benchmarkModes.size) {
            for(i in 0 until results.length()) {
                val row=results.optJSONObject(i) ?:continue
                if(row.optString("status")!="START_UND_FPS_NACHGEWIESEN" ||
                    row.optInt("fps_samples")<12) continue
                val mode=benchmarkModes.getOrNull(i) ?:continue
                val p10=row.optDouble("fps_lower_10_percent",0.0)
                if(p10>0.0) found.add(mode to p10)
            }
        }
        val selected=if(found.size==benchmarkModes.size) {
            val max=found.maxOf { it.second }
            val qualityRank=mapOf(
                GameDeckProfileEngine.Mode.QUALITY to 3,
                GameDeckProfileEngine.Mode.RECOMMENDED to 2,
                GameDeckProfileEngine.Mode.PERFORMANCE to 1)
            found.filter { it.second>=max*0.9 && max-it.second<=5.0 }
                .maxByOrNull { qualityRank[it.first] ?: 0 }?.first ?:initial
        } else initial
        GameDeckProfileEngine.saveSelectedMode(activity,game,selected)
        benchmarkResult=if(found.size==benchmarkModes.size) {
            "Drei native Profile mit jeweils mindestens 12 FPS-Werten verglichen. "+
                "Messbasierte Präferenz für den nächsten Spielstart: "+selected.label+
                ". ACHTUNG: Testszene statt garantierter Gameplay-Leistung; Änderungen gelten erst beim nächsten Start."
        } else {
            "Profilvergleich nicht ausreichend belegbar; vorheriges Profil "+
                initial.label+" wurde wiederhergestellt."
        }
    }''',"evidence-based benchmark")
s=one(s,'''            .put("total",games.size).put("results",JSONArray(results.toString()))''',
'''            .put("total",games.size).put("results",JSONArray(results.toString()))
            .put("benchmark_result",benchmarkResult)''',"benchmark output json")
s=one(s,'''            val recovery=j.optString("recovery_note")''',
'''            val benchmark=j.optString("benchmark_result")
            if(benchmark.isNotBlank()) append("Profilvergleich: ")
                .append(benchmark).append("\\n\\n")
            val recovery=j.optString("recovery_note")''',"benchmark report")
s=one(s,'''                append("\\n").append("  ").append(item.optString("detail")).append("\\n")''',
'''                append("  ").append(item.optString("detail"))
                    .append(" · Profil: ").append(item.optString("profile_mode","unbekannt"))
                    .append("\\n")''',"show modes in each report")
p.write_text(s,encoding="utf-8")

p=d/"GameDeckActivity.kt";s=p.read_text()
s=one(s,'''        root.addView(infoPanel("Automatische Leistungsanalyse", advisor.details))''',
'''        root.addView(infoPanel("Automatische Leistungsanalyse", advisor.details))
        root.addView(secondaryButton("Grafikprofile automatisch vergleichen") {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Drei native Grafikprofile testen")
                .setMessage("GameDeck startet dieses Spiel je 75 Sekunden in Performance, Empfohlen und Qualität. Eden-Einstellungen werden vor jedem Durchlauf tatsächlich geschrieben.\\n\\nNur wenn alle drei Läufe genügend echte FPS-Daten liefern, wählt GameDeck ein gemessenes Profil für den NÄCHSTEN Start. Andernfalls bleibt das ursprüngliche Profil. Gleiche Startmenüs ersetzen keinen Gameplay-Benchmark.\\n\\nDauer und Akkubelastung können hoch sein.")
                .setPositiveButton("Drei Profile testen") { _, _ ->
                    GameDeckAutoTester.startBenchmark(this,game)
                }.setNegativeButton("Abbrechen",null).show()
        })''',"bench button")
p.write_text(s,encoding="utf-8")
print("GameDeck v26b: real native graphics application and opt-in A/B/C comparison")
