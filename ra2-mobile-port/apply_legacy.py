#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path
import apply as base

read = base.read
write = base.write
require = base.require
replace_once = base.replace_once
insert_before_once = base.insert_before_once
SRC = base.SRC
APP_ID = "com.winlator"

def copy_sources_legacy(root: Path) -> None:
    dst = root / "app/src/main/java/com/winlator"
    dst.mkdir(parents=True, exist_ok=True)
    for name in ["RA2LauncherActivity.java", "RA2TouchDock.java", "RA2DisplayController.java", "Iso9660Extractor.java", "RA2DiagnosticsActivity.java"]:
        require(SRC / name)
        text = read(SRC / name)

        if name == "RA2LauncherActivity.java":
            text = text.replace("import com.winlator.box64.Box64Preset;\n", "import com.winlator.box86_64.Box86_64Preset;\n")
            text = text.replace("import com.winlator.container.GraphicsDrivers;\n", "")
            text = text.replace("import com.winlator.xenvironment.RootFS;\n", "import com.winlator.xenvironment.ImageFs;\n")
            text = text.replace("import com.winlator.xenvironment.RootFSInstaller;\n", "import com.winlator.xenvironment.ImageFsInstaller;\n")
            text = text.replace("RootFS rootFS = RootFS.find(this);", "ImageFs rootFS = ImageFs.find(this);")
            text = text.replace("RootFSInstaller.LATEST_VERSION", "ImageFsInstaller.LATEST_VERSION")
            text = text.replace("RootFSInstaller.installIfNeeded(this);", "ImageFsInstaller.installIfNeeded(this);")
            text = text.replace("GraphicsDrivers.getDefaultDriver(this)", "\"turnip\"")
            text = text.replace('data.put("dxwrapper", "wined3d");', 'data.put("dxwrapper", "cnc-ddraw");')
            text = text.replace('data.put("dxwrapperConfig", "ddrawWrapper=cnc-ddraw");', 'data.put("dxwrapperConfig", "");')
            text = text.replace('data.put("box64Preset", Box64Preset.STABILITY);',
                'data.put("wow64Mode", false);\n            data.put("box86Preset", Box86_64Preset.STABILITY);\n            data.put("box64Preset", Box86_64Preset.STABILITY);')
            text = text.replace('target.setDXWrapper("wined3d");', 'target.setDXWrapper("cnc-ddraw");')
            text = text.replace('target.setDXWrapperConfig("ddrawWrapper=cnc-ddraw");', 'target.setDXWrapperConfig("");')
            text = text.replace('target.setBox64Preset(Box64Preset.STABILITY);',
                'target.setWoW64Mode(false);\n        target.setBox86Preset(Box86_64Preset.STABILITY);\n        target.setBox64Preset(Box86_64Preset.STABILITY);')
            text = text.replace("WineUtils.changeServicesStatus(target, Container.STARTUP_SELECTION_NORMAL);",
                                "WineUtils.changeServicesStatus(target, false);")
            text = text.replace("WineUtils.changeServicesStatus(container, Container.STARTUP_SELECTION_NORMAL);",
                                "WineUtils.changeServicesStatus(container, false);")
            text = text.replace('registry.getSymlinkValue("System\\\\CurrentControlSet", "SymbolicLinkValue")',
                                '"System\\\\CurrentControlSet"')

        elif name == "RA2DiagnosticsActivity.java":
            text = text.replace("import com.winlator.xenvironment.RootFS;\n", "import com.winlator.xenvironment.ImageFs;\n")
            text = text.replace("import com.winlator.xenvironment.RootFSInstaller;\n", "import com.winlator.xenvironment.ImageFsInstaller;\n")
            text = text.replace("RootFS root = RootFS.find(this);", "ImageFs root = ImageFs.find(this);")
            text = text.replace("RootFSInstaller.LATEST_VERSION", "ImageFsInstaller.LATEST_VERSION")
            text = text.replace("Wine/RootFS", "Wine/ImageFS")
            text = text.replace("RootFS ist älter", "ImageFS ist älter")
            text = text.replace("RootFS gültig", "ImageFS gültig")
            text = text.replace("WineUtils.changeServicesStatus(container, Container.STARTUP_SELECTION_NORMAL);",
                                "WineUtils.changeServicesStatus(container, false);")

            text = text.replace("                checkRuntime();\n",
                                "                checkRuntime();\n                checkLegacyX86Mode();\n", 1)

            marker = "    private void checkIsoSlot(String group, String label, String key, boolean required) {"
            legacy = '''    private void checkLegacyX86Mode() {
        if (container == null) return;
        if (!container.isWoW64Mode()) {
            add(Level.PASS, "32-Bit-Laufzeit", "Legacy-x86-Modus aktiv",
                "Wine 9.2 • Box86 • alter 32-Bit-Pfad",
                "GAME.EXE läuft damit nicht mehr über Wines experimentellen neuen WoW64-Pfad.");
        }
        else {
            add(Level.FAIL, "32-Bit-Laufzeit", "WoW64-Modus unerwartet aktiv",
                "container.isWoW64Mode() = true",
                "RA2 benötigt in diesem Build den Legacy-x86-/Box86-Pfad.");
        }

        File imageRoot = ImageFs.find(this).getRootDir();
        File wine32 = new File(imageRoot, "opt/wine/bin/wine");
        File box86 = new File(imageRoot, "usr/local/bin/box86");
        if (wine32.isFile()) {
            add(Level.PASS, "32-Bit-Laufzeit", "Wine-Loader vorhanden",
                wine32.getAbsolutePath(), "");
        }
        else {
            add(Level.FAIL, "32-Bit-Laufzeit", "Wine-Loader fehlt",
                wine32.getAbsolutePath(), "Legacy-Laufzeit unvollständig.");
        }
        if (box86.isFile()) {
            add(Level.PASS, "32-Bit-Laufzeit", "Box86 vorhanden",
                box86.getAbsolutePath(), "");
        }
        else {
            add(Level.FAIL, "32-Bit-Laufzeit", "Box86 fehlt",
                box86.getAbsolutePath(), "32-Bit-x86-Übersetzung fehlt.");
        }
    }

'''
            if marker not in text:
                raise SystemExit("diagnostics marker missing")
            text = text.replace(marker, legacy + marker, 1)

            # In legacy mode the 32-bit service process is expected through the old architecture,
            # but do not hard-fail merely because a separate SysWOW64 rpcss.exe is absent.
            text = text.replace(
                'checkRuntimeBinary("Windows-Dienste", new File(windows, "syswow64/rpcss.exe"), "rpcss.exe (32-Bit/SysWOW64)", false);',
                'checkRuntimeBinary("Windows-Dienste", new File(windows, "syswow64/rpcss.exe"), "rpcss.exe (32-Bit/SysWOW64)", false);'
            )

            # Recognize the exact failure we found in v0.14.
            text = text.replace(
                'boolean rpc = log.contains("rpc_s_server_unavailable") || log.contains("failed to start rpcss");',
                'boolean rpc = log.contains("rpc_s_server_unavailable") || log.contains("failed to start rpcss") || log.contains("receive failed with error 6be");'
            )
            text = text.replace(
                '"sc.exe meldet RpcSs als laufend, das Wine-Log enthält aber RPC_S_SERVER_UNAVAILABLE.",',
                '"sc.exe meldet RpcSs als laufend, das Wine-Log enthält aber einen RPC-Abbruch (6ba/6be).",'
            )

        write(dst / name, text)

