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
    for name in ['FreewareCatalog.java','FreewareDataActivity.java','FreewareDownloadClient.java','FreewareIsoVerifier.java','GermanPackageInstaller.java','MobileTouchDock.java','MobileTouchGuide.java','MobileDisplayController.java']:
        shutil.copy2(SRC/name,dst/name)
    native_dst=root/'platform/android'
    native_dst.mkdir(parents=True,exist_ok=True)
    shutil.copy2(HERE/'native/android_inno_bridge.cpp', native_dst/'android_inno_bridge.cpp')

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
    helper='''    static void ensureNativeLibrariesForDataTools() {
        ensureNativeLibrariesLoaded();
    }

'''
    s=insert_before_once(s,
'''    @Override
    protected void attachBaseContext(Context newBase) {''',helper,'ensureNativeLibrariesForDataTools()','data-tools native loader')
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
    <string name="freeware_auto_german_button">Deutsch komplett automatisch installieren</string>
    <string name="freeware_auto_english_button">Englisch automatisch installieren</string>
    <string name="freeware_german_upgrade_title">Deutsche Spieldaten installieren</string>
    <string name="freeware_german_upgrade_explanation">Die englische Basis ist bereits installiert. Die App lädt jetzt nur noch die geprüften deutschen Sprach-, Einheiten- und Kampagnenvideodaten nach.</string>
    <string name="freeware_german_upgrade_button">Deutsch jetzt nachinstallieren</string>
    <string name="freeware_german_upgrade_ready">Bereit. Für Deutsch werden zusätzlich etwa 0,62 GB heruntergeladen.</string>
    <string name="freeware_auto_ready">Bereit. Wähle Deutsch oder Englisch. Deutsch enthält zusätzlich deutsche Sprache und Kampagnenvideos.</string>
    <string name="freeware_manual_fallback">Falls der automatische Download nicht funktioniert, kannst du die beiden Downloadseiten weiterhin manuell öffnen.</string>
    <string name="freeware_auto_confirm_title">Automatischer Download</string>
    <string name="freeware_auto_confirm">Es werden etwa %1$s aus dem Internet geladen. Während Download und Installation werden vorübergehend ungefähr %2$s freier Speicher benötigt. Danach löscht die App die temporären Installationsdateien automatisch.</string>
    <string name="freeware_german_confirm_title">Deutsche Fassung installieren</string>
    <string name="freeware_german_confirm">Für die deutsche Fassung werden etwa %1$s heruntergeladen. Während der Installation werden ungefähr %2$s freier Speicher benötigt. Enthalten sind deutsche Texte, EVA-/Einheitenstimmen und deutsche Kampagnenvideos.</string>
    <string name="freeware_auto_start">Herunterladen</string>
    <string name="freeware_auto_space_error">Nicht genug freier Speicher. Benötigt: ca. %1$s. Verfügbar: %2$s.</string>
    <string name="freeware_auto_preparing">Download wird vorbereitet…</string>
    <string name="freeware_auto_downloading">%1$s wird geladen: %2$s / %3$s</string>
    <string name="freeware_auto_verifying">%1$s wird geprüft…</string>
    <string name="freeware_auto_installing">Spieldaten werden installiert…</string>
    <string name="freeware_german_extracting">Deutsche Sprach- und Videopakete werden entpackt…</string>
    <string name="freeware_german_applying">Deutsche Spieldaten werden eingerichtet…</string>
    <string name="freeware_german_incomplete">Die deutschen Spieldaten wurden nicht vollständig installiert.</string>
    <string name="freeware_auto_verify_error">Die heruntergeladene Datei konnte nicht als bekannte Freeware-ISO bestätigt werden.</string>
    <string name="freeware_auto_complete">Installation abgeschlossen.</string>
    <string name="freeware_auto_complete_message">GDI- und Nod-Spieldaten wurden geprüft und installiert. Die temporären Installationsdateien wurden entfernt.</string>
    <string name="freeware_german_complete">Deutsche Fassung installiert.</string>
    <string name="freeware_german_complete_message">Deutsch wurde eingerichtet: deutsche Spieltexte, EVA-/Einheitenstimmen und Kampagnenvideos. Das Spiel wird jetzt neu gestartet.</string>
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
    <string name="mobile_touch_menu">Spielsteuerung öffnen</string>
    <string name="mobile_touch_zoom">Zoom</string>
    <string name="mobile_language_title">Sprache</string>
    <string name="mobile_language_system">Systemsprache</string>
    <string name="mobile_language_german">Deutsch</string>
    <string name="mobile_language_english">Englisch</string>
    <string name="mobile_language_note">Menüs und unterstützte Spieltexte werden sofort umgestellt. Sprache und Videos folgen den installierten Spieldaten.</string>
    <string name="mobile_display_title">Darstellung &amp; Zoom</string>
    <string name="mobile_display_note">HD-Grafik ist für die vorhandenen 4x-Ersatzgrafiken aktiviert; nicht ersetzte Einheiten, Gebäude und Terrain bleiben original. Zoom vergrößert die Spielfläche bis 200 %; ein Pinch mit zwei Fingern funktioniert ebenfalls.</string>
    <string name="mobile_touch_help_title">Touch-Steuerung</string>
    <string name="mobile_touch_help_text">Tippe eine Einheit an, um sie auszuwählen. Tippe auf den Boden zum Bewegen oder auf einen Gegner zum Angreifen. Ziehe mit einem Finger, um einen Auswahlrahmen aufzuziehen. Halte einen Finger gedrückt für die Sekundär-/Rechtsklick-Aktion. Ziehe mit zwei Fingern parallel, um die Karte zu verschieben. Verändere den Abstand der beiden Finger deutlich, um hinein- oder herauszuzoomen. Das Zahnrad oben rechts öffnet Zurück, PAN, Befehle, Mehr und die Zoom-Steuerung.</string>
    <string name="mobile_touch_help_close">Spielen</string>
