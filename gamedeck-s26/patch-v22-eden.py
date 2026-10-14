# Hooks actual Eden EmulationActivity lifecycle; never infer a successful game launch from a request.
from pathlib import Path
import sys
root=Path(sys.argv[1])
p=root/"src/android/app/src/main/java/org/yuzu/yuzu_emu/activities/EmulationActivity.kt"
s=p.read_text(encoding="utf-8")
def once(old,new,what):
 global s
 n=s.count(old)
 if n!=1: raise RuntimeError("Eden lifecycle hook "+what+": found "+str(n))
 s=s.replace(old,new,1)
once("        NativeLibrary.playTimeManagerStart()",
     "        NativeLibrary.playTimeManagerStart()\n        org.yuzu.yuzu_emu.gamedeck.GameDeckAutoTester.onNativeStarted()",
     "started")
once("        hasEmulationSession = false\n        processHasEmulationSession = false",
     "        org.yuzu.yuzu_emu.gamedeck.GameDeckAutoTester.onNativeStopped(status)\n        hasEmulationSession = false\n        processHasEmulationSession = false",
     "stopped")
p.write_text(s,encoding="utf-8")
print("GameDeck v22 verified native emulation hooks inserted")