def patch_build_gradle(root: Path) -> None:
    p = root / "app/build.gradle"
    require(p)
    s = read(p)
    s = s.replace("android {\n", "android {\n    namespace 'com.winlator'\n", 1)
    s = s.replace("versionCode 16", "versionCode 215")
    s = s.replace('versionName "7.1"', 'versionName "0.15.0-ra2-legacy"')
    s = s.replace("abiFilters 'arm64-v8a', 'armeabi-v7a'", "abiFilters 'arm64-v8a'")
    s = s.replace("    lintOptions {\\n        checkReleaseBuilds false\\n    }\\n",
                  "    lintOptions {\\n        checkReleaseBuilds false\\n    }\\n\\n    aaptOptions {\\n        noCompress 'txz', 'tzst'\\n    }\\n")
    s = s.replace("""    ndkVersion '22.1.7171670'

    externalNativeBuild {
        cmake {
            version '3.22.1'
            path 'src/main/cpp/CMakeLists.txt'
        }
    }
""", """    sourceSets {
        main {
            jniLibs.srcDirs = ['src/main/jniLibs']
        }
    }
""")
    write(p, s)

def patch_manifest(root: Path) -> None:
    p = root / "app/src/main/AndroidManifest.xml"
    require(p)
    s = read(p)

    old_filter = '''            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
'''
    s = s.replace(old_filter, "")
    s = s.replace(
        '<activity android:name="com.winlator.MainActivity"\n            android:theme="@style/AppTheme"\n            android:exported="true"',
        '<activity android:name="com.winlator.MainActivity"\n            android:theme="@style/AppTheme"\n            android:exported="false"'
    )

    launcher = '''        <activity
            android:name="com.winlator.RA2DiagnosticsActivity"
            android:theme="@style/AppThemeFullscreen"
            android:exported="false"
            android:screenOrientation="sensorLandscape"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|density|navigation" />

        <activity
            android:name="com.winlator.RA2LauncherActivity"
            android:theme="@style/AppThemeFullscreen"
            android:exported="true"
            android:screenOrientation="sensorLandscape"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|density|navigation">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

'''
    anchor = '        <activity android:name="com.winlator.MainActivity"'
    if 'android:name="com.winlator.RA2LauncherActivity"' not in s:
        if anchor not in s:
            raise SystemExit("MainActivity manifest anchor missing")
        s = s.replace(anchor, launcher + anchor, 1)
    write(p, s)

