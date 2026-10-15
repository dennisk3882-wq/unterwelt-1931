from pathlib import Path
import sys
root=Path(sys.argv[1])
d=root/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"
def patch(s,old,new,label):
 c=s.count(old)
 if c!=1:raise RuntimeError("v28 "+label+": "+str(c)+" anchors")
 return s.replace(old,new,1)

(d/"GameDeckSceneObserver.kt").write_text(
    Path("gamedeck-s26/GameDeckSceneObserver.kt").read_text(encoding="utf-8"),
    encoding="utf-8")

p=d/"GameDeckVisualProbe.kt"
s=p.read_text(encoding="utf-8")
s=patch(s,'        activity=WeakReference(screen)',
'''        activity=WeakReference(screen)
        GameDeckSceneObserver.attach(screen)''',"OCR attach")
s=patch(s,'    fun reset() {\n        generation++',
'''    fun reset() {
        GameDeckSceneObserver.reset()
        generation++''',"OCR reset")
s=patch(s,'    fun detach() {\n        generation++',
'''    fun detach() {
        GameDeckSceneObserver.detach()
        generation++''',"OCR detach")
s=patch(s,'    fun captureIfDue() {\n        if (busy) return',
'''    fun captureIfDue() {
        GameDeckSceneObserver.captureIfDue()
        if (busy) return''',"OCR scheduling")
s=patch(s,'.put("classification",classify())',
'''.put("classification",classify())
            .put("text_recognition",GameDeckSceneObserver.snapshot())''',"OCR reporting")
p.write_text(s,encoding="utf-8")

p=d/"GameDeckAutoPilot.kt"
s=p.read_text(encoding="utf-8")
s=patch(s,'    private var gameKey=""',
'''    private var gameKey=""
    private var strategy="EXPLORATION"
    private var goal="ENTER_GAME"
    private var gameOverRetries=0
    private var loadingWaits=0
    private var possibleVictoryFrames=0''',"goal state")
s=patch(s,'    private const val MAX_ACTIONS=18',
'''    private const val MAX_ACTIONS=55''',"action budget")
s=patch(s,'        lastActionKey=""',
'''        lastActionKey=""
        goal="ENTER_GAME"
        gameOverRetries=0;loadingWaits=0;possibleVictoryFrames=0''',"goal reset")
s=patch(s,'    fun configure(context:Context,programId:String) {',
'''    fun configure(context:Context,programId:String,title:String="") {''',"strategy parameter")
s=patch(s,'        gameKey=programId.ifBlank { "unknown" }',
'''        gameKey=programId.ifBlank { "unknown" }
        val name=title.lowercase()
        strategy=when {
            listOf("kart","race","racing","grand prix").any {name.contains(it)} -> "RACING"
            listOf("mario","sonic","kirby","donkey kong","celeste",
                "metroid","hollow knight").any {name.contains(it)} -> "PLATFORMER"
            listOf("tetris","puzzle","picross","suika").any {name.contains(it)} -> "PUZZLE"
            else -> "EXPLORATION"
        }''',"title strategy")
s=patch(s,'        if(frame.optInt("captures",0)==0) return // Blind gameplay forbidden.',
'''        if(frame.optInt("captures",0)==0) return // Blind gameplay forbidden.
        val scene=frame.optJSONObject("text_recognition")
            ?.optString("scene","UNKNOWN") ?: "UNKNOWN"
        if(scene=="LOADING") {goal="WAIT_FOR_LOADING";loadingWaits++;return}
        if(scene=="POSSIBLE_SUCCESS") {
            goal="POSSIBLE_LEVEL_CLEAR";possibleVictoryFrames++;return
        }
        if(scene=="LOAD_ERROR") {goal="INVESTIGATE_LOAD_ERROR";return}
        if(scene=="GAME_OVER") {
            goal="RETRY_AFTER_FAILURE"
            if(gameOverRetries>=2)return
        } else if(scene=="MENU")goal="ENTER_GAME"
        else if(scene=="PAUSE")goal="RESUME_GAME"
        else goal="EXPLORE"''',"OCR action guard")
s=patch(s,'        lastLabel=choice.label',
'''        lastLabel=choice.label
        if(scene=="GAME_OVER")gameOverRetries++''',"failed retry counter")
s=patch(s,'        log.put(JSONObject().put("index",stepCount)',
'''        log.put(JSONObject().put("goal",goal).put("strategy",strategy)
            .put("text_scene",scene).put("index",stepCount)''',"goal telemetry")
s=patch(s,'        val state=frame.optString("classification")',
'''        val state=frame.optString("classification")
        val scene=frame.optJSONObject("text_recognition")
            ?.optString("scene","UNKNOWN") ?: "UNKNOWN"
        if(scene=="GAME_OVER") return Choice(
            "Wiederholen",KeyEvent.KEYCODE_BUTTON_A,
            reason="Wiederholter Game-Over-Bildschirmtext")
        if(scene=="PAUSE") return Choice(
            "Fortsetzen",KeyEvent.KEYCODE_BUTTON_START,
            reason="Wiederholter Pausen-Bildschirmtext")
        if(scene=="MENU") return Choice(
            "Spiel starten",KeyEvent.KEYCODE_BUTTON_A,
            reason="Wiederholter Menü-Bildschirmtext")
        if(scene=="TUTORIAL") return Choice(
            "Anleitung bestätigen",KeyEvent.KEYCODE_BUTTON_A,
            reason="Wiederholter Anleitungs-Bildschirmtext")''',"menu actions")
