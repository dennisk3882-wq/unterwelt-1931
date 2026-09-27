#!/usr/bin/env python3
from __future__ import annotations

import argparse
import shutil
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "src"
APP_ID = "de.ahnsen.ra2yuri"

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
    for name in ["RA2LauncherActivity.java", "RA2TouchDock.java", "RA2DisplayController.java"]:
        require(SRC / name)
        shutil.copy2(SRC / name, dst / name)

def patch_build_gradle(root: Path) -> None:
    p = root / "app/build.gradle"
    require(p)
    s = read(p)
    s = s.replace("applicationId 'com.winlator'", f"applicationId '{APP_ID}'")
    s = s.replace('versionName "11.2"', 'versionName "0.1.0-ra2"')
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
    write(p, s)

def validate(root: Path) -> None:
    checks = {
        "launcher": root / "app/src/main/java/com/winlator/RA2LauncherActivity.java",
        "dock": root / "app/src/main/java/com/winlator/RA2TouchDock.java",
        "zoom": root / "app/src/main/java/com/winlator/RA2DisplayController.java",
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
