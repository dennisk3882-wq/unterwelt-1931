# GameDeck S26 v27 – tatsächliche native KI-Controllereingaben

Version 27 korrigiert den entscheidenden Unterschied zwischen Edens Android-Gamepad-Eingabe und Edens virtueller Overlay-Eingabe. Für virtuelle Tasten nutzt der Agent `NativeInput.onOverlayButtonEvent` mit `NativeButton` und für die Bewegungsachsen `NativeInput.onOverlayJoystickEvent` mit `NativeAnalog.LStick`. Für physische Gamepads bleiben die Gerätekennung und der physische Port des registrierten Controllers aktiv. Testberichte kennzeichnen diesen gewählten nativen Eingabekanal ausdrücklich.

Ergänzt wurden Angaben zu tatsächlich bei Eden registrierten Eingabegeräten in der Hardwarediagnose. Vorherige Bibliothekstests, Bildschirmbeobachtung, FPS-Erfassung, Profilbenchmarks und Sicherheitsschutz bleiben erhalten.

Grenzen: Quellcode-Build und Signaturprüfung beweisen nicht, dass die jeweilige Steuerung an reale Spiele gebunden ist oder eine KI ein Spiel erfolgreich lösen kann. Daher als experimentelle Vorabversion veröffentlichen und Spielstände vor aktiven Tests sichern.
