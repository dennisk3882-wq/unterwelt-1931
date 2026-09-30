package com.winlator;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.FileUtils;
import com.winlator.core.WineRegistryEditor;
import com.winlator.core.WineUtils;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RA2DiagnosticsActivity extends AppCompatActivity {
    private static final String PREFS = "ra2_mobile";
    private static final String KEY_CONTAINER = "container_id";
    private static final String KEY_ALLIED = "iso_allied";
    private static final String KEY_SOVIET = "iso_soviet";
    private static final String KEY_YURI = "iso_yuri";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Result> results = new ArrayList<>();
    private final List<String> suspiciousLogLines = new ArrayList<>();

    private SharedPreferences prefs;
    private Container container;
    private LinearLayout resultBox;
    private TextView headline;
    private TextView summary;
    private ProgressBar progress;
    private Button copyButton;
    private Button rescanButton;
    private Button repairButton;
    private String reportText = "";
    private String lastLaunchText = "";
    private String primaryDiagnosis = "";

    private enum Level { PASS, WARN, FAIL, INFO }

    private static final class Result {
        final Level level;
        final String group;
        final String title;
        final String detail;
        final String action;

        Result(Level level, String group, String title, String detail, String action) {
            this.level = level;
            this.group = group;
            this.title = title;
            this.detail = detail == null ? "" : detail;
            this.action = action == null ? "" : action;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildUi());
        runDiagnostics();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(16), dp(18), dp(18));
        page.setBackgroundColor(Color.rgb(8, 12, 14));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        Button back = button("‹ Zurück");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(100), dp(46)));

        headline = text(25, Color.rgb(238, 220, 170), true);
        headline.setText("RA2 / Yuri Diagnose-Center v0.25");
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        hp.leftMargin = dp(14);
        top.addView(headline, hp);

        page.addView(top);

        summary = text(14, Color.LTGRAY, false);
        summary.setPadding(dp(3), dp(10), dp(3), dp(8));
        summary.setText("Automatische Tiefenprüfung wird gestartet …");
        page.addView(summary);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        page.addView(progress, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(5)));

        ScrollView scroll = new ScrollView(this);
        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        resultBox.setPadding(0, dp(12), 0, dp(12));
        scroll.addView(resultBox);
        page.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);

        repairButton = button("Sicher reparieren");
        rescanButton = button("Neu prüfen");
        copyButton = button("Protokoll kopieren");
        repairButton.setEnabled(false);
        rescanButton.setEnabled(false);
        copyButton.setEnabled(false);

        repairButton.setOnClickListener(v -> performSafeRepair());
        rescanButton.setOnClickListener(v -> runDiagnostics());
        copyButton.setOnClickListener(v -> copyReport());

        actions.addView(repairButton, new LinearLayout.LayoutParams(dp(170), dp(50)));
        LinearLayout.LayoutParams rpFix = new LinearLayout.LayoutParams(dp(145), dp(50));
        rpFix.leftMargin = dp(8);
        actions.addView(rescanButton, rpFix);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(185), dp(50));
        cp.leftMargin = dp(8);
        actions.addView(copyButton, cp);
        page.addView(actions);
        Button export = button("Diagnose-ZIP speichern");
        export.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(android.content.Intent.EXTRA_TITLE, "RA2-Diagnose-" + System.currentTimeMillis() + ".zip");
            startActivityForResult(intent, 2401);
        });
        page.addView(export, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));

        return page;
    }

    private void runDiagnostics() {
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        repairButton.setEnabled(false);
        rescanButton.setEnabled(false);
        copyButton.setEnabled(false);
        resultBox.removeAllViews();
        results.clear();
        suspiciousLogLines.clear();
        lastLaunchText = "";
        primaryDiagnosis = "";
        summary.setText("Prüfe Laufzeit, Medien, Installation, Registry, Grafik und Startprotokolle …");

        worker.execute(() -> {
            try {
                checkRuntime();
                checkIsoSlot("Originalmedien", "Alliierte ISO", KEY_ALLIED, true);
                checkIsoSlot("Originalmedien", "Sowjet ISO", KEY_SOVIET, true);
                checkIsoSlot("Originalmedien", "Yuri ISO", KEY_YURI, false);
                checkContainerStorage();
                checkWineDriveMappings();
                checkArchiveStaging();
                checkRa2Files();
                checkYuriFiles();
                checkRegistry();
                checkWineServices();
                checkRpcPreflight();
                checkCncDdraw();
                checkSafeDisc();
                checkExecutableFormat(false);
                checkExecutableFormat(true);
                checkPeDependencies(false);
                checkPeDependencies(true);
                checkVirtualMedia();
                checkLastLaunch();
                checkLaunchTimeline();
                createRootCauseAnalysis();
                createAssessment();
            }
            catch (Throwable t) {
                add(Level.FAIL, "Diagnose", "Diagnose selbst ist abgebrochen",
                    t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()),
                    "Screenshot dieser Meldung schicken.");
            }

            runOnUiThread(this::renderResults);
        });
    }

    private void checkRuntime() {
        RootFS root = RootFS.find(this);
        if (!root.isValid()) {
            add(Level.FAIL, "Laufzeit", "Wine/RootFS fehlt oder ist ungültig",
                root.getRootDir().getAbsolutePath(),
                "App frisch installieren und die Laufzeit erneut initialisieren.");
        }
        else if (root.getVersion() < RootFSInstaller.LATEST_VERSION) {
            add(Level.WARN, "Laufzeit", "RootFS ist älter als erwartet",
                "Installiert: " + root.getVersion() + " / erwartet: " + RootFSInstaller.LATEST_VERSION,
                "Laufzeit neu aufbauen.");
        }
        else {
            add(Level.PASS, "Laufzeit", "Wine/RootFS gültig",
                "Version " + root.getVersion(), "");
        }

        int containerId = prefs.getInt(KEY_CONTAINER, 0);
        ContainerManager manager = new ContainerManager(this);
        container = containerId > 0 ? manager.getContainerById(containerId) : null;
        if (container == null) {
            add(Level.FAIL, "Laufzeit", "RA2-Container fehlt",
                "Container-ID: " + containerId,
                "Alarmstufe Rot 2 erneut einrichten.");
        }
        else {
            add(Level.PASS, "Laufzeit", "RA2-Container vorhanden",
                "ID " + container.id + " • " + container.getName(), "");
        }
    }

    private void checkIsoSlot(String group, String label, String key, boolean required) {
        String value = prefs.getString(key, "");
        if (value.isEmpty()) {
            add(required ? Level.FAIL : Level.WARN, group, label + " nicht ausgewählt",
                "", required ? "ISO im Startbildschirm auswählen." : "Für Yuri die ISO auswählen.");
            return;
        }

        Uri uri = Uri.parse(value);
        long size = querySize(uri);
        boolean readable = false;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) {
                byte[] test = new byte[32];
                readable = in.read(test) >= 0;
            }
        }
        catch (Exception ignored) {}

        if (!readable) {
            add(Level.FAIL, group, label + " nicht lesbar",
                uri.toString(), "ISO erneut auswählen.");
            return;
        }

        String name = displayName(uri);
        if (size > 0 && size < 50L * 1024L * 1024L) {
            add(Level.WARN, group, label + " ungewöhnlich klein",
                name + " • " + humanSize(size),
                "Prüfen, ob dies wirklich die vollständige Original-CD ist.");
        }
        else {
            add(Level.PASS, group, label + " lesbar",
                name + (size > 0 ? " • " + humanSize(size) : ""), "");
        }
    }

    private void checkContainerStorage() {
        if (container == null) return;
        File root = container.getRootDir();
        File driveC = new File(root, ".wine/drive_c");
        if (!driveC.isDirectory()) {
            add(Level.FAIL, "Speicher", "Wine-Laufwerk C: fehlt",
                driveC.getAbsolutePath(), "Container neu erstellen.");
            return;
        }

        long free = driveC.getFreeSpace();
        Level level = free < 750L * 1024L * 1024L ? Level.FAIL :
            free < 2L * 1024L * 1024L * 1024L ? Level.WARN : Level.PASS;
        add(level, "Speicher", "Freier App-Speicher",
            humanSize(free),
            level == Level.PASS ? "" : "Mindestens 2 GB freien Speicher schaffen.");

        File game = gameDir();
        if (game.isDirectory()) {
            long[] stats = directoryStats(game, 7);
            add(Level.INFO, "Speicher", "Spielordner",
                stats[0] + " Dateien • " + humanSize(stats[1]) + " • " + game.getAbsolutePath(), "");
        }
        else {
            add(Level.FAIL, "Speicher", "Spielordner fehlt",
                game.getAbsolutePath(), "Alarmstufe Rot 2 erneut einrichten.");
        }
    }

    private void checkArchiveStaging() {
        if (container == null) return;
        File base = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile");
        File ra2Cab = new File(base, "RA2CAB");
        File yuriCab = new File(base, "YURICAB");

        reportArchiveFolder("RA2 CAB/HDR", ra2Cab, true);
        reportArchiveFolder("Yuri CAB/HDR", yuriCab, false);
    }

    private void reportArchiveFolder(String label, File dir, boolean ra2) {
        if (!dir.isDirectory()) {
            boolean installed = ra2
                ? findIgnoreCase(gameDir(), "ra2.mix", 2) != null && findIgnoreCase(gameDir(), "language.mix", 2) != null
                : findIgnoreCase(gameDir(), "gamemd.exe", 2) != null;
            add(installed ? Level.INFO : Level.WARN, "Archive",
                installed ? label + "-Arbeitsordner wurde nach erfolgreicher Einrichtung bereinigt" : label + "-Ordner fehlt",
                dir.getAbsolutePath(),
                installed ? "" : "Der Ordner entsteht während der Einrichtung.");
            return;
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            add(Level.WARN, "Archive", label + "-Ordner ist leer",
                dir.getAbsolutePath(),
                "Einrichtung erneut starten.");
            return;
        }

        int count = 0;
        long bytes = 0;
        StringBuilder names = new StringBuilder();
        File preferred = null;
        for (File file : files) {
            if (!file.isFile()) continue;
            String upper = file.getName().toUpperCase(Locale.ENGLISH);
            if (!upper.endsWith(".CAB") && !upper.endsWith(".HDR")) continue;
            count++;
            bytes += file.length();
            if (names.length() > 0) names.append(", ");
            names.append(file.getName()).append(" (").append(humanSize(file.length())).append(")");
            if (ra2 && upper.equals("GAME1.CAB")) preferred = file;
        }

        if (count == 0) {
            add(Level.FAIL, "Archive", label + " nicht gefunden",
                "Im Ordner liegen keine CAB/HDR-Dateien.",
                "Originalmedium erneut einrichten.");
            return;
        }

        add(Level.PASS, "Archive", label + " erkannt",
            count + " Datei(en) • " + humanSize(bytes) + " • " + names, "");

        if (ra2) {
            add(preferred != null ? Level.PASS : Level.WARN,
                "Archive",
                preferred != null ? "RA2 Game1.CAB erkannt" : "Game1.CAB nicht exakt benannt",
                preferred != null ? preferred.getName() : names.toString(),
                preferred != null ? "" : "v0.9 sucht jetzt unabhängig von Groß-/Kleinschreibung und nutzt notfalls das größte CAB.");
        }
    }

    private void checkRa2Files() {
        if (container == null) return;
        File dir = gameDir();

        String[] critical = {
            "game.exe", "ra2.exe", "ra2.mix", "language.mix",
            "binkw32.dll", "blowfish.dll",
            "maps01.mix", "maps02.mix", "movies01.mix", "movies02.mix",
            "multi.mix", "theme.mix"
        };
        String[] support = {
            "blowfish.tlb", "drvmgt.dll", "mph.exe", "patchget.dat",
            "patchw32.dll", "ra2.lcf", "ra2.tlb"
        };

        int missingCritical = checkFileSet("RA2 Dateien", dir, critical, true);
        int missingSupport = checkFileSet("RA2 Dateien", dir, support, false);

        if (missingCritical == 0) {
            add(Level.PASS, "RA2 Dateien", "RA2-Kernbestand vollständig",
                critical.length + " Kernbestandteile vorhanden.", "");
        }
        if (missingSupport > 0) {
            add(Level.WARN, "RA2 Dateien", "Kompatibilitätsbestand nicht vollständig",
                missingSupport + " unterstützende Originaldatei(en) fehlen.",
                "RA2 erneut einrichten.");
        }

        StringBuilder onlineMissing = new StringBuilder();
        for (String name : new String[]{"woldatA.key", "nl.cfg", "wolapi.dll", "wolapi.war"}) {
            if (findIgnoreCase(dir, name, 2) == null) {
                if (onlineMissing.length() > 0) onlineMissing.append(", ");
                onlineMissing.append(name);
            }
        }
        if (onlineMissing.length() > 0) {
            add(Level.INFO, "RA2 Dateien", "Alte Westwood-Online-Dateien fehlen",
                onlineMissing.toString(),
                "Für Kampagne/Skirmish kein primärer Startblocker; deshalb nur Information.");
        }

        reportOptionalDirectory(new File(dir, "rmcache"), "RMCACHE");
        reportOptionalDirectory(new File(dir, "taunts"), "TAUNTS");
    }

    private void checkYuriFiles() {
        if (container == null) return;
        File dir = gameDir();
        boolean anyYuri = findIgnoreCase(dir, "gamemd.exe", 3) != null ||
            findIgnoreCase(dir, "ra2md.mix", 3) != null;

        if (!anyYuri) {
            add(Level.WARN, "Yuri Dateien", "Yuri noch nicht vollständig installiert",
                "Keine GAMEMD.EXE/RA2MD.MIX-Installation erkannt.",
                "Nach funktionierendem RA2 „Yuri’s Rache einrichten“ ausführen.");
            return;
        }

        String[] critical = {
            "gamemd.exe", "ra2md.exe", "ra2md.mix", "langmd.mix",
            "mapsmd03.mix", "movmd03.mix", "multimd.mix", "thememd.mix"
        };
        int missing = checkFileSet("Yuri Dateien", dir, critical, true);
        checkFileSet("Yuri Dateien", dir, new String[]{"yuri.exe"}, false);

        StringBuilder optional = new StringBuilder();
        for (String name : new String[]{"expandmd01.mix", "xyr.dll"}) {
            if (findIgnoreCase(dir, name, 3) == null) {
                if (optional.length() > 0) optional.append(", ");
                optional.append(name);
            }
        }
        if (optional.length() > 0) {
            add(Level.INFO, "Yuri Dateien", "Optionale Patch-/Zusatzdateien nicht vorhanden",
                optional.toString(),
                "Kein primärer Startblocker für den direkt aus den Originalmedien installierten Kernbestand.");
        }

        if (missing == 0) {
            add(Level.PASS, "Yuri Dateien", "Yuri-Kernbestand vollständig",
                critical.length + " Kernbestandteile vorhanden.", "");
        }
    }

    private int checkFileSet(String group, File dir, String[] names, boolean critical) {
        int missing = 0;
        for (String name : names) {
            File file = findIgnoreCase(dir, name, 3);
            if (file == null) {
                missing++;
                add(critical ? Level.FAIL : Level.WARN, group, name + " fehlt",
                    "", "Originalmedien erneut einrichten.");
            }
            else if (file.length() <= 0) {
                missing++;
                add(Level.FAIL, group, name + " ist leer",
                    file.getAbsolutePath(), "Datei neu aus dem Originalmedium übernehmen.");
            }
            else {
                add(Level.PASS, group, name,
                    humanSize(file.length()), "");
            }
        }
        return missing;
    }

    private void reportOptionalDirectory(File dir, String label) {
        if (!dir.isDirectory()) {
            add(Level.INFO, "RA2 Dateien", label + " fehlt",
                dir.getAbsolutePath(),
                "Optional; kein primärer Grund für den aktuellen Startabbruch.");
            return;
        }
        long[] stats = directoryStats(dir, 5);
        add(Level.PASS, "RA2 Dateien", label + " vorhanden",
            stats[0] + " Dateien • " + humanSize(stats[1]), "");
    }

    private void checkDirectory(String group, File dir, String label, boolean critical) {
        if (!dir.isDirectory()) {
            add(critical ? Level.FAIL : Level.WARN, group, label + " fehlt",
                dir.getAbsolutePath(), "Originalmedien erneut einrichten.");
            return;
        }
        long[] stats = directoryStats(dir, 5);
        add(Level.PASS, group, label + " vorhanden",
            stats[0] + " Dateien • " + humanSize(stats[1]), "");
    }

    private void checkRegistry() {
        if (container == null) return;
        File reg = new File(container.getRootDir(), ".wine/system.reg");
        if (!reg.isFile()) {
            add(Level.FAIL, "Registry", "Wine system.reg fehlt",
                reg.getAbsolutePath(), "Container neu erstellen.");
            return;
        }

        String text = FileUtils.readString(reg);
        String lower = text == null ? "" : text.toLowerCase(Locale.ENGLISH);

        checkRegToken(lower, "westwood\\\\red alert 2", "RA2-Registry-Schlüssel");
        checkRegToken(lower, "c:\\\\westwood\\\\ra2\\\\game.exe", "RA2 InstallPath → GAME.EXE");

        File wolapi = findIgnoreCase(gameDir(), "wolapi.dll", 2);
        if (wolapi != null) {
            checkRegToken(lower, "westwood\\\\wolapi", "WOLAPI-Registry-Schlüssel");
        }

        boolean yuriInstalled = findIgnoreCase(gameDir(), "gamemd.exe", 2) != null;
        if (yuriInstalled) {
            checkRegToken(lower, "westwood\\\\yuri", "Yuri-Registry-Schlüssel");
        }
    }

    private void checkRegToken(String lowerReg, String token, String label) {
        if (lowerReg.contains(token.toLowerCase(Locale.ENGLISH))) {
            add(Level.PASS, "Registry", label + " vorhanden", "", "");
        }
        else {
            add(Level.FAIL, "Registry", label + " fehlt",
                token, "Einrichtung erneut ausführen; Registry wird dabei neu geschrieben.");
        }
    }

    private void checkWineServices() {
        if (container == null) return;

        add(container.getStartupSelection() == Container.STARTUP_SELECTION_NORMAL ? Level.PASS : Level.FAIL,
            "Windows-Dienste",
            "Winlator-Dienstmodus",
            container.getStartupSelection() == Container.STARTUP_SELECTION_NORMAL
                ? "Normal – alle benötigten Dienste"
                : "Nicht Normal (Wert " + container.getStartupSelection() + ")",
            container.getStartupSelection() == Container.STARTUP_SELECTION_NORMAL
                ? ""
                : "RA2 benötigt insbesondere RpcSs. Der aktuelle Build stellt den Container automatisch auf Normal.");

        File reg = new File(container.getRootDir(), ".wine/system.reg");
        if (!reg.isFile()) {
            add(Level.FAIL, "Windows-Dienste", "system.reg fehlt", reg.getAbsolutePath(), "Container neu erstellen.");
            return;
        }

        try (WineRegistryEditor editor = new WineRegistryEditor(reg)) {
            String controlSet = editor.getSymlinkValue("System\\CurrentControlSet", "SymbolicLinkValue");
            if (controlSet == null || controlSet.isEmpty()) controlSet = "System\\CurrentControlSet";

            checkServiceStart(editor, controlSet, "RpcSs", 2, true);
            checkServiceStart(editor, controlSet, "PlugPlay", 2, true);
            checkServiceStart(editor, controlSet, "Eventlog", 2, true);
            checkServiceStart(editor, controlSet, "NDIS", 2, true);
            checkServiceStart(editor, controlSet, "nsiproxy", 2, true);
            checkServiceStart(editor, controlSet, "MSIServer", 3, false);
            checkServiceStart(editor, controlSet, "FontCache", 3, false);
        }
        catch (Exception e) {
            add(Level.WARN, "Windows-Dienste", "Dienst-Registry konnte nicht gelesen werden",
                String.valueOf(e.getMessage()), "Nach erneutem App-Start noch einmal prüfen.");
        }

        File windows = new File(container.getRootDir(), ".wine/drive_c/windows");
        checkRuntimeBinary("Windows-Dienste", new File(windows, "system32/services.exe"), "services.exe", true);
        checkRuntimeBinary("Windows-Dienste", new File(windows, "system32/rpcss.exe"), "rpcss.exe (64-Bit/System32)", true);
        checkRuntimeBinary("Windows-Dienste", new File(windows, "syswow64/rpcss.exe"), "rpcss.exe (32-Bit/SysWOW64)", false);
        checkRuntimeBinary("WoW64-Laufzeit", new File(windows, "syswow64/ntdll.dll"), "SysWOW64 ntdll.dll", true);
        checkRuntimeBinary("WoW64-Laufzeit", new File(windows, "syswow64/kernel32.dll"), "SysWOW64 kernel32.dll", true);
        checkRuntimeBinary("WoW64-Laufzeit", new File(windows, "syswow64/rpcrt4.dll"), "SysWOW64 rpcrt4.dll", true);
        checkRuntimeBinary("WoW64-Laufzeit", new File(windows, "syswow64/ole32.dll"), "SysWOW64 ole32.dll", true);
    }

    private void checkRuntimeBinary(String group, File file, String label, boolean critical) {
        if (!file.isFile() || file.length() == 0) {
            add(critical ? Level.FAIL : Level.WARN, group, label + " fehlt",
                file.getAbsolutePath(),
                critical ? "Wine-Laufzeit/Prefix ist für den 32-Bit-Start unvollständig." : "Kann bei älteren 32-Bit-Programmen relevant sein.");
        }
        else {
            add(Level.PASS, group, label + " vorhanden",
                humanSize(file.length()), "");
        }
    }

    private void checkServiceStart(WineRegistryEditor editor, String controlSet,
                                   String service, int expected, boolean critical) {
        Integer actual = editor.getDwordValue(controlSet + "\\Services\\" + service, "Start");
        if (actual == null) {
            add(critical ? Level.FAIL : Level.WARN, "Windows-Dienste",
                service + " nicht registriert",
                "", "Der aktuelle Build repariert die Dienstkonfiguration vor dem Spielstart.");
            return;
        }

        if (actual == expected) {
            add(Level.PASS, "Windows-Dienste", service + " korrekt",
                "Start=" + actual, "");
        }
        else {
            add(critical ? Level.FAIL : Level.WARN, "Windows-Dienste",
                service + " falsch konfiguriert",
                "Start=" + actual + " • erwartet=" + expected,
                "Der aktuelle Build setzt diesen Dienst vor dem Spielstart automatisch korrekt.");
        }
    }

    private void checkRpcPreflight() {
        if (container == null) return;
        File file = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/rpcss-preflight.txt");
        if (!file.isFile()) {
            add(Level.INFO, "RPC-Preflight", "Noch kein aktiver RpcSs-Starttest vorhanden",
                file.getAbsolutePath(),
                "Der nächste Spielstart führt automatisch sc.exe start/query RpcSs aus.");
            return;
        }

        String text = FileUtils.readString(file);
        String lower = text == null ? "" : text.toLowerCase(Locale.ENGLISH);
        boolean running = lower.contains("running") || lower.contains("state") && lower.contains("4");
        boolean failed = lower.contains("failed") || lower.contains("error") ||
            lower.contains("1060") || lower.contains("1058") || lower.contains("1053");

        if (running) {
            add(Level.PASS, "RPC-Preflight", "RpcSs vor GAME.EXE gestartet",
                compactLines(text, 8),
                "");
        }
        else if (failed) {
            add(Level.FAIL, "RPC-Preflight", "RpcSs konnte vor GAME.EXE nicht sauber gestartet werden",
                compactLines(text, 10),
                "Das ist jetzt wichtiger als Grafik oder fehlende optionale RA2-Dateien.");
        }
        else {
            add(Level.WARN, "RPC-Preflight", "RpcSs-Status nicht eindeutig",
                compactLines(text, 10),
                "Nächsten Start erneut prüfen; die App protokolliert den Dienststatus direkt vor GAME.EXE.");
        }
    }

    private String compactLines(String text, int maxLines) {
        if (text == null || text.trim().isEmpty()) return "<leer>";
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (String raw : text.split("\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (count++ >= maxLines) {
                out.append(" …");
                break;
            }
            if (out.length() > 0) out.append(" | ");
            out.append(line);
        }
        return out.toString();
    }

    private void checkCncDdraw() {
        if (container == null) return;
        File dir = gameDir();

        File runtimeDll = new File(container.getRootDir(), ".wine/drive_c/windows/syswow64/ddraw.dll");
        File localDll = findIgnoreCase(dir, "ddraw.dll", 1);
        File ini = new File(container.getRootDir(), ".wine/drive_c/ProgramData/cnc-ddraw/ddraw.ini");

        if (!runtimeDll.isFile() || runtimeDll.length() == 0) {
            add(Level.FAIL, "Grafik", "Winlator-DDraw-Runtime fehlt",
                runtimeDll.getAbsolutePath(),
                "Die Wine-Laufzeit muss CNC-DDraw vor dem Spielstart extrahieren.");
        }
        else {
            add(Level.PASS, "Grafik", "Winlator DDraw-Runtime vorhanden",
                humanSize(runtimeDll.length()), "");
        }

        if (localDll != null) {
            String runtimeHash = runtimeDll.isFile() ? md5(runtimeDll) : "";
            String localHash = md5(localDll);
            if (!runtimeHash.isEmpty() && runtimeHash.equalsIgnoreCase(localHash)) {
                add(Level.PASS, "Grafik", "Lokaler CNC-DDraw-Wrapper synchron",
                    localDll.getAbsolutePath() + " • " + humanSize(localDll.length()) + " • MD5 " + localHash,
                    "");
            }
            else {
                add(Level.WARN, "Grafik", "Lokale ddraw.dll weicht von der Winlator-Runtime ab",
                    localDll.getAbsolutePath() + " • " + humanSize(localDll.length()),
                    "Spiel erneut starten; der aktuelle Build synchronisiert den Wrapper unmittelbar vor GAME.EXE/GAMEMD.EXE.");
            }
        }
        else {
            add(Level.INFO, "Grafik", "Noch keine lokale ddraw.dll",
                "Sie wird beim nächsten Spielstart aus der Winlator-Runtime synchronisiert.", "");
        }

        if (!ini.isFile()) {
            add(Level.FAIL, "Grafik", "CNC-DDraw ddraw.ini fehlt",
                ini.getAbsolutePath(), "Einrichtung/Laufzeit erneut starten.");
        }
        else {
            String cfg = FileUtils.readString(ini);
            String renderer = activeConfigValue(cfg, "renderer");
            String singlecpu = activeConfigValue(cfg, "singlecpu");

            add("opengl".equalsIgnoreCase(renderer) ? Level.PASS : Level.WARN,
                "Grafik", "Renderer-Konfiguration",
                "renderer=" + (renderer.isEmpty() ? "<nicht aktiv gesetzt>" : renderer),
                "OpenGL ist für diesen Build die bevorzugte Einstellung.");

            add("true".equalsIgnoreCase(singlecpu) ? Level.PASS : Level.WARN,
                "Grafik", "Single-CPU-Kompatibilität",
                "singlecpu=" + (singlecpu.isEmpty() ? "<nicht aktiv gesetzt>" : singlecpu),
                "singlecpu=true setzen.");
        }

        File ra2Ini = findIgnoreCase(dir, "ra2.ini", 2);
        if (ra2Ini == null) {
            add(Level.WARN, "Grafik", "RA2.INI fehlt",
                "", "Die App legt eine kompatible RA2.INI an.");
        }
        else {
            add(Level.PASS, "Grafik", "RA2.INI vorhanden",
                humanSize(ra2Ini.length()), "");
        }
    }

    private String activeConfigValue(String cfg, String key) {
        if (cfg == null) return "";
        for (String line : cfg.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) continue;
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String left = trimmed.substring(0, eq).trim();
            if (left.equalsIgnoreCase(key)) return trimmed.substring(eq + 1).trim();
        }
        return "";
    }

    private void checkSafeDisc() {
        if (container == null) return;

        File game = findIgnoreCase(gameDir(), "game.exe", 2);
        File drvmgt = findIgnoreCase(gameDir(), "drvmgt.dll", 2);
        File secdrvGame = findIgnoreCase(gameDir(), "secdrv.sys", 5);
        File secdrv32 = new File(container.getRootDir(), ".wine/drive_c/windows/system32/drivers/secdrv.sys");
        File secdrv64 = new File(container.getRootDir(), ".wine/drive_c/windows/syswow64/drivers/secdrv.sys");

        boolean indicator = RA2LaunchBatch.isSafeDisc(game) || drvmgt != null || (game != null && containsAscii(game, "secdrv"));
        if (indicator) {
            add(Level.WARN, "Kopierschutz", "Original-CD/SafeDisc erkannt",
                "Die CD-Version verwendet den alten SafeDisc-Treiberpfad.",
                "Dies ist ein Kopierschutz-Indikator, kein Nachweis für die Ursache des letzten Spielendes.");
        }
        else {
            add(Level.INFO, "Kopierschutz", "Kein eindeutiger SafeDisc-Indikator erkannt",
                "", "");
        }

        File active = secdrv32.isFile() ? secdrv32 : (secdrv64.isFile() ? secdrv64 : secdrvGame);
        if (active == null || !active.isFile()) {
            add(Level.WARN, "Kopierschutz", "secdrv.sys fehlt",
                "Weder im Wine-Treiberordner noch im Spielordner gefunden.",
                "Eine Treiberdatei ist für den CD-Kompatibilitätshelfer kein Funktionsnachweis. Tatsächlichen Startweg und Helferprotokoll prüfen.");
            return;
        }

        String md5 = md5(active);
        String knownYuri = "f376a1580204e47f37a721e1cbc5582a";
        Level level = knownYuri.equalsIgnoreCase(md5) ? Level.PASS : Level.WARN;
        add(level, "Kopierschutz", "secdrv.sys vorhanden",
            active.getAbsolutePath() + " • " + humanSize(active.length()) + " • MD5 " + md5,
            level == Level.PASS
                ? ""
                : "Treiber vorhanden, aber nicht als der bekannte Yuri-SafeDisc-2.40.010-Treiber erkannt.");
    }

    private boolean containsAscii(File file, String needle) {
        if (file == null || !file.isFile()) return false;
        byte[] target = needle.toLowerCase(Locale.ENGLISH).getBytes(StandardCharsets.US_ASCII);
        byte[] window = new byte[1024 * 1024];
        int carry = 0;

        try (FileInputStream in = new FileInputStream(file)) {
            int read;
            while ((read = in.read(window, carry, window.length - carry)) > 0) {
                int total = carry + read;
                for (int i = 0; i <= total - target.length; i++) {
                    boolean match = true;
                    for (int j = 0; j < target.length; j++) {
                        int b = window[i + j] & 0xff;
                        if (b >= 'A' && b <= 'Z') b += 32;
                        if (b != (target[j] & 0xff)) {
                            match = false;
                            break;
                        }
                    }
                    if (match) return true;
                }
                carry = Math.min(target.length - 1, total);
                if (carry > 0) System.arraycopy(window, total - carry, window, 0, carry);
            }
        }
        catch (Exception ignored) {}
        return false;
    }

    private String md5(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[256 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            StringBuilder out = new StringBuilder();
            for (byte b : digest.digest()) out.append(String.format(Locale.US, "%02x", b & 0xff));
            return out.toString();
        }
        catch (Exception e) {
            return "unbekannt";
        }
    }

    private void checkPeDependencies(boolean yuri) {
        if (container == null) return;
        File exe = findIgnoreCase(gameDir(), yuri ? "gamemd.exe" : "game.exe", 2);
        String group = yuri ? "Yuri EXE-Abhängigkeiten" : "RA2 EXE-Abhängigkeiten";
        if (exe == null) {
            add(yuri ? Level.WARN : Level.FAIL, group,
                (yuri ? "GAMEMD.EXE" : "GAME.EXE") + " fehlt",
                "", yuri ? "Yuri einrichten." : "RA2 einrichten.");
            return;
        }

        try {
            Set<String> imports = readPeImports(exe);
            if (imports.isEmpty()) {
                add(Level.WARN, group, "Keine PE-Imports gelesen",
                    "Die EXE konnte gelesen werden, aber keine Importtabelle wurde erkannt.",
                    "Bei weiterem Absturz Wine-Protokoll beachten.");
                return;
            }

            int missing = 0;
            for (String dll : imports) {
                if (isSystemDll(dll)) continue;
                if (!resolveDll(dll)) {
                    missing++;
                    add(Level.FAIL, group, "Importierte DLL fehlt: " + dll,
                        "Von " + exe.getName() + " benötigt.",
                        "Fehlende DLL aus den Originalmedien übernehmen.");
                }
            }

            add(missing == 0 ? Level.PASS : Level.FAIL, group,
                "PE-Importprüfung",
                imports.size() + " importierte Bibliotheken • " + missing + " nicht auflösbar",
                missing == 0 ? "" : "Die rot markierten DLLs müssen vorhanden sein.");
        }
        catch (Exception e) {
            add(Level.WARN, group, "PE-Importprüfung nicht möglich",
                e.getMessage(), "Wine-Protokoll als nächste Quelle verwenden.");
        }
    }

    private void checkVirtualMedia() {
        if (container == null) return;
        File root = container.getRootDir();
        File driveX = new File(root, ".wine/drive_x");
        File ra2Media = new File(root, ".wine/drive_ra2_media");
        File yuriMedia = new File(root, ".wine/drive_yuri_media");

        if (ra2Media.isDirectory()) {
            long[] stats = directoryStats(ra2Media, 6);
            add(Level.PASS, "Virtuelle CDs", "RA2-CD-Medium gespeichert",
                stats[0] + " Dateien • " + humanSize(stats[1]), "");
        }
        else {
            add(Level.WARN, "Virtuelle CDs", "RA2-CD-Medium nicht archiviert",
                ra2Media.getAbsolutePath(),
                "RA2 erneut einrichten.");
        }

        if (driveX.exists()) {
            add(Level.PASS, "Virtuelle CDs", "Wine-Laufwerk X: vorhanden",
                driveX.getAbsolutePath(), "");
        }
        else {
            add(Level.WARN, "Virtuelle CDs", "Wine-Laufwerk X: fehlt",
                driveX.getAbsolutePath(),
                "Beim nächsten Spielstart wird X: automatisch aktiviert.");
        }

        if (yuriMedia.isDirectory()) {
            long[] stats = directoryStats(yuriMedia, 6);
            add(Level.PASS, "Virtuelle CDs", "Yuri-CD-Medium gespeichert",
                stats[0] + " Dateien • " + humanSize(stats[1]), "");
        }
    }

    private void checkLastLaunch() {
        if (container == null) return;

        File log = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/last-start.log");
        File liveLog = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/ra2-live.log");
        String finalText = log.isFile() ? FileUtils.readString(log) : "";
        // Batch markers are written directly by Wine, not through the Java callback.
        String liveText = sessionText("ra2-live.previous.log") + (liveLog.isFile() ? FileUtils.readString(liveLog) : "");
        if (liveText.isEmpty()) liveText = sessionText("ra2-evidence.log");
        String text = liveText != null && !liveText.isEmpty() ? liveText : (finalText == null ? "" : finalText);
        lastLaunchText = text;

        int exit = prefs.getInt("last_exit_code", Integer.MIN_VALUE);
        boolean hasExit = prefs.contains("last_exit_code");
        boolean hasChild = prefs.contains("last_saw_child");
        boolean sawChild = prefs.getBoolean("last_saw_child", false);
        String clue = prefs.getString("last_diag_clue", "");

        if (!hasExit && text != null) exit = parseIntValue(text, "exit=", Integer.MIN_VALUE);
        if (!hasChild && text != null) {
            String childText = value(text, "sawGameChild=");
            if (!childText.isEmpty()) {
                sawChild = Boolean.parseBoolean(childText);
                hasChild = true;
            }
        }

        Integer gameResult = RA2LaunchBatch.readGameExit(new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/game-exit.txt"));
        if (gameResult != null) exit = gameResult;
        if (prefs.contains("last_launcher_exit_code")) {
            add(Level.INFO, "Letzter Start", "Separater Laufzeit-Exit-Code",
                String.valueOf(prefs.getInt("last_launcher_exit_code", 0)),
                "Beim Beenden der Sitzung kann die App die Laufzeit mit Code 137 stoppen. Maßgeblich ist der Spiel-Rückgabecode.");
        }
        File registration = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/blowfish-register.txt");
        if (registration.isFile()) add(Level.INFO, "Spielvorbereitung", "32-Bit-Blowfish-Registrierung",
            FileUtils.readString(registration), "registerExit=0 bedeutet erfolgreich.");
        File rpcAfter = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/rpcss-after-game.txt");
        if (rpcAfter.isFile()) add(Level.INFO, "RPC nach Spielende", "RpcSs-Zustand nach GAME.EXE",
            compactLines(FileUtils.readString(rpcAfter), 10), "");
        if (liveText != null) add(Level.INFO, "CD-Kompatibilität", "Startweg",
            liveText.contains("cdCompatibility=true") ? "SafeDisc-Kompatibilitätshelfer" : "Direkter Spielstart", "");
        for (String name : new String[]{"safedisc-injector.log", "safedisc.log"}) {
            File helperLog = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/" + name);
            if (helperLog.isFile()) {
                String helperText = FileUtils.readString(helperLog);
                if (helperText != null && !helperText.isEmpty()) add(Level.INFO, "CD-Kompatibilität", name,
                    helperText.length() > 12000 ? helperText.substring(helperText.length() - 12000) : helperText, "");
            }
        }
        String phase = RA2LaunchBatch.gamePhase(liveText);
        if (!phase.isEmpty()) {
            String tail = phase.length() > 12000 ? phase.substring(phase.length() - 12000) : phase;
            add(Level.INFO, "Spielprotokoll", "Ausgabe während des Spielstarts", tail, "");
        }
        String stage = lastValue(text, "stage=");
        if (!stage.isEmpty()) {
            add(Level.INFO, "Letzter Start", "Letzte erreichte Startphase",
                stage, "");
        }

        if (!hasExit && (text == null || text.isEmpty())) {
            add(Level.WARN, "Letzter Start", "Detailliertes Startprotokoll fehlt",
                "Der vorherige Build hat den Rücksprung protokolliert, aber das Wine-Detailprotokoll nicht dauerhaft erhalten.",
                "Der aktuelle Build schreibt das Protokoll bereits während des Starts fortlaufend.");
            return;
        }

        if (exit != Integer.MIN_VALUE) {
            add(exit == 0 ? Level.WARN : Level.FAIL, "Letzter Start",
                gameResult != null ? "Spiel-Rückgabecode" : "Bisheriger Launcher-Code",
                String.valueOf(exit),
                exit == 0
                    ? "Exit-Code 0 bei schwarzem Bildschirm kann bei der Original-CD auf SafeDisc-Kompatibilität hindeuten."
                    : "Fehlerzeilen darunter prüfen.");
        }

        if (hasChild) {
            add(sawChild ? Level.PASS : Level.FAIL, "Letzter Start",
                "Spielfenster erkannt",
                sawChild ? "Ja" : "Nein",
                sawChild ? "" : "Start scheitert vor oder beim Erzeugen des eigentlichen Spiels.");
        }

        if (!clue.isEmpty()) {
            add(clue.startsWith("Spielprozess beendet: 0") ? Level.INFO : Level.FAIL, "Letzter Start", "Letzte Startmeldung",
                clue, hintForLog(clue));
        }

        collectSuspiciousLines(text);
        int shown = 0;
        for (String line : suspiciousLogLines) {
            if (shown++ >= 16) break;
            add(Level.WARN, "Wine-Protokoll", "Verdächtige Wine-Zeile",
                line, hintForLog(line));
        }

        if (suspiciousLogLines.isEmpty() && (log.isFile() || liveLog.isFile())) {
            add(Level.INFO, "Wine-Protokoll", "Keine offensichtliche Wine-Fehlerzeile erkannt",
                humanSize(log.isFile() ? log.length() : liveLog.length()) + " Logdaten ausgewertet.",
                "Bei Exit-Code 0 und Original-CD ist SafeDisc als Ursache besonders relevant.");
        }
    }

    private void checkWineDriveMappings() {
        if (container == null) return;
        File dos = new File(container.getRootDir(), ".wine/dosdevices");
        if (!dos.isDirectory()) {
            add(Level.FAIL, "Wine-Laufwerke", "dosdevices fehlt",
                dos.getAbsolutePath(), "Wine-Prefix reparieren bzw. Container neu initialisieren.");
            return;
        }

        File system = new File(container.getRootDir(), ".wine/system.reg");
        if (system.isFile()) {
            try (WineRegistryEditor registry = new WineRegistryEditor(system)) {
                String type = registry.getStringValue("Software\\Wine\\Drives", "x:");
                add("cdrom".equalsIgnoreCase(type) ? Level.PASS : Level.WARN, "Wine-Laufwerke", "Konfigurierter Typ von X:",
                    type == null || type.isEmpty() ? "Kein CD-ROM-Typ eingetragen" : type,
                    "Die Registry ist nur die Soll-Konfiguration. Tatsächliche GetDriveType-Rückgabe im Spiel-Relay prüfen (5 = CD-ROM). Beim nächsten Start wird X: als cdrom eingetragen.");
            }
        }
        checkDosDevice(dos, "c:", true);
        checkDosDevice(dos, "x:", false);
        checkDosDevice(dos, "z:", false);

        File driveX = new File(container.getRootDir(), ".wine/drive_x");
        add(driveX.exists() ? Level.PASS : Level.WARN,
            "Wine-Laufwerke", "Physisches X:-Ziel",
            driveX.getAbsolutePath() + (FileUtils.isSymlink(driveX) ? " • Symlink" : ""),
            driveX.exists() ? "" : "Das aktive CD-Medium vor dem nächsten Start erneut aktivieren.");
    }

    private void checkDosDevice(File dos, String name, boolean critical) {
        File link = new File(dos, name);
        boolean present = link.exists() || FileUtils.isSymlink(link);
        if (!present) {
            add(critical ? Level.FAIL : Level.WARN, "Wine-Laufwerke",
                "Wine " + name.toUpperCase(Locale.ENGLISH) + " nicht gemappt",
                link.getAbsolutePath(),
                critical ? "Wine-Prefix prüfen." : "Wird beim Start normalerweise automatisch angelegt.");
            return;
        }

        String target = "";
        try { target = link.getCanonicalPath(); }
        catch (Exception ignored) {}
        add(Level.PASS, "Wine-Laufwerke",
            "Wine " + name.toUpperCase(Locale.ENGLISH) + " gemappt",
            target.isEmpty() ? link.getAbsolutePath() : target, "");
    }

    private void checkExecutableFormat(boolean yuri) {
        if (container == null) return;
        File exe = findIgnoreCase(gameDir(), yuri ? "gamemd.exe" : "game.exe", 2);
        String group = yuri ? "Yuri EXE-Format" : "RA2 EXE-Format";
        if (exe == null) {
            add(yuri ? Level.WARN : Level.FAIL, group,
                (yuri ? "GAMEMD.EXE" : "GAME.EXE") + " fehlt", "", "");
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(exe, "r")) {
            byte[] mz = new byte[64];
            raf.readFully(mz);
            long peOffset = u32(mz, 0x3c);
            raf.seek(peOffset + 4);
            byte[] machineBytes = new byte[2];
            raf.readFully(machineBytes);
            int machine = u16(machineBytes, 0);

            raf.seek(peOffset + 24);
            byte[] magicBytes = new byte[2];
            raf.readFully(magicBytes);
            int magic = u16(magicBytes, 0);

            String machineName = machine == 0x14c ? "x86 (32-Bit)" :
                machine == 0x8664 ? "x64 (64-Bit)" : "0x" + Integer.toHexString(machine);
            String peKind = magic == 0x10b ? "PE32" : magic == 0x20b ? "PE32+" : "0x" + Integer.toHexString(magic);

            Level level = machine == 0x14c && magic == 0x10b ? Level.PASS : Level.WARN;
            add(level, group, exe.getName() + " Architektur",
                machineName + " • " + peKind + " • " + humanSize(exe.length()),
                level == Level.PASS ? "" : "RA2/Yuri erwarten einen klassischen 32-Bit-x86-Pfad.");
        }
        catch (Exception e) {
            add(Level.WARN, group, "PE-Header konnte nicht gelesen werden",
                String.valueOf(e.getMessage()), "");
        }
    }

    private void checkLaunchTimeline() {
        if (lastLaunchText == null || lastLaunchText.isEmpty()) {
            add(Level.INFO, "Start-Timeline", "Noch keine Timeline vorhanden",
                "", "Nach einem Spielstart erneut diagnostizieren.");
            return;
        }

        LinkedHashSet<String> stages = new LinkedHashSet<>();
        List<String> snapshots = new ArrayList<>();
        for (String line : lastLaunchText.split("\\n")) {
            if (line.startsWith("stage=")) stages.add(line.substring(6).trim());
            if (line.startsWith("processes=") && snapshots.size() < 6) {
                snapshots.add(line.substring("processes=".length()).trim());
            }
        }

        add(Level.INFO, "Start-Timeline", "Erreichte Phasen",
            stages.isEmpty() ? "<keine>" : joinStrings(stages, " → "),
            "");

        if (!snapshots.isEmpty()) {
            for (int i = 0; i < snapshots.size(); i++) {
                add(Level.INFO, "Start-Timeline", "Prozess-Snapshot " + (i + 1),
                    snapshots.get(i), "");
            }
        }

        boolean wineAttached = (lastLaunchText.contains("stage=wine-debug-attached") || lastLaunchText.contains("stage=legacy-x86-start"));
        boolean wrapperReady = (lastLaunchText.contains("stage=runtime-wrapper-ready") || lastLaunchText.contains("stage=legacy-runtime-wrapper-ready"));
        boolean terminated = (lastLaunchText.contains("stage=launcher-terminated") || lastLaunchText.contains("stage=legacy-launch-terminated"));
        boolean helperDone = lastLaunchText.contains("stage=helper-complete");
        boolean targetTimeout = lastLaunchText.contains("stage=target-not-seen");

        add(wineAttached ? Level.PASS : Level.WARN, "Start-Timeline",
            "Wine-Debugger gekoppelt", wineAttached ? "Ja" : "Nein",
            wineAttached ? "" : "Dieser Protokollmarker fehlt; daraus allein lässt sich kein Wine-Startfehler ableiten.");
        add(wrapperReady ? Level.PASS : Level.WARN, "Start-Timeline",
            "Runtime-Wrapper vorbereitet", wrapperReady ? "Ja" : "Nein",
            wrapperReady ? "" : "CNC-DDraw/Runtime wurde nicht bis zum vorbereiteten Zustand erreicht.");
        add(terminated ? Level.INFO : Level.WARN, "Start-Timeline",
            "Launcher-Ende protokolliert", terminated ? "Ja" : "Nein",
            terminated ? "" : "Fehlt bei manuellem Exit oder wenn Wine im Hintergrund weiterlief.");

        if (helperDone) {
            add(Level.PASS, "Start-Timeline", "Setup-Helfer automatisch beendet",
                "7-Zip/Setup-Prozess wurde erkannt und die App ist selbstständig zurückgekehrt.", "");
        }
        if (targetTimeout) {
            add(Level.FAIL, "Start-Timeline", "App hat den Start abgebrochen",
                "Der alte 30-Sekunden-Watchdog hat die Laufzeit ohne erkanntes Spielfenster beendet.",
                "Exit 137 kann dadurch entstehen und beweist keinen selbstständigen Spielabsturz.");
        }
    }

    private String sessionText(String name) {
        if (container == null) return "";
        File file = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/" + name);
        return file.isFile() ? FileUtils.readString(file) : "";
    }

    private void createRootCauseAnalysis() {
        if (container == null) return;
        Integer code = RA2LaunchBatch.readGameExit(new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/game-exit.txt"));
        RA2DiagnosticEvidence.Analysis analysis = RA2DiagnosticEvidence.analyze(lastLaunchText,
            sessionText("safedisc-injector.log"), sessionText("safedisc.log"),
            sessionText("rpcss-preflight.txt"), sessionText("rpcss-after-game.txt"), code,
            prefs.getBoolean("last_saw_child", false));
        primaryDiagnosis = analysis.summary;
        add(Level.INFO, "Sitzung", "Aktueller Testlauf", sessionText("session.json"),
            "Nur Daten dieses Laufs werden für die Ursachenanalyse verwendet. Ältere Läufe stehen im ZIP getrennt bereit.");
        add(Level.INFO, "Sitzung", "Ereignisse mit Zeitstempel", sessionText("events.log"),
            "Zeitstempel sind Millisekunden seit Unix-Epoch; Wine-Zeilen tragen zusätzlich wineMs seit Laufzeitstart.");
        File compactTrace = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/ra2-evidence.log");
        if (compactTrace.length() >= 16 * 1024 * 1024) add(Level.WARN, "Messabdeckung", "Kompaktprotokoll hat Größenlimit erreicht",
            "Spätere Wine-Zeilen können fehlen; Ereignisprotokoll und letzte rohe Trace-Segmente bleiben im ZIP.",
            "Keine Aussage über spätere Fehler aus fehlenden Zeilen ableiten.");
        for (RA2DiagnosticEvidence.Finding finding : analysis.findings) {
            Level level = finding.severity.equals("FAIL") ? Level.FAIL : finding.severity.equals("WARN") ? Level.WARN : Level.INFO;
            add(level, "Ursachenanalyse nach Fehlerbelegen", finding.confidence + " • " + finding.title,
                finding.evidence, finding.next);
        }
        File history = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/history");
        File[] runs = history.listFiles();
        if (runs != null) {
            Arrays.sort(runs, (a,b) -> b.getName().compareTo(a.getName()));
            for (int i=0; i<Math.min(3, runs.length); i++) {
                File oldResult = new File(runs[i], "game-exit.txt");
                File meta = new File(runs[i], "session.json");
                add(Level.INFO, "Frühere Testläufe", runs[i].getName(),
                    "Spielcode: " + RA2DiagnosticEvidence.decode(RA2LaunchBatch.readGameExit(oldResult)) +
                    (meta.isFile() ? "\n" + FileUtils.readString(meta) : "\nKein Sitzungssnapshot im früheren Build"),
                    "Frühere Fehler werden nicht in die aktuelle Ursachenanalyse übernommen.");
            }
        }
    }

    private Integer readServiceStart(String service) {
        if (container == null) return null;
        File reg = new File(container.getRootDir(), ".wine/system.reg");
        if (!reg.isFile()) return null;
        try (WineRegistryEditor editor = new WineRegistryEditor(reg)) {
            String controlSet = editor.getSymlinkValue("System\\CurrentControlSet", "SymbolicLinkValue");
            if (controlSet == null || controlSet.isEmpty()) controlSet = "System\\CurrentControlSet";
            return editor.getDwordValue(controlSet + "\\Services\\" + service, "Start");
        }
        catch (Exception e) {
            return null;
        }
    }

    private String joinStrings(Iterable<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append(separator);
            out.append(value);
        }
        return out.toString();
    }

    private void performSafeRepair() {
        if (container == null) return;
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        repairButton.setEnabled(false);
        rescanButton.setEnabled(false);
        copyButton.setEnabled(false);
        summary.setText("Sichere Reparaturen werden angewendet …");

        worker.execute(() -> {
            try {
                container.setStartupSelection(Container.STARTUP_SELECTION_NORMAL);
                container.saveData();
                WineUtils.changeServicesStatus(container, Container.STARTUP_SELECTION_NORMAL);

                File systemReg = new File(container.getRootDir(), ".wine/system.reg");
                if (systemReg.isFile()) {
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
                }

                File userReg = new File(container.getRootDir(), ".wine/user.reg");
                if (userReg.isFile()) {
                    try (WineRegistryEditor registry = new WineRegistryEditor(userReg)) {
                        registry.setStringValue("Software\\Wine\\DllOverrides", "ddraw", "native,builtin");
                    }
                }

                File globalIni = new File(container.getRootDir(), ".wine/drive_c/ProgramData/cnc-ddraw/ddraw.ini");
                if (globalIni.isFile()) {
                    String cfg = FileUtils.readString(globalIni);
                    cfg = setIniValue(cfg, "renderer", "opengl");
                    cfg = setIniValue(cfg, "singlecpu", "true");
                    cfg = setIniValue(cfg, "windowed", "false");
                    cfg = setIniValue(cfg, "fullscreen", "true");
                    cfg = setIniValue(cfg, "nonexclusive", "true");
                    cfg = setIniValue(cfg, "maxfps", "60");
                    FileUtils.writeString(globalIni, cfg);
                }
            }
            catch (Exception e) {
                runOnUiThread(() -> summary.setText("Reparaturfehler: " + e.getMessage()));
            }
            runOnUiThread(this::runDiagnostics);
        });
    }

    private String setIniValue(String cfg, String key, String value) {
        if (cfg == null) cfg = "";
        String[] lines = cfg.split("\\n", -1);
        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!replaced && !trimmed.startsWith(";") && !trimmed.startsWith("#")) {
                int eq = trimmed.indexOf('=');
                if (eq > 0 && trimmed.substring(0, eq).trim().equalsIgnoreCase(key)) {
                    out.append(key).append("=").append(value);
                    replaced = true;
                }
                else out.append(line);
            }
            else out.append(line);
            out.append("\n");
        }
        if (!replaced) out.append(key).append("=").append(value).append("\n");
        return out.toString();
    }

    private void createAssessment() {
        int fail = 0, warn = 0, pass = 0;
        for (Result r : results) {
            if (r.level == Level.FAIL) fail++;
            else if (r.level == Level.WARN) warn++;
            else if (r.level == Level.PASS) pass++;
        }

        if (fail == 0) {
            add(Level.INFO, "Gesamtbewertung", "Keine statischen Blocker gefunden",
                pass + " Prüfungen bestanden • " + warn + " Warnungen.",
                "Wenn RA2 weiterhin beendet wird, ist die Laufzeit-/Wine-Kompatibilität der nächste Schwerpunkt.");
        }
        else {
            add(Level.INFO, "Gesamtbewertung", fail + " fehlgeschlagene Prüfungen",
                pass + " bestanden • " + warn + " Warnungen • " + fail + " Fehler.",
                "Zuerst die roten Punkte beheben; danach erneut prüfen.");
        }
    }

    private void renderResults() {
        progress.setVisibility(View.GONE);
        repairButton.setEnabled(container != null);
        rescanButton.setEnabled(true);
        copyButton.setEnabled(true);

        int fail = 0, warn = 0, pass = 0;
        for (Result r : results) {
            if (r.level == Level.FAIL) fail++;
            else if (r.level == Level.WARN) warn++;
            else if (r.level == Level.PASS) pass++;
        }

        String baseSummary = "Fertig: " + pass + " OK • " + warn + " Warnungen • " + fail + " Fehler";
        summary.setText(primaryDiagnosis.isEmpty() ? baseSummary : baseSummary + "\nDiagnose nach Fehlerbelegen: " + primaryDiagnosis);

        String lastGroup = "";
        StringBuilder report = new StringBuilder();
        report.append("RA2 / Yuri Diagnose-Center\n");
        report.append(summary.getText()).append("\n\n");

        for (Result r : results) {
            if (!r.group.equals(lastGroup)) {
                TextView group = text(19, Color.WHITE, true);
                group.setText(r.group);
                group.setPadding(dp(2), dp(13), dp(2), dp(5));
                resultBox.addView(group);
                lastGroup = r.group;
                report.append("\n[").append(r.group).append("]\n");
            }

            TextView row = text(14, levelColor(r.level), false);
            String icon = icon(r.level);
            StringBuilder line = new StringBuilder(icon).append("  ").append(r.title);
            if (!r.detail.isEmpty()) line.append("\n     ").append(r.detail);
            if (!r.action.isEmpty()) line.append("\n     → ").append(r.action);
            row.setText(line.toString());
            row.setPadding(dp(7), dp(8), dp(7), dp(8));
            row.setBackgroundColor(Color.rgb(18, 23, 26));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = dp(4);
            resultBox.addView(row, rp);

            report.append(icon).append(" ").append(r.title);
            if (!r.detail.isEmpty()) report.append(" | ").append(r.detail);
            if (!r.action.isEmpty()) report.append(" | Empfehlung: ").append(r.action);
            report.append("\n");
        }

        reportText = report.toString();
    }

    private void add(Level level, String group, String title, String detail, String action) {
        results.add(new Result(level, group, title, detail, action));
    }

    private void copyReport() {
        ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("RA2 Diagnose", reportText));
        summary.setText(summary.getText() + " • Protokoll kopiert");
    }

    @Override
    protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 2401 || result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri destination = data.getData();
        final String report = reportText;
        worker.execute(() -> {
            try (java.io.OutputStream output = getContentResolver().openOutputStream(destination);
                 java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(output)) {
                zip.putNextEntry(new java.util.zip.ZipEntry("Diagnosebericht.txt"));
                zip.write(report.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                if (container != null) {
                    File session = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile");
                    zipSession(zip, session, "aktueller-lauf/");
                    File[] history = new File(session, "history").listFiles();
                    if (history != null) {
                        Arrays.sort(history, (a,b) -> b.getName().compareTo(a.getName()));
                        for (int i=0;i<Math.min(3,history.length);i++) zipSession(zip,history[i],"history/"+history[i].getName()+"/");
                    }
                }
                runOnUiThread(() -> android.widget.Toast.makeText(this,"Diagnose-ZIP gespeichert",android.widget.Toast.LENGTH_LONG).show());
            } catch (Exception error) {
                runOnUiThread(() -> android.widget.Toast.makeText(this,"ZIP konnte nicht gespeichert werden: " + error.getMessage(),android.widget.Toast.LENGTH_LONG).show());
            }
        });
    }

    private void zipSession(java.util.zip.ZipOutputStream zip, File session, String prefix) throws java.io.IOException {
        // Explicit allowlist: no ISOs, registry, saved games or unrelated app files.
        for (String name : new String[]{"session.json", "events.log", "ra2-evidence.log", "ra2-live.log", "ra2-live.previous.log", "game-exit.txt", "rpcss-preflight.txt", "rpcss-after-game.txt", "blowfish-register.txt", "safedisc.log", "safedisc-injector.log"}) {
            File file = new File(session,name);
            if (!file.isFile()) continue;
            zip.putNextEntry(new java.util.zip.ZipEntry(prefix+name));
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count=input.read(buffer))!=-1) zip.write(buffer,0,count);
            }
            zip.closeEntry();
        }
    }

    private int checkFileSetDummy() { return 0; }

    private File gameDir() {
        return new File(container.getRootDir(), ".wine/drive_c/Westwood/RA2");
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
            File found = findIgnoreCase(file, filename, depth - 1);
            if (found != null) return found;
        }
        return null;
    }

    private long[] directoryStats(File root, int depth) {
        long files = 0, bytes = 0;
        if (root == null || !root.exists() || depth < 0) return new long[]{0, 0};
        if (root.isFile()) return new long[]{1, root.length()};
        File[] children = root.listFiles();
        if (children == null) return new long[]{0, 0};
        for (File child : children) {
            if (child.isFile()) {
                files++;
                bytes += child.length();
            }
            else if (depth > 0) {
                long[] nested = directoryStats(child, depth - 1);
                files += nested[0];
                bytes += nested[1];
            }
        }
        return new long[]{files, bytes};
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
            new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getLong(0);
        }
        catch (Exception ignored) {}
        return -1;
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
            new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) return name;
            }
        }
        catch (Exception ignored) {}
        return uri.getLastPathSegment();
    }

    private void collectSuspiciousLines(String text) {
        if (text == null || text.isEmpty()) return;
        Set<String> unique = new LinkedHashSet<>();
        String[] lines = text.split("\\n");
        for (String raw : lines) {
            String line = raw.trim();
            String lower = line.toLowerCase(Locale.ENGLISH);
            boolean sehRegisterDump =
                lower.contains("trace:seh:dispatch_exception rip=") ||
                lower.contains("trace:seh:dispatch_exception rax=") ||
                lower.contains("trace:seh:dispatch_exception rsi=") ||
                lower.contains("trace:seh:dispatch_exception r10=") ||
                lower.contains("trace:seh:dispatch_exception r14=");
            if (sehRegisterDump) continue;

            if (lower.contains("err:") || lower.contains("unhandled") ||
                lower.contains("dispatch_exception code=") || lower.contains("failed") ||
                lower.contains("cannot") || lower.contains("missing") ||
                lower.contains("not found") || lower.contains("import_dll") ||
                lower.contains("page fault") || lower.contains("experimental wow64")) {
                if (line.length() > 260) line = line.substring(0, 260);
                unique.add(line);
            }
        }
        suspiciousLogLines.addAll(unique);
    }

    private String hintForLog(String line) {
        String lower = line == null ? "" : line.toLowerCase(Locale.ENGLISH);
        if (lower.contains("import_dll") || lower.contains("library") && lower.contains("not found"))
            return "Fehlende DLL/Abhängigkeit prüfen; die PE-Prüfung oben listet lokale Bibliotheken.";
        if (lower.contains("page fault") || lower.contains("unhandled"))
            return "Absturz im Windows-Programm; Renderer, Wrapper und fehlende Originaldateien prüfen.";
        if (lower.contains("ddraw") || lower.contains("directdraw"))
            return "CNC-DDraw-Konfiguration und ddraw.dll prüfen.";
        if (lower.contains("access denied") || lower.contains("permission"))
            return "Dateizugriff bzw. Wine-Pfade prüfen.";
        if (lower.contains("rpcss") || lower.contains("rpc_s_server_unavailable"))
            return "RPC-Meldung: mit Vor-/Nachabfrage und Spielphase vergleichen; allein kein Beweis für einen Dienstausfall.";
        if (lower.contains("experimental wow64"))
            return "Kompatibilitätshinweis: Ein 32-Bit-Prozess läuft im WoW64-Pfad; diese Meldung allein ist kein Absturz.";
        if (lower.contains("nsi:poll_events") && lower.contains("errno 13"))
            return "Wahrscheinlich sekundär: Netzwerk-Socket wurde von Android/Wine abgewiesen; für Einzelspieler meist kein primärer Startblocker.";
        if (lower.contains("secdrv") || lower.contains("safedisc"))
            return "SafeDisc-Treiberpfad prüfen; Yuri enthält eine neuere secdrv.sys-Version.";
        if (lower.contains("registry"))
            return "Westwood-Registry-Einträge prüfen.";
        return "Nicht eindeutig zugeordnet. Maßgeblich ist die Analyse nach Spiel-TID und Startphase.";
    }

    private String configValue(String cfg, String key) {
        if (cfg == null) return "-";
        for (String line : cfg.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ENGLISH).startsWith(key.toLowerCase(Locale.ENGLISH) + "="))
                return trimmed;
        }
        return key + "=<nicht gesetzt>";
    }

    private String value(String text, String prefix) {
        if (text == null) return "";
        for (String line : text.split("\\n")) {
            if (line.startsWith(prefix)) return line.substring(prefix.length()).trim();
        }
        return "";
    }

    private String lastValue(String text, String prefix) {
        if (text == null || text.isEmpty()) return "";
        String result = "";
        for (String line : text.split("\\n")) {
            if (line.startsWith(prefix)) result = line.substring(prefix.length()).trim();
        }
        return result;
    }

    private int parseIntValue(String text, String prefix, int fallback) {
        try { return Integer.parseInt(value(text, prefix)); }
        catch (Exception ignored) { return fallback; }
    }

    private Set<String> readPeImports(File exe) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(exe, "r")) {
            if (raf.length() < 512) throw new Exception("EXE zu klein");
            byte[] mz = new byte[64];
            raf.readFully(mz);
            if ((mz[0] & 0xff) != 'M' || (mz[1] & 0xff) != 'Z') throw new Exception("Kein PE/MZ");
            long peOffset = u32(mz, 0x3c);
            raf.seek(peOffset);
            byte[] sig = new byte[24];
            raf.readFully(sig);
            if (sig[0] != 'P' || sig[1] != 'E') throw new Exception("PE-Signatur fehlt");

            int sections = u16(sig, 6);
            int optSize = u16(sig, 20);
            byte[] opt = new byte[optSize];
            raf.readFully(opt);
            int magic = u16(opt, 0);
            int dataDir = magic == 0x20b ? 112 : 96;
            if (opt.length < dataDir + 16) throw new Exception("PE Optional Header unvollständig");
            long importRva = u32(opt, dataDir + 8);
            if (importRva == 0) return new LinkedHashSet<>();

            long sectionTable = peOffset + 24L + optSize;
            List<long[]> sectionInfo = new ArrayList<>();
            raf.seek(sectionTable);
            for (int i = 0; i < sections; i++) {
                byte[] sh = new byte[40];
                raf.readFully(sh);
                long virtualSize = u32(sh, 8);
                long virtualAddress = u32(sh, 12);
                long rawSize = u32(sh, 16);
                long rawPtr = u32(sh, 20);
                sectionInfo.add(new long[]{virtualAddress, Math.max(virtualSize, rawSize), rawPtr});
            }

            long importOffset = rvaToOffset(importRva, sectionInfo);
            if (importOffset < 0) throw new Exception("Import-RVA nicht abbildbar");
            Set<String> imports = new LinkedHashSet<>();

            for (int i = 0; i < 256; i++) {
                raf.seek(importOffset + i * 20L);
                byte[] d = new byte[20];
                raf.readFully(d);
                long nameRva = u32(d, 12);
                long firstThunk = u32(d, 16);
                if (nameRva == 0 && firstThunk == 0) break;
                long nameOffset = rvaToOffset(nameRva, sectionInfo);
                if (nameOffset < 0) continue;
                String dll = readCString(raf, nameOffset, 160);
                if (!dll.isEmpty()) imports.add(dll);
            }
            return imports;
        }
    }

    private boolean resolveDll(String dll) {
        File game = findIgnoreCase(gameDir(), dll, 1);
        if (game != null) return true;
        File c = new File(container.getRootDir(), ".wine/drive_c/windows");
        return findIgnoreCase(new File(c, "system32"), dll, 2) != null ||
            findIgnoreCase(new File(c, "syswow64"), dll, 2) != null;
    }

    private boolean isSystemDll(String dll) {
        String d = dll.toLowerCase(Locale.ENGLISH);
        return Arrays.asList(
            "kernel32.dll", "user32.dll", "gdi32.dll", "advapi32.dll",
            "ole32.dll", "oleaut32.dll", "shell32.dll", "comdlg32.dll",
            "comctl32.dll", "winmm.dll", "ddraw.dll", "dsound.dll",
            "wsock32.dll", "ws2_32.dll", "version.dll", "imm32.dll",
            "wininet.dll", "shlwapi.dll", "msvcrt.dll", "ntdll.dll"
        ).contains(d);
    }

    private long rvaToOffset(long rva, List<long[]> sections) {
        for (long[] s : sections) {
            if (rva >= s[0] && rva < s[0] + s[1]) return s[2] + (rva - s[0]);
        }
        return -1;
    }

    private String readCString(RandomAccessFile raf, long offset, int max) throws Exception {
        raf.seek(offset);
        byte[] buf = new byte[max];
        int len = 0;
        while (len < max) {
            int b = raf.read();
            if (b <= 0) break;
            buf[len++] = (byte)b;
        }
        return new String(buf, 0, len, StandardCharsets.US_ASCII);
    }

    private int u16(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8);
    }

    private long u32(byte[] b, int o) {
        return Integer.toUnsignedLong((b[o] & 0xff) |
            ((b[o + 1] & 0xff) << 8) |
            ((b[o + 2] & 0xff) << 16) |
            ((b[o + 3] & 0xff) << 24));
    }

    private String humanSize(long bytes) {
        if (bytes < 0) return "unbekannte Größe";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.GERMANY, unit == 0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }

    private int levelColor(Level level) {
        switch (level) {
            case PASS: return Color.rgb(168, 224, 177);
            case WARN: return Color.rgb(245, 206, 112);
            case FAIL: return Color.rgb(255, 126, 112);
            default: return Color.rgb(175, 205, 230);
        }
    }

    private String icon(Level level) {
        switch (level) {
            case PASS: return "✓";
            case WARN: return "⚠";
            case FAIL: return "✖";
            default: return "ℹ";
        }
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        return b;
    }

    private TextView text(int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
