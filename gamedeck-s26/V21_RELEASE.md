# GameDeck S26 v21

Built from v18 with native v19 TV Presentation, live library diagnostics, v20 measured FPS analysis with opt-in graphics-profile recommendations, and v21 periodic secure update checks for GameDeck-only GitHub releases.

The automated game test checks start requirements; it cannot fully play through arbitrary titles or prove gameplay correctness. A distinct DeX display must be available for the true TV UI. GitHub release APK distribution must be configured before updates can be downloaded in-app.

Checks: Kotlin, APK signature, native core parity, ZIP integrity. Real S26 Ultra and HDMI tests remain separate from CI.
