# v31 build marker: ensure the workflow uses the v31 integration and release step.
# GameDeck v31: interactive local co-op objective and tailored coaching hints.
from pathlib import Path
import sys

root = Path(sys.argv[1])
d = root / "overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"

def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError("v31 " + label + ": expected one anchor, found " + str(count))
    return text.replace(old, new, 1)

source = Path("gamedeck-s26/GameDeckCoopCoachOverlay.kt").read_text(encoding="utf-8")
(d / "GameDeckCoopCoachOverlay.kt").write_text(source, encoding="utf-8")

p = d / "GameDeckRuntime.kt"
s = p.read_text(encoding="utf-8")
s = once(s, 'const val BUILD = "GameDeck v30"', 'const val BUILD = "GameDeck v31"', "runtime version")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckUpdateChecker.kt"
s = p.read_text(encoding="utf-8")
s = once(s, 'private const val LOCAL_VERSION = 30', 'private const val LOCAL_VERSION = 31', "update version")
p.write_text(s, encoding="utf-8")

print("GameDeck v31 interactive local co-op goal coach integrated")
