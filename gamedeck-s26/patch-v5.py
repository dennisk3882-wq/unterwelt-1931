#!/usr/bin/env python3
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
path = root / "overlay/src/main/java/org/yuzu/yuzu_emu/gamedeck/GameDeckActivity.kt"
text = path.read_text(encoding="utf-8")

old = "import androidx.core.view.WindowCompat\n"
new = "import androidx.core.view.ViewCompat\nimport androidx.core.view.WindowCompat\nimport androidx.core.view.WindowInsetsCompat\n"
if old not in text:
    raise SystemExit("WindowCompat import marker not found")
text = text.replace(old, new, 1)

old = """        root.addView(buildBottomNavigation())
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
"""
new = """        root.addView(buildBottomNavigation())
        setContentView(root)

        // Samsung/Android 15+ may enforce edge-to-edge even when decor fitting is requested.
        // Apply the real navigation-bar inset to the whole GameDeck root so our bottom bar
        // always sits completely above the 3-button/gesture navigation area.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                navigation.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
"""
if old not in text:
    raise SystemExit("root/insets marker not found")
text = text.replace(old, new, 1)

old = """        if (metadata.screenshots.isNotEmpty()) {
            container.addView(sectionTitle("Screenshots"))
            val strip = LinearLayout(this).apply {
"""
new = """        if (metadata.screenshots.isNotEmpty()) {
            container.addView(sectionTitle("Screenshots"))
            container.addView(TextView(this).apply {
                text = "Antippen zum Vergrößern"
                textSize = 9f
                setTextColor(MUTED)
                setPadding(dp(2), 0, dp(2), dp(6))
            })
            val strip = LinearLayout(this).apply {
"""
if old not in text:
    raise SystemExit("screenshots heading marker not found")
text = text.replace(old, new, 1)

old = """                val shot = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = rounded(CARD_2, dp(12).toFloat(), BLUE_DARK)
                    clipToOutline = true
                }
                metadataRepository.loadImage(url, shot)
"""
new = """                val shot = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = rounded(CARD_2, dp(12).toFloat(), BLUE_DARK)
                    clipToOutline = true
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Screenshot vergrößern"
                    setOnClickListener { showScreenshotViewer(url) }
                }
                metadataRepository.loadImage(url, shot)
"""
if old not in text:
    raise SystemExit("screenshot image marker not found")
text = text.replace(old, new, 1)

marker = """    private fun formatReleaseDate(value: String): String {
"""
if marker not in text:
    raise SystemExit("viewer insertion marker not found")
viewer = r'''    private fun showScreenshotViewer(url: String) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
        }

        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "Vergrößerter Screenshot"
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        metadataRepository.loadImage(url, image)
        frame.addView(
            image,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val close = TextView(this).apply {
            text = "✕"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.argb(210, 8, 20, 34), dp(22).toFloat(), BORDER)
            contentDescription = "Bild schließen"
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        }
        frame.addView(
            close,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(18)
                rightMargin = dp(16)
            }
        )

        val hint = TextView(this).apply {
            text = "Zurück oder ✕ zum Schließen"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.argb(185, 8, 20, 34), dp(16).toFloat(), BORDER)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        frame.addView(
            hint,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dp(20)
            }
        )

        ViewCompat.setOnApplyWindowInsetsListener(frame) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        dialog.setContentView(frame)
        dialog.setCancelable(true)
        dialog.show()
        dialog.window?.apply {
            statusBarColor = Color.BLACK
            navigationBarColor = Color.BLACK
        }
        ViewCompat.requestApplyInsets(frame)
    }

'''
text = text.replace(marker, viewer + marker, 1)

path.write_text(text, encoding="utf-8")
print("GameDeck v5 system-insets and fullscreen screenshot viewer patch applied.")
