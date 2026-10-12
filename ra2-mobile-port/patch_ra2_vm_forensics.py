#!/usr/bin/env python3
"""Android RA2 VM diagnostics: log only query outcomes, never serial contents."""
from pathlib import Path

def replace_one(source: str, before: str, after: str, name: str) -> str:
    count = source.count(before)
    if count != 1:
        raise RuntimeError(f"{name}: expected one anchor, found {count}")
    return source.replace(before, after, 1)

p = Path("src/vm86/shim/winmm.ts")
s = p.read_text()
s = replace_one(s,
    "type User32Chain = InstanceType<ReturnType<typeof withUser32>>;",
    "const ra2RegistryEvidence = new Set<string>();\n"
    "type User32Chain = InstanceType<ReturnType<typeof withUser32>>;",
    "registry evidence state")

lookup = "          if (!data) return { eax: 2 }; // ERROR_FILE_NOT_FOUND lets the game use defaults."
trace = """          if (globalThis.location?.hostname === 'ra2.android.local') {
            const registryClass = id.includes('wchat') && id.includes('sysid') ? 'AutoDet-SysID' :
              id.includes('westwood') && valueName === 'serial' ? 'Westwood-Serial' :
              id.includes('westwood') && ['installpath','folderpath','sku','version'].includes(valueName)
                ? 'Westwood-' + valueName : '';
            const view = path.includes('wow6432node') ? 'Wow6432Node' : 'nativ';
            const evidence = registryClass + ':' + view + ':' + (data ? 'OK' : 'FEHLT');
            if (registryClass && !ra2RegistryEvidence.has(evidence)) {
              ra2RegistryEvidence.add(evidence);
              console.info('[RA2-FORENSIK] Win32 RegQuery ' + registryClass +
                ' View=' + view + ' Ergebnis=' + (data ? 'OK' : 'FEHLT') +
                ' Typ=' + (data ? type : '-') + ' Bytes=' + (data?.length ?? 0));
            }
          }
"""
s = replace_one(s, lookup, trace + lookup, "registry query evidence")
p.write_text(s)

p = Path("src/vm86/shim/kernel32.ts")
s = p.read_text()
s = replace_one(s,
    "const FILETIME_UNIX_EPOCH = 116_444_736_000_000_000n;",
    "const ra2GuestCdTypeEvidence = new Set<number>();\n"
    "const FILETIME_UNIX_EPOCH = 116_444_736_000_000_000n;",
    "guest optical drive evidence state")
drive_anchor = """        case 'KERNEL32.DLL!GetDriveTypeA':
          return { eax: this.getDriveType(a[0] ?? 0) };"""
drive_trace = """        case 'KERNEL32.DLL!GetDriveTypeA': {
          const drive = this.readCString(a[0] ?? 0);
          const result = this.getDriveType(a[0] ?? 0);
          if (globalThis.location?.hostname === 'ra2.android.local' &&
              drive.toUpperCase().startsWith('D:') &&
              !ra2GuestCdTypeEvidence.has(result)) {
            ra2GuestCdTypeEvidence.add(result);
            console.info('[RA2-FORENSIK] Win32 GetDriveTypeA D:=' +
              (result === DRIVE_CDROM ? 'CDROM' : result === 1 ? 'NICHT_VORHANDEN' : result));
          }
          return { eax: result };
        }"""
s = replace_one(s, drive_anchor, drive_trace, "GetDriveTypeA evidence")
p.write_text(s)
print("RA2 VM forensics patched; no keys logged")
