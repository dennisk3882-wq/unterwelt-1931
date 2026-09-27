# C&C Alarmstufe Rot 2 + Yuri – Android Touch

Isolierter Android-Build für Red Alert 2 / Alarmstufe Rot 2 und Yuri's Revenge / Yuri's Rache.

## Ziel

Die APK enthält keine EA-/Westwood-Spieldateien. Beim ersten Start wählt der Nutzer seine eigenen Original-ISOs:

1. Alliierte CD
2. Sowjet CD
3. Yuri's Rache

Deutsch ist im Launcher standardmäßig aktiv, Englisch bleibt als Option vorhanden. Die tatsächlichen Spielstimmen, Texte und Videos stammen aus den ausgewählten Originalmedien.

## Mobile Bedienung

- Tippen: auswählen / Befehl
- Ziehen: RTS-Auswahlrahmen
- langer Druck: Rechtsklick
- Zwei-Finger-Tipp: Rechtsklick
- Pinch: 100–200 % Bildschirmzoom
- kleines Zahnrad oben rechts: kompakte Touch-Steuerung
- keine dauerhafte Emulatorleiste

## Runtime

Der Build basiert auf dem gepinnten öffentlichen Winlator-Quellstand:
brunodev85/winlator-app @ 3981d86efa4f333b2a34a7da8b6521476cd8c8b9

Die App verwendet Wine/Box64 und den bereits mit Winlator gelieferten CNC-DDraw-Wrapper. Der CNC-DDraw-Stand enthält eigene RA2-/Yuri-Kompatibilitätsprofile für game.exe und gamemd.exe.

## Medienablauf

Da Androids Storage Access Framework eine ausgewählte content://-ISO nicht direkt an den Wine-Gast als normalen Dateipfad durchreichen kann, wird jeweils nur die aktuell verarbeitete ISO temporär nach C:\RA2Mobile\staging.iso kopiert. 7-Zip aus dem eingebauten RootFS bereitet den Inhalt als virtuelles CD-Laufwerk X: auf. Die temporäre ISO-Kopie wird danach gelöscht.

Nach dem Original-Setup werden die vorbereiteten RA2- und Yuri-Medien getrennt gespeichert und beim jeweiligen Spielstart wieder als X: aktiviert.

## Build

GitHub Actions führt .github/workflows/ra2-yuri-android.yml aus und erzeugt das Artifact:

ra2-yuri-android-debug/app-debug.apk

Der normale main-Branch und der vorhandene Tiberian-Dawn-Android-Build bleiben unverändert.

Build-Version: 0.7