'''
    else:
        add='''\n    <string name="import_no_discs">I do not have the original discs</string>
    <string name="freeware_title">Install game data automatically</string>
    <string name="freeware_explanation">Command &amp; Conquer: Tiberian Dawn was released as freeware. The app can download the known GDI and Nod ISO images from the public ModDB download pages, verify them, install the data, and remove the temporary ISO files afterwards.</string>
    <string name="freeware_auto_button">Download &amp; install game data automatically</string>
    <string name="freeware_auto_german_button">Install complete German version automatically</string>
    <string name="freeware_auto_english_button">Install English automatically</string>
    <string name="freeware_german_upgrade_title">Install German game data</string>
    <string name="freeware_german_upgrade_explanation">The English baseline is already installed. The app now downloads only the verified German text, speech, unit voices and campaign video resources.</string>
    <string name="freeware_german_upgrade_button">Install German data now</string>
    <string name="freeware_german_upgrade_ready">Ready. German adds about 0.62 GB of downloads.</string>
    <string name="freeware_auto_ready">Ready. Choose German or English. German additionally installs German speech and campaign videos.</string>
    <string name="freeware_manual_fallback">If automatic download stops working, the two public download pages remain available below as a manual fallback.</string>
    <string name="freeware_auto_confirm_title">Automatic download</string>
    <string name="freeware_auto_confirm">About %1$s will be downloaded. Download and installation temporarily require roughly %2$s of free storage. Temporary installer files are removed afterwards.</string>
    <string name="freeware_german_confirm_title">Install German version</string>
    <string name="freeware_german_confirm">The German version downloads about %1$s and temporarily needs roughly %2$s of free storage. It includes German game text, EVA/unit speech and German campaign videos.</string>
    <string name="freeware_auto_start">Download</string>
    <string name="freeware_auto_space_error">Not enough free storage. Required: about %1$s. Available: %2$s.</string>
    <string name="freeware_auto_preparing">Preparing download…</string>
    <string name="freeware_auto_downloading">Downloading %1$s: %2$s / %3$s</string>
    <string name="freeware_auto_verifying">Verifying %1$s…</string>
    <string name="freeware_auto_installing">Installing game data…</string>
    <string name="freeware_german_extracting">Extracting German language and video packages…</string>
    <string name="freeware_german_applying">Applying German game data…</string>
    <string name="freeware_german_incomplete">German game data was not installed completely.</string>
    <string name="freeware_auto_verify_error">The downloaded file could not be verified as a known freeware ISO.</string>
    <string name="freeware_auto_complete">Installation complete.</string>
    <string name="freeware_auto_complete_message">The GDI and Nod game data was verified and installed. Temporary installer files were removed.</string>
    <string name="freeware_german_complete">German version installed.</string>
    <string name="freeware_german_complete_message">German is installed: game text, EVA/unit speech and campaign videos. The game will now restart.</string>
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
    <string name="mobile_touch_menu">Open game controls</string>
    <string name="mobile_touch_zoom">Zoom</string>
    <string name="mobile_language_title">Language</string>
    <string name="mobile_language_system">System language</string>
    <string name="mobile_language_german">German</string>
    <string name="mobile_language_english">English</string>
    <string name="mobile_language_note">Menus and supported game text change immediately. Speech and videos follow the installed game data.</string>
    <string name="mobile_display_title">Display &amp; zoom</string>
    <string name="mobile_display_note">HD artwork is enabled for the bundled 4x replacement assets; units, buildings and terrain without replacements stay original. Zoom enlarges the game surface up to 200%; two-finger pinch works as well.</string>
    <string name="mobile_touch_help_title">Touch controls</string>
    <string name="mobile_touch_help_text">Tap a unit to select it. Tap terrain to move or tap an enemy to attack. Drag one finger to draw a selection box. Hold one finger for the secondary/right-click action. Drag two fingers together to pan the map. Change the distance between both fingers clearly to zoom in or out. The gear in the upper-right opens Back, PAN, Commands, More and zoom controls.</string>
    <string name="mobile_touch_help_close">Play</string>
