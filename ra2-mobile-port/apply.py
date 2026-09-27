#!/usr/bin/env python3
from __future__ import annotations

import argparse
import shutil
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "src"
APP_ID = "com.winlator"

def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")

def write(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8", newline="\n")

def require(path: Path) -> None:
    if not path.exists():
        raise SystemExit(f"Required upstream file missing: {path}")

def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Patch anchor {label!r} expected once, found {count}")
    return text.replace(old, new, 1)

def insert_before_once(text: str, anchor: str, insertion: str, marker: str, label: str) -> str:
    if marker in text:
        return text
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(f"Patch anchor {label!r} expected once, found {count}")
    return text.replace(anchor, insertion + anchor, 1)

def copy_sources(root: Path) -> None:
    dst = root / "app/src/main/java/com/winlator"
    dst.mkdir(parents=True, exist_ok=True)
    for name in ["RA2LauncherActivity.java", "RA2TouchDock.java", "RA2DisplayController.java", "Iso9660Extractor.java", "RA2DiagnosticsActivity.java"]:
        require(SRC / name)
        shutil.copy2(SRC / name, dst / name)

def patch_build_gradle(root: Path) -> None:
    p = root / "app/build.gradle"
    require(p)
    s = read(p)
    s = s.replace("applicationId 'com.winlator'", f"applicationId '{APP_ID}'")
    s = s.replace("versionCode 33", "versionCode 211")
    s = s.replace('versionName "11.2"', 'versionName "0.11.0-ra2"')
    write(p, s)

def patch_package_paths(root: Path) -> None:
    replacements = {
        root / "app/src/main/cpp/winlator/include/winlator.h":
            ('"/data/data/com.winlator/cache"', f'"/data/data/{APP_ID}/cache"'),
        root / "app/src/main/cpp/vortekrenderer/include/vortek.h":
            ('"/data/data/com.winlator/files/rootfs/tmp/.vortek/V0"', f'"/data/data/{APP_ID}/files/rootfs/tmp/.vortek/V0"'),
        root / "app/src/main/cpp/gladiorenderer/include/gladio.h":
            ('"/data/data/com.winlator/files/rootfs/tmp/.X11-unix/X0"', f'"/data/data/{APP_ID}/files/rootfs/tmp/.X11-unix/X0"'),
        root / "app/src/main/java/com/winlator/core/AppUtils.java":
            ('"/data/data/com.winlator/storage"', f'"/data/data/{APP_ID}/storage"'),
        root / "app/src/main/java/com/winlator/core/FileUtils.java":
            ('"com.winlator.FileProvider"', f'"{APP_ID}.FileProvider"'),
    }
    for p, (old, new) in replacements.items():
        require(p)
        s = read(p)
        if new not in s:
            if old not in s:
                raise SystemExit(f"Package-path anchor missing in {p}")
            s = s.replace(old, new)
        write(p, s)

def patch_manifest(root: Path) -> None:
    p = root / "app/src/main/AndroidManifest.xml"
    require(p)
    s = read(p)
    s = s.replace('android:authorities="com.winlator.FileProvider"', f'android:authorities="{APP_ID}.FileProvider"')

    old_filter = '''            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
'''
    s = s.replace(old_filter, "")
    s = s.replace(
        '<activity android:name="com.winlator.MainActivity"\n            android:theme="@style/AppThemeDark"\n            android:exported="true"',
        '<activity android:name="com.winlator.MainActivity"\n            android:theme="@style/AppThemeDark"\n            android:exported="false"'
    )

    launcher = '''        <activity
            android:name="com.winlator.RA2DiagnosticsActivity"
            android:theme="@style/AppThemeFullscreenDark"
            android:exported="false"
            android:screenOrientation="sensorLandscape"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|density|navigation" />

        <activity
            android:name="com.winlator.RA2LauncherActivity"
            android:theme="@style/AppThemeFullscreenDark"
            android:exported="true"
            android:screenOrientation="sensorLandscape"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|density|navigation">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

'''
    if 'android:name="com.winlator.RA2LauncherActivity"' not in s:
        anchor = '        <activity android:name="com.winlator.MainActivity"'
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

def patch_rootfs_installer(root: Path) -> None:
    p = root / "app/src/main/java/com/winlator/xenvironment/RootFSInstaller.java"
    require(p)
    s = read(p)
    s = s.replace("public static void install(final MainActivity activity)",
                  "public static void install(final AppCompatActivity activity)")
    s = s.replace("public static void installIfNeeded(final MainActivity activity)",
                  "public static void installIfNeeded(final AppCompatActivity activity)")
    write(p, s)

def patch_touchpad(root: Path) -> None:
    p = root / "app/src/main/java/com/winlator/widget/TouchpadView.java"
    require(p)
    s = read(p)

    s = replace_once(
        s,
        "    private boolean moveCursorToTouchpoint = false;\n",
        "    private boolean moveCursorToTouchpoint = false;\n    private boolean rtsMode = false;\n",
        "RTS field"
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
        "long press helper"
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
        "RTS touch down"
    )

    s = replace_once(
        s,
        '''            case 1:
                if (finger1.isTap()) {
                    if (moveCursorToTouchpoint) xServer.injectPointerMove(finger1.x, finger1.y);
                    pressPointerButtonLeft(finger1);
                }
                break;
''',
        '''            case 1:
                if (rtsMode && finger1.isLongPress()) {
                    xServer.injectPointerMove(finger1.x, finger1.y);
                    pressPointerButtonRight(finger1);
                }
                else if (finger1.isTap()) {
                    if (moveCursorToTouchpoint || rtsMode) xServer.injectPointerMove(finger1.x, finger1.y);
                    pressPointerButtonLeft(finger1);
                }
                break;
''',
        "RTS long press"
    )

    s = replace_once(
        s,
        '''    private void handleFingerMove(Finger finger1) {
        if (!isEnabled()) return;
        boolean skipPointerMove = false;
''',
        '''    private void handleFingerMove(Finger finger1) {
        if (!isEnabled()) return;

        if (rtsMode && numFingers == 1) {
            if (finger1.travelDistance() >= MAX_TAP_TRAVEL_DISTANCE &&
                    !xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT)) {
                pressPointerButtonLeft(finger1);
            }
            if (xServer.pointer.isButtonPressed(Pointer.Button.BUTTON_LEFT)) {
                xServer.injectPointerMove(finger1.x, finger1.y);
            }
            return;
        }

        boolean skipPointerMove = false;
''',
        "RTS drag selection"
    )

    setter = '''    public void setRtsMode(boolean enabled) {
        rtsMode = enabled;
        if (enabled) moveCursorToTouchpoint = true;
    }

'''
    s = insert_before_once(
        s,
        "    public boolean isMoveCursorToTouchpoint() {\n",
        setter,
        "setRtsMode(boolean enabled)",
        "RTS setter"
    )
    write(p, s)

def patch_xserver(root: Path) -> None:
    p = root / "app/src/main/java/com/winlator/XServerDisplayActivity.java"
    require(p)
    s = read(p)

    s = replace_once(
        s,
        "import com.winlator.xserver.Property;\n",
        "import com.winlator.xserver.Property;\nimport com.winlator.xserver.Pointer;\n",
        "Pointer import"
    )

    s = replace_once(
        s,
        "    private TouchpadView touchpadView;\n",
        "    private TouchpadView touchpadView;\n    private RA2DisplayController ra2DisplayController;\n    private RA2TouchDock ra2TouchDock;\n",
        "RA2 controller fields"
    )

    s = replace_once(
        s,
        '''        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setMoveCursorToTouchpoint(preferences.getBoolean("move_cursor_to_touchpoint", false));
''',
        '''        touchpadView.setSensitivity(globalCursorSpeed);
        boolean ra2Mode = getIntent().getBooleanExtra("ra2_mode", false);
        touchpadView.setMoveCursorToTouchpoint(ra2Mode || preferences.getBoolean("move_cursor_to_touchpoint", false));
        touchpadView.setRtsMode(ra2Mode);
''',
        "RA2 touch mode"
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

                @Override public void onZoomOut() {
                    ra2DisplayController.zoomOut();
                }

                @Override public void onZoomReset() {
                    ra2DisplayController.reset();
                }

                @Override public void onZoomIn() {
                    ra2DisplayController.zoomIn();
                }
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
        "        if (MainActivity.DEBUG_MODE) rootView.addView(AppUtils.createDebugMsgTextView(this));\n",
        setup,
        "new RA2TouchDock(",
        "RA2 in-game UI"
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
        "pinch dispatcher"
    )


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
            final File ra2LiveLog = new File(rootFS.getRootDir(), RootFS.WINEPREFIX+"/drive_c/RA2Mobile/ra2-live.log");
            File ra2LogParent = ra2LiveLog.getParentFile();
            if (ra2LogParent != null && !ra2LogParent.isDirectory()) ra2LogParent.mkdirs();
            FileUtils.writeString(ra2LiveLog,
                "stage=wine-debug-attached\\nstartedAt=" + ra2StartedAt + "\\n");

            envVars.put("WINEDEBUG", "+seh,+process,+loaddll,+service,+winedevice,+ntoskrnl");
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
                finishRa2GameLaunch(status, ra2StartedAt, ra2Debug, ra2LiveLog));
        }
        else {
            guestProgramLauncherComponent.setTerminationCallback((status) -> exit());
        }
        environment.addComponent(guestProgramLauncherComponent);
