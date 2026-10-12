package com.winlator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.winlator.box64.Box64Preset;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.FileUtils;
import com.winlator.core.WineRegistryEditor;
import com.winlator.core.WineUtils;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RA2LauncherActivity extends AppCompatActivity {
    private static final String PREFS = "ra2_mobile";
    private static final String KEY_CONTAINER = "container_id";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_ALLIED = "iso_allied";
    private static final String KEY_SOVIET = "iso_soviet";
    private static final String KEY_YURI = "iso_yuri";
    private static final String KEY_RA2_EXE = "ra2_exe";
    private static final String KEY_YURI_EXE = "yuri_exe";
    private static final String KEY_STAGE = "pipeline_stage";
    private static final String KEY_RUNTIME_OUTSTANDING = "runtime_outstanding";
    private static final String KEY_LAST_GAME_LAUNCH = "last_game_launch";
    private static final String KEY_LAST_GAME_LAUNCH_AT = "last_game_launch_at";
    private static final String KEY_LAST_EXIT_CODE = "last_exit_code";
    private static final String KEY_LAST_SAW_CHILD = "last_saw_child";
    private static final String KEY_LAST_DIAG_CLUE = "last_diag_clue";

    private static final String STAGE_NONE = "";
    private static final String STAGE_RA2_ALLIED = "ra2_allied";
    private static final String STAGE_RA2_SOVIET = "ra2_soviet";
    private static final String STAGE_RA2_SETUP = "ra2_setup";
    private static final String STAGE_RA2_CAB = "ra2_cab";
    private static final String STAGE_YURI_EXTRACT = "yuri_extract";
    private static final String STAGE_YURI_SETUP = "yuri_setup";
    private static final String STAGE_YURI_CAB = "yuri_cab";

    private static final int PICK_ALLIED = 4101;
    private static final int PICK_SOVIET = 4102;
    private static final int PICK_YURI = 4103;
    private static final int PICK_XWIS_RA2 = 4111;
    private static final int PICK_XWIS_YURI = 4112;
    private static final String KEY_XWIS_PENDING = "xwis_installer_pending";
    private static final String KEY_XWIS_DOWNLOAD_ID = "xwis_download_id";
    private static final String KEY_XWIS_DOWNLOAD_YURI = "xwis_download_yuri";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private SharedPreferences prefs;
    private Container container;
    private boolean runtimeStarting;

    private TextView title;
    private TextView subtitle;
    private TextView status;
    private TextView storageHint;
    private ProgressBar progress;
    private Button cdKeysButton;
    private Button languageDe;
    private volatile boolean installationBusy;
    private Button languageEn;
    private Button alliedButton;
    private Button sovietButton;
    private Button yuriIsoButton;
    private Button installRa2Button;
    private Button installYuriButton;
    private Button playRa2Button;
    private Button playYuriButton;
    private Button playXwisRa2;
    private Button playXwisYuri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildUi());
        applyLanguage();
        ensureRuntimeAndContainer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs == null) return;
        if (container != null && prefs.getLong(KEY_XWIS_DOWNLOAD_ID, -1) > 0) checkCommunityDownload(false);
        if (prefs.getBoolean(KEY_RUNTIME_OUTSTANDING, false) && !runtimeStarting) {
            String pendingXwis = prefs.getString(KEY_XWIS_PENDING, "");
            if (!pendingXwis.isEmpty()) {
                prefs.edit().putBoolean(KEY_RUNTIME_OUTSTANDING, false).remove(KEY_XWIS_PENDING).apply();
                finishCommunityInstallation("yuri".equals(pendingXwis));
                refreshGameState();
                return;
            }
            prefs.edit().putBoolean(KEY_RUNTIME_OUTSTANDING, false).apply();
            continuePipeline(prefs.getString(KEY_STAGE, STAGE_NONE));
        }
        else {
            reportPreviousGameExit();
        }
        refreshGameState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (prefs != null && prefs.getBoolean(KEY_RUNTIME_OUTSTANDING, false)) {
            runtimeStarting = false;
        }
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8, 12, 14));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        title = label(30, Color.rgb(238, 220, 170));
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap(0));

        subtitle = label(15, Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle, matchWrap(0));
        TextView version = label(12, Color.LTGRAY);
        version.setText("v0.42 · CD-Schlüssel direkt im Spiel eingeben");
        version.setGravity(Gravity.CENTER);
        root.addView(version, matchWrap(0));

        LinearLayout languageRow = horizontal();
        languageDe = button("");
        languageEn = button("");
        languageRow.addView(languageDe, weighted());
        languageRow.addView(languageEn, weighted());
        root.addView(languageRow, matchWrap(dp(8)));

        languageDe.setOnClickListener(v -> setLanguage("de"));
        languageEn.setOnClickListener(v -> setLanguage("en"));

        TextView mediaHeading = label(20, Color.WHITE);
        mediaHeading.setText("Originalmedien");
        mediaHeading.setTag("mediaHeading");
        root.addView(mediaHeading, matchWrap(dp(20)));

        alliedButton = button("");
        sovietButton = button("");
        yuriIsoButton = button("");
        root.addView(alliedButton, matchWrap(dp(8)));
        root.addView(sovietButton, matchWrap(dp(8)));
        root.addView(yuriIsoButton, matchWrap(dp(8)));

        alliedButton.setOnClickListener(v -> pickIso(PICK_ALLIED));
        sovietButton.setOnClickListener(v -> pickIso(PICK_SOVIET));
        yuriIsoButton.setOnClickListener(v -> pickIso(PICK_YURI));

        storageHint = label(13, Color.LTGRAY);
        storageHint.setPadding(dp(4), dp(8), dp(4), dp(6));
        root.addView(storageHint, matchWrap(0));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setVisibility(View.GONE);
        root.addView(progress, matchWrap(dp(4)));

        status = label(14, Color.rgb(185, 220, 190));
        status.setPadding(dp(4), dp(7), dp(4), dp(12));
        root.addView(status, matchWrap(0));

        cdKeysButton = button("");
        cdKeysButton.setOnClickListener(v -> editOriginalKeys());
        root.addView(cdKeysButton, matchWrap(dp(7)));

        installRa2Button = accentButton("");
        installYuriButton = accentButton("");
        root.addView(installRa2Button, matchWrap(dp(7)));
        root.addView(installYuriButton, matchWrap(dp(7)));
        installRa2Button.setOnClickListener(v -> beginRa2Install());
        installYuriButton.setOnClickListener(v -> beginYuriInstall());

        playRa2Button = button("");
        playYuriButton = button("");
        root.addView(playRa2Button, matchWrap(dp(18)));
        root.addView(playYuriButton, matchWrap(dp(7)));
        Button originalStartProbe = button("");
        originalStartProbe.setTag("originalStartProbeButton");
        root.addView(originalStartProbe, matchWrap(dp(7)));
        originalStartProbe.setOnClickListener(v -> launchGame(false, true));
        playRa2Button.setOnClickListener(v -> launchGame(false));
        playYuriButton.setOnClickListener(v -> launchGame(true));

        TextView communityHeading = label(20, Color.rgb(235, 211, 162));
        communityHeading.setText("XWIS – kompatibler Start ohne Original-CD-Launcher");
        root.addView(communityHeading, matchWrap(dp(20)));
        TextView communityInfo = label(13, Color.LTGRAY);
        communityInfo.setText("Separater Spielclient. Original-ISOs, Kampagnen, Videos und bisherige Installation bleiben erhalten.");
        root.addView(communityInfo, matchWrap(dp(5)));
        LinearLayout communityDownloads = horizontal();
        Button downloadXwisRa2 = accentButton("XWIS RA2 herunterladen");
        Button downloadXwisYuri = accentButton("XWIS Yuri herunterladen");
        communityDownloads.addView(downloadXwisRa2, weighted());
        communityDownloads.addView(downloadXwisYuri, weighted());
        root.addView(communityDownloads, matchWrap(dp(7)));
        Button xwisBrowser = button("Archiv im Browser öffnen (RA2)");
        root.addView(xwisBrowser, matchWrap(dp(7)));
        xwisBrowser.setOnClickListener(v -> openCommunityDownloadInBrowser(false));
        Button xwisCheck = button("XWIS-Download prüfen / übernehmen");
        root.addView(xwisCheck, matchWrap(dp(7)));
        xwisCheck.setOnClickListener(v -> checkCommunityDownload(true));
        downloadXwisRa2.setOnClickListener(v -> downloadCommunityInstaller(false, true));
        downloadXwisYuri.setOnClickListener(v -> downloadCommunityInstaller(true, true));
        LinearLayout communityImports = horizontal();
        Button importXwisRa2 = button("RA2-Installer auswählen");
        Button importXwisYuri = button("Yuri-Installer auswählen");
        communityImports.addView(importXwisRa2, weighted());
        communityImports.addView(importXwisYuri, weighted());
        root.addView(communityImports, matchWrap(dp(7)));
        importXwisRa2.setOnClickListener(v -> pickCommunityInstaller(false));
        importXwisYuri.setOnClickListener(v -> pickCommunityInstaller(true));
        playXwisRa2 = button("RA2 – XWIS-Client starten");
        playXwisYuri = button("Yuri – XWIS-Client starten");
        root.addView(playXwisRa2, matchWrap(dp(7)));
        root.addView(playXwisYuri, matchWrap(dp(7)));
        playXwisRa2.setOnClickListener(v -> launchCommunityGame(false));
        playXwisYuri.setOnClickListener(v -> launchCommunityGame(true));

        TextView vmTitle = label(20, Color.rgb(180, 235, 210));
        vmTitle.setText("NEUE SPIELENGINE – RA2 + YURI OHNE WINE");
        root.addView(vmTitle, matchWrap(dp(18)));
        TextView vmHint = label(13, Color.LTGRAY);
        vmHint.setText("Experimenteller GPL-Client RA2 VM: nutzt Original-EXEs mit einer eigenen x86-/Win32-Laufzeit. Kampagnen noch nicht vollständig geprüft. Originaldateien bleiben erhalten.");
        root.addView(vmHint, matchWrap(dp(7)));
        Button vmKeyEntry = button("🔑 Original-CD-Schlüssel für die neue Engine");
        root.addView(vmKeyEntry, matchWrap(dp(8)));
        vmKeyEntry.setOnClickListener(v -> editOriginalKeys());
        Button vmLaunch = accentButton("RA2 / YURI MIT NEUER ENGINE TESTEN");
        root.addView(vmLaunch, matchWrap(dp(12)));
        vmLaunch.setOnClickListener(v -> startActivity(new Intent(this, RA2VmActivity.class)));

        TextView touchInfo = label(13, Color.LTGRAY);
        touchInfo.setTag("touchInfo");
        touchInfo.setPadding(dp(4), dp(18), dp(4), dp(84));
        root.addView(touchInfo, matchWrap(0));

        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(Color.rgb(8, 12, 14));
        shell.addView(scroll, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        Button diagnostics = accentButton("🩺 Diagnose");
        diagnostics.setTag("diagnosticsButton");
        diagnostics.setOnClickListener(v -> {
            Intent intent = new Intent(this, RA2DiagnosticsActivity.class);
            startActivity(intent);
        });

        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(
            dp(170), dp(54), Gravity.BOTTOM | Gravity.END);
        dp.setMargins(dp(12), dp(12), dp(18), dp(18));
        shell.addView(diagnostics, dp);

        return shell;
    }

    private void setLanguage(String language) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
        applyWineLocale();
        applyLanguage();
    }

    private boolean isGerman() {
        return !"en".equals(prefs.getString(KEY_LANGUAGE, "de"));
    }

    private void applyLanguage() {
        if (title == null) return;
        boolean de = isGerman();
        title.setText("COMMAND & CONQUER\nALARMSTUFE ROT 2");
        subtitle.setText(de ? "Yuri’s Rache • Android Touch Edition" : "Yuri’s Revenge • Android Touch Edition");
        languageDe.setText(de ? "✓ Deutsch" : "Deutsch");
        languageEn.setText(!de ? "✓ English" : "English");

        alliedButton.setText(slotText(KEY_ALLIED, de ? "Alliierte-CD auswählen" : "Select Allied disc"));
        sovietButton.setText(slotText(KEY_SOVIET, de ? "Sowjet-CD auswählen" : "Select Soviet disc"));
        yuriIsoButton.setText(slotText(KEY_YURI, de ? "Yuri’s Rache auswählen" : "Select Yuri’s Revenge"));

        storageHint.setText(de
            ? "Die ISO wird während der Einrichtung temporär in den App-Bereich kopiert und direkt von Android gelesen. Die CD-Daten bleiben als virtuelles Laufwerk X: erhalten, damit Originalvideos und CD-Abfragen funktionieren."
            : "The ISO is copied temporarily into private app storage and read directly by Android. Disc data remains as virtual drive X: for original movies and disc checks.");

        cdKeysButton.setText(de ? "Original-CD-Schlüssel" : "Original CD keys");
        installRa2Button.setText(de ? "Alarmstufe Rot 2 einrichten" : "Set up Red Alert 2");
        installYuriButton.setText(de ? "Yuri’s Rache einrichten" : "Set up Yuri’s Revenge");
        playRa2Button.setText(de ? "ALARMSTUFE ROT 2 STARTEN" : "START RED ALERT 2");
        playYuriButton.setText(de ? "YURI’S RACHE STARTEN (ORIGINAL)" : "START YURI’S REVENGE (ORIGINAL)");
        if (playXwisRa2 != null) playXwisRa2.setText(de ? "RA2 – XWIS-Client starten" : "Launch RA2 XWIS client");
        if (playXwisYuri != null) playXwisYuri.setText(de ? "Yuri – XWIS-Client starten" : "Launch Yuri XWIS client");

        View root = title.getRootView();
        TextView mediaHeading = root.findViewWithTag("mediaHeading");
        if (mediaHeading != null) mediaHeading.setText(de ? "Originalmedien" : "Original media");
        TextView touchInfo = root.findViewWithTag("touchInfo");
        if (touchInfo != null) {
            touchInfo.setText(de
                ? "Touch: Tippen = auswählen/Befehl • Ziehen = Auswahlrahmen • langer Druck oder Zwei-Finger-Tipp = Rechtsklick • Pinch = Zoom • ⚙ oben rechts = Steuerung."
                : "Touch: tap = select/command • drag = selection box • long press or two-finger tap = right click • pinch = zoom • ⚙ top-right = controls.");
        }
        Button diagnostics = root.findViewWithTag("diagnosticsButton");
        if (diagnostics != null) diagnostics.setText(de ? "🩺 Diagnose" : "🩺 Diagnostics");
        Button originalProbe = root.findViewWithTag("originalStartProbeButton");
        if (originalProbe != null) originalProbe.setText(de ? "🧪 RA2.EXE-Originalstart testen" : "🧪 Test original RA2.EXE launcher");
        refreshGameState();
    }

    private String slotText(String key, String emptyText) {
        String value = prefs.getString(key, "");
        if (value.isEmpty()) return "＋  " + emptyText;
        Uri uri = Uri.parse(value);
        return "✓  " + displayName(uri);
    }

    private void pickIso(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        catch (Exception ignored) {}

        String key;
        if (requestCode == PICK_XWIS_RA2 || requestCode == PICK_XWIS_YURI) {
            importCommunityInstaller(uri, requestCode == PICK_XWIS_YURI);
            return;
        }
        if (requestCode == PICK_ALLIED) key = KEY_ALLIED;
        else if (requestCode == PICK_SOVIET) key = KEY_SOVIET;
        else if (requestCode == PICK_YURI) key = KEY_YURI;
        else return;

        prefs.edit().putString(key, uri.toString()).apply();
        applyLanguage();
    }

    private void ensureRuntimeAndContainer() {
        RootFS rootFS = RootFS.find(this);
        if (!rootFS.isValid() || rootFS.getVersion() < RootFSInstaller.LATEST_VERSION) {
            setBusy(true, isGerman() ? "Windows-Spielumgebung wird einmalig eingerichtet…" : "Setting up the Windows game runtime…");
            RootFSInstaller.installIfNeeded(this);
            pollRuntime();
        }
        else {
            ensureContainer();
        }
    }

    private void pollRuntime() {
        if (isFinishing() || isDestroyed()) return;
        RootFS rootFS = RootFS.find(this);
        if (rootFS.isValid() && rootFS.getVersion() >= RootFSInstaller.LATEST_VERSION) {
            ensureContainer();
        }
        else {
            handler.postDelayed(this::pollRuntime, 700);
        }
    }

    private void ensureContainer() {
        ContainerManager manager = new ContainerManager(this);
        int wantedId = prefs.getInt(KEY_CONTAINER, 0);
        container = wantedId > 0 ? manager.getContainerById(wantedId) : null;
        if (container != null) {
            configureContainer(container);
            setBusy(false, isGerman() ? "Laufzeit bereit." : "Runtime ready.");
            refreshGameState();
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("name", "Red Alert 2 + Yuri");
            data.put("screenSize", "1280x720");
            data.put("envVars", Container.DEFAULT_ENV_VARS);
            data.put("graphicsDriver", GraphicsDrivers.getDefaultDriver(this));
            data.put("dxwrapper", "wined3d");
            data.put("dxwrapperConfig", "ddrawWrapper=cnc-ddraw");
            data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
            data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
            data.put("drives", Container.DEFAULT_DRIVES);
            data.put("startupSelection", Container.STARTUP_SELECTION_NORMAL);
            data.put("box64Preset", Box64Preset.STABILITY);
            data.put("extraData", new JSONObject());

            setBusy(true, isGerman() ? "RA2-Spielumgebung wird angelegt…" : "Creating RA2 game environment…");
            manager.createContainerAsync(data, created -> {
                container = created;
                if (created == null) {
                    setBusy(false, isGerman() ? "Spielumgebung konnte nicht erstellt werden." : "Could not create game environment.");
                    return;
                }
                prefs.edit().putInt(KEY_CONTAINER, created.id).apply();
                configureContainer(created);
                setBusy(false, isGerman() ? "Bereit für deine Original-ISOs." : "Ready for your original ISOs.");
                refreshGameState();
            });
        }
        catch (Exception e) {
            setBusy(false, "Container error: " + e.getMessage());
        }
    }

    private void configureContainer(Container target) {
        target.setName("Red Alert 2 + Yuri");
        target.setScreenSize("1280x720");
        target.setDXWrapper("wined3d");
        target.setDXWrapperConfig("ddrawWrapper=cnc-ddraw");
        target.setStartupSelection(Container.STARTUP_SELECTION_NORMAL);
        target.setBox64Preset(Box64Preset.STABILITY);
        target.saveData();

        // RA2/Yuri use OLE/RPC during startup. Winlator's ESSENTIAL mode disables
        // RpcSs and several related services, which causes RPC_S_SERVER_UNAVAILABLE
        // before GAME.EXE can reach the actual game window.
        WineUtils.changeServicesStatus(target, Container.STARTUP_SELECTION_NORMAL);
        repairRa2Services(target);
        applyWineLocale();
    }

    private void repairRa2Services(Container target) {
        File systemReg = new File(target.getRootDir(), ".wine/system.reg");
        if (!systemReg.isFile()) return;

        try (WineRegistryEditor registry = new WineRegistryEditor(systemReg)) {
            String controlSet = registry.getSymlinkValue("System\\CurrentControlSet", "SymbolicLinkValue");
            if (controlSet == null || controlSet.isEmpty()) controlSet = "System\\CurrentControlSet";

            registry.setDwordValue(controlSet + "\\Services\\RpcSs", "Start", 2);
            registry.setDwordValue(controlSet + "\\Services\\PlugPlay", "Start", 2);
            registry.setDwordValue(controlSet + "\\Services\\Eventlog", "Start", 2);
            registry.setDwordValue(controlSet + "\\Services\\NDIS", "Start", 2);
            registry.setDwordValue(controlSet + "\\Services\\nsiproxy", "Start", 2);
            registry.setDwordValue(controlSet + "\\Services\\MSIServer", "Start", 3);
            registry.setDwordValue(controlSet + "\\Services\\FontCache", "Start", 3);
        }
        catch (Exception ignored) {}
    }

    private void applyWineLocale() {
        if (container == null) return;
        File userReg = new File(container.getRootDir(), ".wine/user.reg");
        if (!userReg.isFile()) return;
        try (WineRegistryEditor registry = new WineRegistryEditor(userReg)) {
            if (isGerman()) {
                registry.setStringValue("Control Panel\\International", "LocaleName", "de-DE");
                registry.setStringValue("Control Panel\\International", "sLanguage", "DEU");
                registry.setStringValue("Control Panel\\International", "iCountry", "49");
            }
            else {
                registry.setStringValue("Control Panel\\International", "LocaleName", "en-US");
                registry.setStringValue("Control Panel\\International", "sLanguage", "ENU");
                registry.setStringValue("Control Panel\\International", "iCountry", "1");
            }
        }
        catch (Exception ignored) {}
    }

    private void beginRa2Install() {
        if (!readyForInstall()) return;

        // v0.16 recovery path: if both discs were already copied and GAME1.CAB
        // survived a failed helper run, retry only CAB extraction instead of
        // making the user re-copy ~1.3 GB of ISO data.
        File existingCab = selectRa2Cab();
        File existingGame = findIgnoreCase(gameDir(), "game.exe", 4);
        File existingRa2Mix = findIgnoreCase(gameDir(), "ra2.mix", 6);
        File existingLanguageMix = findIgnoreCase(gameDir(), "language.mix", 6);
        if (existingCab != null && existingCab.isFile() && existingGame != null &&
                (existingRa2Mix == null || existingLanguageMix == null)) {
            String cabName = existingCab.getName();
            prefs.edit().putString(KEY_STAGE, STAGE_RA2_CAB).apply();
            setBusy(true, isGerman()
                ? "Vorhandenes GAME1.CAB erkannt. Die fehlenden MIX-Dateien werden direkt erneut entpackt…"
                : "Existing GAME1.CAB found. Retrying the missing MIX extraction directly…");
            extractCabPayload(false);
            return;
        }
        Uri allied = savedUri(KEY_ALLIED);
        Uri soviet = savedUri(KEY_SOVIET);
        if (allied == null || soviet == null) {
            message(isGerman() ? "Bitte zuerst Alliierte- und Sowjet-ISO auswählen." : "Select the Allied and Soviet ISOs first.");
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle(isGerman() ? "Alarmstufe Rot 2 einrichten" : "Set up Red Alert 2")
            .setMessage(isGerman()
                ? "Beide Original-CDs werden direkt ausgewertet. Die Spieldateien werden ohne das alte Westwood-Setup installiert. Deinen Original-CD-Schlüssel kannst du über den Button Original-CD-Schlüssel eintragen."
                : "Both original discs will be read directly. The game files are installed without the old Westwood setup.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(isGerman() ? "Starten" : "Start", (d, w) -> {
                preparePhysicalDriveX();
                copyAndExtract(allied, STAGE_RA2_ALLIED);
            })
            .show();
    }

    private void beginYuriInstall() {
        if (!readyForInstall()) return;
        if (selectYuriCab() != null && findIgnoreCase(gameDir(), "gamemd.exe", 2) != null && isInstalled(false)) {
            extractCabPayload(true);
            return;
        }
        Uri yuri = savedUri(KEY_YURI);
        if (yuri == null) {
            message(isGerman() ? "Bitte zuerst die Yuri’s-Rache-ISO auswählen." : "Select the Yuri’s Revenge ISO first.");
            return;
        }
        if (!isInstalled(false)) {
            message(isGerman() ? "Bitte zuerst Alarmstufe Rot 2 installieren." : "Install Red Alert 2 first.");
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle(isGerman() ? "Yuri’s Rache einrichten" : "Set up Yuri’s Revenge")
            .setMessage(isGerman()
                ? "Die Yuri-CD wird direkt ausgewertet und in dieselbe RA2-Installation integriert."
                : "The Yuri disc will be read directly and merged into the same RA2 installation.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(isGerman() ? "Starten" : "Start", (d, w) -> {
                preparePhysicalDriveX();
                copyAndExtract(yuri, STAGE_YURI_EXTRACT);
            })
            .show();
    }

    private boolean readyForInstall() {
        if (installationBusy) return false;
        if (container == null) {
            message(isGerman() ? "Die Laufzeit wird noch vorbereitet." : "The runtime is still being prepared.");
            return false;
        }
        return true;
    }

    private void copyAndExtract(Uri source, String stage) {
        File staging = stagingIso();
        setBusy(true, isGerman() ? "ISO wird in den App-Speicher kopiert…" : "Copying ISO into app storage…");
        ioExecutor.execute(() -> {
            try {
                long sourceSize = querySize(source);
                long free = container.getRootDir().getFreeSpace();
                if (sourceSize > 0 && free < sourceSize + 300L * 1024L * 1024L) {
                    throw new Exception(isGerman()
                        ? "Nicht genug freier Speicher für die temporäre ISO-Kopie."
                        : "Not enough free space for the temporary ISO copy.");
                }

                File parent = staging.getParentFile();
                if (!parent.isDirectory()) parent.mkdirs();
                FileUtils.delete(staging);

                try (InputStream in = getContentResolver().openInputStream(source);
                     FileOutputStream out = new FileOutputStream(staging)) {
                    if (in == null) throw new Exception("Unable to open ISO");
                    byte[] buffer = new byte[1024 * 1024];
                    long copied = 0;
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        out.write(buffer, 0, read);
                        copied += read;
                        long finalCopied = copied;
                        if (sourceSize > 0) {
                            runOnUiThread(() ->
                                progress.setProgress((int)Math.min(400, finalCopied * 400L / sourceSize)));
                        }
                    }
                    out.flush();
                }

                runOnUiThread(() ->
                    status.setText(isGerman()
                        ? "CD-Daten werden direkt unter Android vorbereitet…"
                        : "Preparing disc files directly on Android…"));

                final String[] lastShownName = {""};
                Iso9660Extractor.extract(staging, driveX(), (done, imageBytes, currentName) -> {
                    int extractionProgress = imageBytes > 0
                        ? (int)Math.min(600, done * 600L / imageBytes)
                        : 0;
                    if (!currentName.equals(lastShownName[0])) {
                        lastShownName[0] = currentName;
                        runOnUiThread(() -> {
                            progress.setProgress(400 + extractionProgress);
                            status.setText((isGerman() ? "Entpacke: " : "Extracting: ") + currentName);
                        });
                    }
                    else {
                        runOnUiThread(() -> progress.setProgress(400 + extractionProgress));
                    }
                });

                String volumeName = Iso9660Extractor.readVolumeId(staging);
                if (!volumeName.isEmpty() &&
                        !FileUtils.writeString(new File(driveX(), ".windows-label"), volumeName + "\n"))
                    throw new java.io.IOException("ISO volume name could not be saved");
                FileUtils.delete(staging);

                runOnUiThread(() -> {
                    prefs.edit().putString(KEY_STAGE, stage).apply();
                    progress.setProgress(1000);
                    status.setText(isGerman() ? "CD vorbereitet." : "Disc prepared.");
                    continuePipeline(stage);
                });
            }
            catch (Exception e) {
                FileUtils.delete(staging);
                runOnUiThread(() -> {
                    prefs.edit().putString(KEY_STAGE, STAGE_NONE).apply();
                    setBusy(false, (isGerman() ? "ISO-Fehler: " : "ISO error: ") + e.getMessage());
                });
            }
        });
    }

    private void continuePipeline(String stage) {
        ioExecutor.execute(() -> continuePipelineWorker(stage));
    }

    private void continuePipelineWorker(String stage) {
        if (container == null) {
            runOnUiThread(this::ensureRuntimeAndContainer);
            return;
        }
        FileUtils.delete(stagingIso());

        if (STAGE_RA2_ALLIED.equals(stage)) {
            try {
                setBusy(true, isGerman() ? "Alliierte CD wird in den Spielordner übernommen…" : "Copying Allied disc files into the game folder…");
                prepareRa2Disc(true);
                preparePhysicalDriveX();

                Uri soviet = savedUri(KEY_SOVIET);
                if (soviet == null) {
                    stopPipeline("Soviet ISO missing");
                    return;
                }
                copyAndExtract(soviet, STAGE_RA2_SOVIET);
            }
            catch (Exception e) {
                stopPipeline((isGerman() ? "Dateifehler: " : "File error: ") + e.getMessage());
            }
            return;
        }

        if (STAGE_RA2_SOVIET.equals(stage)) {
            try {
                setBusy(true, isGerman() ? "Sowjet-CD wird in den Spielordner übernommen…" : "Copying Soviet disc files into the game folder…");
                prepareRa2Disc(false);

                File cab = selectRa2Cab();
                if (cab == null || !cab.isFile()) {
                    stopPipeline(isGerman()
                        ? "Kein RA2-CAB-Archiv gefunden. Erkannte CAB/HDR-Dateien: " + listArchiveFiles(ra2CabDir())
                        : "No RA2 CAB archive found. Detected CAB/HDR files: " + listArchiveFiles(ra2CabDir()));
                    return;
                }

                String cabName = cab.getName();
                prefs.edit().putString(KEY_STAGE, STAGE_RA2_CAB).apply();
                setBusy(true, isGerman()
                    ? "RA2.MIX und LANGUAGE.MIX werden aus " + cabName + " entpackt…"
                    : "Extracting RA2.MIX and LANGUAGE.MIX from " + cabName + "…");
                extractCabPayload(false);
            }
            catch (Exception e) {
                stopPipeline((isGerman() ? "Dateifehler: " : "File error: ") + e.getMessage());
            }
            return;
        }

        if (STAGE_RA2_CAB.equals(stage)) {
            File game = findIgnoreCase(gameDir(), "game.exe", 4);
            File ra2Mix = findIgnoreCase(gameDir(), "ra2.mix", 6);
            File languageMix = findIgnoreCase(gameDir(), "language.mix", 6);

            flattenKnownMix(gameDir(), ra2Mix, "ra2.mix");
            flattenKnownMix(gameDir(), languageMix, "language.mix");
            ra2Mix = findIgnoreCase(gameDir(), "ra2.mix", 2);
            languageMix = findIgnoreCase(gameDir(), "language.mix", 2);

            String[] recommended = {
                "binkw32.dll", "blowfish.dll", "blowfish.tlb", "drvmgt.dll",
                "mph.exe", "patchget.dat", "patchw32.dll", "ra2.exe",
                "ra2.lcf", "ra2.tlb"
            };

            if (game == null || ra2Mix == null || languageMix == null) {
                StringBuilder missing = new StringBuilder();
                if (game == null) missing.append(" GAME.EXE");
                if (ra2Mix == null) missing.append(" RA2.MIX");
                if (languageMix == null) missing.append(" LANGUAGE.MIX");
                stopPipeline((isGerman()
                    ? "Direktinstallation unvollständig. Es fehlen:"
                    : "Direct installation incomplete. Missing:") + missing);
                return;
            }

            StringBuilder optionalMissing = new StringBuilder();
            for (String name : recommended) {
                if (findIgnoreCase(gameDir(), name, 2) == null) {
                    if (optionalMissing.length() > 0) optionalMissing.append(", ");
                    optionalMissing.append(name);
                }
            }

            storeMedia(false);
            writeWestwoodRegistry(false);
            tuneCncDdraw();
            prefs.edit()
                .putString(KEY_RA2_EXE, game.getAbsolutePath())
                .putString(KEY_STAGE, STAGE_NONE)
                .apply();
            FileUtils.delete(ra2CabDir());
            File secdrv = new File(container.getRootDir(), ".wine/drive_c/windows/system32/drivers/secdrv.sys");
            if (optionalMissing.length() == 0) {
                if (!secdrv.isFile() && savedUri(KEY_YURI) != null) {
                    setBusy(false, isGerman()
                        ? "RA2-Dateien vollständig. Beim Start wird für erkannte SafeDisc-EXEs automatisch der CD-Kompatibilitätshelfer verwendet."
                        : "RA2 files are complete. Recognized SafeDisc executables automatically use the CD compatibility helper at launch.");
                }
                else {
                    setBusy(false, isGerman()
                        ? "Alarmstufe Rot 2 vollständig aus den Original-CDs installiert."
                        : "Red Alert 2 fully installed from the original discs.");
                }
            }
            else {
                setBusy(false, (isGerman()
                    ? "RA2 installiert. Zusätzliche Kompatibilitätsdateien fehlen: "
                    : "RA2 installed. Additional compatibility files missing: ") + optionalMissing);
            }
            runOnUiThread(this::refreshGameState);
            return;
        }

        if (STAGE_YURI_EXTRACT.equals(stage)) {
            try {
                setBusy(true, isGerman() ? "Yuri-CD wird in den Spielordner übernommen…" : "Copying Yuri disc files into the game folder…");
                prepareYuriDisc();

                File cab = selectYuriCab();
                if (cab == null || !cab.isFile()) {
                    stopPipeline(isGerman()
                        ? "Kein CAB-Archiv auf der Yuri-CD gefunden. Erkannte INSTALL-Dateien: " + listInstallFiles(driveX())
                        : "No CAB archive found on the Yuri disc. Detected INSTALL files: " + listInstallFiles(driveX()));
                    return;
                }

                String cabName = cab.getName();
                prefs.edit().putString(KEY_STAGE, STAGE_YURI_CAB).apply();
                setBusy(true, isGerman()
                    ? "RA2MD.MIX und LANGMD.MIX werden aus den Yuri-CAB-Dateien entpackt…"
                    : "Extracting RA2MD.MIX and LANGMD.MIX from the Yuri CAB files…");
                extractCabPayload(true);
            }
            catch (Exception e) {
                stopPipeline((isGerman() ? "Dateifehler: " : "File error: ") + e.getMessage());
            }
            return;
        }

        if (STAGE_YURI_CAB.equals(stage)) {
            File game = findIgnoreCase(gameDir(), "gamemd.exe", 4);
            File ra2Mix = findIgnoreCase(gameDir(), "ra2md.mix", 6);
            File languageMix = findIgnoreCase(gameDir(), "langmd.mix", 6);

            if (game == null || ra2Mix == null || languageMix == null) {
                StringBuilder missing = new StringBuilder();
                if (game == null) missing.append(" GAMEMD.EXE");
                if (ra2Mix == null) missing.append(" RA2MD.MIX");
                if (languageMix == null) missing.append(" LANGMD.MIX");
                stopPipeline((isGerman()
                    ? "Yuri-Direktinstallation unvollständig. Es fehlen:"
                    : "Yuri direct installation incomplete. Missing:") + missing);
                return;
            }

            flattenKnownMix(gameDir(), ra2Mix, "ra2md.mix");
            flattenKnownMix(gameDir(), languageMix, "langmd.mix");
            game = findIgnoreCase(gameDir(), "gamemd.exe", 2);

            installSafeDiscDriverFromYuri();
            storeMedia(true);
            writeWestwoodRegistry(true);
            tuneCncDdraw();
            File launcher = findIgnoreCase(gameDir(), "ra2md.exe", 2);
            if (launcher == null) launcher = game;
            prefs.edit()
                .putString(KEY_YURI_EXE, launcher.getAbsolutePath())
                .putString(KEY_STAGE, STAGE_NONE)
                .apply();
            FileUtils.delete(yuriCabDir());
            setBusy(false, isGerman()
                ? "Yuri’s Rache wurde direkt aus der Original-CD installiert."
                : "Yuri’s Revenge was installed directly from the original disc.");
            runOnUiThread(this::refreshGameState);
        }
    }


    private void extractCabPayload(boolean yuri) {
        setBusy(true, isGerman() ? "Spieldateien werden direkt unter Android entpackt…" : "Extracting game files on Android…");
        ioExecutor.execute(() -> {
            try {
                String report = CabExtractor.extractRequired(yuri ? yuriCabDir() : ra2CabDir(), gameDir(),
                    yuri ? new String[]{"ra2md.mix", "langmd.mix"} : new String[]{"ra2.mix", "language.mix"},
                    name -> runOnUiThread(() -> status.setText("Entpacke: " + name)));
                FileUtils.writeString(new File(gameDir(), yuri ? "yuri-install.log" : "ra2-install.log"), report);
                continuePipeline(yuri ? STAGE_YURI_CAB : STAGE_RA2_CAB);
            } catch (Exception | LinkageError e) {
                stopPipeline("CAB-Fehler: " + e.getMessage());
                runOnUiThread(this::refreshGameState);
            }
        });
    }

    private File gameDir() {
        File dir = new File(container.getRootDir(), ".wine/drive_c/Westwood/RA2");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    private File ra2CabDir() {
        File dir = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/RA2CAB");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    private File yuriCabDir() {
        File dir = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/YURICAB");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    private void prepareRa2Disc(boolean allied) throws Exception {
        File root = driveX();
        File install = findChildIgnoreCase(root, "INSTALL");
        if (install == null || !install.isDirectory()) {
            throw new Exception(isGerman() ? "INSTALL-Ordner der RA2-CD fehlt." : "RA2 INSTALL folder is missing.");
        }

        File dst = gameDir();

        // Preserve the original install payload. This is intentionally broader than
        // the first implementation because classic RA2 expects several launcher/
        // patch/security files in addition to GAME.EXE and the MIX archives.
        copyInstallPayload(install, dst, 4);

        copyOptional(root, dst,
            "MULTI.MIX", "THEME.MIX", "WDT.MIX",
            "NL.CFG", "WOLDATA.KEY", "WOLAPI.DLL", "WOLAPI.WAR");

        if (allied) {
            copyRequired(root, dst, "MAPS01.MIX", "MOVIES01.MIX");
        }
        else {
            copyRequired(root, dst, "MAPS02.MIX", "MOVIES02.MIX");
        }

        if (allied) {
            FileUtils.delete(ra2CabDir());
            ra2CabDir().mkdirs();
            int archives = copyCabArchivesRecursive(install, ra2CabDir(), 4);
            if (archives == 0) {
                throw new Exception(isGerman()
                    ? "Keine RA2-CAB/HDR-Dateien gefunden."
                    : "No RA2 CAB/HDR files found.");
            }
        }
    }

    private void prepareYuriDisc() throws Exception {
        File root = driveX();
        File install = findChildIgnoreCase(root, "INSTALL");
        if (install == null || !install.isDirectory()) {
            throw new Exception(isGerman() ? "INSTALL-Ordner der Yuri-CD fehlt." : "Yuri INSTALL folder is missing.");
        }

        File dst = gameDir();
        copyRequired(root, dst, "MAPSMD03.MIX", "MOVMD03.MIX", "MULTIMD.MIX", "THEMEMD.MIX");
        copyInstallPayload(install, dst, 4);
        copyRequired(install, dst, "GAMEMD.EXE", "MPHMD.EXE", "RA2MD.EXE", "YURI.EXE");

        FileUtils.delete(yuriCabDir());
        yuriCabDir().mkdirs();

        int copiedCabFiles = copyCabArchivesRecursive(install, yuriCabDir(), 3);
        if (copiedCabFiles == 0) {
            throw new Exception(isGerman()
                ? "Keine CAB/HDR-Dateien im Yuri-INSTALL-Ordner gefunden. Vorhanden: " + listDirectoryNames(install)
                : "No CAB/HDR files found in Yuri INSTALL. Present: " + listDirectoryNames(install));
        }
    }

    private void copyInstallPayload(File source, File destination, int depth) throws Exception {
        if (source == null || depth < 0 || !source.exists()) return;

        if (source.isFile()) {
            String upper = source.getName().toUpperCase(Locale.ENGLISH);
            if (upper.endsWith(".CAB") || upper.endsWith(".HDR")) return;
            if (upper.startsWith("SETUP") && upper.endsWith(".EXE")) return;

            if (!destination.getParentFile().isDirectory()) destination.getParentFile().mkdirs();
            if (!FileUtils.copy(source, destination)) {
                throw new Exception((isGerman() ? "Kopieren fehlgeschlagen: " : "Copy failed: ") + source.getName());
            }
            return;
        }

        if (!destination.isDirectory() && !destination.mkdirs()) {
            throw new Exception((isGerman() ? "Ordner konnte nicht erstellt werden: " : "Could not create folder: ") + destination.getName());
        }

        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) {
            File target = new File(destination, child.getName());
            if (child.isDirectory()) {
                if (depth > 0) copyInstallPayload(child, target, depth - 1);
            }
            else {
                copyInstallPayload(child, target, depth);
            }
        }
    }

    private void copyRequired(File sourceDir, File destinationDir, String... names) throws Exception {
        for (String name : names) {
            File source = findChildIgnoreCase(sourceDir, name);
            if (source == null || !source.isFile()) {
                throw new Exception((isGerman() ? "Datei fehlt: " : "Missing file: ") + name);
            }
            if (!FileUtils.copy(source, new File(destinationDir, source.getName()))) {
                throw new Exception((isGerman() ? "Kopieren fehlgeschlagen: " : "Copy failed: ") + name);
            }
        }
    }

    private void copyOptional(File sourceDir, File destinationDir, String... names) {
        for (String name : names) {
            File source = findChildIgnoreCase(sourceDir, name);
            if (source != null && source.isFile()) {
                FileUtils.copy(source, new File(destinationDir, source.getName()));
            }
        }
    }

    private void copyOptionalDirectory(File sourceDir, File destinationDir, String name) {
        File source = findChildIgnoreCase(sourceDir, name);
        if (source != null && source.isDirectory()) {
            FileUtils.copy(source, new File(destinationDir, source.getName()));
        }
    }

    private File findChildIgnoreCase(File dir, String name) {
        if (dir == null || !dir.isDirectory()) return null;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.getName().equalsIgnoreCase(name)) return child;
        }
        return null;
    }

    private void flattenKnownMix(File targetDir, File source, String targetName) {
        if (source == null || !source.isFile()) return;
        File target = new File(targetDir, targetName);
        if (!source.equals(target)) {
            FileUtils.copy(source, target);
        }
    }

    private void installSafeDiscDriverFromYuri() {
        if (container == null) return;

        File secdrv = findIgnoreCase(gameDir(), "secdrv.sys", 5);
        if (secdrv == null || !secdrv.isFile()) return;

        File driveC = new File(container.getRootDir(), ".wine/drive_c");
        File system32Drivers = new File(driveC, "windows/system32/drivers");
        File syswow64Drivers = new File(driveC, "windows/syswow64/drivers");
        system32Drivers.mkdirs();
        syswow64Drivers.mkdirs();

        FileUtils.copy(secdrv, new File(system32Drivers, "secdrv.sys"));
        FileUtils.copy(secdrv, new File(syswow64Drivers, "secdrv.sys"));

        File systemReg = new File(container.getRootDir(), ".wine/system.reg");
        try (WineRegistryEditor registry = new WineRegistryEditor(systemReg)) {
            String key = "System\\CurrentControlSet\\Services\\Secdrv";
            registry.setStringValue(key, "ImagePath", "C:\\windows\\system32\\drivers\\secdrv.sys");
            registry.setDwordValue(key, "Type", 1);
            registry.setDwordValue(key, "Start", 3);
            registry.setDwordValue(key, "ErrorControl", 1);
        }
        catch (Exception ignored) {}
    }

    private void editOriginalKeys() {
        if(installationBusy || runtimeStarting) {
            message(isGerman()?"Bitte die laufende Einrichtung zuerst beenden.":"Finish the current setup first.");
            return;
        }
        boolean de=isGerman();
        LinearLayout form = new LinearLayout(this);
        form.setBackgroundColor(Color.rgb(24,29,34));
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20),dp(10),dp(20),dp(8));
        TextView hint=label(14,Color.WHITE);
        hint.setText(de?"Trage die Schlüssel von deinen Originalverpackungen ein. Leer lassen behält den bisherigen Wert. Die Eingabe bleibt auf deinem Gerät. Die App prüft das Eingabeformat, nicht die Echtheit des Schlüssels.":"Enter the keys from your original boxes. Leave a field empty to keep its current value. Keys stay on this device. The app checks input format, not authenticity.");
        form.addView(hint,matchWrap(0));
        android.widget.EditText ra2 = new android.widget.EditText(this);
        android.widget.EditText yuri = new android.widget.EditText(this);
        android.widget.EditText[] inputs={ra2,yuri};
        for(int i=0;i<inputs.length;i++) {
            TextView name=label(14,Color.WHITE);
            name.setText(i==0?"Alarmstufe Rot 2 / Red Alert 2":"Yuri’s Rache / Yuri’s Revenge");
            form.addView(name,matchWrap(dp(10)));
            inputs[i].setSingleLine(true);
            inputs[i].setTextColor(Color.WHITE);
            inputs[i].setHintTextColor(Color.LTGRAY);
            inputs[i].setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            inputs[i].setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
            inputs[i].setHint(de?"Neuer Schlüssel (optional)":"New key (optional)");
            form.addView(inputs[i],matchWrap(0));
        }
        ScrollView keyScroll=new ScrollView(this);
        keyScroll.setBackgroundColor(Color.rgb(24,29,34));
        keyScroll.addView(form);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setTitle(de?"Original-CD-Schlüssel":"Original CD keys").setView(keyScroll)
            .setNegativeButton(de?"Abbrechen":"Cancel",null).setPositiveButton(de?"Speichern":"Save",null).create();
        dialog.setOnShowListener(v -> {
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
                String a=ra2.getText().toString(), b=yuri.getText().toString();
                if((!a.trim().isEmpty() && !RA2SerialPolicy.acceptableInput(a)) || (!b.trim().isEmpty() && !RA2SerialPolicy.acceptableInput(b))) {
                    if(!a.trim().isEmpty() && !RA2SerialPolicy.acceptableInput(a)) ra2.setError(de?"Bitte den vollständigen Schlüssel mit Buchstaben/Ziffern eingeben.":"Enter the complete key using letters/digits.");
                    if(!b.trim().isEmpty() && !RA2SerialPolicy.acceptableInput(b)) yuri.setError(de?"Bitte den vollständigen Schlüssel mit Buchstaben/Ziffern eingeben.":"Enter the complete key using letters/digits.");
                    return;
                }
                SharedPreferences.Editor edit=prefs.edit();
                if(!a.trim().isEmpty()) edit.putString("original_cd_key_ra2",RA2SerialPolicy.normalize(a));
                if(!b.trim().isEmpty()) edit.putString("original_cd_key_yuri",RA2SerialPolicy.normalize(b));
                edit.apply();
                if(container!=null) {
                    writeWestwoodRegistry(false);
                    if(findIgnoreCase(gameDir(),"gamemd.exe",2)!=null) writeWestwoodRegistry(true);
                }
                dialog.dismiss();
                message(de?"Schlüssel gespeichert. Starte das Spiel erneut; die Einrichtung muss nicht wiederholt werden.":"Keys saved. Start the game again; no reinstall is needed.");
            });
        });
        dialog.show();
    }

    private void writeWestwoodRegistry(boolean yuri) {
        if (yuri) writeWestwoodRegistry(false);
        File systemReg = new File(container.getRootDir(), ".wine/system.reg");
        try (WineRegistryEditor registry = new WineRegistryEditor(systemReg)) {
            String product = yuri ? "Yuri's Revenge" : "Red Alert 2";
            String root = "Software\\Westwood\\" + product;
            String root32 = "Software\\Wow6432Node\\Westwood\\" + product;
            String serial = RA2SerialPolicy.resolve(prefs.getString(yuri ? "original_cd_key_yuri" : "original_cd_key_ra2", ""),
                registry.getStringValue(root32, "Serial"), registry.getStringValue(root, "Serial"));
            String[] views = container.isWoW64Mode() ? new String[]{root, root32} : new String[]{root};
            for (String view : views) {
                registry.setStringValue(view, "Name", product);
                registry.setStringValue(view, "InstallPath", yuri
                    ? "C:\\Westwood\\RA2\\GAMEMD.EXE"
                    : "C:\\Westwood\\RA2\\GAME.EXE");
                registry.setStringValue(view, "FolderPath", "C:\\Westwood\\RA2");
                registry.setStringValue(view, "Serial", serial);
                registry.setStringValue(view, "Language", isGerman() ? "German" : "English");
                registry.setDwordValue(view, "SKU", yuri ? 0x00000901 : 0x00000801);
                registry.setDwordValue(view, "Version", yuri ? 0x00010001 : 0x00010006);
            }
            File wolapi = findIgnoreCase(gameDir(), "wolapi.dll", 2);
            if (wolapi != null) {
                registry.setStringValue("Software\\Westwood\\WOLAPI", "InstallPath", "C:\\Westwood\\RA2\\WOLAPI.DLL");
                if(container.isWoW64Mode()) registry.setStringValue("Software\\Wow6432Node\\Westwood\\WOLAPI", "InstallPath", "C:\\Westwood\\RA2\\WOLAPI.DLL");
            }
        }
        catch (Exception ignored) {}
    }

    private void tuneCncDdraw() {
        File globalIni = new File(container.getRootDir(), ".wine/drive_c/ProgramData/cnc-ddraw/ddraw.ini");
        if (globalIni.isFile()) {
            String cfg = FileUtils.readString(globalIni);
            if (cfg != null && !cfg.isEmpty()) {
                cfg = replaceConfig(cfg, "renderer", "opengl");
                cfg = replaceConfig(cfg, "windowed", "false");
                cfg = replaceConfig(cfg, "fullscreen", "true");
                cfg = replaceConfig(cfg, "nonexclusive", "true");
                cfg = replaceConfig(cfg, "singlecpu", "true");
                cfg = replaceConfig(cfg, "maxfps", "60");
                cfg = replaceConfig(cfg, "adjmouse", "true");
                cfg = replaceConfig(cfg, "maintas", "true");
                FileUtils.writeString(globalIni, cfg);
                FileUtils.copy(globalIni, new File(gameDir(), "ddraw.ini"));
            }
        }

        // Do not copy ddraw.dll here. At this point Winlator may still expose the
        // builtin Wine DLL. The runtime copies the real CNC-DDraw wrapper after
        // extractDXWrapperFiles() has completed.
        FileUtils.delete(new File(gameDir(), "ddraw.dll"));

        File ra2Ini = new File(gameDir(), "RA2.INI");
        if (!ra2Ini.isFile()) {
            FileUtils.writeString(ra2Ini,
                "[Video]\\n" +
                "ScreenWidth=1024\\n" +
                "ScreenHeight=768\\n" +
                "VideoBackBuffer=no\\n" +
                "AllowHiResModes=yes\\n");
        }
    }

    private String replaceConfig(String cfg, String key, String value) {
        String[] lines = cfg.split("\\n", -1);
        boolean replaced = false;
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!replaced && trimmed.startsWith(key + "=") && !trimmed.startsWith(";")) {
                out.append(key).append("=").append(value);
                replaced = true;
            }
            else {
                out.append(line);
            }
            out.append("\\n");
        }
        if (!replaced) out.append(key).append("=").append(value).append("\\n");
        return out.toString();
    }

    private int copyCabArchivesRecursive(File source, File destination, int depth) {
        if (source == null || depth < 0 || !source.exists()) return 0;
        int copied = 0;
        if (source.isFile()) {
            String upper = source.getName().toUpperCase(Locale.ENGLISH);
            if (upper.endsWith(".CAB") || upper.endsWith(".HDR")) {
                File target = new File(destination, source.getName());
                if (FileUtils.copy(source, target)) copied++;
            }
            return copied;
        }

        File[] children = source.listFiles();
        if (children == null) return 0;
        for (File child : children) {
            if (child.isDirectory() && depth > 0) {
                copied += copyCabArchivesRecursive(child, destination, depth - 1);
            }
            else if (child.isFile()) {
                String upper = child.getName().toUpperCase(Locale.ENGLISH);
                if (upper.endsWith(".CAB") || upper.endsWith(".HDR")) {
                    File target = new File(destination, child.getName());
                    if (FileUtils.copy(child, target)) copied++;
                }
            }
        }
        return copied;
    }

    private File selectRa2Cab() {
        File preferred = findChildIgnoreCase(ra2CabDir(), "Game1.CAB");
        if (preferred != null && preferred.isFile()) return preferred;

        File[] files = ra2CabDir().listFiles();
        if (files == null) return null;

        File largest = null;
        for (File file : files) {
            if (!file.isFile()) continue;
            String upper = file.getName().toUpperCase(Locale.ENGLISH);
            if (!upper.endsWith(".CAB")) continue;

            if (upper.equals("GAME1.CAB")) return file;
            if (largest == null || file.length() > largest.length()) largest = file;
        }
        return largest;
    }

    private String listArchiveFiles(File dir) {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null || files.length == 0) return "-";

        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (File file : files) {
            if (!file.isFile()) continue;
            String upper = file.getName().toUpperCase(Locale.ENGLISH);
            if (!upper.endsWith(".CAB") && !upper.endsWith(".HDR")) continue;
            if (shown++ >= 20) {
                out.append(" …");
                break;
            }
            if (out.length() > 0) out.append(", ");
            out.append(file.getName()).append(" (").append(file.length() / (1024 * 1024)).append(" MB)");
        }
        return out.length() == 0 ? "-" : out.toString();
    }

    private File selectYuriCab() {
        File preferred = findChildIgnoreCase(yuriCabDir(), "Game1.CAB");
        if (preferred != null && preferred.isFile()) return preferred;
        preferred = findChildIgnoreCase(yuriCabDir(), "Game6.CAB");
        if (preferred != null && preferred.isFile()) return preferred;

        File[] files = yuriCabDir().listFiles();
        if (files == null) return null;
        File best = null;
        for (File file : files) {
            if (!file.isFile() || !file.getName().toUpperCase(Locale.ENGLISH).endsWith(".CAB")) continue;
            if (best == null || file.getName().compareToIgnoreCase(best.getName()) < 0) best = file;
        }
        return best;
    }

    private String listInstallFiles(File root) {
        File install = findChildIgnoreCase(root, "INSTALL");
        return install == null ? "-" : listDirectoryNames(install);
    }

    private String listDirectoryNames(File dir) {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null || files.length == 0) return "-";
        StringBuilder names = new StringBuilder();
        int shown = 0;
        for (File file : files) {
            if (shown++ >= 12) {
                names.append(" …");
                break;
            }
            if (names.length() > 0) names.append(", ");
            names.append(file.getName());
        }
        return names.toString();
    }

    private void reportPreviousGameExit() {
        String last = prefs.getString(KEY_LAST_GAME_LAUNCH, "");
        long started = prefs.getLong(KEY_LAST_GAME_LAUNCH_AT, 0L);
        if (last.isEmpty() || started <= 0L) return;

        long seconds = Math.max(0L, (System.currentTimeMillis() - started) / 1000L);
        prefs.edit().remove(KEY_LAST_GAME_LAUNCH).remove(KEY_LAST_GAME_LAUNCH_AT).apply();

        File diagnostic = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/last-start.log");
        String diag = diagnostic.isFile() ? FileUtils.readString(diagnostic) : "";
        String exitCode = prefs.contains(KEY_LAST_EXIT_CODE)
            ? String.valueOf(prefs.getInt(KEY_LAST_EXIT_CODE, -999))
            : diagnosticValue(diag, "exit=");
        Integer gameResult = RA2LaunchBatch.readGameExit(new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/game-exit.txt"));
        if (gameResult != null) exitCode = String.valueOf(gameResult);
        String child = prefs.contains(KEY_LAST_SAW_CHILD)
            ? String.valueOf(prefs.getBoolean(KEY_LAST_SAW_CHILD, false))
            : diagnosticValue(diag, "sawGameChild=");
        String clue = prefs.getString(KEY_LAST_DIAG_CLUE, "");
        if (clue.isEmpty()) clue = diagnosticClue(diag);
        if (gameResult != null) clue = "Spiel-Rückgabecode " + gameResult +
            "; Details im Diagnose-Center.";
        // Keep the last detailed diagnostics for the Diagnose-Center.

        StringBuilder message = new StringBuilder();
        if (isGerman()) {
            message.append(last).append(" wurde nach ").append(seconds).append(" s beendet.");
            if (!exitCode.isEmpty()) message.append(" Exit-Code ").append(exitCode).append(".");
            if ("false".equalsIgnoreCase(child)) message.append(" Kein Spielfenster wurde erkannt.");
            if (!clue.isEmpty()) message.append(" Diagnose: ").append(clue);
        }
        else {
            message.append(last).append(" exited after ").append(seconds).append(" s.");
            if (!exitCode.isEmpty()) message.append(" Exit code ").append(exitCode).append(".");
            if ("false".equalsIgnoreCase(child)) message.append(" No running GAME.EXE child process was detected.");
            if (!clue.isEmpty()) message.append(" Diagnostic: ").append(clue);
        }
        status.setText(message.toString());
    }

    private String diagnosticValue(String text, String prefix) {
        if (text == null || text.isEmpty()) return "";
        String[] lines = text.split("\\n");
        for (String line : lines) {
            if (line.startsWith(prefix)) return line.substring(prefix.length()).trim();
        }
        return "";
    }

    private String diagnosticClue(String text) {
        if (text == null || text.isEmpty()) return "";
        String[] lines = text.split("\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            String lower = line.toLowerCase(Locale.ENGLISH);
            if (lower.contains("err:") || lower.contains("exception") ||
                lower.contains("failed") || lower.contains("cannot") ||
                lower.contains("missing") || lower.contains("not found")) {
                if (line.length() > 180) line = line.substring(0, 180);
                return line;
            }
        }
        return "";
    }

    private void stopPipeline(String text) {
        prefs.edit()
            .putString(KEY_STAGE, STAGE_NONE)
            .putBoolean(KEY_RUNTIME_OUTSTANDING, false)
            .apply();
        setBusy(false, text);
    }

    private void launchRuntimeDos(String dosPath, String args) {
        runtimeStarting = true;
        prefs.edit().putBoolean(KEY_RUNTIME_OUTSTANDING, true).apply();
        Intent intent = new Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_dos_path", dosPath);
        intent.putExtra("exec_args", args == null ? "" : args);
        int slash = Math.max(dosPath.lastIndexOf('\\'), dosPath.lastIndexOf('/'));
        intent.putExtra("ra2_helper_process", slash >= 0 ? dosPath.substring(slash + 1) : dosPath);
        intent.putExtra("ra2_helper_stage", prefs.getString(KEY_STAGE, STAGE_NONE));
        intent.putExtra("ra2_mode", true);
        intent.putExtra("ra2_language", isGerman() ? "de" : "en");
        startActivity(intent);
    }

    private File communityFolder() {
        File folder = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/XWISClient");
        if (!folder.isDirectory()) folder.mkdirs();
        return folder;
    }

    private File communityPackages() {
        File folder = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/XWISPackages");
        if (!folder.isDirectory()) folder.mkdirs();
        return folder;
    }

    private void pickCommunityInstaller(boolean yuri) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, yuri ? PICK_XWIS_YURI : PICK_XWIS_RA2);
    }

    private void importCommunityInstaller(Uri uri, boolean yuri) {
        if (container == null) { message("Wine-Laufzeit noch nicht bereit."); return; }
        setBusy(true, "Übernehme ausgewählten XWIS-Installer …");
        ioExecutor.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException("Installer kann nicht gelesen werden");
                copyCommunityPackage(in, yuri, -1);
                runOnUiThread(() -> startCommunitySetup(yuri));
            } catch (Exception error) {
                runOnUiThread(() -> setBusy(false, "XWIS-Import fehlgeschlagen: " + error.getMessage()));
            }
        });
    }

    /**
     * Hand downloads to Android's DownloadManager. Its system HTTP stack handles
     * the large Internet Archive CDN redirects and survives activity pauses.
     * Old XWIS links are documented but no longer assumed reachable.
     */
    private void downloadCommunityInstaller(boolean yuri, boolean archive) {
        if (container == null) {message("Wine-Laufzeit noch nicht bereit.");return;}
        String source = archive ? (yuri ? RA2XwisClient.YURI_ARCHIVE : RA2XwisClient.RA2_ARCHIVE)
                                : (yuri ? RA2XwisClient.YURI_URL : RA2XwisClient.RA2_URL);
        try {
            android.app.DownloadManager manager=(android.app.DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            if(manager==null)throw new java.io.IOException("Android DownloadManager fehlt");
            long existing=prefs.getLong(KEY_XWIS_DOWNLOAD_ID,-1);
            if(existing>0){
                android.app.DownloadManager.Query q=new android.app.DownloadManager.Query().setFilterById(existing);
                try(android.database.Cursor c=manager.query(q)){
                    if(c!=null && c.moveToFirst()){
                        int state=c.getInt(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS));
                        if(state==android.app.DownloadManager.STATUS_PENDING||
                           state==android.app.DownloadManager.STATUS_RUNNING||
                           state==android.app.DownloadManager.STATUS_PAUSED){
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Ein Download läuft bereits")
                                .setMessage("Bitte erst den bestehenden Download prüfen oder abbrechen.")
                                .setPositiveButton("Fortschritt prüfen",(d,w)->checkCommunityDownload(true))
                                .setNeutralButton("Alten Auftrag abbrechen",(d,w)->{
                                    manager.remove(existing);
                                    prefs.edit().remove(KEY_XWIS_DOWNLOAD_ID).apply();
                                    downloadCommunityInstaller(yuri,archive);
                                })
                                .setNegativeButton("Schließen",null).show();
                            return;
                        }
                    }
                }
                prefs.edit().remove(KEY_XWIS_DOWNLOAD_ID).apply();
            }
            String name = (yuri ? "Yuri-XWIS-" : "RA2-XWIS-") + System.currentTimeMillis() + ".exe";
            android.app.DownloadManager.Request request=new android.app.DownloadManager.Request(Uri.parse(source));
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setTitle("Alarmstufe Rot 2 – " + (yuri?"Yuri":"RA2") + " XWIS-Client");
            request.setDescription("Community-Client wird über Android heruntergeladen");
            request.setMimeType("application/octet-stream");
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, android.os.Environment.DIRECTORY_DOWNLOADS, name);
            long id=manager.enqueue(request);
            prefs.edit().putLong(KEY_XWIS_DOWNLOAD_ID,id)
                .putBoolean(KEY_XWIS_DOWNLOAD_YURI,yuri).apply();
            setBusy(false,"Android-Download gestartet ("+(yuri?"Yuri":"RA2")+
                "). Öffne Benachrichtigungen oder tippe auf „Download prüfen“.");
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Download an Android übergeben")
                .setMessage("Der System-Download läuft im Hintergrund. Wenn er fertig ist, kehre zur App zurück und drücke „XWIS-Download prüfen / übernehmen“. Falls das Internet Archive ebenfalls nicht erreichbar ist, kannst du die Archivseite im Browser öffnen und die Datei anschließend importieren.")
                .setPositiveButton("Download prüfen",(d,w)->checkCommunityDownload(true))
                .setNeutralButton("Archiv im Browser",(d,w)->openCommunityDownloadInBrowser(yuri))
                .setNegativeButton("Schließen",null).show();
        } catch (Exception e) {
            reportCommunityDownloadFailure("Android-Download konnte nicht gestartet werden: "+e.getClass().getSimpleName()+" – "+e.getMessage(),yuri);
        }
    }

    private void checkCommunityDownload(boolean interactive) {
        if(prefs==null)return;
        long id=prefs.getLong(KEY_XWIS_DOWNLOAD_ID,-1);
        boolean yuri=prefs.getBoolean(KEY_XWIS_DOWNLOAD_YURI,false);
        if(id<=0) {
            if(interactive)
                new androidx.appcompat.app.AlertDialog.Builder(this)
                  .setTitle("Kein aktiver XWIS-Download")
                  .setMessage("Starte den Archivdownload oder wähle einen bereits heruntergeladenen Installer aus.")
                  .setPositiveButton("RA2-Archiv laden",(d,w)->downloadCommunityInstaller(false,true))
                  .setNeutralButton("Datei auswählen",(d,w)->pickCommunityInstaller(false))
                  .setNegativeButton("Schließen",null).show();
            return;
        }
        try {
            android.app.DownloadManager manager=(android.app.DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            if(manager==null)throw new java.io.IOException("DownloadManager nicht verfügbar");
            try(android.database.Cursor cursor=manager.query(new android.app.DownloadManager.Query().setFilterById(id))){
                if(cursor==null||!cursor.moveToFirst()) {
                    prefs.edit().remove(KEY_XWIS_DOWNLOAD_ID).apply();
                    if(interactive) reportCommunityDownloadFailure("Android hat keinen Eintrag mehr für diesen Download.",yuri);
                    return;
                }
                int state=cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS));
                if(state==android.app.DownloadManager.STATUS_SUCCESSFUL) {
                    Uri downloaded=manager.getUriForDownloadedFile(id);
                    prefs.edit().remove(KEY_XWIS_DOWNLOAD_ID).apply();
                    if(downloaded==null)throw new java.io.IOException("Android hat keine heruntergeladene Datei geliefert");
                    setBusy(false,"XWIS-Paket heruntergeladen. Import und Prüfung starten …");
                    importCommunityInstaller(downloaded,yuri);
                    return;
                }
                if(state==android.app.DownloadManager.STATUS_FAILED) {
                    int error=cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_REASON));
                    prefs.edit().remove(KEY_XWIS_DOWNLOAD_ID).apply();
                    reportCommunityDownloadFailure("Android-Systemdownload fehlgeschlagen (Fehlercode "+error+"). Die Quelle ist eventuell nicht erreichbar.",yuri);
                    return;
                }
                long current=cursor.getLong(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total=cursor.getLong(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                String msg="XWIS-Download "+(state==android.app.DownloadManager.STATUS_PAUSED?"pausiert":"läuft")+
                    ": "+(current/(1024*1024))+" MB"+(total>0?" / "+(total/(1024*1024))+" MB":"");
                if(status!=null)status.setText(msg);
                if(interactive)
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                      .setTitle("XWIS-Downloadstatus")
                      .setMessage(msg+"\nDu kannst die App verlassen und später den Status prüfen.")
                      .setPositiveButton("Archivseite öffnen",(d,w)->openCommunityDownloadInBrowser(yuri))
                      .setNegativeButton("OK",null).show();
            }
        }catch(Exception e){if(interactive)reportCommunityDownloadFailure("Downloadprüfung: "+e.getMessage(),yuri);}
    }

    private void openCommunityDownloadInBrowser(boolean yuri) {
        String source = yuri ? "https://archive.org/details/yuris-revenge-multiplayer"
                             : "https://archive.org/details/red-alert-2-multiplayer";
        try {
            Intent browser=new Intent(Intent.ACTION_VIEW,Uri.parse(source));
            browser.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(browser);
            if(status!=null)status.setText("Archivseite geöffnet. Nach dem Download den EXE-Installer über „Installer auswählen“ importieren.");
        }catch(Exception e){
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Keine Browser-App gefunden")
                .setMessage("Öffne manuell: "+source+"\nNach dem Download die EXE-Datei in der App auswählen.")
                .setPositiveButton("URL kopieren",(d,w)->{
                    android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    if(cb!=null)cb.setPrimaryClip(android.content.ClipData.newPlainText("XWIS-Archiv",source));
                }).setNegativeButton("OK",null).show();
        }
    }

    private void reportCommunityDownloadFailure(String reason, boolean yuri) {
        setBusy(false,reason);
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("XWIS-Download nicht erfolgreich")
            .setMessage(reason+"\n\nDer Original-Spielordner wurde nicht verändert. Du kannst die Archivseite im Browser öffnen oder die heruntergeladene EXE-Datei auswählen.")
            .setPositiveButton("Archivseite öffnen",(d,w)->openCommunityDownloadInBrowser(yuri))
            .setNeutralButton("EXE auswählen",(d,w)->pickCommunityInstaller(yuri))
            .setNegativeButton("Schließen",null).show();
    }

    private void copyCommunityPackage(InputStream input, boolean yuri, long length) throws java.io.IOException {
        if (length>RA2XwisClient.MAX_INSTALLER) throw new java.io.IOException("Installer zu groß");
        File out = new File(communityPackages(),yuri ? RA2XwisClient.YURI_INSTALLER : RA2XwisClient.RA2_INSTALLER);
        File temporary = new File(communityPackages(),out.getName()+".partial");
        long total=0;
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(temporary)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n=input.read(buffer))!=-1) {
                total+=n;
                if(total>RA2XwisClient.MAX_INSTALLER) throw new java.io.IOException("Installer zu groß");
                stream.write(buffer,0,n);
                if (total % (8L*1024*1024) < n) {
                    long copied=total;
                    runOnUiThread(() -> {if (status!=null)
                        status.setText("XWIS-Paket: " + (copied/1024/1024) + " MB geladen");});
                }
            }
            stream.getFD().sync();
            if (!RA2XwisClient.isPeInstaller(temporary))
                throw new java.io.IOException("Keine vollständige Windows-EXE (MZ/Größe).");
            if (!temporary.renameTo(out))
                throw new java.io.IOException("Installer konnte nicht aktiviert werden");
        } finally {temporary.delete();}
    }

    private void startCommunitySetup(boolean yuri) {
        setBusy(false, "XWIS-Installer gespeichert. Installation läuft im Wine-Fenster.");
        // Installer writes ONLY to C:\RA2Mobile\XWISClient; original game is untouched.
        prefs.edit().putString(KEY_XWIS_PENDING,yuri ? "yuri" : "ra2").apply();
        launchRuntimeDos(RA2XwisClient.dosInstaller(yuri), RA2XwisClient.installerArgs());
    }

    private void finishCommunityInstallation(boolean yuri) {
        if (container == null) return;
        setBusy(true, "Prüfe XWIS-Client und ergänze Originalmissionen …");
        ioExecutor.execute(() -> {
            try {
                File client=RA2XwisClient.findClient(communityFolder(),yuri);
                if(client==null) throw new java.io.IOException(
                    "Kein XWIS-" + (yuri?"Yuri":"RA2") + "-Spielclient gefunden. Installer/Startprotokoll prüfen.");
                int added=RA2XwisClient.addOwnedResources(gameDir(),communityFolder());
                runOnUiThread(() -> {
                    setBusy(false,"XWIS-Client bereit: "+client.getName()+" • "+added+
                        " Originalarchive ergänzt. Jetzt XWIS-Client starten.");
                    refreshGameState();
                });
            } catch (Exception e) {
                runOnUiThread(() -> setBusy(false,"XWIS-Einrichtung noch nicht abgeschlossen: "+e.getMessage()));
            }
        });
    }

    private void launchCommunityGame(boolean yuri) {
        if (container==null) {message("Laufzeit nicht bereit");return;}
        File client=RA2XwisClient.findClient(communityFolder(),yuri);
        if(client==null) {
            message("XWIS-Client noch nicht installiert. Bitte XWIS-Paket herunterladen oder auswählen.");
            return;
        }
        setBusy(true,"Prüfe XWIS-Spielordner …");
        ioExecutor.execute(() -> {
            try {
                int added=RA2XwisClient.addOwnedResources(gameDir(),communityFolder());
                runOnUiThread(() -> {
                    setBusy(false,"XWIS bereit • "+added+" Originalarchive ergänzt");
                    startCommunityGame(yuri,client);
                });
            } catch (Exception e) {
                runOnUiThread(() -> setBusy(false,"XWIS-Spielordner nicht bereit: "+e.getMessage()));
            }
        });
    }

    private void startCommunityGame(boolean yuri, File client) {
        targetNormalServicesBeforeLaunch();
        activateMedia(yuri);
        writeWestwoodRegistry(yuri);
        File registryFile=new File(container.getRootDir(), ".wine/system.reg");
        try (WineRegistryEditor registry=new WineRegistryEditor(registryFile)) {
            String product=yuri ? "Yuri's Revenge" : "Red Alert 2";
            String root="Software\\Westwood\\"+product;
            String path=RA2XwisClient.dosClientPath(client);
            for(String key:container.isWoW64Mode() ?
                new String[]{root, "Software\\Wow6432Node\\Westwood\\"+product} : new String[]{root}) {
                registry.setStringValue(key,"InstallPath",path);
                registry.setStringValue(key,"FolderPath","C:\\RA2Mobile\\XWISClient");
            }
        } catch(Exception e) {message("XWIS-Registry konnte nicht gesetzt werden: "+e.getMessage());return;}
        prefs.edit().putString(KEY_LAST_GAME_LAUNCH,yuri?"XWIS Yuri":"XWIS RA2")
            .putLong(KEY_LAST_GAME_LAUNCH_AT,System.currentTimeMillis())
            .remove(KEY_LAST_EXIT_CODE).remove(KEY_LAST_SAW_CHILD).apply();
        writeLaunchSeed(client,yuri,false);
        Intent intent=new Intent(this,XServerDisplayActivity.class);
        intent.putExtra("container_id",container.id);
        intent.putExtra("exec_path",client.getAbsolutePath());
        intent.putExtra("ra2_mode",true);
        intent.putExtra("ra2_game_launch",true);
        intent.putExtra("ra2_community_client",true);
        intent.putExtra("ra2_language",isGerman() ? "de" : "en");
        startActivity(intent);
    }

    private void launchGame(boolean yuri) { launchGame(yuri, false); }

    private void launchGame(boolean yuri, boolean originalLauncherProbe) {
        if (container == null) return;
        if (installationBusy || !isInstalled(yuri)) {
            message(isGerman() ? "Spieldateien fehlen. Bitte zuerst Einrichtung abschließen." : "Game files are missing. Complete setup first.");
            return;
        }

        File exe = findIgnoreCase(gameDir(), originalLauncherProbe ? "ra2.exe" : (yuri ? "gamemd.exe" : "game.exe"), 2);
        if (exe == null && !originalLauncherProbe) {
            exe = findIgnoreCase(gameDir(), yuri ? "ra2md.exe" : "ra2.exe", 2);
        }
        if (exe == null || !exe.isFile()) {
            scanInstalledGames(false);
            message(isGerman() ? "Installation wird geprüft. Bitte danach erneut starten." : "Checking the installation. Try again in a moment.");
            return;
        }

        prefs.edit()
            .putString(yuri ? KEY_YURI_EXE : KEY_RA2_EXE, exe.getAbsolutePath())
            .putString(KEY_LAST_GAME_LAUNCH, originalLauncherProbe ? "RA2.EXE Originalstart-Test" : (yuri ? "Yuri’s Rache" : "Alarmstufe Rot 2"))
            .putLong(KEY_LAST_GAME_LAUNCH_AT, System.currentTimeMillis())
            .remove("last_launcher_exit_code")
            .remove(KEY_LAST_EXIT_CODE)
            .remove(KEY_LAST_SAW_CHILD)
            .remove(KEY_LAST_DIAG_CLUE)
            .apply();

        targetNormalServicesBeforeLaunch();
        writeLaunchSeed(exe, yuri, originalLauncherProbe);
        activateMedia(yuri);
        writeWestwoodRegistry(yuri);
        tuneCncDdraw();
        applyWineLocale();

        Intent intent = new Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_path", exe.getAbsolutePath());
        intent.putExtra("ra2_mode", true);
        intent.putExtra("ra2_game_launch", true);
        intent.putExtra("ra2_launcher_probe", originalLauncherProbe);
        intent.putExtra("ra2_language", isGerman() ? "de" : "en");
        startActivity(intent);
    }

    private void targetNormalServicesBeforeLaunch() {
        if (container == null) return;
        if (container.getStartupSelection() != Container.STARTUP_SELECTION_NORMAL) {
            container.setStartupSelection(Container.STARTUP_SELECTION_NORMAL);
            container.saveData();
        }
        WineUtils.changeServicesStatus(container, Container.STARTUP_SELECTION_NORMAL);
        repairRa2Services(container);
    }

    private void writeLaunchSeed(File exe, boolean yuri, boolean originalLauncherProbe) {
        if (container == null) return;
        File log = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/last-start.log");
        File parent = log.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();

        File secdrv32 = new File(container.getRootDir(), ".wine/drive_c/windows/system32/drivers/secdrv.sys");
        File secdrv64 = new File(container.getRootDir(), ".wine/drive_c/windows/syswow64/drivers/secdrv.sys");
        File globalDdraw = new File(container.getRootDir(), ".wine/drive_c/ProgramData/cnc-ddraw/ddraw.ini");

        StringBuilder seed = new StringBuilder();
        seed.append("stage=launch-requested\n");
        seed.append("launchMode=").append(originalLauncherProbe ? "original-launcher-probe" : "direct-engine").append("\n");
        seed.append("title=").append(yuri ? "Yuri's Revenge" : "Red Alert 2").append("\n");
        seed.append("exe=").append(exe.getAbsolutePath()).append("\n");
        seed.append("exeSize=").append(exe.length()).append("\n");
        seed.append("secdrvSystem32=").append(secdrv32.isFile()).append("\n");
        seed.append("secdrvSyswow64=").append(secdrv64.isFile()).append("\n");
        seed.append("cncDdrawConfig=").append(globalDdraw.isFile()).append("\n");
        seed.append("timestamp=").append(System.currentTimeMillis()).append("\n");
        FileUtils.writeString(log, seed.toString());
    }

    private void scanInstalledGames(boolean report) {
        if (container == null) return;
        ioExecutor.execute(() -> {
            File driveC = new File(container.getRootDir(), ".wine/drive_c");
            File ra2Found = findIgnoreCase(driveC, "game.exe", 10);
            if (ra2Found == null) ra2Found = findIgnoreCase(driveC, "ra2.exe", 10);
            File yuriFound = findIgnoreCase(driveC, "gamemd.exe", 10);
            if (yuriFound == null) yuriFound = findIgnoreCase(driveC, "ra2md.exe", 10);
            final File ra2 = ra2Found;
            final File yuri = yuriFound;

            SharedPreferences.Editor edit = prefs.edit();
            if (ra2 != null) edit.putString(KEY_RA2_EXE, ra2.getAbsolutePath());
            if (yuri != null) edit.putString(KEY_YURI_EXE, yuri.getAbsolutePath());
            edit.apply();

            runOnUiThread(() -> {
                refreshGameState();
                if (report) {
                    boolean de = isGerman();
                    if (isInstalled(false) || isInstalled(true)) {
                        setBusy(false, de ? "Installation erkannt. Spielstart ist bereit." : "Installation detected. Game launch is ready.");
                    }
                    else {
                        setBusy(false, de
                            ? "Die Direktinstallation konnte noch keine Spiel-EXE bestätigen. Starte die Einrichtung erneut; die Statuszeile zeigt dann die fehlende Datei."
                            : "The direct installation could not confirm a game executable yet. Run setup again; the status line will show the missing file.");
                    }
                }
            });
        });
    }

    private void refreshGameState() {
        if (prefs == null || playRa2Button == null) return;
        boolean ra2 = !installationBusy && isInstalled(false);
        boolean yuri = !installationBusy && isInstalled(true);
        playRa2Button.setEnabled(ra2);
        playYuriButton.setEnabled(yuri);
        playRa2Button.setAlpha(ra2 ? 1.0f : 0.45f);
        playYuriButton.setAlpha(yuri ? 1.0f : 0.45f);
        installYuriButton.setEnabled(!installationBusy && container != null && ra2 && savedUri(KEY_YURI) != null);
        installRa2Button.setEnabled(!installationBusy && container != null && savedUri(KEY_ALLIED) != null && savedUri(KEY_SOVIET) != null);
    }

    private boolean isInstalled(boolean yuri) {
        if (prefs == null || container == null) return false;
        String[] required = yuri
            ? new String[]{"gamemd.exe", "ra2md.mix", "langmd.mix", "mapsmd03.mix", "movmd03.mix", "multimd.mix", "thememd.mix", "binkw32.dll", "blowfish.dll"}
            : new String[]{"game.exe", "ra2.mix", "language.mix", "maps01.mix", "maps02.mix", "movies01.mix", "movies02.mix", "multi.mix", "theme.mix", "binkw32.dll", "blowfish.dll"};
        for (String name : required) {
            File file = findChildIgnoreCase(gameDir(), name);
            if (file == null || !file.isFile() || file.length() == 0) return false;
        }
        return !yuri || isInstalled(false);
    }

    private void preparePhysicalDriveX() {
        if (container == null) return;
        File driveX = driveX();
        FileUtils.delete(driveX);
        driveX.mkdirs();
        FileUtils.chmod(driveX, 0771);
    }

    private File driveX() {
        return new File(container.getRootDir(), ".wine/drive_x");
    }

    private File mediaDir(boolean yuri) {
        return new File(container.getRootDir(), yuri ? ".wine/drive_yuri_media" : ".wine/drive_ra2_media");
    }

    private void storeMedia(boolean yuri) {
        File driveX = driveX();
        File media = mediaDir(yuri);
        File[] entries = driveX.listFiles();
        if (FileUtils.isSymlink(driveX) || entries == null || entries.length == 0) return;
        FileUtils.delete(media);
        if (driveX.isDirectory() && !FileUtils.isSymlink(driveX)) {
            if (!driveX.renameTo(media)) {
                setBusy(false, isGerman() ? "Warnung: CD-Daten konnten nicht archiviert werden." : "Warning: disc data could not be stored.");
            }
        }
        driveX.mkdirs();
        FileUtils.chmod(driveX, 0771);
    }

    private void activateMedia(boolean yuri) {
        File media = mediaDir(yuri);
        if (!media.isDirectory()) return;
        // The existing game files remain untouched. Repair only the missing
        // ISO-9660 volume label in installations created before this version.
        File marker = new File(media, ".windows-label");
        String existing = marker.isFile() ? FileUtils.readString(marker) : "";
        if (existing == null || existing.trim().isEmpty()) {
            Uri source = savedUri(yuri ? KEY_YURI : KEY_SOVIET);
            if (source != null) {
                try (InputStream stream = getContentResolver().openInputStream(source)) {
                    String original = Iso9660Extractor.readVolumeId(stream);
                    if (!original.isEmpty())
                        FileUtils.writeString(marker, original + "\n");
                } catch (Exception e) {
                    android.util.Log.w("RA2-CD", "Original volume name unavailable", e);
                }
            }
        }
        File driveX = driveX();
        FileUtils.delete(driveX);
        FileUtils.symlink(media.getName(), driveX.getAbsolutePath());
    }

    private File stagingIso() {
        return new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/staging.iso");
    }

    private Uri savedUri(String key) {
        String value = prefs.getString(key, "");
        return value.isEmpty() ? null : Uri.parse(value);
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getLong(0);
        }
        catch (Exception ignored) {}
        return -1;
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        }
        catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        return last == null ? "ISO" : last;
    }

    private File findIgnoreCase(File root, String filename, int depth) {
        if (root == null || depth < 0 || !root.exists()) return null;
        if (root.isFile()) return root.getName().equalsIgnoreCase(filename) ? root : null;
        File[] files = root.listFiles();
        if (files == null) return null;
        for (File file : files) {
            if (file.isFile() && file.getName().equalsIgnoreCase(filename)) return file;
        }
        if (depth == 0) return null;
        for (File file : files) {
            if (!file.isDirectory()) continue;
            String lower = file.getName().toLowerCase(Locale.ENGLISH);
            if (lower.equals("windows") && depth < 10) continue;
            File found = findIgnoreCase(file, filename, depth - 1);
            if (found != null) return found;
        }
        return null;
    }

    private String toXDosPath(File file) {
        String root = driveX().getAbsolutePath();
        String path = file.getAbsolutePath();
        String relative = path.startsWith(root) ? path.substring(root.length()) : "/" + file.getName();
        return "X:" + relative.replace("/", "\\");
    }

    private void setBusy(boolean busy, String text) {
        installationBusy = busy;
        runOnUiThread(() -> {
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            if (!busy) progress.setProgress(0);
            status.setText(text == null ? "" : text);
            refreshGameState();
            installRa2Button.setEnabled(!busy && container != null && savedUri(KEY_ALLIED) != null && savedUri(KEY_SOVIET) != null);
            installYuriButton.setEnabled(!busy && container != null && isInstalled(false) && savedUri(KEY_YURI) != null);
        });
    }

    private void message(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private TextView label(int sp, int color) {
        TextView view = new TextView(this);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private Button button(String text) {
        Button view = new Button(this);
        view.setText(text);
        view.setAllCaps(false);
        view.setTextSize(15);
        view.setTextColor(Color.WHITE);
        view.setBackground(buttonBackground(false));
        return view;
    }

    private Button accentButton(String text) {
        Button view = button(text);
        view.setBackground(buttonBackground(true));
        return view;
    }

    private GradientDrawable buttonBackground(boolean accent) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(accent ? Color.rgb(130, 31, 31) : Color.rgb(39, 45, 48));
        bg.setStroke(dp(1), accent ? Color.rgb(215, 101, 83) : Color.rgb(91, 105, 110));
        return bg;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private LinearLayout.LayoutParams matchWrap(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = top;
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
