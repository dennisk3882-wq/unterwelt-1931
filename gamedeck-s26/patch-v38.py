# GameDeck v38 maintenance patch: repair update discovery for preview releases.
# No AI gameplay logic is changed in this patch.
from pathlib import Path
import sys

d = Path(sys.argv[1]) / "overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"

def once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError("v38 " + label + ": expected one anchor, found " + str(count))
    return text.replace(old, new, 1)

p = d / "GameDeckRuntime.kt"
s = p.read_text(encoding="utf-8")
s = once(s, 'const val BUILD = "GameDeck v37"', 'const val BUILD = "GameDeck v38"', "runtime version")
p.write_text(s, encoding="utf-8")

p = d / "GameDeckUpdateChecker.kt"
s = p.read_text(encoding="utf-8")
s = once(s, "private const val LOCAL_VERSION = 37", "private const val LOCAL_VERSION = 38", "local updater version")
s = once(
    s,
    'if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue',
    'if (release.optBoolean("draft")) continue // GameDeck preview APKs are intentionally published as prereleases.',
    "preview release filtering",
)
p.write_text(s, encoding="utf-8")

print("GameDeck v38: update checker can now discover newer GameDeck preview APK releases; no AI gameplay logic changed.")
