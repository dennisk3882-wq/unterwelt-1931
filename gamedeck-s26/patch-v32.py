# GameDeck v32: persistent episode memory and bounded anti-loop recovery.
# This remains feedback from visual change, not proof of task success.
from pathlib import Path
import sys

d = Path(sys.argv[1]) / "overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"

def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError("v32 " + label + ": expected one anchor, found " + str(count))
    return text.replace(old, new, 1)

p = d / "GameDeckAutoPilot.kt"
s = p.read_text(encoding="utf-8")

s = once(s, '''    private var lastActionKey=""''', '''    private var lastActionKey=""
    // Per-game episode memory: recent actions and no-response recovery.
    private val recentEpisodeActions=mutableListOf<String>()
    private var consecutiveNoResponse=0
    private var episodeRecoveryCount=0''', "episode state")

s = once(s, '''        gameKey=programId.ifBlank { "unknown" }''', '''        gameKey=programId.ifBlank { "unknown" }
        recentEpisodeActions.clear()
        val savedEpisode=prefs?.getString("episode_actions_"+gameKey.hashCode(),"").orEmpty()
        savedEpisode.split("|").filter { it.isNotBlank() }.takeLast(8)
            .forEach { recentEpisodeActions.add(it) }
        consecutiveNoResponse=0
        episodeRecoveryCount=0''', "restore per-game episode")

s = once(s, '''            if(reacted) {
                responses++
                lastOutcome="Veränderung der Bildproben beobachtet"
            } else {
                noResponses++
                lastOutcome="keine sichere Bildänderung beobachtet"
            }
            learn(lastActionKey,reacted)''', '''            if(reacted) {
                responses++
                consecutiveNoResponse=0
                lastOutcome="Veränderung der Bildproben beobachtet"
            } else {
                noResponses++
                consecutiveNoResponse++
                lastOutcome="keine sichere Bildänderung beobachtet"
            }
            rememberEpisodeAction(lastActionKey)
            learn(lastActionKey,reacted)''', "feedback memory")

s = once(s, '''        val choice=decide(frame)''', '''        val baseChoice=decide(frame)
        val choice=chooseWithEpisodeMemory(baseChoice)''', "memory-guided choice")

s = once(s, '''    private fun issue(choice:Choice) {''', '''    /**
     * If several fresh frames show no visible reaction, avoid repeating the
     * same recent action cycle. This is a bounded exploration fallback; pixel
     * motion is only feedback, never evidence of winning or progress.
     */
    private fun rememberEpisodeAction(action:String) {
        if(action.isBlank()) return
        recentEpisodeActions.add(action)
        while(recentEpisodeActions.size>8) recentEpisodeActions.removeAt(0)
        prefs?.edit()
            ?.putString("episode_actions_"+gameKey.hashCode(),
                recentEpisodeActions.joinToString("|"))
            ?.apply()
    }

    private fun chooseWithEpisodeMemory(base:Choice):Choice {
        if(consecutiveNoResponse<2) return base
        val alternatives=when(strategy) {
            "PLATFORMER" -> listOf(
                Choice("Vorwärts",axis=MotionEvent.AXIS_X,value=0.65f,
                    reason="Episode-Recovery: Bewegungsalternative"),
                Choice("Rückwärts",axis=MotionEvent.AXIS_X,value=-0.55f,
                    reason="Episode-Recovery: Gegenrichtung testen"),
                Choice("Springen",KeyEvent.KEYCODE_BUTTON_B,
                    reason="Episode-Recovery: Sprungreaktion testen"),
                Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,
                    reason="Episode-Recovery: Interaktion testen"))
            "RACING" -> listOf(
                Choice("Gas",KeyEvent.KEYCODE_BUTTON_A,
                    reason="Episode-Recovery: Beschleunigung testen"),
                Choice("Rechts lenken",axis=MotionEvent.AXIS_X,value=0.55f,
                    reason="Episode-Recovery: Lenkalternative"),
                Choice("Links lenken",axis=MotionEvent.AXIS_X,value=-0.55f,
                    reason="Episode-Recovery: Gegenlenken testen"),
                Choice("Bremse/Alternative",KeyEvent.KEYCODE_BUTTON_B,
                    reason="Episode-Recovery: alternative Taste testen"))
            "PUZZLE" -> listOf(
                Choice("Rechts",KeyEvent.KEYCODE_DPAD_RIGHT,
                    reason="Episode-Recovery: Puzzle-Schritt wechseln"),
                Choice("Unten",KeyEvent.KEYCODE_DPAD_DOWN,
                    reason="Episode-Recovery: Puzzle-Schritt wechseln"),
                Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,
                    reason="Episode-Recovery: Auswahl bestätigen"),
                Choice("Zurück",KeyEvent.KEYCODE_BUTTON_B,
                    reason="Episode-Recovery: Auswahl zurücknehmen"))
            else -> listOf(
                Choice("Rechts",KeyEvent.KEYCODE_DPAD_RIGHT,
                    reason="Episode-Recovery: Erkundungsalternative"),
                Choice("Links",KeyEvent.KEYCODE_DPAD_LEFT,
                    reason="Episode-Recovery: Gegenrichtung erkunden"),
                Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,
                    reason="Episode-Recovery: Interaktion testen"),
                Choice("Zurück",KeyEvent.KEYCODE_BUTTON_B,
                    reason="Episode-Recovery: alternative Taste testen"))
        }
        val recent=recentEpisodeActions.takeLast(4).toSet()
        val alternate=alternatives.firstOrNull {
            it.label!=base.label && it.label !in recent
        } ?: alternatives.firstOrNull { it.label!=base.label }
        if(alternate!=null) {
            episodeRecoveryCount++
            lastOutcome="Keine sichere Bildreaktion: Aktion anhand der Episodenhistorie gewechselt"
            return alternate.copy(reason=alternate.reason+"; keine Wiederholung der letzten Aktionsfolge")
        }
        return base
    }

    private fun issue(choice:Choice) {''', "episode recovery policy")

s = once(s, '''        .put("learning_feedback_samples",learningEvidence)''', '''        .put("learning_feedback_samples",learningEvidence)
        .put("episode_recoveries",episodeRecoveryCount)
        .put("no_response_streak",consecutiveNoResponse)
        .put("recent_action_memory",org.json.JSONArray(recentEpisodeActions))''', "episode telemetry")

s = once(s, '''        .put("goal",goal)''', '''        .put("goal",goal)
        .put("episode_recoveries",episodeRecoveryCount)
        .put("no_response_streak",consecutiveNoResponse)''', "live HUD telemetry")

p.write_text(s, encoding="utf-8")

p = d / "GameDeckRuntime.kt"
s = p.read_text(encoding="utf-8")
s = once(s, 'const val BUILD = "GameDeck v31"', 'const val BUILD = "GameDeck v32"', "runtime version")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckUpdateChecker.kt"
s = p.read_text(encoding="utf-8")
s = once(s, 'private const val LOCAL_VERSION = 31', 'private const val LOCAL_VERSION = 32', "updater version")
p.write_text(s, encoding="utf-8")

print("GameDeck v32 per-game episode memory and bounded anti-loop recovery integrated")
