# GameDeck v35: scene-transition and scene-dwell telemetry for closed-loop decision support.
# Transitions are observable signals, not proof of objective progress or victory.
from pathlib import Path
import sys

d = Path(sys.argv[1]) / "overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"

def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError("v35 " + label + ": expected one anchor, found " + str(count))
    return text.replace(old, new, 1)

p = d / "GameDeckSceneObserver.kt"
s = p.read_text(encoding="utf-8")
s = once(s, '''    private var stable="UNKNOWN"
    private var consecutive=0''', '''    private var stable="UNKNOWN"
    private var previousStable="UNKNOWN"
    private var sceneTransitions=0
    private var stableSince=0L
    private var consecutive=0''', "scene transition state")
s = once(s, '''        stable="UNKNOWN";consecutive=0;maxLoading=0;excerpt=""''', '''        stable="UNKNOWN";previousStable="UNKNOWN";sceneTransitions=0
        stableSince=0L;consecutive=0;maxLoading=0;excerpt=""''', "reset transition state")
s = once(s, '''                    stable=if(consecutive>=2)candidate else "UNKNOWN"
                    if(stable=="LOADING")maxLoading=maxOf(maxLoading,consecutive)''', '''                    stable=if(consecutive>=2)candidate else "UNKNOWN"
                    if(stable!=previousStable) {
                        previousStable=stable
                        sceneTransitions++
                        stableSince=SystemClock.elapsedRealtime()
                    }
                    if(stable=="LOADING")maxLoading=maxOf(maxLoading,consecutive)''', "stable scene transitions")
s = once(s, '''        .put("consecutive_matches",consecutive)
        .put("longest_loading_sequence",maxLoading)''', '''        .put("consecutive_matches",consecutive)
        .put("scene_transition_count",sceneTransitions)
        .put("scene_stable_age_ms",if(stableSince==0L) -1L else SystemClock.elapsedRealtime()-stableSince)
        .put("longest_loading_sequence",maxLoading)''', "scene transition telemetry")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckRuntime.kt"
s = p.read_text(encoding="utf-8")
s = once(s, 'const val BUILD = "GameDeck v34"', 'const val BUILD = "GameDeck v35"', "runtime version")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckUpdateChecker.kt"
s = p.read_text(encoding="utf-8")
s = once(s, 'private const val LOCAL_VERSION = 34', 'private const val LOCAL_VERSION = 35', "updater version")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckAiLiveOverlay.kt"
s = p.read_text(encoding="utf-8")
s = once(s, '''        val sceneAge = scene?.optLong("capture_age_ms", -1L) ?: -1L''', '''        val sceneAge = scene?.optLong("capture_age_ms", -1L) ?: -1L
        val sceneTransitions = scene?.optInt("scene_transition_count", 0) ?: 0
        val sceneStableAge = scene?.optLong("scene_stable_age_ms", -1L) ?: -1L''', "HUD scene transition values")
s = once(s, '''            "Szene: $sceneName · Sperre: $sceneGate · Bildalter: " +''', '''            "Szene: $sceneName · Sperre: $sceneGate · Wechsel: $sceneTransitions · stabil: " +
            (if (sceneStableAge < 0L) "unbekannt" else "${sceneStableAge}ms") + " · Bildalter: " +''', "HUD transition display")
p.write_text(s, encoding="utf-8")

print("GameDeck v35 scene-transition and stable-duration telemetry integrated")
