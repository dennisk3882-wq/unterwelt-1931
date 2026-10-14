# GameDeck S26 v18

Native upgrade based on the existing Eden integration and original package, with progressive v16, v17 and v18 patch scripts.

## Verified in source
- v15: persistent library sort/favorites, collapsible search header, large cover toggle, start count, local GameDeck settings backup.
- v16: console connection check (real Android display mode, controller hardware enumeration, audio, battery), refresh on returning to Devices.
- v17: live technical systemcheck (app, gamepad, external screen, audio, storage, logs, driver support, optional game preflight), with copy/share.
- v18: game profile explanation based on Eden's actual intended config, profile selection JSON backup, release build label.

## Deliberate limits
- No claims of simulated full game playthrough or invented FPS telemetry.
- Native v14 Eden binary files retained and hash-checked. No automatic core rollback.
- Release requires successful signed APK build and S26 Ultra real-device smoke tests.
- A full secondary-display console library, automatic game benchmark and remote emulator control require additional integrations and are not claimed complete.