''',
        "RA2 detached game lifetime"
    )

    ra2_helpers = '''
    private void finishRa2GameLaunch(int status, long startedAt, StringBuilder debug, File liveLog) {
        getSharedPreferences("ra2_mobile", MODE_PRIVATE).edit()
            .putInt("last_exit_code", status)
            .apply();
        appendRa2Log(liveLog, "launcherExit=" + status + "\\n");

        Executors.newSingleThreadExecutor().execute(() -> {
            boolean sawGameChild = false;
            long noChildDeadline = System.currentTimeMillis() + 12000L;
            int snapshots = 0;

            while (true) {
                boolean alive = isRa2GuestProcessAlive();
                if (alive) sawGameChild = true;

                if ((snapshots++ % 4) == 0) {
                    appendRa2Log(liveLog, "processes=" + ra2ProcessSnapshot() + "\\n");
                }

                if (sawGameChild && !alive) break;
                if (!sawGameChild && System.currentTimeMillis() >= noChildDeadline) break;

                try {
                    Thread.sleep(500L);
                }
                catch (InterruptedException ignored) {
                    break;
                }
            }

            long elapsed = Math.max(0L, System.currentTimeMillis() - startedAt);
            StringBuilder output = new StringBuilder();
            output.append("stage=launcher-terminated\\n");
            output.append("exit=").append(status).append("\\n");
            output.append("elapsedMs=").append(elapsed).append("\\n");
            output.append("sawGameChild=").append(sawGameChild).append("\\n");
            output.append("processes=").append(ra2ProcessSnapshot()).append("\\n");
            synchronized (debug) {
                output.append(debug);
            }

            File logFile = new File(rootFS.getRootDir(), RootFS.WINEPREFIX+"/drive_c/RA2Mobile/last-start.log");
            File parent = logFile.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            FileUtils.writeString(logFile, output.toString());
            appendRa2Log(liveLog,
                "stage=launcher-terminated\\nexit=" + status +
                "\\nelapsedMs=" + elapsed +
                "\\nsawGameChild=" + sawGameChild + "\\n");

            String clue = "";
            synchronized (debug) {
                String[] lines = debug.toString().split("\\n");
                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i].trim();
                    String lower = line.toLowerCase();
                    if (lower.contains("err:") || lower.contains("exception") ||
                        lower.contains("failed") || lower.contains("cannot") ||
                        lower.contains("missing") || lower.contains("not found") ||
                        lower.contains("secdrv") || lower.contains("safedisc")) {
                        clue = line.length() > 220 ? line.substring(0, 220) : line;
                        break;
                    }
                }
            }

            getSharedPreferences("ra2_mobile", MODE_PRIVATE).edit()
                .putInt("last_exit_code", status)
                .putBoolean("last_saw_child", sawGameChild)
                .putString("last_diag_clue", clue)
                .apply();

            ProcessHelper.removeAllDebugCallbacks();
            runOnUiThread(this::exit);
        });
    }

    private void appendRa2Log(File file, String value) {
        if (file == null || value == null) return;
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file, true)) {
            out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
        }
        catch (Exception ignored) {}
    }

    private String ra2ProcessSnapshot() {
        StringBuilder out = new StringBuilder();
        for (ProcessHelper.PStat process : ProcessHelper.getChildProcesses()) {
            if (!process.guestProcess) continue;
            if (out.length() > 0) out.append(" | ");
            out.append(process.pid).append(":")
               .append(process.shortName).append("/")
               .append(process.name).append("/")
               .append(process.state);
            if (out.length() > 700) break;
        }
        return out.length() == 0 ? "<none>" : out.toString();
    }

    private boolean isRa2GuestProcessAlive() {
        for (ProcessHelper.PStat process : ProcessHelper.getChildProcesses()) {
            if (process.state == ProcessHelper.PState.DEAD ||
                process.state == ProcessHelper.PState.ZOMBIE ||
                process.state == ProcessHelper.PState.STOPPED) {
                continue;
            }

            String name = ((process.name == null ? "" : process.name) + " " +
                (process.shortName == null ? "" : process.shortName)).toLowerCase();
            if (name.contains("game.exe") || name.contains("gamemd.exe") ||
                name.contains("ra2.exe") || name.contains("ra2md.exe") ||
                name.contains("yuri.exe")) {
                return true;
            }
        }
        return false;
    }

    private void prepareRa2RuntimeWrapper() {
        if (!getIntent().getBooleanExtra("ra2_game_launch", false)) return;

        String execPath = getIntent().getStringExtra("exec_path");
        if (execPath == null || execPath.isEmpty()) return;

        File gameDir = new File(FileUtils.getDirname(execPath));
        if (!gameDir.isDirectory()) return;

        File winePrefix = new File(rootFS.getRootDir(), RootFS.WINEPREFIX);
        File systemDdraw = new File(winePrefix, "drive_c/windows/syswow64/ddraw.dll");
        File localDdraw = new File(gameDir, "ddraw.dll");
        if (systemDdraw.isFile()) FileUtils.copy(systemDdraw, localDdraw);

        File globalIni = new File(winePrefix, "drive_c/ProgramData/cnc-ddraw/ddraw.ini");
        if (globalIni.isFile()) {
            String cfg = FileUtils.readString(globalIni);
            if (cfg != null && !cfg.isEmpty()) {
                cfg = setRa2CncValue(cfg, "renderer", "opengl");
                cfg = setRa2CncValue(cfg, "windowed", "false");
                cfg = setRa2CncValue(cfg, "fullscreen", "true");
                cfg = setRa2CncValue(cfg, "nonexclusive", "true");
                cfg = setRa2CncValue(cfg, "singlecpu", "true");
                cfg = setRa2CncValue(cfg, "maxfps", "60");
                cfg = setRa2CncValue(cfg, "adjmouse", "true");
                cfg = setRa2CncValue(cfg, "maintas", "true");
                FileUtils.writeString(globalIni, cfg);
                FileUtils.copy(globalIni, new File(gameDir, "ddraw.ini"));
            }
        }

        File userReg = new File(winePrefix, "user.reg");
        try (com.winlator.core.WineRegistryEditor registry = new com.winlator.core.WineRegistryEditor(userReg)) {
            registry.setStringValue("Software\\\\Wine\\\\DllOverrides", "ddraw", "native,builtin");
        }
        catch (Exception ignored) {}

        File liveLog = new File(winePrefix, "drive_c/RA2Mobile/ra2-live.log");
        appendRa2Log(liveLog,
            "stage=runtime-wrapper-ready\\n" +
            "winePrefix=" + winePrefix.getAbsolutePath() + "\\n" +
            "execPath=" + execPath + "\\n" +
            "gameDir=" + gameDir.getAbsolutePath() + "\\n" +
            "systemDdraw=" + systemDdraw.isFile() + ":" + systemDdraw.length() + "\\n" +
            "localDdraw=" + localDdraw.isFile() + ":" + localDdraw.length() + "\\n" +
            "cncIni=" + globalIni.isFile() + ":" + globalIni.length() + "\\n");
    }

    private String setRa2CncValue(String cfg, String key, String value) {
        String[] lines = cfg.split("\\\\n", -1);
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
                else {
                    out.append(line);
                }
            }
            else {
                out.append(line);
            }
            out.append("\\n");
        }
        if (!replaced) out.append(key).append("=").append(value).append("\\n");
        return out.toString();
    }