s=patch(s,'        // A limited cyclic exploration policy. Avoid HOME/capture/system',
'''        // Small title-family policies use game type, visual dynamics and
        // repeat observations. A text cue never proves that a level is solved.
        if(dynamic && stepCount>=3) {
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
        }
        // A limited cyclic exploration policy. Avoid HOME/capture/system''',"bounded game-specific policy")
s=patch(s,'        .put("learning_feedback_samples",learningEvidence)',
'''        .put("learning_feedback_samples",learningEvidence)
        .put("strategy",strategy).put("goal",goal)
        .put("loading_waits",loadingWaits)
        .put("possible_victory_frames",possibleVictoryFrames)
        .put("game_over_retries",gameOverRetries)''',"goal JSON")
p.write_text(s,encoding="utf-8")

p=d/"GameDeckAutoTester.kt";s=p.read_text(encoding="utf-8")
s=patch(s,'    private const val RUN_WINDOW=75000L',
'''    private const val RUN_WINDOW=75000L
    private const val AI_SINGLE_WINDOW=180000L''',"solo AI window")
s=patch(s,'GameDeckAutoPilot.configure(it,games[index].programId)',
'''GameDeckAutoPilot.configure(it,games[index].programId,games[index].title)''',"title passed")
s=patch(s,'        if(now-coreAt>=RUN_WINDOW) {',
'''        val timeLimit=if(aiMode && games.size==1) AI_SINGLE_WINDOW else RUN_WINDOW
        if(now-coreAt>=timeLimit) {''',"AI timer")
s=patch(s,'                "75 Sekunden reales Emulationsfenster")',
'''                "Reales Emulationsfenster: "+(timeLimit/1000)+" Sekunden")''',"duration report")
s=patch(s,'                    append("  Native Eingabe: ").append(ai.optString("input_abi","unbekannt"))',
'''                    append("  KI-Strategie: ").append(ai.optString("strategy","unbekannt"))
                        .append(" · Ziel: ").append(ai.optString("goal","unbekannt"))
                        .append("\\n")
                    append("  Native Eingabe: ").append(ai.optString("input_abi","unbekannt"))''',"report")
p.write_text(s,encoding="utf-8")

p=d/"GameDeckSmartAnalyzer.kt";s=p.read_text(encoding="utf-8")
s=patch(s,'        val frameClass=visual.optString("classification","NICHT_EINDEUTIG")',
'''        val frameClass=visual.optString("classification","NICHT_EINDEUTIG")
        val ocr=visual.optJSONObject("text_recognition") ?: JSONObject()
        val scene=ocr.optString("scene","UNKNOWN")''',"OCR diagnostic")
s=patch(s,'        if(samples==0 && failures>0) findings.put(',
'''        when(scene) {
            "LOAD_ERROR" -> flag(75,
                "Mehrfach Lade-/Systemfehler-Text im Bild erkannt. Fehlerprotokoll, Firmware oder Spieldatei prüfen.")
            "GAME_OVER" -> findings.put(
                "Spielende-/Fehlschlagtext erkannt: Spielziel nicht erreicht.")
            "POSSIBLE_SUCCESS" -> findings.put(
                "Möglicher Levelabschluss-Text erkannt, kein abgeschlossener Storymodus nachgewiesen.")
            "LOADING" -> {
                findings.put("Ladebildschirmtext erkannt.")
                if(ocr.optInt("longest_loading_sequence")>=6)
                    flag(40,"Möglicher Ladehänger bei wiederholtem identischem Ladehinweis.")
            }
            "MENU" -> findings.put("Menütext erkannt. Gameplay damit nicht nachgewiesen.")
            "PAUSE" -> findings.put("Pause erkannt: Statisches Bild nicht zwingend fehlerhaft.")
        }
        if(samples==0 && failures>0) findings.put(''',"OCR error findings")
s=patch(s,'            .put("visual_classification",frameClass)',
'''            .put("visual_classification",frameClass)
            .put("text_scene",scene)
            .put("ocr_captures",ocr.optInt("captures",0))
            .put("semantic_gameplay_proven",false)''',"OCR results")
p.write_text(s,encoding="utf-8")

p=d/"GameDeckActivity.kt";s=p.read_text(encoding="utf-8")
s=patch(s,'        root.addView(actionCard("Intelligenter KI-Spieletester",',
'''        root.addView(actionCard("Letzten KI-Testbericht",
            "Bild, Texterkennung, Fehler und Controlleraktionen") {
            showAutoTestReport()
        })
        root.addView(actionCard("Intelligenter KI-Spieletester",''',"report UI")
s=patch(s,'                .setMessage("Die KI sendet reale Emulator-Eingaben und beobachtet Bildveränderungen. Ein laufendes Spiel darf nicht aktiv sein. Bitte Spielstand vorher sichern; echte Eingaben können Fortschritte und Einstellungen ändern.")',
'''                .setMessage("Bis zu 3 Minuten aktiver KI-Einzeltest: GameDeck erkennt Start-, Lade- und Fehlertexte lokal und plant begrenzte Testaktionen zur Spielart. Kein garantiertes Durchspielen. Vorher Spielstände sichern!")''',"AI consent")
p.write_text(s,encoding="utf-8")
p=d/"GameDeckRuntime.kt";s=p.read_text(encoding="utf-8")
s=patch(s,'const val BUILD = "GameDeck v27"','const val BUILD = "GameDeck v28"',"version")
p.write_text(s,encoding="utf-8")
p=d/"GameDeckUpdateChecker.kt";s=p.read_text(encoding="utf-8")
s=patch(s,'private const val LOCAL_VERSION = 27','private const val LOCAL_VERSION = 28',"update")
p.write_text(s,encoding="utf-8")
print("GameDeck v28 offline OCR and scene-directed bounded self-playing agent integrated")
