#!/usr/bin/env python3
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
path = root / "overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck/GameDeckActivity.kt"
text = path.read_text(encoding="utf-8")

old = "import androidx.activity.result.contract.ActivityResultContracts\n"
new = "import androidx.activity.OnBackPressedCallback\nimport androidx.activity.result.contract.ActivityResultContracts\n"
if old not in text:
    raise SystemExit("import marker not found")
text = text.replace(old, new, 1)

old = "    private var currentScreen = Screen.HOME\n    private var currentDetailKey: String? = null\n"
new = "    private var currentScreen = Screen.HOME\n    private var detailReturnScreen = Screen.GAMES\n    private var currentDetailKey: String? = null\n"
if old not in text:
    raise SystemExit("field marker not found")
text = text.replace(old, new, 1)

old = """        root.addView(buildBottomNavigation())
        setContentView(root)

        lifecycleScope.launch {
"""
new = """        root.addView(buildBottomNavigation())
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!handleInAppBack()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        lifecycleScope.launch {
"""
if old not in text:
    raise SystemExit("onCreate marker not found")
text = text.replace(old, new, 1)

start = text.index("    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {")
end = text.index("\n    private fun buildHeader(): View {", start)
new_block = """    private fun handleInAppBack(): Boolean {
        return when (currentScreen) {
            Screen.DETAIL -> {
                if (detailReturnScreen == Screen.HOME) renderHome() else renderGames()
                true
            }
            Screen.PROFILES, Screen.DEVICES, Screen.MORE, Screen.GAMES -> {
                renderHome()
                true
            }
            Screen.SESSION -> {
                // Leave only the GameDeck control-center view; emulation keeps running.
                renderHome()
                true
            }
            Screen.HOME -> false
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK) {
            if (handleInAppBack()) return true
        }
        return super.onKeyDown(keyCode, event)
    }
"""
text = text[:start] + new_block + text[end:]

old = """    private fun renderGameDetail(game: Game) {
        currentScreen = Screen.DETAIL
"""
new = """    private fun renderGameDetail(game: Game) {
        detailReturnScreen = if (currentScreen == Screen.HOME) Screen.HOME else Screen.GAMES
        currentScreen = Screen.DETAIL
"""
if old not in text:
    raise SystemExit("detail marker not found")
text = text.replace(old, new, 1)

old = '        root.addView(secondaryButton("‹ Zurück") { renderGames() })\n'
new = '        root.addView(secondaryButton("‹ Zurück") { handleInAppBack() })\n'
if old not in text:
    raise SystemExit("detail back button marker not found")
text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print("GameDeck v4 Android/system back navigation patch applied.")
