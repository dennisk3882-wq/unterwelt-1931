# GameDeck S26 v16: clearer console/HDMI readiness and live controller status
from pathlib import Path
import sys
root=Path(sys.argv[1])
p=root/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck/GameDeckActivity.kt"
s=p.read_text(encoding="utf-8")
def replace_once(old,new,name):
 global s
 n=s.count(old)
 if n != 1: raise RuntimeError("v16 "+name+": expected one match, got "+str(n))
 s=s.replace(old,new,1)

replace_once("""        mainHandler.post(statusTicker)

    }

    override fun onPause()""","""        mainHandler.post(statusTicker)
        // Refresh physical device status when returning from Bluetooth/display settings.
        if (::content.isInitialized && currentScreen == Screen.DEVICES) {
            renderDevices()
        }
    }

    override fun onPause()""","refresh device status")
replace_once("""        root.addView(infoPanel("🔋 Systemmonitor", "Akku ${status.batteryPercent}%${if (status.charging) " · lädt" else ""} · ${"%.1f".format(status.batteryTempC)} °C · RAM ${status.ramText}"))""","""        root.addView(infoPanel("🔋 Systemmonitor", "Akku ${status.batteryPercent}%${if (status.charging) " · lädt" else ""} · ${"%.1f".format(status.batteryTempC)} °C · RAM ${status.ramText}"))
        root.addView(actionCard("Konsolen-Check", "Controller, Bildschirm und Audio vor dem Spielen kontrollieren") {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Konsolen-Check")
                .setMessage(consoleReadinessReport())
                .setPositiveButton("OK", null)
                .setNeutralButton("Controller testen") { _, _ ->
                    startActivity(Intent(this, GameDeckTechnicalActivity::class.java)
                        .putExtra("section", "controller"))
                }.show()
        })""","devices check")
replace_once("""    private fun externalModeText(): String {""","""    private fun consoleReadinessReport(): String {
        val status = statusProvider.snapshot()
        val controllers = InputDevice.getDeviceIds().mapNotNull { InputDevice.getDevice(it) }
            .filter { org.yuzu.yuzu_emu.utils.InputHandler.isPhysicalGameController(it) }
        val display = status.externalDisplay
        val mode = runCatching { display?.mode }.getOrNull()
        return buildString {
            append("Controller: ")
            append(if (controllers.isEmpty()) "Keiner erkannt" else controllers.joinToString { it.name })
            append("\\n\\nBeamer / TV: ")
            append(if (display == null) "Nicht erkannt" else display.name)
            if (mode != null) {
                append("\\nAusgabe: ")
                append(mode.physicalWidth).append(" × ").append(mode.physicalHeight)
                append(" · ").append("%.0f".format(java.util.Locale.ROOT, mode.refreshRate)).append(" Hz")
            }
            append("\\nHDMI-Start: ").append(if (getSharedPreferences("gamedeck", MODE_PRIVATE)
                .getBoolean("external_display", true)) "Aktiviert" else "Deaktiviert")
            append("\\n\\nAudio: ").append(status.audioText)
            append("\\n\\nAkku: ").append(status.batteryPercent).append("%")
            append(if (status.charging) " · lädt" else " · Akku")
            append("\\nAkku-Temperatur: ").append("%.1f".format(java.util.Locale.ROOT, status.batteryTempC)).append(" °C")
            append("\\n\\nHinweis: Die Anzeige prüft erkannte Geräte. Sie bestätigt keine erfolgreiche Bildausgabe oder Spielkompatibilität.")
        }
    }

    private fun externalModeText(): String {""","console report")
p.write_text(s,encoding="utf-8")
print("GameDeck v16 native console status integrated")
