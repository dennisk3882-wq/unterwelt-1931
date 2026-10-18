# GameDeck v38 maintenance patch: reliable update discovery for preview releases.
# The release build is still a maintenance build; device behavior must be tested separately.
from pathlib import Path
import re
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

# Source formatting changed across reconstructed patch generations. Match the
# actual predicate semantically rather than requiring one exact whitespace form.
pattern = re.compile(r'if\s*\(\s*release\.optBoolean\("draft"\)\s*\|\|\s*release\.optBoolean\("prerelease"\)\s*\)\s*continue')
s2, count = pattern.subn('if (release.optBoolean("draft")) continue // GameDeck preview APKs are intentionally published as prereleases.', s, count=1)
if count == 0:
    # Also handle the same condition split across multiple lines.
    pattern2 = re.compile(r'if\s*\(\s*release\.optBoolean\("draft"\).*?release\.optBoolean\("prerelease"\).*?\)\s*continue', re.S)
    s2, count = pattern2.subn('if (release.optBoolean("draft")) continue // GameDeck preview APKs are intentionally published as prereleases.', s, count=1)
if count != 1:
    raise RuntimeError("v38 preview release filtering: could not find draft+prerelease exclusion; updater source format changed")
p.write_text(s2, encoding="utf-8")

print("GameDeck v38: update checker now considers prereleases; runtime and updater versions set to 38.")
