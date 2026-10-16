# GameDeck v34: faster local scene sampling and explicit OCR freshness telemetry.
from pathlib import Path
import sys

d = Path(sys.argv[1]) / "overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"

def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError("v34 " + label + ": expected one anchor, found " + str(count))
    return text.replace(old, new, 1)

p = d / "GameDeckSceneObserver.kt"
s = p.read_text(encoding="utf-8")
s = once(s, "if(now-lastCapture<7200L)return", "if(now-lastCapture<2800L)return", "scene sampling cadence")
s = once(s, '''        .put("errors",errors).put("excerpt",excerpt)
        .put("level_completed_proven",false)''', '''        .put("errors",errors).put("excerpt",excerpt)
        .put("capture_interval_ms",2800)
        .put("capture_age_ms",if(lastCapture==0L) -1L else SystemClock.elapsedRealtime()-lastCapture)
        .put("level_completed_proven",false)''', "OCR freshness telemetry")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckRuntime.kt"
s = p.read_text(encoding="utf-8")
s = once(s, 'const val BUILD = "GameDeck v33"', 'const val BUILD = "GameDeck v34"', "runtime version")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckUpdateChecker.kt"
s = p.read_text(encoding="utf-8")
s = once(s, 'private const val LOCAL_VERSION = 33', 'private const val LOCAL_VERSION = 34', "updater version")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckAiLiveOverlay.kt"
s = p.read_text(encoding="utf-8")
s = once(s, '''        val sceneName = scene?.optString("scene", "UNKNOWN") ?: "UNKNOWN"''', '''        val sceneName = scene?.optString("scene", "UNKNOWN") ?: "UNKNOWN"
        val sceneAge = scene?.optLong("capture_age_ms", -1L) ?: -1L''', "HUD scene age")
s = once(s, '''            "Szene: $sceneName · Eingabesperre: $sceneGate" +''', '''            "Szene: $sceneName · Sperre: $sceneGate · Bildalter: " +
            (if (sceneAge < 0L) "unbekannt" else "${sceneAge}ms") +''', "HUD freshness")
p.write_text(s, encoding="utf-8")

print("GameDeck v34 faster OCR sampling and freshness telemetry integrated")

# Trigger build after workflow integration for v34.

# Re-trigger build after release workflow skips older tags.
