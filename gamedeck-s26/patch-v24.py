# GameDeck S26 v24: smarter evidence, crash recovery, explicit preview updates
from pathlib import Path
import sys
root=Path(sys.argv[1])
d=root/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"
def replace_one(text, old, new, label):
    count=text.count(old)
    if count!=1: raise RuntimeError("v24 "+label+": "+str(count)+" anchors")
    return text.replace(old,new,1)

tester=d/"GameDeckAutoTester.kt"
s=tester.read_text(encoding="utf-8")
s=replace_one(s,'''    fun isActive()=active''','''    /** A killed Android process cannot be resumed blindly. Preserve evidence. */
    fun recoverInterruptedRun(context:Context) {
        if(active) return
        val file=File(context.filesDir,"gamedeck_auto_test/latest.json")
        if(!file.isFile) return
        runCatching {
            val previous=JSONObject(file.readText(Charsets.UTF_8))
            val state=previous.optString("state")
            if(state=="LAEUFT" || state=="GESTARTET") {
                previous.put("state","UNTERBROCHEN_NACH_PROZESSENDE")
                previous.put("recovery_note",
                    "Android hat den Testprozess unterbrochen. Dies ist kein bestätigter Spielfehler. Test manuell erneut starten.")
                val queueAt=previous.optInt("total")
                val checked=previous.optJSONArray("results")?.length() ?: 0
                previous.put("last_unfinished_index",checked)
                previous.put("unverified_remaining",maxOf(0,queueAt-checked))
                file.writeText(previous.toString(2),Charsets.UTF_8)
            }
        }
    }

    fun isActive()=active''',"process recovery")
s=replace_one(s,'''        val entry=JSONObject().put''','''        val entry=JSONObject().put''',"noop") if "        val entry=JSONObject().put" in s else s
s=replace_one(s,'            append("\\n\\n")\n            for(i in 0 until entries.length()) {',
'''            append("\\n\\n")
            val recovery=j.optString("recovery_note")
            if(recovery.isNotBlank()) append("Wiederherstellung: ").append(recovery).append("\\n\\n")
            for(i in 0 until entries.length()) {''',"recovery in report")
tester.write_text(s,encoding="utf-8")

visual=d/"GameDeckVisualProbe.kt"
v=visual.read_text(encoding="utf-8")
v=replace_one(v,"    private var motionTotal=0.0",
'''    private var motionTotal=0.0
    private var lumaVarianceSum=0.0
    private var textureSum=0.0
    private var changingFrames=0
    private var colorfulFrames=0
    private var substantialLumaFrames=0''',"additional frame quality evidence")
v=replace_one(v,"captures=0;dark=0;unchanged=0;copyFailures=0;motionTotal=0.0;prev=null",
'''captures=0;dark=0;unchanged=0;copyFailures=0;motionTotal=0.0;prev=null
        lumaVarianceSum=0.0; textureSum=0.0; changingFrames=0
        colorfulFrames=0; substantialLumaFrames=0''',"frame counters reset")
v=replace_one(v,"        var almostBlack=0",
'''        var almostBlack=0
        var colorSum=0.0''',"color score")
v=replace_one(v,"            sum+=l",
'''            sum+=l
            colorSum+=(maxOf(r,g,b)-minOf(r,g,b))''',"color variation")
v=replace_one(v,"        captures++",
'''        captures++
        val mean=sum/64.0
        var spread=0.0
        var texture=0.0
        for(y in 0 until 8) for(x in 0 until 8) {
            val value=cells[y*8+x].toDouble()
            spread+=(value-mean)*(value-mean)
            if(x>0) texture+=kotlin.math.abs(cells[y*8+x]-cells[y*8+x-1])
            if(y>0) texture+=kotlin.math.abs(cells[y*8+x]-cells[(y-1)*8+x])
        }
        lumaVarianceSum+=spread/64.0
        textureSum+=texture/(112.0*255.0)
        if(colorSum/64.0>18.0) colorfulFrames++
        if(mean>35.0) substantialLumaFrames++''',"variance and texture")
v=replace_one(v,"            if(diff<0.007) unchanged++",
'''            if(diff<0.007) unchanged++
            if(diff>0.06) changingFrames++''',"visually meaningful transitions")
v=replace_one(v,"    fun snapshot():JSONObject {",
'''    private fun classify():String = when {
        captures==0 -> "KEINE_BILDPROBEN"
        captures>=4 && dark>=captures-1 -> "DUNKLE_BILDABFOLGE"
        captures>=4 && changingFrames>=2 -> "WECHSELNDE_BILDER"
        captures>=5 && unchanged>=captures-2 -> "STATISCHE_BILDABFOLGE"
        captures>=3 && substantialLumaFrames>=2 -> "SICHTBARE_BILDER"
        else -> "NICHT_EINDEUTIG"
    }

    fun snapshot():JSONObject {''',"video outcome classification")