'''
    s = insert_before_once(
        s,
        "    private String getWineStartCommand() {\n",
        ra2_helpers,
        "finishRa2GameLaunch(int status",
        "RA2 lifetime helpers"
    )

    old = '''        if (shortcut != null) {
            execArgs = shortcut.getExtra("execArgs");
'''
    new = '''        Intent directIntent = getIntent();
        if (directIntent.hasExtra("exec_dos_path")) {
            String directPath = directIntent.getStringExtra("exec_dos_path");
            String directArgs = directIntent.getStringExtra("exec_args");
            if (directPath != null && !directPath.isEmpty()) {
                int slash = Math.max(directPath.lastIndexOf('\\\\'), directPath.lastIndexOf('/'));
                String directDir = slash >= 0 ? directPath.substring(0, slash) : "C:\\\\";
                String directFile = slash >= 0 ? directPath.substring(slash + 1) : directPath;
                cmdArgs = "/dir " + StringUtils.escapeDOSPath(directDir) + " \\\"" + directFile + "\\\"";
                if (directArgs != null && !directArgs.isEmpty()) cmdArgs += " " + directArgs;
            }
        }
        else if (shortcut != null) {
            execArgs = shortcut.getExtra("execArgs");
'''
    s = replace_once(s, old, new, "direct DOS execution")
    s = replace_once(
        s,
        '''                setupWineSystemFiles();
                extractGraphicsDriverFiles();
''',
        '''                setupWineSystemFiles();
                if (getIntent().getBooleanExtra("ra2_game_launch", false)) prepareRa2RuntimeWrapper();
                extractGraphicsDriverFiles();
''',
        "RA2 runtime wrapper timing"
    )

    write(p, s)

def validate(root: Path) -> None:
    checks = {
        "launcher": root / "app/src/main/java/com/winlator/RA2LauncherActivity.java",
        "dock": root / "app/src/main/java/com/winlator/RA2TouchDock.java",
        "zoom": root / "app/src/main/java/com/winlator/RA2DisplayController.java",
        "iso": root / "app/src/main/java/com/winlator/Iso9660Extractor.java",
        "diagnostics": root / "app/src/main/java/com/winlator/RA2DiagnosticsActivity.java",
    }
    for label, path in checks.items():
        require(path)
        if path.stat().st_size < 500:
            raise SystemExit(f"{label} source unexpectedly small")

    expected = {
        root / "app/build.gradle": APP_ID,
        root / "app/src/main/AndroidManifest.xml": "RA2LauncherActivity",
        root / "app/src/main/java/com/winlator/XServerDisplayActivity.java": "exec_dos_path",
        root / "app/src/main/java/com/winlator/widget/TouchpadView.java": "setRtsMode",
        root / "app/src/main/cpp/winlator/include/winlator.h": APP_ID,
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

    copy_sources(root)
    patch_build_gradle(root)
    patch_package_paths(root)
    patch_manifest(root)
    patch_strings(root)
    patch_rootfs_installer(root)
    patch_touchpad(root)
    patch_xserver(root)
    validate(root)
    print("RA2/Yuri Android patch applied successfully.")

if __name__ == "__main__":
    main()
