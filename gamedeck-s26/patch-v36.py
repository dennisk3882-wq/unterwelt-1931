# GameDeck v36: longer bounded autonomous gameplay control loops.
from pathlib import Path
import sys

d = Path(sys.argv[1]) / "overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"

def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError("v36 " + label + ": expected one anchor, found " + str(count))
    return text.replace(old, new, 1)

p = d / "GameDeckAutoPilot.kt"
s = p.read_text(encoding="utf-8")
s = once(s, "private const val MAX_ACTIONS=55", "private const val MAX_ACTIONS=600", "action budget")
s = once(s, "private const val COOLDOWN_MS=3300L", "private const val COOLDOWN_MS=1700L", "action cadence")
s = once(s, "private const val BUTTON_HOLD_MS=220L", "private const val BUTTON_HOLD_MS=260L", "button pulse")
s = once(s, "private const val AXIS_HOLD_MS=450L", "private const val AXIS_HOLD_MS=650L", "axis pulse")

old = '''        if(dynamic && stepCount>=3) {
            when(strategy) {
                "RACING" -> return when(stepCount%4) {
                    0,1 -> Choice("Gas",KeyEvent.KEYCODE_BUTTON_A,
                        reason="Rennspiel-Teststrategie")
                    2 -> Choice("Rechts lenken",axis=MotionEvent.AXIS_X,value=0.55f,
                        reason="Rennspiel-Teststrategie")
                    else -> Choice("Links lenken",axis=MotionEvent.AXIS_X,value=-0.55f,
                        reason="Rennspiel-Teststrategie")
                }
                "PLATFORMER" -> return when(stepCount%3) {
                    0,1 -> Choice("Vorwärts",axis=MotionEvent.AXIS_X,value=0.65f,
                        reason="Plattformer-Teststrategie")
                    else -> Choice("Springen",KeyEvent.KEYCODE_BUTTON_B,
                        reason="Plattformer-Teststrategie")
                }
                "PUZZLE" -> return when(stepCount%3) {
                    0 -> Choice("Rechts",KeyEvent.KEYCODE_DPAD_RIGHT,
                        reason="Puzzle-Teststrategie")
                    1 -> Choice("Unten",KeyEvent.KEYCODE_DPAD_DOWN,
                        reason="Puzzle-Teststrategie")
                    else -> Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,
                        reason="Puzzle-Teststrategie")
                }
            }
        }'''
new = '''        // Bounded genre macros. Each pulse returns to observation and the OCR gate.
        if(dynamic && stepCount>=2) {
            when(strategy) {
                "RACING" -> return when(stepCount%6) {
                    0,1,2 -> Choice("Gas",KeyEvent.KEYCODE_BUTTON_A,
                        reason="Rennrunde: beschleunigen und Bildrückmeldung prüfen")
                    3 -> Choice("Rechts lenken",axis=MotionEvent.AXIS_X,value=0.42f,
                        reason="Rennrunde: kurze Rechtskorrektur")
                    4 -> Choice("Gas",KeyEvent.KEYCODE_BUTTON_A,
                        reason="Rennrunde: Beschleunigung fortsetzen")
                    else -> Choice("Links lenken",axis=MotionEvent.AXIS_X,value=-0.42f,
                        reason="Rennrunde: kurze Linkskorrektur")
                }
                "PLATFORMER" -> return when(stepCount%5) {
                    0,1 -> Choice("Vorwärts",axis=MotionEvent.AXIS_X,value=0.68f,
                        reason="Plattformlauf: Vorwärtsbewegung")
                    2 -> Choice("Springen",KeyEvent.KEYCODE_BUTTON_B,
                        reason="Plattformlauf: Sprungimpuls")
                    3 -> Choice("Vorwärts",axis=MotionEvent.AXIS_X,value=0.52f,
                        reason="Plattformlauf: weiterlaufen")
                    else -> Choice("Springen",KeyEvent.KEYCODE_BUTTON_B,
                        reason="Plattformlauf: alternativer Sprungimpuls")
                }
                "PUZZLE" -> return when(stepCount%8) {
                    0 -> Choice("Rechts",KeyEvent.KEYCODE_DPAD_RIGHT,reason="Rätsel-Erkundung: rechts")
                    1 -> Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,reason="Rätsel-Erkundung: Auswahl prüfen")
                    2 -> Choice("Unten",KeyEvent.KEYCODE_DPAD_DOWN,reason="Rätsel-Erkundung: nächste Zeile")
                    3 -> Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,reason="Rätsel-Erkundung: Zustand beobachten")
                    4 -> Choice("Links",KeyEvent.KEYCODE_DPAD_LEFT,reason="Rätsel-Erkundung: links prüfen")
                    5 -> Choice("Bestätigen",KeyEvent.KEYCODE_BUTTON_A,reason="Rätsel-Erkundung: Alternative prüfen")
                    6 -> Choice("Oben",KeyEvent.KEYCODE_DPAD_UP,reason="Rätsel-Erkundung: vorherige Zeile")
                    else -> Choice("Zurück",KeyEvent.KEYCODE_BUTTON_B,reason="Rätsel-Erkundung: begrenzter Rückschritt")
                }
            }
        }'''
s = once(s, old, new, "genre macros")

s = once(s, "},if(choice.button!=0) BUTTON_HOLD_MS else AXIS_HOLD_MS)",
'''},when {
                choice.label=="Gas" -> 1350L
                choice.label=="Vorwärts" -> 900L
                choice.label=="Springen" -> 360L
                choice.button!=0 -> BUTTON_HOLD_MS
                else -> AXIS_HOLD_MS
            })''', "genre input duration")

s = once(s, '''        .put("episode_recoveries",episodeRecoveryCount)
        .put("no_response_streak",consecutiveNoResponse)''',
'''        .put("episode_recoveries",episodeRecoveryCount)
        .put("no_response_streak",consecutiveNoResponse)
        .put("autonomous_session_action_budget",MAX_ACTIONS)
        .put("autonomous_control_loop","observe_gate_plan_issue_release_repeat")
        .put("objective_progress_verified",false)''', "autonomy telemetry")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckAutoTester.kt"
s = p.read_text(encoding="utf-8")
s = once(s, "private const val AI_SINGLE_WINDOW=180000L", "private const val AI_SINGLE_WINDOW=1200000L", "20 minute session")
s = once(s, "Bis zu 3 Minuten aktiver KI-Einzeltest: GameDeck erkennt Start-, Lade- und Fehlertexte lokal und plant begrenzte Testaktionen zur Spielart. Kein garantiertes Durchspielen. Vorher Spielstände sichern!",
'''Bis zu 20 Minuten aktiver KI-Einzelspielversuch: GameDeck nutzt szenengesteuerte Eingabesperren, längere Genre-Aktionsfolgen, frische Bildrückmeldungen und Anti-Wiederholungs-Erholung. Rätsel werden erkundend bearbeitet; eine abgeschlossene Runde/ein Level ist nicht garantiert. Vorher Spielstände sichern!''', "session consent text")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckRuntime.kt"
s = p.read_text(encoding="utf-8")
s = once(s, 'const val BUILD = "GameDeck v35"', 'const val BUILD = "GameDeck v36"', "runtime version")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckUpdateChecker.kt"
s = p.read_text(encoding="utf-8")
s = once(s, "private const val LOCAL_VERSION = 35", "private const val LOCAL_VERSION = 36", "updater version")
p.write_text(s, encoding="utf-8")

print("GameDeck v36 long-running autonomous control loop integrated")
