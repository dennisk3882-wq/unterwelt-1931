#!/usr/bin/env python3
"""Forensic Win32 probes for RA2 Android. Never log key text or registry values."""
from pathlib import Path

def replace_one(p: Path, anchor: str, replacement: str, reason: str) -> None:
    txt = p.read_text()
    occurrences = txt.count(anchor)
    if occurrences != 1:
        raise RuntimeError(f"{p} {reason}: expected 1 anchor, found {occurrences}")
    p.write_text(txt.replace(anchor, replacement, 1))

p = Path("src/vm86/shim/winmm.ts")
replace_one(p,
    "const ra2RegistryEvidence = new Set<string>();",
    "const ra2RegistryEvidence = new Set<string>();\n"
    "let ra2RegistryOpenCount = 0;\n"
    "let ra2RegistryQueryCount = 0;",
    "Registry call counters")
open_anchor = "          if (out) this.writeU32(out, handle);"
open_trace = """          if (globalThis.location?.hostname === 'ra2.android.local' &&
              ra2RegistryOpenCount++ < 6) {
            console.info('[RA2-FORENSIK] Win32 RegOpen #' + ra2RegistryOpenCount +
              ' Kategorie=' + (child.includes('westwood') ? 'Westwood' :
                child.includes('wchat') ? 'WChat' : 'Andere') +
              ' Root=' + (parent === 'hkcr' ? 'HKCR' :
                parent === 'hklm' ? 'HKLM' : 'Andere'));
          }
"""
replace_one(p, open_anchor, open_trace + open_anchor, "RegOpen trace")
query_anchor = "          if (!data) return { eax: 2 }; // ERROR_FILE_NOT_FOUND lets the game use defaults."
query_trace = """          if (globalThis.location?.hostname === 'ra2.android.local' &&
              ra2RegistryQueryCount++ < 12) {
            console.info('[RA2-FORENSIK] Win32 RegQuery #' + ra2RegistryQueryCount +
              ' Kategorie=' + (id.includes('westwood') ? 'Westwood' :
                id.includes('wchat') ? 'WChat' : 'Andere') +
              ' Ergebnis=' + (data ? 'OK' : 'FEHLT'));
          }
"""
replace_one(p, query_anchor, query_trace + query_anchor, "limited RegQuery trace")

p = Path("src/vm86/shim/kernel32.ts")
replace_one(p,
    "const ra2GuestCdTypeEvidence = new Set<number>();",
    "const ra2GuestCdTypeEvidence = new Set<number>();\n"
    "let ra2CdSpaceProbeCount = 0;\n"
    "let ra2CdVolumeProbeCount = 0;\n"
    "let ra2CdLogicalDrivesProbeCount = 0;",
    "CD API probes")
disk_anchor = """        case 'KERNEL32.DLL!GetDiskFreeSpaceA': {
          const cdrom = this.getDriveType(a[0] ?? 0) === DRIVE_CDROM;"""
disk_new = """        case 'KERNEL32.DLL!GetDiskFreeSpaceA': {
          const tested = this.readCString(a[0] ?? 0);
          const cdrom = this.getDriveType(a[0] ?? 0) === DRIVE_CDROM;
          if (globalThis.location?.hostname === 'ra2.android.local' &&
              tested.toUpperCase().startsWith('D:') && ra2CdSpaceProbeCount++ < 3) {
            console.info('[RA2-FORENSIK] Win32 GetDiskFreeSpaceA(D:)=' +
              (cdrom ? 'CD_NUR_LESEN' : 'NICHT_CD'));
          }"""
replace_one(p, disk_anchor, disk_new, "GetDiskFreeSpace")
vol_anchor = """        case 'KERNEL32.DLL!GetVolumeInformationA': {
          const cdrom = this.getDriveType(a[0] ?? 0) === DRIVE_CDROM;"""
vol_new = """        case 'KERNEL32.DLL!GetVolumeInformationA': {
          const tested = this.readCString(a[0] ?? 0);
          const cdrom = this.getDriveType(a[0] ?? 0) === DRIVE_CDROM;
          if (globalThis.location?.hostname === 'ra2.android.local' &&
              tested.toUpperCase().startsWith('D:') && ra2CdVolumeProbeCount++ < 3) {
            console.info('[RA2-FORENSIK] Win32 GetVolumeInformationA(D:)=' +
              (cdrom ? (this.gameProfile.cdromVolumeLabel ?? 'CDROM') : 'FESTPLATTE'));
          }"""
replace_one(p, vol_anchor, vol_new, "GetVolumeInformation")
drives_anchor = """        case 'KERNEL32.DLL!GetLogicalDriveStringsA': {
          const drives ="""
drives_new = """        case 'KERNEL32.DLL!GetLogicalDriveStringsA': {
          if (globalThis.location?.hostname === 'ra2.android.local' &&
              ra2CdLogicalDrivesProbeCount++ < 2) {
            console.info('[RA2-FORENSIK] Win32 GetLogicalDriveStrings D_vorhanden=' +
              this.driveTypes.has('D'));
          }
          const drives ="""
replace_one(p, drives_anchor, drives_new, "GetLogicalDriveStrings")

# Shell-page milestones help establish the exact time the player left the lobby.
p = Path("src/adapter/vmWorkerController.ts")
replace_one(p,
    "      onShellPage: (title) => this.post({ type: 'shell-page', title }),",
    "      onShellPage: (title) => {\n"
    "        if (globalThis.location?.hostname === 'ra2.android.local')\n"
    "          console.info('[RA2-FORENSIK] ShellPage=' + title);\n"
    "        this.post({ type: 'shell-page', title });\n"
    "      },",
    "shell page timeline")
print("RA2 Android Win32 probe instrumentation applied; all values redacted")
