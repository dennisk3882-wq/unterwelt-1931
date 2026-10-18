# GameDeck v39: remove autonomous play/coop entry points; retain diagnostics and v38 updater behavior.
from pathlib import Path
import re, sys

root=Path(sys.argv[1])
d=root/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"

def remove_kotlin_statement_block(src, needle, label):
    pos=src.find(needle)
    if pos<0:
        return src, False
    # Find the enclosing root.addView(actionCard(...)) expression by locating
    # the beginning of the line and balancing Kotlin braces/parentheses.
    start=src.rfind("\n",0,pos)+1
    while start<len(src) and src[start] in " \t":
        start+=1
    # Start at the actionCard call's line indentation.
    line_start=src.rfind("\n",0,pos)+1
    depth_paren=depth_brace=0
    in_string=False; escaped=False; line_comment=False; block_comment=False
    i=line_start
    seen=False
    while i<len(src):
        ch=src[i]; nxt=src[i+1] if i+1<len(src) else ""
        if line_comment:
            if ch=="\n": line_comment=False
            i+=1; continue
        if block_comment:
            if ch=="*" and nxt=="/": block_comment=False; i+=2; continue
            i+=1; continue
        if in_string:
            if escaped: escaped=False
            elif ch=="\\": escaped=True
            elif ch=='"': in_string=False
            i+=1; continue
        if ch=="/" and nxt=="/": line_comment=True; i+=2; continue
        if ch=="/" and nxt=="*": block_comment=True; i+=2; continue
        if ch=='"': in_string=True; i+=1; continue
        if ch=="(": depth_paren+=1; seen=True
        elif ch==")":
            depth_paren-=1
            if seen and depth_paren==0 and depth_brace==0:
                end=i+1
                if end<len(src) and src[end]=="\r": end+=1
                if end<len(src) and src[end]=="\n": end+=1
                return src[:line_start]+src[end:], True
        elif ch=="{": depth_brace+=1; seen=True
        elif ch=="}": depth_brace-=1
        i+=1
    raise RuntimeError("v39 could not safely remove UI block: "+label)

p=d/"GameDeckActivity.kt"
s=p.read_text(encoding="utf-8")
for needle,label in [
    ('actionCard("KI aktiv spielen lassen"', "autonomous-play card"),
    ('actionCard("Koop-Coach"', "coop coach card"),
    ('actionCard("Koop-Modus"', "coop mode card"),
    ('actionCard("Co-op-Coach"', "co-op coach card"),
]:
    s,_=remove_kotlin_statement_block(s,needle,label)
# Remove menu action blocks where the feature label appears with other capitalization/wording.
for pattern in [
    r'(?i)actionCard\("[^"\n]*(?:autonom(?:e|er|es)? spiel|ki aktiv spielen|selbstständig spielen)[^"\n]*"',
    r'(?i)actionCard\("[^"\n]*(?:coop|co-op|kooperativ)[^"\n]*(?:coach|modus|spieler)[^"\n]*"',
]:
    while True:
        m=re.search(pattern,s)
        if not m: break
        s,ok=remove_kotlin_statement_block(s,m.group(0),"matched AI/coop menu")
        if not ok: break
p.write_text(s,encoding="utf-8")

runtime=d/"GameDeckRuntime.kt"
r=runtime.read_text(encoding="utf-8")
if 'const val BUILD = "GameDeck v38"' in r:
    r=r.replace('const val BUILD = "GameDeck v38"','const val BUILD = "GameDeck v39"',1)
elif 'const val BUILD = "GameDeck v39"' not in r:
    raise RuntimeError("v39 expected v38/v39 runtime baseline")
runtime.write_text(r,encoding="utf-8")
updater=d/"GameDeckUpdateChecker.kt"
u=updater.read_text(encoding="utf-8")
if "private const val LOCAL_VERSION = 38" in u:
    u=u.replace("private const val LOCAL_VERSION = 38","private const val LOCAL_VERSION = 39",1)
elif "private const val LOCAL_VERSION = 39" not in u:
    raise RuntimeError("v39 expected v38/v39 updater baseline")
updater.write_text(u,encoding="utf-8")

# Explicitly remove the live overlays and autonomous controller from Eden's
# emulation lifecycle. Keep the diagnostic start/FPS tester separate.
eden=Path(sys.argv[1]).parent/"eden/src/android/app/src/main/java/org/yuzu/yuzu_emu/activities/EmulationActivity.kt"
if eden.exists():
    e=eden.read_text(encoding="utf-8")
    e=re.sub(r"^\s*GameDeckAiLiveOverlay\.(?:attach\(this\)|detach\(\))\s*\n","",e,flags=re.M)
    e=re.sub(r"^\s*GameDeckCoopCoachOverlay\.(?:attach\(this\)|detach\(\))\s*\n","",e,flags=re.M)
    eden.write_text(e,encoding="utf-8")

print("GameDeck v39: removed visible autonomous-play/coop entry points and detached their overlays; v38 updater behavior retained.")
