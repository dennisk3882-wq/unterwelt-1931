# GameDeck v30: attach co-op coach to active emulation window.
from pathlib import Path
import sys

p = Path(sys.argv[1]) / "src/android/app/src/main/java/org/yuzu/yuzu_emu/activities/EmulationActivity.kt"
s = p.read_text(encoding="utf-8")
before = "org.yuzu.yuzu_emu.gamedeck.GameDeckAiLiveOverlay.attach(this)"
after = before + "\n        org.yuzu.yuzu_emu.gamedeck.GameDeckCoopCoachOverlay.attach(this)"
if s.count(before) != 1:
    raise RuntimeError("v30 coach attach hook: expected one, found " + str(s.count(before)))
s = s.replace(before, after, 1)
before = "org.yuzu.yuzu_emu.gamedeck.GameDeckAiLiveOverlay.detach()"
after = before + "\n        org.yuzu.yuzu_emu.gamedeck.GameDeckCoopCoachOverlay.detach()"
if s.count(before) != 1:
    raise RuntimeError("v30 coach detach hook: expected one, found " + str(s.count(before)))
s = s.replace(before, after, 1)
p.write_text(s, encoding="utf-8")
print("GameDeck v30 co-op coach attached to emulation lifecycle")
