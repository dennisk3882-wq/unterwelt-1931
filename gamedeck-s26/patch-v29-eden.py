# GameDeck v29: show the autonomous agent's live HUD over the actual emulation window.
from pathlib import Path
import sys
p=Path(sys.argv[1])/"src/android/app/src/main/java/org/yuzu/yuzu_emu/activities/EmulationActivity.kt"
s=p.read_text(encoding="utf-8")
before="org.yuzu.yuzu_emu.gamedeck.GameDeckAutoTester.onNativeStarted(this)"
after=before+"\n        org.yuzu.yuzu_emu.gamedeck.GameDeckAiLiveOverlay.attach(this)"
if s.count(before)!=1: raise RuntimeError("v29 attach hook: expected one, found "+str(s.count(before)))
s=s.replace(before,after,1)
before="org.yuzu.yuzu_emu.gamedeck.GameDeckAutoTester.onNativeStopped(status)"
after=before+"\n        org.yuzu.yuzu_emu.gamedeck.GameDeckAiLiveOverlay.detach()"
if s.count(before)!=1: raise RuntimeError("v29 detach hook: expected one, found "+str(s.count(before)))
s=s.replace(before,after,1)
p.write_text(s,encoding="utf-8")
print("GameDeck v29 visible live status overlay hooked into Eden lifecycle")
