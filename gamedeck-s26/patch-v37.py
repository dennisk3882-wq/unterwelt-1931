# GameDeck v37: let observed per-game action feedback influence genre policies.
# Pixel/image response is a weak signal, not proof of objective progress or victory.
from pathlib import Path
import sys

d = Path(sys.argv[1]) / "overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"

def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError("v37 " + label + ": expected one anchor, found " + str(count))
    return text.replace(old, new, 1)

p = d / "GameDeckAutoPilot.kt"
s = p.read_text(encoding="utf-8")

s = once(s, '''    private fun chooseWithEpisodeMemory(base:Choice):Choice {
        if(consecutiveNoResponse<2) return base''', '''    private var adaptivePolicyUsed=false
    private var adaptiveBestActionScore=0.0

    private fun chooseWithEpisodeMemory(base:Choice):Choice {
        adaptivePolicyUsed=false
        adaptiveBestActionScore=0.0
        // The genre macros in decide() are only a fallback. Once this game has
        // enough feedback, prefer actions with a better persisted image-response
        // history. Image motion is not a reward for completing the objective.
        if(learningEvidence>=3) {
            val candidates=when(strategy) {
                "RACING" -> listOf(
                    Choice("Gas",KeyEvent.KEYCODE_BUTTON_A,reason="Adaptiv: beschleunigen"),
                    Choice("Rechts lenken",axis=MotionEvent.AXIS_X,value=0.42f,reason="Adaptiv: rechts lenken"),
                    Choice("Links lenken",axis=MotionEvent.AXIS_X,value=-0.42f,reason="Adaptiv: links lenken"),
                    Choice("Bremse/Alternative",KeyEvent.KEYCODE_BUTTON_B,reason="Adaptiv: alternative Taste"))
                "PLATFORMER" -> listOf(
                    Choice("Vorwärts",axis=MotionEvent.AXIS_X,value=0.60f,reason="Adaptiv: vorwärts"),
                    Choice("Springen",KeyEvent.KEYCODE_BUTTON_B,reason="Adaptiv: springen"),
                    Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,reason="Adaptiv: interagieren"),
                    Choice("Rückwärts",axis=MotionEvent.AXIS_X,value=-0.55f,reason="Adaptiv: rückwärts"))
                "PUZZLE" -> listOf(
                    Choice("Rechts",KeyEvent.KEYCODE_DPAD_RIGHT,reason="Adaptiv: rechts"),
                    Choice("Unten",KeyEvent.KEYCODE_DPAD_DOWN,reason="Adaptiv: unten"),
                    Choice("Links",KeyEvent.KEYCODE_DPAD_LEFT,reason="Adaptiv: links"),
                    Choice("Oben",KeyEvent.KEYCODE_DPAD_UP,reason="Adaptiv: oben"),
                    Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,reason="Adaptiv: bestätigen"),
                    Choice("Zurück",KeyEvent.KEYCODE_BUTTON_B,reason="Adaptiv: zurück"))
                else -> listOf(
                    Choice("Rechts",KeyEvent.KEYCODE_DPAD_RIGHT,reason="Adaptiv: rechts erkunden"),
                    Choice("Links",KeyEvent.KEYCODE_DPAD_LEFT,reason="Adaptiv: links erkunden"),
                    Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,reason="Adaptiv: interagieren"),
                    Choice("Zurück",KeyEvent.KEYCODE_BUTTON_B,reason="Adaptiv: zurück"))
            }
            val best=candidates.maxByOrNull { responseScore(it.label) }
            val score=best?.let { responseScore(it.label) } ?: 0.0
            if(best!=null && score>0.65) {
                adaptivePolicyUsed=true
                adaptiveBestActionScore=score
                lastOutcome="Adaptiv: Aktion mit besserer gespeicherter Bildreaktion gewählt; kein Fortschrittsnachweis"
                return best.copy(reason=best.reason+"; anhand vorheriger Bildreaktionen für dieses Spiel bevorzugt")
            }
        }
        if(consecutiveNoResponse<2) return base''', "feedback-guided action selection")

s = once(s, '''        .put("objective_progress_verified",false)''', '''        .put("objective_progress_verified",false)
        .put("adaptive_policy_used",adaptivePolicyUsed)
        .put("adaptive_best_action_score",adaptiveBestActionScore)''', "adaptive telemetry")

p.write_text(s, encoding="utf-8")

p = d / "GameDeckRuntime.kt"
s = p.read_text(encoding="utf-8")
s = once(s, 'const val BUILD = "GameDeck v36"', 'const val BUILD = "GameDeck v37"', "runtime version")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckUpdateChecker.kt"
s = p.read_text(encoding="utf-8")
s = once(s, "private const val LOCAL_VERSION = 36", "private const val LOCAL_VERSION = 37", "updater version")
p.write_text(s, encoding="utf-8")

print("GameDeck v37 per-game response feedback now overrides genre macros when evidence is sufficient")