'''
    write(p,s.replace('</resources>',add+'</resources>',1))

def patch_hd_artwork_default(root):
    p=root/'common/settings.cpp'; require(p); s=read(p)
    old='''void SettingsClass::Apply_Android_Startup_Defaults()
{
    // A Quest process always opens with the crisp indexed presentation and
    // original artwork. The visual options dialog can still change these
    // fields after startup; the normal save path retains the user's choice
    // for inspection/migration, but the next process deliberately resets the
    // two startup modes again.
    Video.PresentationMode = 0;
    Video.ArtworkMode = 0;
}'''
    new='''void SettingsClass::Apply_Android_Startup_Defaults()
{
    // The touch-first mobile build keeps the crisp indexed presentation but
    // enables the bundled 4x ModernArt replacements where they exist.
    // Unsupported sprites/terrain continue to use the original artwork.
    Video.PresentationMode = 0;
    Video.ArtworkMode = 1;
}'''
    s=replace_once(s,old,new,'mobile HD artwork default')
    write(p,s)

def patch_android_inno_bridge(root):
    p=root/'tiberiandawn/CMakeLists.txt'; require(p); s=read(p)
    s=replace_once(s,
'''        ${CMAKE_SOURCE_DIR}/platform/android/android_import.cpp
        ${CMAKE_SOURCE_DIR}/platform/android/android_save_bridge.cpp''',
'''        ${CMAKE_SOURCE_DIR}/platform/android/android_import.cpp
        ${CMAKE_SOURCE_DIR}/platform/android/android_inno_bridge.cpp
        ${CMAKE_SOURCE_DIR}/platform/android/android_save_bridge.cpp''','Android Inno bridge source')
    marker='''    if(ANDROID_PORT)
        target_link_libraries(TiberianDawn dl)
    endif()