def patch_strings(root: Path) -> None:
    p = root / "app/src/main/res/values/strings.xml"
    require(p)
    s = read(p)
    s = s.replace('<string name="app_name">Winlator</string>',
                  '<string name="app_name">C&amp;C Alarmstufe Rot 2 + Yuri</string>')
    write(p, s)

def patch_imagefs_installer(root: Path) -> None:
    p = root / "app/src/main/java/com/winlator/xenvironment/ImageFsInstaller.java"
    require(p)
    s = read(p)
    s = s.replace("private static void installFromAssets(final MainActivity activity)",
                  "private static void installFromAssets(final AppCompatActivity activity)")
    s = s.replace("public static void installIfNeeded(final MainActivity activity)",
                  "public static void installIfNeeded(final AppCompatActivity activity)")
    write(p, s)

def patch_touchpad(root: Path) -> None:
    p = root / "app/src/main/java/com/winlator/widget/TouchpadView.java"
    require(p)
    s = read(p)

    s = replace_once(
        s,
        "    private boolean scrolling = false;\n",
        "    private boolean scrolling = false;\n    private boolean rtsMode = false;\n",
        "legacy RTS field"
    )

    s = replace_once(
        s,
        '''        private boolean isTap() {
            return (System.currentTimeMillis() - touchTime) < MAX_TAP_MILLISECONDS && travelDistance() < MAX_TAP_TRAVEL_DISTANCE;
        }

''',
        '''        private boolean isTap() {
            return (System.currentTimeMillis() - touchTime) < MAX_TAP_MILLISECONDS && travelDistance() < MAX_TAP_TRAVEL_DISTANCE;
        }

        private boolean isLongPress() {
            return (System.currentTimeMillis() - touchTime) >= 450 && travelDistance() < MAX_TAP_TRAVEL_DISTANCE;
        }

''',
        "legacy long press helper"
    )

    s = replace_once(
        s,
        '''                fingers[pointerId] = new Finger(event.getX(actionIndex), event.getY(actionIndex));
                numFingers++;
                break;
''',
        '''                fingers[pointerId] = new Finger(event.getX(actionIndex), event.getY(actionIndex));
                numFingers++;
                if (rtsMode && numFingers == 1) {
                    xServer.injectPointerMove(fingers[pointerId].x, fingers[pointerId].y);
                }
                break;
''',
        "legacy RTS touch down"
    )

    s = replace_once(
        s,
        '''            case 1:
                if (finger1.isTap()) pressPointerButtonLeft(finger1);
                break;
''',
        '''            case 1:
                if (rtsMode && finger1.isLongPress()) {
                    xServer.injectPointerMove(finger1.x, finger1.y);
                    pressPointerButtonRight(finger1);
                }
                else if (finger1.isTap()) {
                    if (rtsMode) xServer.injectPointerMove(finger1.x, finger1.y);
                    pressPointerButtonLeft(finger1);
                }
                break;
''',
        "legacy RTS finger up"
    )

    s = replace_once(
        s,
        '''    private void handleFingerMove(Finger finger1) {
        boolean skipPointerMove = false;
''',
        '''    private void handleFingerMove(Finger finger1) {
        if (rtsMode && numFingers == 1) {
            if (finger1.travelDistance() >= MAX_TAP_TRAVEL_DISTANCE &&
                    !xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT)) {
                xServer.injectPointerMove(finger1.startX, finger1.startY);
                pressPointerButtonLeft(finger1);
            }
            if (xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT)) {
                xServer.injectPointerMove(finger1.x, finger1.y);
            }
            return;
        }

        boolean skipPointerMove = false;
''',
        "legacy RTS drag selection"
    )

    setter = '''    public void setRtsMode(boolean enabled) {
        rtsMode = enabled;
    }

'''
    anchor = "    public void setSensitivity(float sensitivity) {\n"
    if setter not in s:
        if anchor not in s:
            raise SystemExit("touchpad setter anchor missing")
        s = s.replace(anchor, setter + anchor, 1)

    write(p, s)

