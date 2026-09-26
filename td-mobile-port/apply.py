#!/usr/bin/env python3
from __future__ import annotations
import argparse, shutil
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / 'src'

def read(p): return p.read_text(encoding='utf-8')
def write(p,s): p.write_text(s,encoding='utf-8',newline='\n')
def require(p):
    if not p.exists(): raise SystemExit(f'Required upstream file missing: {p}')
def replace_once(s, old, new, label):
    if new in s: return s
    n=s.count(old)
    if n != 1: raise SystemExit(f'Anchor {label!r} expected once, found {n}')
    return s.replace(old,new,1)
def insert_before_once(s, anchor, insertion, marker, label):
    if marker in s: return s
    n=s.count(anchor)
    if n != 1: raise SystemExit(f'Anchor {label!r} expected once, found {n}')
    return s.replace(anchor,insertion+anchor,1)

def copy_sources(root):
    dst=root/'android/app/src/main/java/org/tiberiandawn/android'
    dst.mkdir(parents=True,exist_ok=True)
    for name in ['FreewareCatalog.java','FreewareDataActivity.java','FreewareDownloadClient.java','FreewareIsoVerifier.java','MobileTouchDock.java','MobileTouchGuide.java']:
        shutil.copy2(SRC/name,dst/name)

def patch_import(root):
    p=root/'android/app/src/main/java/org/tiberiandawn/android/GameDataImportActivity.java'; require(p); s=read(p)
    wrapper='''    static String installDownloadedGameData(File firstIso, File secondIso, File productDirectory)
            throws UnsatisfiedLinkError {
        ensureNativeLibrariesLoaded();
        return nativeInstallGameData(
            firstIso.getAbsolutePath(),
            secondIso.getAbsolutePath(),
            productDirectory.getAbsolutePath());
    }

'''
    s=insert_before_once(s,
'''    @Override
    protected void attachBaseContext(Context newBase) {''',wrapper,'installDownloadedGameData(File firstIso','downloaded-data native wrapper')
    s=replace_once(s,
'''        content.addView(languageButton, matchWrapParams(dp(12)));\n\n        firstIsoButton = new Button(this);''',
'''        content.addView(languageButton, matchWrapParams(dp(12)));\n\n        Button freewareButton = new Button(this);\n        freewareButton.setText(R.string.import_no_discs);\n        freewareButton.setAllCaps(false);\n        freewareButton.setOnClickListener(view ->\n            startActivity(new Intent(this, FreewareDataActivity.class)));\n        content.addView(freewareButton, matchWrapParams(dp(12)));\n\n        firstIsoButton = new Button(this);''','freeware button')
    s=replace_once(s,
'''            File secondIso = copyToStaging(secondUri, secondName,\n                new File(attemptDirectory, "second.iso"), progress);\n\n            publishInstallingProgress();''',
'''            File secondIso = copyToStaging(secondUri, secondName,\n                new File(attemptDirectory, "second.iso"), progress);\n\n            verifyKnownFreewareIso(firstIso, firstName);\n            verifyKnownFreewareIso(secondIso, secondName);\n\n            publishInstallingProgress();''','freeware verification calls')
    method='''    private void verifyKnownFreewareIso(File file, String displayName)\n            throws IOException {\n        FreewareIsoVerifier.Result result =\n            FreewareIsoVerifier.verify(file, displayName);\n        if (result == FreewareIsoVerifier.Result.INVALID_SIZE) {\n            throw new IOException(getString(\n                R.string.import_error_freeware_size, displayName));\n        }\n        if (result == FreewareIsoVerifier.Result.INVALID_HASH) {\n            throw new IOException(getString(\n                R.string.import_error_freeware_hash, displayName));\n        }\n    }\n\n'''
    s=insert_before_once(s,
'''    private File copyToStaging(Uri source, String displayName, File destination,\n                               ProgressCounter progress) throws IOException {''',method,'verifyKnownFreewareIso(File file','freeware verification method')
    write(p,s)

def patch_manifest(p, quest):
    require(p); s=read(p)
    if 'android:name=".FreewareDataActivity"' in s: return
    allow='            android:allowEmbedded="true"\n' if quest else ''
    block=f'''\n        <activity\n            android:name=".FreewareDataActivity"\n            android:alwaysRetainTaskState="true"\n{allow}            android:configChanges="keyboard|keyboardHidden|layoutDirection|locale|navigation|orientation|screenLayout|screenSize|smallestScreenSize|uiMode"\n            android:exported="false"\n            android:resizeableActivity="true"\n            android:screenOrientation="userLandscape" />\n\n'''
    s=insert_before_once(s,'    </application>',block,'android:name=".FreewareDataActivity"','manifest activity')
    write(p,s)

