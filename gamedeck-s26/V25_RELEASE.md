# GameDeck S26 v25 – aktiver KI-Spieltester (Vorabversion)

Dieser Build integriert erstmals einen Agenten mit echtem Feedbackkreislauf: Bildschirmstichprobe → begrenzte Aktion → Emulator-Controller-Schnittstelle → erneute Bildschirmbeobachtung → weitere Aktion. Android PixelCopy misst echte Bildreaktionen, die Spielereignisse selbst bleiben unverifiziert. Nach ausdrücklicher Zustimmung wählt der Nutzer zwischen passivem Start-/FPS-Test oder explorativer Spielsteuerung.

Sicherheitsgrenzen: 18 Eingaben pro Spiel in einer 75-Sekunden-Testsitzung; Eingaben werden losgelassen; keine HOME/System-Tasten; Temperatur- und Akkuschutz; kein Abschluss- oder Spielbarkeitsversprechen. GameDeck unterstützt dadurch **noch keine universelle trainierte KI**, die beliebige Spiele bis zum Ende durchspielen kann. Testen zunächst nur mit gesicherten eigenen Spielständen.

Veröffentlichung bleibt eine als experimentell markierte APK mit unveränderten Eden-Core-Bibliotheken und vorhandener Android-App-Signatur.