'''
    anchor='''    target_link_libraries(TiberianDawn commonv ${VANILLA_LIBS} ${STATIC_LIBS})
'''
    if marker not in s:
        if s.count(anchor) != 1:
            raise SystemExit("Android dl link anchor not found exactly once")
        s=s.replace(anchor,anchor+marker,1)
    write(p,s)

def patch_german_runtime(root):
    p=root/'tiberiandawn/init.cpp'; require(p); s=read(p)
    old='''        CCDebugString("C&C95 - About to register MOVIES.MIX\\n");
        if (!MoviesMix)
            MoviesMix = new MFCD("MOVIES.MIX"); // Never cached.
'''
    new='''        const bool android_german_data =
#if defined(ANDROID_PORT)
            stricmp(Language_Name("CONQUER"), "CONQUER.GER") == 0
            && CCFileClass("MOVIESGER.MIX").Is_Available();
#else
            false;
#endif
        CCDebugString(android_german_data
            ? "C&C95 - About to register MOVIESGER.MIX\\n"
            : "C&C95 - About to register MOVIES.MIX\\n");
        if (!MoviesMix)
            MoviesMix = new MFCD(android_german_data ? "MOVIESGER.MIX" : "MOVIES.MIX"); // Never cached.
'''
    s=replace_once(s,old,new,'German movie mix selection')

    old='''    CCDebugString("C&C95 - About to register SPEECH.MIX\\n");
    if (CCFileClass("SPEECH.MIX").Is_Available()) {
        new MFCD("SPEECH.MIX"); // Never cached.
    }
    CCDebugString("C&C95 - About to register SOUNDS.MIX\\n");
    new MFCD("SOUNDS.MIX"); // Cached.
'''
    new='''#if defined(ANDROID_PORT)
    const bool android_german_speech =
        stricmp(Language_Name("CONQUER"), "CONQUER.GER") == 0
        && CCFileClass("SPEECGER.MIX").Is_Available();
    CCDebugString(android_german_speech
        ? "C&C95 - About to register SPEECGER.MIX\\n"
        : "C&C95 - About to register SPEECH.MIX\\n");
    if (CCFileClass(android_german_speech ? "SPEECGER.MIX" : "SPEECH.MIX").Is_Available()) {
        new MFCD(android_german_speech ? "SPEECGER.MIX" : "SPEECH.MIX");
    }
#else
    CCDebugString("C&C95 - About to register SPEECH.MIX\\n");
    if (CCFileClass("SPEECH.MIX").Is_Available()) {
        new MFCD("SPEECH.MIX"); // Never cached.
    }
#endif
    CCDebugString("C&C95 - About to register SOUNDS.MIX\\n");
    new MFCD("SOUNDS.MIX"); // Cached.
#if defined(ANDROID_PORT)
    if (android_german_speech && CCFileClass("TALKGER.MIX").Is_Available()) {
        CCDebugString("C&C95 - About to register TALKGER.MIX\\n");
        new MFCD("TALKGER.MIX");
    }
#endif
'''
    s=replace_once(s,old,new,'German speech mix selection')
    write(p,s)

    p=root/'tiberiandawn/conquer.cpp'; require(p); s=read(p)
    old='''        MoviesMix = new MFCD("MOVIES.MIX");
        GeneralMix = new MFCD("GENERAL.MIX");
        ScoreMix = new MFCD("SCORES.MIX");
'''
    new='''#if defined(ANDROID_PORT)
        const bool android_german_movies =
            stricmp(Language_Name("CONQUER"), "CONQUER.GER") == 0
            && CCFileClass("MOVIESGER.MIX").Is_Available();
        MoviesMix = new MFCD(android_german_movies ? "MOVIESGER.MIX" : "MOVIES.MIX");
#else
        MoviesMix = new MFCD("MOVIES.MIX");