def add_strings(p,german):
    require(p); s=read(p)
    if 'name="import_no_discs"' in s: return
    if german:
        add='''\n    <string name="import_no_discs">Ich habe keine Original-CDs</string>
    <string name="freeware_title">Spieldaten automatisch installieren</string>
    <string name="freeware_explanation">Command &amp; Conquer: Der Tiberiumkonflikt wurde als Freeware veröffentlicht. Die App kann die bekannten GDI- und Nod-ISOs von den öffentlichen ModDB-Downloadseiten automatisch laden, prüfen, installieren und danach wieder löschen.</string>
    <string name="freeware_auto_button">Spieldaten automatisch herunterladen &amp; installieren</string>
    <string name="freeware_auto_ready">Bereit. Für den Download werden etwa 1,20 GB übertragen.</string>
    <string name="freeware_manual_fallback">Falls der automatische Download nicht funktioniert, kannst du die beiden Downloadseiten weiterhin manuell öffnen.</string>
    <string name="freeware_auto_confirm_title">Automatischer Download</string>
    <string name="freeware_auto_confirm">Es werden etwa %1$s aus dem Internet geladen. Während Download und Installation werden vorübergehend ungefähr %2$s freier Speicher benötigt. Danach löscht die App die ISO-Dateien automatisch.</string>
    <string name="freeware_auto_start">Herunterladen</string>
    <string name="freeware_auto_space_error">Nicht genug freier Speicher. Benötigt: ca. %1$s. Verfügbar: %2$s.</string>
    <string name="freeware_auto_preparing">Download wird vorbereitet…</string>
    <string name="freeware_auto_downloading">%1$s wird geladen: %2$s / %3$s</string>
    <string name="freeware_auto_verifying">%1$s wird geprüft…</string>
    <string name="freeware_auto_installing">Spieldaten werden installiert…</string>
    <string name="freeware_auto_verify_error">Die heruntergeladene Datei konnte nicht als bekannte Freeware-ISO bestätigt werden.</string>
    <string name="freeware_auto_complete">Installation abgeschlossen.</string>
    <string name="freeware_auto_complete_message">GDI- und Nod-Spieldaten wurden geprüft und installiert. Die temporären ISO-Dateien wurden entfernt.</string>
    <string name="freeware_auto_failed">Automatischer Download nicht abgeschlossen.</string>
    <string name="freeware_auto_download_error">Die Spieldaten konnten nicht automatisch heruntergeladen werden. Du kannst es erneut versuchen oder unten die manuellen Downloadseiten öffnen.</string>
    <string name="freeware_download_gdi">GDI-Freeware-ISO manuell öffnen</string>
    <string name="freeware_download_nod">Nod-Freeware-ISO manuell öffnen</string>
    <string name="freeware_expected_files">Erwartete Dateien:\\n%1$s\\n%2$s</string>
    <string name="freeware_back_to_import">Zurück zum ISO-Import</string>
    <string name="freeware_open_error_title">Seite konnte nicht geöffnet werden</string>
    <string name="freeware_open_error">Es ist kein Browser verfügbar, um die Freeware-Downloadseite zu öffnen.</string>
    <string name="import_error_freeware_size">Die heruntergeladene Freeware-ISO %1$s hat eine unerwartete Größe. Sie wurde verworfen und muss erneut geladen werden.</string>
    <string name="import_error_freeware_hash">Die heruntergeladene Freeware-ISO %1$s hat die Integritätsprüfung nicht bestanden. Sie wurde verworfen und muss erneut geladen werden.</string>
    <string name="mobile_touch_commands">Befehle</string>
    <string name="mobile_touch_more">Mehr</string>
    <string name="mobile_touch_help_title">Touch-Steuerung</string>
    <string name="mobile_touch_help_text">Tippe eine Einheit an, um sie auszuwählen. Tippe auf den Boden zum Bewegen oder auf einen Gegner zum Angreifen. Ziehe mit einem Finger, um einen Auswahlrahmen aufzuziehen. Halte einen Finger gedrückt für die Sekundär-/Rechtsklick-Aktion. Ziehe mit zwei Fingern, um die Karte zu verschieben; ein kurzer Zwei-Finger-Tipp löst ebenfalls die Sekundäraktion aus. PAN in der unteren Leiste schaltet vorübergehend Ein-Finger-Ziehen auf Kartenbewegung um. Unter Befehle findest du Gruppen und taktische Aktionen.</string>
    <string name="mobile_touch_help_close">Spielen</string>
'''
    else:
        add='''\n    <string name="import_no_discs">I do not have the original discs</string>
    <string name="freeware_title">Install game data automatically</string>
    <string name="freeware_explanation">Command &amp; Conquer: Tiberian Dawn was released as freeware. The app can download the known GDI and Nod ISO images from the public ModDB download pages, verify them, install the data, and remove the temporary ISO files afterwards.</string>
    <string name="freeware_auto_button">Download &amp; install game data automatically</string>
    <string name="freeware_auto_ready">Ready. The download transfers about 1.20 GB.</string>
    <string name="freeware_manual_fallback">If automatic download stops working, the two public download pages remain available below as a manual fallback.</string>
    <string name="freeware_auto_confirm_title">Automatic download</string>
    <string name="freeware_auto_confirm">About %1$s will be downloaded. Download and installation temporarily require roughly %2$s of free storage. The app removes the ISO files after installation.</string>
    <string name="freeware_auto_start">Download</string>
    <string name="freeware_auto_space_error">Not enough free storage. Required: about %1$s. Available: %2$s.</string>
    <string name="freeware_auto_preparing">Preparing download…</string>
    <string name="freeware_auto_downloading">Downloading %1$s: %2$s / %3$s</string>
    <string name="freeware_auto_verifying">Verifying %1$s…</string>
    <string name="freeware_auto_installing">Installing game data…</string>
    <string name="freeware_auto_verify_error">The downloaded file could not be verified as a known freeware ISO.</string>
    <string name="freeware_auto_complete">Installation complete.</string>
    <string name="freeware_auto_complete_message">The GDI and Nod game data was verified and installed. Temporary ISO files were removed.</string>
    <string name="freeware_auto_failed">Automatic download did not complete.</string>
    <string name="freeware_auto_download_error">The game data could not be downloaded automatically. Try again or use the manual download pages below.</string>
    <string name="freeware_download_gdi">Open GDI freeware ISO manually</string>
    <string name="freeware_download_nod">Open Nod freeware ISO manually</string>
    <string name="freeware_expected_files">Expected files:\\n%1$s\\n%2$s</string>
    <string name="freeware_back_to_import">Back to ISO import</string>
    <string name="freeware_open_error_title">Page could not be opened</string>
    <string name="freeware_open_error">No browser was available to open the freeware download page.</string>
    <string name="import_error_freeware_size">The downloaded freeware ISO %1$s has an unexpected size. It was discarded and must be downloaded again.</string>
    <string name="import_error_freeware_hash">The downloaded freeware ISO %1$s failed its integrity check. It was discarded and must be downloaded again.</string>
    <string name="mobile_touch_commands">Commands</string>
    <string name="mobile_touch_more">More</string>
    <string name="mobile_touch_help_title">Touch controls</string>
    <string name="mobile_touch_help_text">Tap a unit to select it. Tap terrain to move or tap an enemy to attack. Drag one finger to draw a selection box. Hold one finger for the secondary/right-click action. Drag with two fingers to pan the map; a quick two-finger tap also performs the secondary action. PAN in the bottom dock temporarily turns one-finger dragging into map movement. Use Commands for groups and tactical actions.</string>
    <string name="mobile_touch_help_close">Play</string>
'''
    write(p,s.replace('</resources>',add+'</resources>',1))

