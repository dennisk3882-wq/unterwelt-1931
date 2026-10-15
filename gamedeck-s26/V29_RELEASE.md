# GameDeck S26 v29 – Live-Anzeige für den KI-Test

- Während die autonome KI aktiv ist, wird direkt über dem laufenden Emulationsbild eine kompakte Statusanzeige eingeblendet.
- Anzeige von Teststatus, Aktionszähler, Strategie, aktuellem Ziel, letzter Controlleraktion, beobachteter Bildreaktion und OCR-Szene/Text.
- Die Anzeige erscheint nur, wenn der Agent aktiv ist, kann minimiert werden und wird beim Ende der Emulation entfernt.
- Zusätzliche Live-Telemetrie des Agenten; bestehende Offline-OCR und begrenzte Aktionen bleiben erhalten.
- Keine Behauptung, dass Bildbewegung einem erfolgreichen Spielzug oder ein erkannter Sieg einem vollständigen Levelabschluss entspricht.

**Einschränkung:** Das ist eine sichtbare Statusanzeige über dem tatsächlich laufenden Emulator, kein Beweis für universelle KI-Spielkompetenz. Die konkrete Spielsteuerung und Anzeige müssen auf dem Zielgerät getestet werden.

Build-Prüfung: Kotlin-Kompilierung und APK-Integrität werden im CI-Workflow verifiziert.
