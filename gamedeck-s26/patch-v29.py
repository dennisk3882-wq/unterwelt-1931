# GameDeck v29: visible live AI test HUD and explicit agent telemetry.
from pathlib import Path
import sys
d=Path(sys.argv[1])/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"
def once(s,old,new,label):
    n=s.count(old)
    if n!=1: raise RuntimeError("v29 "+label+": "+str(n)+" anchors")
    return s.replace(old,new,1)

Path("gamedeck-s26/GameDeckAiLiveOverlay.kt").read_text(encoding="utf-8")
(d/"GameDeckAiLiveOverlay.kt").write_text(
    Path("gamedeck-s26/GameDeckAiLiveOverlay.kt").read_text(encoding="utf-8"),
    encoding="utf-8")

p=d/"GameDeckAutoPilot.kt"; s=p.read_text(encoding="utf-8")
s=once(s,"    fun setEnabled(enabled:Boolean) {",
'''    /** Public read-only state for the in-game live status HUD. */
    fun isRunning():Boolean = running

    fun setEnabled(enabled:Boolean) {''',"public running state")
s=once(s,'.put("controller_source",controlSource)',
''' .put("controller_source",controlSource)
        .put("live_running",running)
        .put("live_step_count",stepCount)
        .put("live_last_action",lastLabel)
        .put("live_last_outcome",lastOutcome)''',"live telemetry")
p.write_text(s,encoding="utf-8")

p=d/"GameDeckRuntime.kt"; s=p.read_text(encoding="utf-8")
s=once(s,'const val BUILD = "GameDeck v28"','const val BUILD = "GameDeck v29"',"runtime version")
p.write_text(s,encoding="utf-8")
p=d/"GameDeckUpdateChecker.kt"; s=p.read_text(encoding="utf-8")
s=once(s,'private const val LOCAL_VERSION = 28','private const val LOCAL_VERSION = 29',"updater version")
p.write_text(s,encoding="utf-8")
print("GameDeck v29 live AI HUD and runtime version integrated")