def patch_game(root):
    p=root/'android/app/src/main/java/org/tiberiandawn/android/TiberianDawnActivity.java'; require(p); s=read(p)
    s=replace_once(s,'    private Button tacticalButton;\n    private boolean handPanMode;','    private Button tacticalButton;\n    private MobileTouchDock mobileTouchDock;\n    private boolean handPanMode;','dock field')
    s=replace_once(s,
'''            installQuestControls();\n            SDLInputDiagnostics.logMarker("activity-onCreate mode="''',
'''            installQuestControls();\n            if (!spatialPanel) {\n                getWindow().getDecorView().post(() -> MobileTouchGuide.showOnce(this,\n                    localizedString(R.string.mobile_touch_help_title),\n                    localizedString(R.string.mobile_touch_help_text),\n                    localizedString(R.string.mobile_touch_help_close)));\n            }\n            SDLInputDiagnostics.logMarker("activity-onCreate mode="''','first-run guide')
    s=replace_once(s,'    private void installQuestControls() {\n        if (spatialPanel) {','    private void installQuestControls() {\n        if (!spatialPanel) {\n            installMobileTouchControls();\n            return;\n        }\n        if (spatialPanel) {','mobile routing')
    methods='''    private MobileTouchDock.Labels mobileTouchLabels() {\n        return new MobileTouchDock.Labels(\n            localizedString(R.string.quest_back_label),\n            localizedString(R.string.quest_pan_label),\n            localizedString(R.string.quest_pan_label_active),\n            localizedString(R.string.mobile_touch_commands),\n            localizedString(R.string.mobile_touch_more));\n    }\n\n    private void installMobileTouchControls() {\n        if (!(mLayout instanceof RelativeLayout)) {\n            Log.w(TAG, "SDL layout does not support mobile touch dock");\n            return;\n        }\n        final RelativeLayout layout = (RelativeLayout) mLayout;\n        mobileTouchDock = new MobileTouchDock(this, layout, mobileTouchLabels(),\n            new MobileTouchDock.Callbacks() {\n                public void onBack() {\n                    SDLInputDiagnostics.logMarker("mobile-back");\n                    SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_ESCAPE);\n                    SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_ESCAPE);\n                }\n                public void onPanChanged(boolean enabled) { setHandPanMode(enabled, true); }\n                public void onCommands() { showTacticalControlsDialog(); }\n                public void onMore() { showMobileMoreDialog(); }\n            });\n        layout.setOnApplyWindowInsetsListener((view, insets) -> {\n            if (mobileTouchDock != null) mobileTouchDock.applyInsets(insets);\n            return insets;\n        });\n        layout.post(() -> {\n            if (mobileTouchDock != null) mobileTouchDock.applyInsets(layout.getRootWindowInsets());\n        });\n    }\n\n    private void showMobileMoreDialog() {\n        final String[] items = {\n            localizedString(R.string.save_transfer_title),\n            localizedString(R.string.mobile_touch_help_title),\n            localizedString(R.string.quest_diagnostics_label)\n        };\n        new AlertDialog.Builder(this)\n            .setTitle(localizedString(R.string.mobile_touch_more))\n            .setItems(items, (dialog, which) -> {\n                if (which == 0) startActivity(new Intent(this, SaveTransferActivity.class));\n                else if (which == 1) MobileTouchGuide.show(this,\n                    localizedString(R.string.mobile_touch_help_title),\n                    localizedString(R.string.mobile_touch_help_text),\n                    localizedString(R.string.mobile_touch_help_close));\n                else if (which == 2) showDiagnosticsDialog();\n            })\n            .setNegativeButton(localizedString(R.string.quest_diagnostics_close), null)\n            .show();\n    }\n\n'''
    s=insert_before_once(s,'    private void updateQuestControlInsets(WindowInsets insets) {',methods,'private void installMobileTouchControls()','mobile methods')
    s=replace_once(s,'    private void setHandPanMode(boolean enabled, boolean announce) {\n        handPanMode = enabled;','    private void setHandPanMode(boolean enabled, boolean announce) {\n        handPanMode = enabled;\n        if (mobileTouchDock != null) mobileTouchDock.setPanActive(handPanMode);','PAN sync')
    s=replace_once(s,'    private void updateQuestControlLanguage() {\n        if (backButton != null) {','    private void updateQuestControlLanguage() {\n        if (mobileTouchDock != null) mobileTouchDock.updateLabels(mobileTouchLabels());\n        if (backButton != null) {','language')
    s=replace_once(s,'        runOnUiThread(() -> {\n            final int visibility = visible ? View.VISIBLE : View.GONE;','        runOnUiThread(() -> {\n            final int visibility = visible ? View.VISIBLE : View.GONE;\n            if (mobileTouchDock != null) mobileTouchDock.setVisible(visible);','visibility')
    write(p,s)

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('checkout',type=Path); root=ap.parse_args().checkout.resolve()
    require(root/'CMakeLists.txt'); require(root/'android/app/build.gradle')
    copy_sources(root); patch_import(root)
    patch_manifest(root/'android/app/src/mobile/AndroidManifest.xml',False)
    patch_manifest(root/'android/app/src/questSpatial/AndroidManifest.xml',True)
    add_strings(root/'android/app/src/main/res/values/strings.xml',False)
    add_strings(root/'android/app/src/main/res/values-de/strings.xml',True)
    patch_game(root)
    print('Tiberian Dawn Android mobile port changes applied.')

if __name__=='__main__': main()