def patch_xserver(root: Path) -> None:
    p = root / "app/src/main/java/com/winlator/XServerDisplayActivity.java"
    require(p)
    s = read(p)

    s = replace_once(
        s,
        "import com.winlator.xserver.Property;\n",
        "import com.winlator.xserver.Property;\nimport com.winlator.xserver.Pointer;\n",
        "legacy Pointer import"
    )

    s = replace_once(
        s,
        "    private TouchpadView touchpadView;\n",
        "    private TouchpadView touchpadView;\n    private RA2DisplayController ra2DisplayController;\n    private RA2TouchDock ra2TouchDock;\n    private volatile boolean ra2GameWindowMapped = false;\n    private volatile boolean ra2AutoExitRequested = false;\n",
        "legacy RA2 fields"
    )

    s = replace_once(
        s,
        '''        touchpadView = new TouchpadView(this, xServer);
        touchpadView.setSensitivity(globalCursorSpeed);
''',
        '''        touchpadView = new TouchpadView(this, xServer);
        touchpadView.setSensitivity(globalCursorSpeed);
        boolean ra2Mode = getIntent().getBooleanExtra("ra2_mode", false);
        touchpadView.setRtsMode(ra2Mode);
''',
        "legacy RA2 touch mode"
    )

    setup = '''        if (ra2Mode) {
            boolean german = !"en".equals(getIntent().getStringExtra("ra2_language"));
            ra2DisplayController = new RA2DisplayController(this, xServerView, touchpadView,
                percent -> {
                    if (ra2TouchDock != null) ra2TouchDock.setZoomPercent(percent);
                });
            ra2TouchDock = new RA2TouchDock(this, rootView, german, new RA2TouchDock.Callbacks() {
                @Override public void onBack() {
                    XServerDisplayActivity.this.onBackPressed();
                }

                @Override public void onRightClick() {
                    xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT);
                    rootView.postDelayed(() ->
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT), 45);
                }

                @Override public void onZoomOut() { ra2DisplayController.zoomOut(); }
                @Override public void onZoomReset() { ra2DisplayController.reset(); }
                @Override public void onZoomIn() { ra2DisplayController.zoomIn(); }
            });
            ra2TouchDock.setZoomPercent(ra2DisplayController.getPercent());
            rootView.setOnApplyWindowInsetsListener((view, insets) -> {
                ra2TouchDock.applyInsets(insets);
                return insets;
            });
        }

'''
    s = insert_before_once(
        s,
        "        AppUtils.observeSoftKeyboardVisibility(drawerLayout, renderer::setScreenOffsetYRelativeToCursor);\n",
        setup,
        "new RA2TouchDock(",
        "legacy RA2 in-game UI"
    )

    dispatch = '''    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (ra2DisplayController != null && ra2DisplayController.onDispatchTouchEvent(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

'''
    s = insert_before_once(
        s,
        "    @Override\n    public boolean dispatchGenericMotionEvent(MotionEvent event) {\n",
        dispatch,
        "ra2DisplayController.onDispatchTouchEvent",
        "legacy pinch dispatcher"
    )

    # Mark a real non-explorer application window. This becomes our game-start signal.
    s = replace_once(
        s,
        '''            @Override
            public void onMapWindow(Window window) {
                assignTaskAffinity(window);
            }
''',
        '''            @Override
            public void onMapWindow(Window window) {
                assignTaskAffinity(window);
                if (getIntent().getBooleanExtra("ra2_game_launch", false) &&
                        window.isApplicationWindow()) {
                    String cls = window.getClassName() == null ? "" : window.getClassName().toLowerCase();
                    if (!cls.contains("explorer")) {
                        ra2GameWindowMapped = true;
                        appendRa2Log(new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/ra2-live.log"),
                            "stage=game-window-mapped\\nclass=" + window.getClassName() + "\\n");
                        getSharedPreferences("ra2_mobile", MODE_PRIVATE).edit()
                            .putBoolean("last_saw_child", true)
                            .putString("last_diag_clue", "")
                            .apply();
                    }
                }
            }
''',
        "legacy map-window monitor"
    )

    # Replace launcher callback with diagnostic capture for game launches.
    s = replace_once(
        s,
        '''        guestProgramLauncherComponent.setEnvVars(envVars);
        guestProgramLauncherComponent.setTerminationCallback((status) -> exit());
        environment.addComponent(guestProgramLauncherComponent);
''',
        '''        guestProgramLauncherComponent.setEnvVars(envVars);
        if (getIntent().getBooleanExtra("ra2_game_launch", false)) {
            final long ra2StartedAt = System.currentTimeMillis();
            final StringBuilder ra2Debug = new StringBuilder();
            final File ra2LiveLog = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/ra2-live.log");
            File parent = ra2LiveLog.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            FileUtils.writeString(ra2LiveLog,
                "stage=legacy-x86-start\\nstartedAt=" + ra2StartedAt +
                "\\nwow64Mode=" + container.isWoW64Mode() + "\\n");
            envVars.put("WINEDEBUG", "+seh,+process,+loaddll,+service,+rpc,+ole");
            ProcessHelper.addDebugCallback((line) -> {
                synchronized (ra2Debug) {
                    if (ra2Debug.length() > 131072) {
                        ra2Debug.delete(0, Math.min(32768, ra2Debug.length()));
                    }
                    ra2Debug.append(line).append("\\n");
                }
                appendRa2Log(ra2LiveLog, line + "\\n");
            });
            guestProgramLauncherComponent.setTerminationCallback((status) ->
                finishRa2LegacyLaunch(status, ra2StartedAt, ra2Debug, ra2LiveLog));
        }
        else {
            guestProgramLauncherComponent.setTerminationCallback((status) -> exit());
        }
        environment.addComponent(guestProgramLauncherComponent);
''',
        "legacy RA2 debug callback"
    )

    helpers = '''    private String legacyUnixToDOSPath(String unixPath) {
        if (unixPath == null || unixPath.isEmpty()) return "";
        File driveC = new File(container.getRootDir(), ".wine/drive_c");
        String base = driveC.getAbsolutePath();
        if (unixPath.startsWith(base)) {
            String tail = unixPath.substring(base.length()).replace('/', '\\\\');
            return "C:" + tail;
        }
        return "Z:" + unixPath.replace('/', '\\\\');
    }

    private void finishRa2LegacyLaunch(int status, long startedAt, StringBuilder debug, File liveLog) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - startedAt);
        StringBuilder output = new StringBuilder();
        output.append("stage=legacy-launch-terminated\\n");
        output.append("exit=").append(status).append("\\n");
        output.append("elapsedMs=").append(elapsed).append("\\n");
        output.append("gameWindowMapped=").append(ra2GameWindowMapped).append("\\n");
        output.append("wow64Mode=").append(container != null && container.isWoW64Mode()).append("\\n");
        synchronized (debug) {
            output.append(debug);
        }
        File logFile = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/last-start.log");
        File parent = logFile.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        FileUtils.writeString(logFile, output.toString());

        String clue = "";
        synchronized (debug) {
            String[] lines = debug.toString().split("\\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                String lower = line.toLowerCase();
                if (lower.contains("err:") || lower.contains("exception") ||
                    lower.contains("failed") || lower.contains("cannot") ||
                    lower.contains("missing") || lower.contains("not found")) {
                    clue = line.length() > 220 ? line.substring(0, 220) : line;
                    break;
                }
            }
        }

        getSharedPreferences("ra2_mobile", MODE_PRIVATE).edit()
            .putInt("last_exit_code", status)
            .putBoolean("last_saw_child", ra2GameWindowMapped)
            .putString("last_diag_clue", clue)
            .apply();
        ProcessHelper.removeAllDebugCallbacks();
        requestRa2Exit();
    }

    private void appendRa2Log(File file, String value) {
        if (file == null || value == null) return;
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file, true)) {
            out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
        }
        catch (Exception ignored) {}
    }

    private long[] helperOutputSizes(String stage) {
        File dir = new File(container.getRootDir(), ".wine/drive_c/Westwood/RA2");
        if ("ra2_cab".equals(stage)) {
            return new long[]{new File(dir, "ra2.mix").length(), new File(dir, "language.mix").length()};
        }
        if ("yuri_cab".equals(stage)) {
            return new long[]{new File(dir, "ra2md.mix").length(), new File(dir, "langmd.mix").length()};
        }
        return new long[]{0, 0};
    }

    private void startRa2Watchdog() {
        if (!getIntent().getBooleanExtra("ra2_mode", false)) return;
        final boolean gameLaunch = getIntent().getBooleanExtra("ra2_game_launch", false);
        final String helperStage = getIntent().getStringExtra("ra2_helper_stage");
        final File liveLog = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/ra2-live.log");

        Executors.newSingleThreadExecutor().execute(() -> {
            long started = System.currentTimeMillis();
            long lastA = -1;
            long lastB = -1;
            int stable = 0;

            while (!ra2AutoExitRequested) {
                if (gameLaunch) {
                    if (ra2GameWindowMapped) return;
                    if (System.currentTimeMillis() - started >= 30000L) {
                        appendRa2Log(liveLog, "stage=target-not-seen\\n");
                        File last = new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/last-start.log");
                        FileUtils.writeString(last,
                            "stage=target-not-seen\\nexit=0\\nelapsedMs=" +
                            (System.currentTimeMillis() - started) +
                            "\\ngameWindowMapped=false\\nwow64Mode=" + container.isWoW64Mode() + "\\n");
                        getSharedPreferences("ra2_mobile", MODE_PRIVATE).edit()
                            .putBoolean("last_saw_child", false)
                            .putString("last_diag_clue", "Kein RA2-Spiel-Fenster innerhalb von 30 s erkannt.")
                            .apply();
                        requestRa2Exit();
                        return;
                    }
                }
                else if (helperStage != null && !helperStage.isEmpty()) {
                    long[] sizes = helperOutputSizes(helperStage);
                    if (sizes[0] > 0 && sizes[1] > 0 &&
                        sizes[0] == lastA && sizes[1] == lastB) stable++;
                    else stable = 0;
                    lastA = sizes[0];
                    lastB = sizes[1];

                    if (stable >= 4) {
                        appendRa2Log(liveLog, "stage=helper-complete\\n");
                        requestRa2Exit();
                        return;
                    }
                }

                try { Thread.sleep(500L); }
                catch (InterruptedException ignored) { return; }
            }
        });
    }

    private void requestRa2Exit() {
        if (ra2AutoExitRequested) return;
        ra2AutoExitRequested = true;
        runOnUiThread(this::exit);
    }

'''
    s = insert_before_once(
        s,
        "    private boolean isGenerateWineprefix() {\n",
        helpers,
        "finishRa2LegacyLaunch(int status",
        "legacy RA2 helper methods"
    )

    # Direct execution path for our launcher.
    old = '''        String args = "";
        if (shortcut != null) {
'''
    new = '''        String args = "";
        Intent directIntent = getIntent();
        if (directIntent.hasExtra("exec_dos_path")) {
            String directPath = directIntent.getStringExtra("exec_dos_path");
            String directArgs = directIntent.getStringExtra("exec_args");
            int slash = directPath == null ? -1 : Math.max(directPath.lastIndexOf('\\\\'), directPath.lastIndexOf('/'));
            String directDir = slash >= 0 ? directPath.substring(0, slash) : "C:\\\\";
            String directFile = slash >= 0 ? directPath.substring(slash + 1) : directPath;
            args += "/dir " + directDir.replace(" ", "\\\\ ") + " \\\"" + directFile + "\\\"";
            if (directArgs != null && !directArgs.isEmpty()) args += " " + directArgs;
        }
        else if (directIntent.hasExtra("exec_path")) {
            String dosPath = legacyUnixToDOSPath(directIntent.getStringExtra("exec_path"));
            int slash = dosPath == null ? -1 : dosPath.lastIndexOf('\\\\');
            String directDir = slash >= 0 ? dosPath.substring(0, slash) : "C:\\\\";
            String directFile = slash >= 0 ? dosPath.substring(slash + 1) : dosPath;
            args += "/dir " + directDir.replace(" ", "\\\\ ") + " \\\"" + directFile + "\\\"";
        }
        else if (shortcut != null) {
'''
    s = replace_once(s, old, new, "legacy direct execution")

    # Prepare local CNC-DDraw before graphics files and start watchdog once X environment is live.
    s = replace_once(
        s,
        '''                setupWineSystemFiles();
                extractGraphicsDriverFiles();
''',
        '''                setupWineSystemFiles();
                if (getIntent().getBooleanExtra("ra2_game_launch", false)) prepareLegacyRa2Files();
                extractGraphicsDriverFiles();
''',
        "legacy pre-launch prepare hook"
    )

    legacy_prepare = '''    private void prepareLegacyRa2Files() {
        if (container == null) return;
        String execPath = getIntent().getStringExtra("exec_path");
        if (execPath == null || execPath.isEmpty()) return;

        File dir = new File(FileUtils.getDirname(execPath));
        File globalIni = new File(container.getRootDir(), ".wine/drive_c/ProgramData/cnc-ddraw/ddraw.ini");
        if (globalIni.isFile()) {
            String cfg = FileUtils.readString(globalIni);
            if (cfg != null) {
                cfg = setLegacyCncValue(cfg, "renderer", "opengl");
                cfg = setLegacyCncValue(cfg, "windowed", "false");
                cfg = setLegacyCncValue(cfg, "fullscreen", "true");
                cfg = setLegacyCncValue(cfg, "singlecpu", "true");
                cfg = setLegacyCncValue(cfg, "maxfps", "60");
                FileUtils.writeString(globalIni, cfg);
                FileUtils.copy(globalIni, new File(dir, "ddraw.ini"));
            }
        }
        appendRa2Log(new File(container.getRootDir(), ".wine/drive_c/RA2Mobile/ra2-live.log"),
            "stage=legacy-runtime-wrapper-ready\\n");
    }

    private String setLegacyCncValue(String cfg, String key, String value) {
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
            out.append("\\n");
        }
        if (!replaced) out.append(key).append("=").append(value).append("\\n");
        return out.toString();
    }

'''
    s = insert_before_once(
        s,
        "    private void setupXEnvironment() {\n",
        legacy_prepare,
        "private void prepareLegacyRa2Files()",
        "legacy CNC prepare"
    )

    s = replace_once(
        s,
        '''        if (isGenerateWineprefix()) generateWineprefix();
        environment.startEnvironmentComponents();

        winHandler.start();
''',
        '''        if (isGenerateWineprefix()) generateWineprefix();
        environment.startEnvironmentComponents();
        if (getIntent().getBooleanExtra("ra2_mode", false)) startRa2Watchdog();

        winHandler.start();
''',
        "legacy watchdog start"
    )

    s = insert_before_once(
        s,
        "    public InputControlsView getInputControlsView() {\\n",
        '''    public SharedPreferences getPreferences() {
        return preferences;
    }

''',
        "public SharedPreferences getPreferences()",
        "legacy preferences accessor"
    )

    write(p, s)

