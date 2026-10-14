from pathlib import Path
import sys
root=Path(sys.argv[1])
folder=root/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck"
p=folder/"GameDeckActivity.kt"
s=p.read_text(encoding="utf-8")
def change(old,new,name):
 global s
 if s.count(old)!=1:raise RuntimeError("v21 "+name+": "+str(s.count(old)))
 s=s.replace(old,new,1)
change("        refreshHeaderStatus()\n        mainHandler.removeCallbacks(statusTicker)",
"        refreshHeaderStatus()\n        GameDeckUpdateChecker.check(this, false)\n        mainHandler.removeCallbacks(statusTicker)","auto update periodic")
change('        root.addView(actionCard("Sicherung & Version", "Lokale GameDeck-Daten und technische Wartung") {',
'''        root.addView(actionCard("Auf GameDeck-Update prüfen", "Veröffentlichte signierte APK-Versionen auf GitHub prüfen") {
            GameDeckUpdateChecker.check(this, true)
        })
        root.addView(actionCard("Sicherung & Version", "Lokale GameDeck-Daten und technische Wartung") {''',"manual update check")
p.write_text(s,encoding="utf-8")
runtime=folder/"GameDeckRuntime.kt"
r=runtime.read_text(encoding="utf-8")
if r.count('const val BUILD = "GameDeck v18"')!=1: raise RuntimeError("release label missing")
runtime.write_text(r.replace('const val BUILD = "GameDeck v18"','const val BUILD = "GameDeck v21"'),encoding="utf-8")
(folder/"GameDeckUpdateChecker.kt").write_text(r'''// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.gamedeck

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Read-only release check against GameDeck-tagged APK assets.
 * Android itself controls installation approval and signature verification.
 * Never silently install packages, and never select unrelated releases.
 */
object GameDeckUpdateChecker {
    private const val LOCAL_VERSION = 21
    private const val RELEASES_URL =
        "https://api.github.com/repos/dennisk3882-wq/unterwelt-1931/releases?per_page=40"
    private val tag = Regex("^gamedeck-s26-v([0-9]+)$", RegexOption.IGNORE_CASE)
    @Volatile private var pending = false

    fun check(activity: Activity, interactive: Boolean) {
        val prefs = activity.getSharedPreferences("gamedeck_update",0)
        val now=System.currentTimeMillis()
        if (!interactive && now - prefs.getLong("last_check",0L) < 86400000L) return
        if (pending) return
        pending=true
        Thread {
            val result=runCatching {
                val c=URL(RELEASES_URL).openConnection() as HttpURLConnection
                c.connectTimeout=7000
                c.readTimeout=9000
                c.setRequestProperty("User-Agent","GameDeck-S26-Android/21")
                c.setRequestProperty("Accept","application/vnd.github+json")
                try {
                    check(c.responseCode==200) { "GitHub API HTTP "+c.responseCode }
                    val body=c.inputStream.bufferedReader().use { it.readText() }
                    val releases=JSONArray(body)
                    var best=LOCAL_VERSION
                    var apkUrl:String?=null
                    for (i in 0 until releases.length()) {
                        val release=releases.optJSONObject(i)?:continue
                        if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
                        val match=tag.matchEntire(release.optString("tag_name")) ?: continue
                        val number=match.groupValues[1].toIntOrNull()?:continue
                        if (number<=best) continue
                        val assets=release.optJSONArray("assets") ?: continue
                        for (j in 0 until assets.length()) {
                            val item=assets.optJSONObject(j)?:continue
                            val url=item.optString("browser_download_url")
                            if (item.optString("name").endsWith(".apk",true) &&
                                url.startsWith("https://github.com/dennisk3882-wq/unterwelt-1931/releases/download/")) {
                                best=number; apkUrl=url; break
                            }
                        }
                    }
                    best to apkUrl
                } finally { c.disconnect() }
            }
            pending=false
            if (activity.isFinishing || activity.isDestroyed) return@Thread
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                prefs.edit().putLong("last_check",System.currentTimeMillis()).apply()
                result.fold({ (version,url) ->
                    if (url!=null) {
                        AlertDialog.Builder(activity).setTitle("GameDeck-Update verfügbar")
                            .setMessage("GameDeck v"+version+
                                " ist als APK veröffentlicht. Herunterladen und die Installation anschließend in Android bestätigen. Deine bestehende App bleibt erhalten, sofern Paketkennung und Signatur übereinstimmen.")
                            .setPositiveButton("APK herunterladen") { _, _ ->
                                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }.setNegativeButton("Später",null).show()
                    } else if (interactive) {
                        AlertDialog.Builder(activity).setTitle("GameDeck-Update")
                            .setMessage("Aktuell keine neuere GameDeck-APK als signiertes GitHub-Release veröffentlicht. Die Prüfung funktioniert erst, wenn eine eigene GameDeck-Release mit APK bereitsteht.")
                            .setPositiveButton("OK",null).show()
                    }
                },{ failure ->
                    if (interactive) AlertDialog.Builder(activity)
                        .setTitle("Update-Prüfung fehlgeschlagen")
                        .setMessage(failure.localizedMessage ?: "Keine Verbindung zum Update-Dienst")
                        .setPositiveButton("OK",null).show()
                })
            }
        }.start()
    }
}
''',encoding="utf-8")
print("v21: 24-hour check + secure release selection + user-controlled APK download")
