# GameDeck v17: truthful hardware, storage and launch diagnostics.
from pathlib import Path
import sys
p=Path(sys.argv[1])/"overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck/GameDeckTechnicalActivity.kt"
s=p.read_text(encoding="utf-8")
def replace_one(before, after, name):
 global s
 n=s.count(before)
 if n!=1: raise RuntimeError("v17 "+name+": "+str(n)+" matches")
 s=s.replace(before,after,1)
replace_one("""                title("Diagnose")
                game?.let { text(it.title) }
                root.addView(button("Berichte anzeigen (${matchingReports().size})") { chooseReport() })""","""                title("Diagnose")
                game?.let { text(it.title) }
                root.addView(button("Systemcheck durchführen", {
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { fullSystemCheck() } }
                        if (!isFinishing && !isDestroyed) {
                            showPlainDiagnostic(result.getOrElse {
                                "Systemcheck fehlgeschlagen: " + it.localizedMessage
                            })
                        }
                    }
                }))
                root.addView(button("Berichte anzeigen (${matchingReports().size})") { chooseReport() })""","diag action")

replace_one("""    private fun matchingReports() = GameDeckRuntime.reports().filter { file ->""","""    private fun fullSystemCheck(): String {
        val checks = mutableListOf<String>()
        fun check(label: String, test: () -> String) {
            checks += runCatching { "✓ " + label + ": " + test() }
                .getOrElse { "✗ " + label + ": " + (it.localizedMessage ?: "unbekannter Fehler") }
        }
        check("App") { packageName + " · Android " + android.os.Build.VERSION.SDK_INT }
        check("Build") { GameDeckRuntime.BUILD + " · Core " + GameDeckRuntime.CORE.take(12) }
        check("Controller") {
            val devices = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
                .filter { InputHandler.isPhysicalGameController(it) }
            if (devices.isEmpty()) "Kein physischer Gamecontroller erkannt" else
                devices.joinToString { it.name }
        }
        check("Externe Anzeige") {
            val screen = DeviceStatusProvider(this).snapshot().externalDisplay
            val mode = screen?.mode
            if (screen == null) "Nicht verbunden" else screen.name + " · " +
                (if (mode == null) "Auflösung unbekannt" else
                    "${mode.physicalWidth}×${mode.physicalHeight}, " +
                    "%.0f".format(java.util.Locale.ROOT, mode.refreshRate) + " Hz")
        }
        check("Audio") { DeviceStatusProvider(this).snapshot().audioText }
        check("Akku und Temperatur") {
            val state = DeviceStatusProvider(this).snapshot()
            state.batteryPercent.toString() + "% · Akku " +
                "%.1f".format(java.util.Locale.ROOT, state.batteryTempC) + " °C"
        }
        check("Lokaler Schreibzugriff") {
            val probe = java.io.File(cacheDir, "gd_diagnostic_probe.tmp")
            try {
                probe.writeText("GameDeck probe", Charsets.UTF_8)
                require(probe.readText(Charsets.UTF_8) == "GameDeck probe")
                "Schreiben und Lesen erfolgreich"
            } finally { probe.delete() }
        }
        check("Diagnoseprotokolle") { GameDeckRuntime.reports().size.toString() + " Berichte gespeichert" }
        check("Grafiktreiber") {
            if (GpuDriverHelper.supportsCustomDriverLoading())
                "Eigene Treiber werden vom Gerät unterstützt" else
                "Systemtreiber; eigene Treiber nicht verfügbar"
        }
        game?.let { selected ->
            check("Spiel-Startprüfung") {
                val issues = GameDeckRuntime.preflight(selected)
                if (issues.isEmpty()) "Grundvoraussetzungen erfüllt (keine Garantie für Spielstart)"
                else "Hinweise: " + issues.joinToString("; ")
            }
        }
        val errors = checks.count { it.startsWith("✗") }
        return "GAMEDECK S26 – SYSTEMCHECK\\n" +
            java.text.DateFormat.getDateTimeInstance().format(java.util.Date()) +
            "\\n" + checks.size + " Prüfungen · " + errors + " technische Fehler" +
            "\\n\\n" + checks.joinToString("\\n") +
            "\\n\\nHinweis: Nicht verbundene Geräte sind kein Programmfehler. " +
            "FPS, Bild/Ton in Spielen und HDMI-Bildqualität wurden nicht automatisch gemessen."
    }

    private fun showPlainDiagnostic(report: String) {
        AlertDialog.Builder(this).setTitle("GameDeck Systemcheck").setMessage(report)
            .setPositiveButton("Schließen", null)
            .setNeutralButton("Kopieren") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("GameDeck Systemcheck", report))
            }
            .setNegativeButton("Teilen") { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Bericht teilen"))
            }.show()
    }

    private fun matchingReports() = GameDeckRuntime.reports().filter { file ->""","diag helper")
p.write_text(s,encoding="utf-8")
print("v17: systemcheck and copy/share implemented")