#endif
        GeneralMix = new MFCD("GENERAL.MIX");
        ScoreMix = new MFCD("SCORES.MIX");
'''
    if new not in s:
        count=s.count(old)
        if count != 2:
            raise SystemExit(f"German movie reinit anchor expected twice, found {count}")
        s=s.replace(old,new)
    write(p,s)

def patch_game(root):
    p=root/'android/app/src/main/java/org/tiberiandawn/android/TiberianDawnActivity.java'; require(p); s=read(p)
    s=replace_once(s,'    private Button tacticalButton;\n    private boolean handPanMode;','    private Button tacticalButton;\n    private MobileTouchDock mobileTouchDock;\n    private MobileDisplayController mobileDisplayController;\n    private boolean handPanMode;','dock field')
    s=replace_once(s,
'''    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Horizon can deliver the same controller ACTION_SCROLL through the
        // touch dispatcher when the ray crosses an ActivityPanel.
        if (consumeSpatialWindowAdjustment(event)) return true;
        return super.dispatchTouchEvent(event);
    }''',
'''    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Horizon can deliver the same controller ACTION_SCROLL through the
        // touch dispatcher when the ray crosses an ActivityPanel.
        if (consumeSpatialWindowAdjustment(event)) return true;
        if (!spatialPanel && mobileDisplayController != null
                && mobileDisplayController.onDispatchTouchEvent(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }''','mobile pinch zoom routing')
    s=replace_once(s,
'''            installQuestControls();\n            SDLInputDiagnostics.logMarker("activity-onCreate mode="''',
'''            installQuestControls();\n            if (!spatialPanel) {\n                getWindow().getDecorView().post(() -> MobileTouchGuide.showOnce(this,\n                    localizedString(R.string.mobile_touch_help_title),\n                    localizedString(R.string.mobile_touch_help_text),\n                    localizedString(R.string.mobile_touch_help_close)));\n            }\n            SDLInputDiagnostics.logMarker("activity-onCreate mode="''','first-run guide')
    s=replace_once(s,'    private void installQuestControls() {\n        if (spatialPanel) {','    private void installQuestControls() {\n        if (!spatialPanel) {\n            installMobileTouchControls();\n            return;\n        }\n        if (spatialPanel) {','mobile routing')
    methods='''    private MobileTouchDock.Labels mobileTouchLabels() {
        return new MobileTouchDock.Labels(
            localizedString(R.string.quest_back_label),
            localizedString(R.string.quest_pan_label),
            localizedString(R.string.quest_pan_label_active),
            localizedString(R.string.mobile_touch_commands),
            localizedString(R.string.mobile_touch_more),
            localizedString(R.string.mobile_touch_menu),
            localizedString(R.string.mobile_touch_zoom));
    }

    private void installMobileTouchControls() {
        if (!(mLayout instanceof RelativeLayout)) {
            Log.w(TAG, "SDL layout does not support mobile touch dock");
            return;
        }
        final RelativeLayout layout = (RelativeLayout) mLayout;

        mobileDisplayController = new MobileDisplayController(this, mSurface, percent -> {
            if (mobileTouchDock != null) mobileTouchDock.setZoomPercent(percent);
        });

        mobileTouchDock = new MobileTouchDock(this, layout, mobileTouchLabels(),
            new MobileTouchDock.Callbacks() {
                public void onBack() {
                    SDLInputDiagnostics.logMarker("mobile-back");
                    SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_ESCAPE);
                    SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_ESCAPE);
                }
                public void onPanChanged(boolean enabled) { setHandPanMode(enabled, true); }
                public void onCommands() { showTacticalControlsDialog(); }
                public void onMore() { showMobileMoreDialog(); }
                public void onZoomOut() {
                    if (mobileDisplayController != null) mobileDisplayController.zoomOut();
                }
                public void onZoomReset() {
                    if (mobileDisplayController != null) mobileDisplayController.resetZoom();
                }
                public void onZoomIn() {
                    if (mobileDisplayController != null) mobileDisplayController.zoomIn();
                }
            });
        if (mobileDisplayController != null) {
            mobileTouchDock.setZoomPercent(mobileDisplayController.getZoomPercent());
        }
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            if (mobileTouchDock != null) mobileTouchDock.applyInsets(insets);
            return insets;
        });
        layout.post(() -> {
            if (mobileTouchDock != null) mobileTouchDock.applyInsets(layout.getRootWindowInsets());
        });
    }

    private void showMobileMoreDialog() {
        final String[] items = {
            localizedString(R.string.save_transfer_title),
            localizedString(R.string.mobile_touch_help_title),
            localizedString(R.string.mobile_language_title),
            localizedString(R.string.mobile_display_title),
            localizedString(R.string.quest_diagnostics_label)
        };
        new AlertDialog.Builder(this)
            .setTitle(localizedString(R.string.mobile_touch_more))
            .setItems(items, (dialog, which) -> {
                if (which == 0) startActivity(new Intent(this, SaveTransferActivity.class));
                else if (which == 1) MobileTouchGuide.show(this,
                    localizedString(R.string.mobile_touch_help_title),
                    localizedString(R.string.mobile_touch_help_text),
                    localizedString(R.string.mobile_touch_help_close));
                else if (which == 2) showMobileLanguageDialog();
                else if (which == 3) showMobileDisplayDialog();
                else if (which == 4) showDiagnosticsDialog();
            })
            .setNegativeButton(localizedString(R.string.quest_diagnostics_close), null)
            .show();
    }

    private void showMobileLanguageDialog() {
        final String[] items = {
            localizedString(R.string.mobile_language_system),
            localizedString(R.string.mobile_language_german),
            localizedString(R.string.mobile_language_english)
        };
        new AlertDialog.Builder(this)
            .setTitle(localizedString(R.string.mobile_language_title))
            .setSingleChoiceItems(items, LanguagePreferences.get(this), (dialog, which) -> {
                if (which == LanguagePreferences.GERMAN
                        && !GermanPackageInstaller.isInstalled(this)) {
                    dialog.dismiss();
                    Intent installer = new Intent(this, FreewareDataActivity.class);
                    installer.putExtra(FreewareDataActivity.EXTRA_GERMAN_ONLY, true);
                    startActivity(installer);
                    return;
                }
                LanguagePreferences.set(this, which);
                try {
                    nativeConfigureLanguage(LanguagePreferences.get(this),
                        LanguagePreferences.systemLanguageTag());
                    languageNativeReady = true;
                } catch (UnsatisfiedLinkError error) {
                    languageNativeReady = false;
                    Log.w(TAG, "Native language bridge is unavailable during mobile update", error);
                }
                updateQuestControlLanguage();
                Toast.makeText(this, localizedString(R.string.mobile_language_note),
                    Toast.LENGTH_LONG).show();
                dialog.dismiss();
            })
            .setNegativeButton(localizedString(R.string.quest_diagnostics_close), null)
            .show();
    }

    private void showMobileDisplayDialog() {
        int percent = mobileDisplayController == null
            ? 100 : mobileDisplayController.getZoomPercent();
        new AlertDialog.Builder(this)
            .setTitle(localizedString(R.string.mobile_display_title))
            .setMessage(localizedString(R.string.mobile_display_note)
                + "\\n\\n" + localizedString(R.string.mobile_touch_zoom)
                + ": " + percent + "%")
            .setNeutralButton("100%", (dialog, which) -> {
                if (mobileDisplayController != null) mobileDisplayController.resetZoom();
            })
            .setNegativeButton(localizedString(R.string.quest_diagnostics_close), null)
            .show();
    }

'''
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
    patch_hd_artwork_default(root)
    patch_android_inno_bridge(root)
    patch_german_runtime(root)
    patch_game(root)
    print('Tiberian Dawn Android mobile port changes applied.')

if __name__=='__main__': main()
