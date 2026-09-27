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
    private Button languageDe;
    private Button languageEn;
    private Button alliedButton;
    private Button sovietButton;
    private Button yuriIsoButton;
    private Button installRa2Button;
    private Button installYuriButton;
    private Button playRa2Button;
    private Button playYuriButton;

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
        if (prefs.getBoolean(KEY_RUNTIME_OUTSTANDING, false) && !runtimeStarting) {
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
        playRa2Button.setOnClickListener(v -> launchGame(false));
        playYuriButton.setOnClickListener(v -> launchGame(true));

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

        installRa2Button.setText(de ? "Alarmstufe Rot 2 einrichten" : "Set up Red Alert 2");
        installYuriButton.setText(de ? "Yuri’s Rache einrichten" : "Set up Yuri’s Revenge");
        playRa2Button.setText(de ? "ALARMSTUFE ROT 2 STARTEN" : "START RED ALERT 2");
        playYuriButton.setText(de ? "YURI’S RACHE STARTEN" : "START YURI’S REVENGE");

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
        Uri allied = savedUri(KEY_ALLIED);
        Uri soviet = savedUri(KEY_SOVIET);
        if (allied == null || soviet == null) {
            message(isGerman() ? "Bitte zuerst Alliierte- und Sowjet-ISO auswählen." : "Select the Allied and Soviet ISOs first.");
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle(isGerman() ? "Alarmstufe Rot 2 einrichten" : "Set up Red Alert 2")
            .setMessage(isGerman()
                ? "Beide Original-CDs werden direkt ausgewertet. Die Spieldateien werden ohne das alte Westwood-Setup installiert."
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
        if (container == null) {
            ensureRuntimeAndContainer();
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
                setBusy(false, isGerman()
                    ? "RA2.MIX und LANGUAGE.MIX werden aus " + cabName + " entpackt…"
                    : "Extracting RA2.MIX and LANGUAGE.MIX from " + cabName + "…");
                launchRuntimeDos(
                    "Z:\\opt\\apps\\7-Zip\\7zG.exe",
                    "x \"C:\\RA2Mobile\\RA2CAB\\" + cabName + "\" -o\"C:\\Westwood\\RA2\" -y -aoa"
                );
            }
            catch (Exception e) {
                stopPipeline((isGerman() ? "Dateifehler: " : "File error: ") + e.getMessage());
            }
            return;
        }

        if (STAGE_RA2_CAB.equals(stage)) {
            File game = findIgnoreCase(gameDir(), "game.exe", 2);
            File ra2Mix = findIgnoreCase(gameDir(), "ra2.mix", 2);
            File languageMix = findIgnoreCase(gameDir(), "language.mix", 2);

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
                        ? "RA2-Dateien vollständig. Die Original-CD nutzt SafeDisc; richte jetzt Yuri’s Rache ein, damit ein vorhandener neuerer secdrv.sys-Treiber aus deinem Yuri-Medium übernommen werden kann."
                        : "RA2 files are complete. The original CD uses SafeDisc; set up Yuri’s Revenge next so a newer secdrv.sys driver from your Yuri media can be installed if present.");
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
            refreshGameState();
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
                setBusy(false, isGerman()
                    ? "RA2MD.MIX und LANGMD.MIX werden aus den Yuri-CAB-Dateien entpackt…"
                    : "Extracting RA2MD.MIX and LANGMD.MIX from the Yuri CAB files…");
                launchRuntimeDos(
                    "Z:\\opt\\apps\\7-Zip\\7zG.exe",
                    "x \"C:\\RA2Mobile\\YURICAB\\" + cabName + "\" -o\"C:\\Westwood\\RA2\" -y -aoa"
                );
            }
            catch (Exception e) {
                stopPipeline((isGerman() ? "Dateifehler: " : "File error: ") + e.getMessage());
            }
            return;
        }

        if (STAGE_YURI_CAB.equals(stage)) {
            File game = findIgnoreCase(gameDir(), "gamemd.exe", 2);
            File ra2Mix = findIgnoreCase(gameDir(), "ra2md.mix", 3);
            File languageMix = findIgnoreCase(gameDir(), "langmd.mix", 3);

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
            refreshGameState();
        }
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

    private void writeWestwoodRegistry(boolean yuri) {
        File systemReg = new File(container.getRootDir(), ".wine/system.reg");
        try (WineRegistryEditor registry = new WineRegistryEditor(systemReg)) {
            String root = yuri ? "Software\\Westwood\\Yuri's Revenge" : "Software\\Westwood\\Red Alert 2";
            registry.setStringValue(root, "Name", yuri ? "Yuri's Revenge" : "Red Alert 2");
            registry.setStringValue(root, "InstallPath", yuri
                ? "C:\\Westwood\\RA2\\GAMEMD.EXE"
                : "C:\\Westwood\\RA2\\GAME.EXE");
            registry.setStringValue(root, "FolderPath", "C:\\Westwood\\RA2");
            registry.setStringValue(root, "Serial", "0");
            registry.setStringValue(root, "Language", isGerman() ? "German" : "English");
            registry.setDwordValue(root, "SKU", yuri ? 0x00000901 : 0x00000801);
            registry.setDwordValue(root, "Version", yuri ? 0x00010001 : 0x00010006);
            File wolapi = findIgnoreCase(gameDir(), "wolapi.dll", 2);
            if (wolapi != null) {
                registry.setStringValue("Software\\Westwood\\WOLAPI", "InstallPath", "C:\\Westwood\\RA2\\WOLAPI.DLL");
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
        String child = prefs.contains(KEY_LAST_SAW_CHILD)
            ? String.valueOf(prefs.getBoolean(KEY_LAST_SAW_CHILD, false))
            : diagnosticValue(diag, "sawGameChild=");
        String clue = prefs.getString(KEY_LAST_DIAG_CLUE, "");
        if (clue.isEmpty()) clue = diagnosticClue(diag);
        // Keep the last detailed diagnostics for the Diagnose-Center.

        StringBuilder message = new StringBuilder();
        if (isGerman()) {
            message.append(last).append(" wurde nach ").append(seconds).append(" s beendet.");
            if (!exitCode.isEmpty()) message.append(" Exit-Code ").append(exitCode).append(".");
            if ("false".equalsIgnoreCase(child)) message.append(" Kein laufender GAME.EXE-Unterprozess wurde erkannt.");
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
        intent.putExtra("ra2_mode", true);
        intent.putExtra("ra2_language", isGerman() ? "de" : "en");
        startActivity(intent);
    }

    private void launchGame(boolean yuri) {
        if (container == null) return;

        File exe = findIgnoreCase(gameDir(), yuri ? "gamemd.exe" : "game.exe", 2);
        if (exe == null) {
            exe = findIgnoreCase(gameDir(), yuri ? "ra2md.exe" : "ra2.exe", 2);
        }
        if (exe == null || !exe.isFile()) {
            scanInstalledGames(false);
            message(isGerman() ? "Installation wird geprüft. Bitte danach erneut starten." : "Checking the installation. Try again in a moment.");
            return;
        }

        prefs.edit()
            .putString(yuri ? KEY_YURI_EXE : KEY_RA2_EXE, exe.getAbsolutePath())
            .putString(KEY_LAST_GAME_LAUNCH, yuri ? "Yuri’s Rache" : "Alarmstufe Rot 2")
            .putLong(KEY_LAST_GAME_LAUNCH_AT, System.currentTimeMillis())
            .remove(KEY_LAST_EXIT_CODE)
            .remove(KEY_LAST_SAW_CHILD)
            .remove(KEY_LAST_DIAG_CLUE)
            .apply();

        targetNormalServicesBeforeLaunch();
        writeLaunchSeed(exe, yuri);
        activateMedia(yuri);
        writeWestwoodRegistry(yuri);
        tuneCncDdraw();
        applyWineLocale();

        Intent intent = new Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_path", exe.getAbsolutePath());
        intent.putExtra("ra2_mode", true);
        intent.putExtra("ra2_game_launch", true);
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

    private void writeLaunchSeed(File exe, boolean yuri) {
        if (container == null) return;
        File log = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/last-start.log");
        File parent = log.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();

        File secdrv32 = new File(container.getRootDir(), ".wine/drive_c/windows/system32/drivers/secdrv.sys");
        File secdrv64 = new File(container.getRootDir(), ".wine/drive_c/windows/syswow64/drivers/secdrv.sys");
        File globalDdraw = new File(container.getRootDir(), ".wine/drive_c/ProgramData/cnc-ddraw/ddraw.ini");

        StringBuilder seed = new StringBuilder();
        seed.append("stage=launch-requested\n");
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
                    if (ra2 != null || yuri != null) {
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
        boolean ra2 = isInstalled(false);
        boolean yuri = isInstalled(true);
        playRa2Button.setEnabled(ra2);
        playYuriButton.setEnabled(yuri);
        playRa2Button.setAlpha(ra2 ? 1.0f : 0.45f);
        playYuriButton.setAlpha(yuri ? 1.0f : 0.45f);
        installYuriButton.setEnabled(container != null && ra2 && savedUri(KEY_YURI) != null);
        installRa2Button.setEnabled(container != null && savedUri(KEY_ALLIED) != null && savedUri(KEY_SOVIET) != null);
    }

    private boolean isInstalled(boolean yuri) {
        if (prefs == null) return false;
        String path = prefs.getString(yuri ? KEY_YURI_EXE : KEY_RA2_EXE, "");
        return !path.isEmpty() && new File(path).isFile();
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
        File driveX = driveX();
        FileUtils.delete(driveX);
        FileUtils.symlink(media, driveX);
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
        runOnUiThread(() -> {
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            if (!busy) progress.setProgress(0);
            status.setText(text == null ? "" : text);
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