def patch_winhandler_compat(root: Path) -> None:
    p = root / "app/src/main/java/com/winlator/winhandler/WinHandler.java"
    require(p)
    s = read(p)
    s = s.replace("    private boolean initReceived = false;", "    boolean initReceived = false;")
    s = s.replace("    private final XServerDisplayActivity activity;", "    final XServerDisplayActivity activity;")
    s = s.replace("    private void addAction(Runnable action) {", "    void addAction(Runnable action) {")

    anchor = "    public void exec(String command) {\\n"
    overload = '''    boolean sendPacket(int port, byte[] data) {
        if (data == null) return false;
        try {
            DatagramPacket packet = new DatagramPacket(data, data.length);
            packet.setAddress(localhost);
            packet.setPort(port);
            socket.send(packet);
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

'''
    if "boolean sendPacket(int port, byte[] data)" not in s:
        if anchor not in s:
            raise SystemExit("WinHandler send overload anchor missing")
        s = s.replace(anchor, overload + anchor, 1)
    write(p, s)

def validate(root: Path) -> None:
    expected = {
        root / "app/src/main/java/com/winlator/RA2LauncherActivity.java": "setWoW64Mode(false)",
        root / "app/src/main/java/com/winlator/RA2DiagnosticsActivity.java": "Legacy-x86-Modus aktiv",
        root / "app/src/main/java/com/winlator/XServerDisplayActivity.java": "legacy-x86-start",
        root / "app/src/main/java/com/winlator/widget/TouchpadView.java": "setRtsMode",
        root / "app/src/main/AndroidManifest.xml": "RA2LauncherActivity",
    }
    for path, marker in expected.items():
        require(path)
        if marker not in read(path):
            raise SystemExit(f"Validation marker {marker!r} missing from {path}")

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("upstream", type=Path)
    args = parser.parse_args()
    root = args.upstream.resolve()

    copy_sources_legacy(root)
    patch_build_gradle(root)
    patch_manifest(root)
    patch_strings(root)
    patch_imagefs_installer(root)
    patch_touchpad(root)
    patch_xserver(root)
    patch_winhandler_compat(root)
    validate(root)
    print("RA2/Yuri legacy x86 Android patch applied successfully.")

if __name__ == "__main__":
    main()
