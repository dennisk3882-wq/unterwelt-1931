"""Adapt pinned SafeDiscLoader2 for a synchronous, logged Android/Wine launch."""
from pathlib import Path
import sys, xml.etree.ElementTree as ET
root=Path(sys.argv[1])
p=root/'VersionInjector/VersionInjector.cpp'
s=p.read_text()
old='strcpy(szSecDrvEmuDLLPath, "version.dll");'
assert old in s
s=s.replace(old,'''GetModuleFileNameA(NULL, szSecDrvEmuDLLPath, MAX_PATH);
    char* slash = strrchr(szSecDrvEmuDLLPath, '\\\\');
    if (!slash) exitlog("Injector path unavailable");
    strcpy(slash + 1, "version.dll");''')
s=s.replace('InjectVersionDLL(pi.dwProcessId);','''if (!bSuccess) {
        log("CreateProcess failed: %lu\\n", GetLastError());
        return (int)GetLastError();
    }
    InjectVersionDLL(pi.dwProcessId);''')
old='ResumeThread(pi.hThread);'
assert old in s
s=s.replace(old,'''ResumeThread(pi.hThread);
    CloseHandle(pi.hThread);
    WaitForSingleObject(pi.hProcess, INFINITE);
    DWORD code = 0;
    GetExitCodeProcess(pi.hProcess, &code);
    CloseHandle(pi.hProcess);
    log("Game exit: %lu\\n", code);
    return (int)code;''')
s=s.replace('\treturn 0;\n}', '\treturn ret;\n}')
s=s.replace('"version.json"', '"ra2-cd-compat.json"')
s=s.replace('if (GetFileAttributesA("Version.dll") == -1L)', '''char helperDLL[MAX_PATH];
    GetModuleFileNameA(NULL, helperDLL, MAX_PATH);
    char* helperSlash = strrchr(helperDLL, '\\\\');
    if (!helperSlash) return ERROR_PATH_NOT_FOUND;
    strcpy(helperSlash + 1, "version.dll");
    if (GetFileAttributesA(helperDLL) == INVALID_FILE_ATTRIBUTES)''')
# A mobile error must return a code, not leave an invisible modal dialog waiting.
s=s.replace('MessageBox(0, text, "VersionInjector Error", MB_ICONERROR | MB_OK);', '')
s=s.replace('AllocConsole();', '// File logging only on Android.')
s=s.replace('freopen("CONOUT$", "w", stdout);', '')
# Verify injected DLL actually loaded; report failure rather than resume unpatched.
s=s.replace('''WaitForSingleObject(hNewThread, INFINITE);''','''WaitForSingleObject(hNewThread, INFINITE);
    DWORD module = 0;
    GetExitCodeThread(hNewThread, &module);
    if (!module) exitlog("Compatibility DLL could not be loaded");''')
p.write_text(s)
p=root/'src/version.cpp'
s=p.read_bytes().decode('cp1252').replace('"version.json"','"ra2-cd-compat.json"')
start=s.index('\t\t// The below is just to force the console window visible')
end=s.index('\t\tNString logFile', start)
s=s[:start]+s[end:]
p.write_bytes(s.encode('cp1252'))
ns={'m':'http://schemas.microsoft.com/developer/msbuild/2003'}
ET.register_namespace('', ns['m'])
for p in root.rglob('*.vcxproj'):
 tree=ET.parse(p)
 for group in tree.getroot().findall('m:ItemDefinitionGroup',ns):
  compile=group.find('m:ClCompile',ns)
  if compile is not None:
   ET.SubElement(compile,'{'+ns['m']+'}RuntimeLibrary').text='MultiThreaded'
 for elem in tree.getroot().findall('.//m:UACExecutionLevel',ns):elem.text='AsInvoker'
 tree.write(p,encoding='utf-8',xml_declaration=True)
print('SafeDisc compatibility helper adapted')
# Upstream resource refers to the author's machine; use the tracked icon.
p=root/'VersionInjector/VersionInjector.rc'
s=p.read_text(encoding='utf-16')
import re
s,n=re.subn(r'"C:.*?SafeDiscLoaderIcon\.ico"', '"../SafeDiscLoaderIcon.ico"', s)
assert n == 1, 'Upstream icon resource changed'
p.write_text(s,encoding='utf-16')
