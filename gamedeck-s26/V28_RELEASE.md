# GameDeck S26 v28 – KI erkennt Bildtexte und reagiert darauf

- Bundled ML Kit Texterkennung läuft offline auf dem Android-Gerät. PixelCopy-Spielbilder werden nicht gespeichert oder übertragen.
- OCR-Szenen: Menü, Ladebildschirm, Pause, Anleitung, Game Over, Ladefehler und möglicher Levelabschluss; die gleiche Kategorie muss in mindestens zwei Messungen auftreten.
- Die KI pausiert während klar erkannter Ladebildschirme und bei möglichem Sieg, versucht maximal zweimal ein Retry nach Game Over und bietet begrenzte Start-Menüaktionen.
- Titelabhängige Erkundungsstrategien für Plattformer, Rennspiel und Puzzle ergänzen die bisherigen Bildreaktionsmessungen.
- KI-Einzeltest: bis zu drei Minuten und 55 begrenzte Controlleraktionen; Akku- und Temperaturschutz erhalten.
- Zusätzliche Testberichte für OCR-Szenen und Fehlerhypothesen sowie eigenständiger Berichtseinstieg in Mehr.

Wichtig: Bildtext und heuristische Aktionen beweisen keine Spielbarkeit, Level-Lösung oder vollständigen Storyabschluss. Echtes S26-Hardwaretesting, Produktionssignatur und ein universelles trainiertes Spielmodell bleiben unbewiesen bzw. ausstehend.
