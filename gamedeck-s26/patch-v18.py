# GameDeck v18: trustworthy native profile guidance, backup completeness and release version.
from pathlib import Path
import sys
root=Path(sys.argv[1])
activity=root/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck/GameDeckActivity.kt"
runtime=root/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck/GameDeckRuntime.kt"
a=activity.read_text(encoding="utf-8")
r=runtime.read_text(encoding="utf-8")
def replace_one(old,new,label):
 global a
 n=a.count(old)
 if n!=1: raise RuntimeError("v18 "+label+" expected one occurrence, got "+str(n))
 a=a.replace(old,new,1)
replace_one("""        root.addView(infoPanel("Einstellungen", GameDeckProfileEngine.settingsSource(this, game)))
        root.addView(sectionTitle("Spielinfos & Bilder"))""","""        val guidance = GameDeckProfileEngine.describe(game, selectedMode)
        root.addView(infoPanel("Aktives Profil", GameDeckProfileEngine.settingsSource(this, game) +
            "\\nAuflösung: " + guidance.resolutionLabel +
            " · " + (if (guidance.docked) "Docked-Modus" else "Handheld-Modus") +
            " · GPU: " + (if (guidance.gpuAccuracyHigh) "Höhere Genauigkeit" else "Normal") +
            "\\n" + guidance.note +
            "\\nDie Werte sind Profilvorgaben, keine gemessenen FPS. Eigene Eden-Änderungen bleiben geschützt."))
        root.addView(sectionTitle("Spielinfos & Bilder"))""","profile explanation")
replace_one("""            data.put("library", org.json.JSONObject(libraryPrefs.all))
            data.put("launcher", org.json.JSONObject(getSharedPreferences("gamedeck", MODE_PRIVATE).all))""","""            data.put("library", org.json.JSONObject(libraryPrefs.all))
            data.put("launcher", org.json.JSONObject(getSharedPreferences("gamedeck", MODE_PRIVATE).all))
            data.put("profiles", org.json.JSONObject(
                getSharedPreferences("gamedeck_profiles", MODE_PRIVATE).all
            ))""","export game mode")
replace_one("""            val appPrefs = getSharedPreferences("gamedeck", MODE_PRIVATE)""","""            val profiles = json.optJSONObject("profiles")
            if (profiles != null) {
                val profileEdit = getSharedPreferences("gamedeck_profiles", MODE_PRIVATE).edit()
                val keys = profiles.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = profiles.optString(key, "")
                    if (key.length in 1..120 &&
                        GameDeckProfileEngine.Mode.entries.any { it.name == value }) {
                        profileEdit.putString(key, value)
                    }
                }
                profileEdit.apply()
            }
            val appPrefs = getSharedPreferences("gamedeck", MODE_PRIVATE)""","import game mode")
activity.write_text(a,encoding="utf-8")
if r.count('const val BUILD = "GameDeck v14"')!=1: raise RuntimeError("v18 runtime release label missing")
r=r.replace('const val BUILD = "GameDeck v14"','const val BUILD = "GameDeck v18"')
runtime.write_text(r,encoding="utf-8")
print("v18: profile summary, JSON game-mode backup and release id")
