#!/usr/bin/env python3
"""Patch the pinned RA2 VM with bounded Android-only automation and House state probes."""
from pathlib import Path

def once(path: str, before: str, after: str, desc: str) -> None:
    p = Path(path)
    source = p.read_text()
    count = source.count(before)
    if count != 1:
        raise RuntimeError(f"{desc} in {path}: expected one anchor, found {count}")
    p.write_text(source.replace(before, after, 1))

dest = Path("src/ui/pages/game/ra2AndroidAutotest.ts")
dest.write_text(Path("../ra2-mobile-port/resources/ra2AndroidAutotest.ts").read_text())

once("src/ui/pages/game/page.ts",
     "import { installCanvasFit, installGameInput, toggleImmersiveFullscreen } from './gameInput';",
     "import { installCanvasFit, installGameInput, toggleImmersiveFullscreen } from './gameInput';\n"
     "import { installAndroidAutotest } from './ra2AndroidAutotest';",
     "autotest page import")
once("src/ui/pages/game/page.ts",
     "  activeVm = vm;\n  toolbar.setMapsAvailable(status.phase === 'running');",
     "  activeVm = vm;\n"
     "  if (window.location.hostname === 'ra2.android.local')\n"
     "    installAndroidAutotest(vm, canvas, () => generation === pageGeneration && !exitHandled);\n"
     "  toolbar.setMapsAvailable(status.phase === 'running');",
     "install autonomous test controller")

once("src/ui/pages/game/vmPageRuntimeCallbacks.ts",
     "      if (debugAutoOpen) canvas.dataset.vmStatus = JSON.stringify(next);",
     "      if (debugAutoOpen || window.location.hostname === 'ra2.android.local')\n"
     "        canvas.dataset.vmStatus = JSON.stringify(next);",
     "Android status monitoring")
once("src/ui/pages/game/vmPageRuntimeCallbacks.ts",
     "      if (debugAutoOpen) {\n"
     "        canvas.dataset.vmFrame =",
     "      if (debugAutoOpen || window.location.hostname === 'ra2.android.local') {\n"
     "        canvas.dataset.vmFrame =",
     "frame status monitoring")

# Read-only House snapshot, using the exact RA2 1.006 layout already covered by
# upstream battleStartupBrowserSmoke.mts. For other EXEs, invalid pointers and
# unexpected human flags are reported as unsupported, not a failed game.
worker = "src/adapter/vmWorkerController.ts"
once(worker,
     "    const callbacks: GameVmCallbacks = {",
     """    let ra2HouseProbeFrames = 0;
    let ra2HouseProbePrevious = '';
    let ra2HouseFirstSeen = 0;
    let ra2HouseUnsupportedReported = false;
    let ra2HouseNoPointerLogged = false;
    let ra2HouseNoShimLogged = false;
    const ra2HouseProbe = () => {
      if (globalThis.location?.hostname !== 'ra2.android.local' || source.game.id !== 'ra2') return;
      // Limit to one probe per ~150 guest logic frames; read only, never change RAM.
      if (++ra2HouseProbeFrames % 150 !== 0) return;
      const shim = (this.core as unknown as {
        shim?: { readU32(ptr: number): number; readU8(ptr: number): number };
      } | null)?.shim;
      if (!shim) {
        if (!ra2HouseNoShimLogged) {
          ra2HouseNoShimLogged = true;
          console.info('[RA2-HOUSE] STATUS=NICHT_LESBAR Grund=VM_Shim_noch_nicht_verfuegbar');
        }
        return;
      }
      try {
        const house = shim.readU32(0x00a35db4) >>> 0;
        if (house < 0x00100000 || house >= 0x07f00000) {
          if (!ra2HouseNoPointerLogged) {
            ra2HouseNoPointerLogged = true;
            console.info('[RA2-HOUSE] STATUS=NICHT_LESBAR Grund=House_Zeiger_ungueltig ' +
              ' Vermutung=abweichendes_GAME_EXE_Speicherlayout');
          }
          return;
        }
        const human = shim.readU8(house + 0x134);
        const dead = shim.readU8(house + 0x13d);
        const members = shim.readU32(house + 0x5434) >>> 0;
        const size = shim.readU32(house + 0x5438) >>> 0;
        if (human !== 1 || size > 4096 || dead > 8) {
          if (!ra2HouseUnsupportedReported) {
            ra2HouseUnsupportedReported = true;
            console.info('[RA2-HOUSE] STATUS=NICHT_LESBAR Grund=ABI_inkompatibel ' +
            ' localHuman=' + human + ' deadFlag=' + dead + ' slots=' + size);
          }
          return;
        }
        let active = 0;
        if (members >= 0x00100000 && members + size * 4 < 0x07f00000)
          for (let i = 0; i < size; i++)
            if (shim.readU32(members + i * 4)) active++;
        const now = Math.round(performance.now());
        if (!ra2HouseFirstSeen) ra2HouseFirstSeen = now;
        const state = dead + ':' + active + ':' + size;
        if (state !== ra2HouseProbePrevious) {
          console.info('[RA2-HOUSE] Battle t=' + Math.round((now - ra2HouseFirstSeen) / 1000) +
            's localHuman=1 dead=' + dead + ' units=' + active + '/' + size +
            (dead ? ' ALARM=SPIELER_TOT' : active === 0 ? ' ALARM=KEINE_EINHEITEN' : ''));
          ra2HouseProbePrevious = state;
        }
      } catch {
        if (!ra2HouseUnsupportedReported) {
          ra2HouseUnsupportedReported = true;
          console.warn('[RA2-HOUSE] Speicherlayout unbekannt; keine Manipulation vorgenommen');
        }
      }
    };
    const callbacks: GameVmCallbacks = {""",
     "bounded original House snapshot")

once(worker,
     "      onLogicFrame: () => {\n        this.callBatchLogicFrames++;\n      },",
     "      onLogicFrame: () => {\n"
     "        this.callBatchLogicFrames++;\n"
     "        ra2HouseProbe();\n"
     "      },",
     "periodic House observation")
print("RA2 autonomous player, record/replay and read-only House probes patched")
