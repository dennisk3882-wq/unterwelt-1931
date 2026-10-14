# GameDeck S26 v22 – automatischer Emulator-Testlauf

* Einmalige ausdrückliche Zustimmung vor automatisiertem sequenziellen Start aller Bibliotheksspiele
* Native Eden Start-/Stopp-Callbacks und sicherer ROM-Wechsel
* 100-Sekunden-Startgrenze, 75-Sekunden-Laufzeitmessung und echte FPS-Stichproben
* Prüfung von Dateizugriff, Schlüsseln, Firmware und optionalen Treibern
* Hitzeschutz, Akkuschutz, Abbruchmöglichkeit und lokaler Ergebnisbericht
* Kein automatisches Abschließen von Storys oder simulierter menschlicher Eingaben
* Unveränderter nativer Emulatorcore (Hashvergleich mit v14)

Die tatsächliche Funktionsprüfung aller Spiele benötigt ein echtes Android-Gerät mit den legal vorhandenen Spieldateien und gegebenenfalls zugehöriger Firmware.

Build-Konfiguration v22 korrigiert; Start-/Stoppsignale werden im Kotlin-Testlauf separat validiert.