v=replace_one(v,'''.put("mean_motion_ratio",if(captures>1) motionTotal/(captures-1) else 0.0)''',
'''.put("mean_motion_ratio",if(captures>1) motionTotal/(captures-1) else 0.0)
            .put("mean_luma_variance",if(captures>0) lumaVarianceSum/captures else 0.0)
            .put("mean_texture_ratio",if(captures>0) textureSum/captures else 0.0)
            .put("changing_frames",changingFrames)
            .put("colorful_frames",colorfulFrames)
            .put("brightness_frames",substantialLumaFrames)
            .put("classification",classify())
            .put("gameplay_proven",false)''',"classification export")
visual.write_text(v,encoding="utf-8")

smart=d/"GameDeckSmartAnalyzer.kt"
a=smart.read_text(encoding="utf-8")
a=replace_one(a,"        val failures=visual.optInt(\"copy_failures\",0)",
'''        val failures=visual.optInt("copy_failures",0)
        val frameClass=visual.optString("classification","NICHT_EINDEUTIG")''',"frame category")
a=replace_one(a,'''        if(samples==0 && failures>0) findings.put(''',
'''        when(frameClass) {
            "WECHSELNDE_BILDER" -> findings.put(
                "Bildausgabe wechselt tatsächlich. Dynamische Bilder alleine bestätigen keine Steuerbarkeit.")
            "SICHTBARE_BILDER" -> findings.put(
                "Android hat nichtschwarze Spielbilder erfasst; eine erfolgreiche Menü- oder Spielinteraktion ist nicht bewiesen.")
            "STATISCHE_BILDABFOLGE" -> findings.put(
                "Statische Bildabfolge: Menü, pausiertes Bild oder Hänger kann nur durch Interaktion unterschieden werden.")
            "DUNKLE_BILDABFOLGE" -> findings.put(
                "Dunkle Bildabfolge: Bei FPS ohne sichtbare Ausgabe möglichen Grafik-/Darstellungsfehler prüfen.")
        }
        if(samples==0 && failures>0) findings.put(''',"frame interpretation")
a=replace_one(a,'''            .put("confidence",confidence)''',
'''            .put("confidence",confidence)
            .put("visual_classification",frameClass)
            .put("evidence_version",2)''',"analysis version")
smart.write_text(a,encoding="utf-8")

activity=d/"GameDeckActivity.kt"
ui=activity.read_text(encoding="utf-8")
needle="        GameDeckUpdateChecker.check(this, false)"
ui=replace_one(ui,needle,
'''        GameDeckAutoTester.recoverInterruptedRun(this)
        GameDeckUpdateChecker.check(this, false)''',"recovery on launch")
activity.write_text(ui,encoding="utf-8")

version=d/"GameDeckRuntime.kt"
x=version.read_text(encoding="utf-8")
x=replace_one(x,'const val BUILD = "GameDeck v23"','const val BUILD = "GameDeck v24"',"runtime version")
version.write_text(x,encoding="utf-8")
updater=d/"GameDeckUpdateChecker.kt"
u=updater.read_text(encoding="utf-8")
u=replace_one(u,"private const val LOCAL_VERSION = 23","private const val LOCAL_VERSION = 24","update version")
u=replace_one(u,"        pending=true\n        Thread {",
'''        pending=true
        val previewsEnabled=prefs.getBoolean("preview_channel",false)
        Thread {''',"update preview setting")
u=replace_one(u,
'''                        if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue''',
'''                        if (release.optBoolean("draft")) continue
                        if (release.optBoolean("prerelease") && !previewsEnabled) continue''',"preview filtering")
u=replace_one(u,
'''                        AlertDialog.Builder(activity).setTitle("GameDeck-Update")
                            .setMessage("Aktuell keine neuere GameDeck-APK als signiertes GitHub-Release veröffentlicht. Die Prüfung funktioniert erst, wenn eine eigene GameDeck-Release mit APK bereitsteht.")
                            .setPositiveButton("OK",null).show()''',
'''                        AlertDialog.Builder(activity).setTitle("GameDeck-Update")
                            .setMessage(if(previewsEnabled)
                                "Kein neueres GameDeck-Release gefunden. Vorabversionen sind aktiviert."
                                else "Kein neueres stabiles GameDeck-Release gefunden. Du kannst auf Wunsch experimentelle Vorabversionen freigeben.")
                            .setPositiveButton("OK",null)
                            .setNeutralButton(if(previewsEnabled) "Nur stabile Versionen" else "Vorabversionen zulassen") { _, _ ->
                                prefs.edit().putBoolean("preview_channel",!previewsEnabled)
                                    .remove("last_check").apply()
                                check(activity,true)
                            }.show()''',"manual preview channel controls")
updater.write_text(u,encoding="utf-8")
print("GameDeck v24: safe crash recovery, expanded real-frame evidence and opt-in preview updates")
