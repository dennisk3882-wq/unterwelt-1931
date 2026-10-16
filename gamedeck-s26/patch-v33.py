# GameDeck v33: OCR scene-aware action gate for loading, pause, terminal/error states.
from pathlib import Path
import sys

d = Path(sys.argv[1]) / "overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"

def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError("v33 " + label + ": expected one anchor, found " + str(count))
    return text.replace(old, new, 1)

p = d / "GameDeckAutoPilot.kt"
s = p.read_text(encoding="utf-8")
s = once(s, '''    private var episodeRecoveryCount=0''', '''    private var episodeRecoveryCount=0
    // Stable OCR scene states that should suspend autonomous controller input.
    private var sceneGate="NONE"''', "scene gate state")

s = once(s, '''        val baseChoice=decide(frame) ?: return
        val choice=chooseWithEpisodeMemory(baseChoice)''', '''        val observedScene=runCatching {
            GameDeckSceneObserver.snapshot().optString("scene","UNKNOWN")
        }.getOrDefault("UNKNOWN")
        sceneGate=when(observedScene) {
            "LOADING" -> "LOADING"
            "LOAD_ERROR" -> "LOAD_ERROR"
            "PAUSE" -> "PAUSE"
            "POSSIBLE_SUCCESS" -> "POSSIBLE_SUCCESS"
            "GAME_OVER" -> "GAME_OVER"
            else -> "NONE"
        }
        if(sceneGate!="NONE") {
            lastOutcome=when(sceneGate) {
                "LOADING" -> "Steuerung pausiert: Ladebildschirm erkannt"
                "LOAD_ERROR" -> "Steuerung pausiert: möglicher Ladefehler erkannt"
                "PAUSE" -> "Steuerung pausiert: Pausenmenü erkannt"
                "POSSIBLE_SUCCESS" -> "Steuerung pausiert: möglicher Abschluss; nicht als Sieg bestätigt"
                else -> "Steuerung pausiert: Game-Over-Szene erkannt"
            }
            return
        }
        val baseChoice=decide(frame) ?: return
        val choice=chooseWithEpisodeMemory(baseChoice)''', "scene-aware input gate")

s = once(s, '''        .put("live_last_outcome",lastOutcome)
        .put("strategy",strategy)''', '''        .put("live_last_outcome",lastOutcome)
        .put("scene_gate",sceneGate)
        .put("strategy",strategy)''', "live scene gate telemetry")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckAiLiveOverlay.kt"
s = p.read_text(encoding="utf-8")
s = once(s, '''        val outcome = agent?.optString("live_last_outcome", "noch keine Rückmeldung") ?: "noch keine Rückmeldung"''', '''        val outcome = agent?.optString("live_last_outcome", "noch keine Rückmeldung") ?: "noch keine Rückmeldung"
        val sceneGate = agent?.optString("scene_gate", "NONE") ?: "NONE"''', "HUD gate value")
s = once(s, '''            "Bildreaktion: $outcome\\nSzene: $sceneName" +''', '''            "Bildreaktion: $outcome\\nSzene: $sceneName · Eingabesperre: $sceneGate" +''', "HUD gate display")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckRuntime.kt"
s = p.read_text(encoding="utf-8")
s = once(s, 'const val BUILD = "GameDeck v32"', 'const val BUILD = "GameDeck v33"', "runtime version")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckUpdateChecker.kt"
s = p.read_text(encoding="utf-8")
s = once(s, 'private const val LOCAL_VERSION = 32', 'private const val LOCAL_VERSION = 33', "updater version")
p.write_text(s, encoding="utf-8")

print("GameDeck v33 stable OCR scene-aware input gate integrated")
