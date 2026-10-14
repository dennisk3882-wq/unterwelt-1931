# Upgrade v22 Eden start hook to pass the actual EmulationActivity so
# Android PixelCopy can inspect rendered output safely, without game control.
from pathlib import Path
import sys
root=Path(sys.argv[1])
p=root/"src/android/app/src/main/java/org/yuzu/yuzu_emu/activities/EmulationActivity.kt"
s=p.read_text(encoding="utf-8")
before="org.yuzu.yuzu_emu.gamedeck.GameDeckAutoTester.onNativeStarted()"
after="org.yuzu.yuzu_emu.gamedeck.GameDeckAutoTester.onNativeStarted(this)"
if s.count(before)!=1:raise RuntimeError("v23 Eden hook: expected one callback, found "+str(s.count(before)))
p.write_text(s.replace(before,after),encoding="utf-8")
print("Eden provides real native emulation Activity to on-device visual analyzer")
