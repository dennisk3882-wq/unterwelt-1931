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
s=s.replace('"version.json"', r'"C:\\Westwood\\RA2\\ra2-cd-compat.json"')
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
# Verify injected DLL actually loaded; record every remote-injection stage.
s=s.replace('''WaitForSingleObject(hNewThread, INFINITE);''','''WaitForSingleObject(hNewThread, INFINITE);
    DWORD module = 0;
    GetExitCodeThread(hNewThread, &module);
    if (!module) {
        log("injection-result=failure\\n");
        exitlog("Compatibility DLL could not be loaded");
    }
    log("injection-result=success module=%08lx\\n", module);''')
p.write_text(s)
p=root/'src/version.cpp'
s=p.read_bytes().decode('cp1252').replace('"version.json"',r'"C:\\Westwood\\RA2\\ra2-cd-compat.json"')
start=s.index('\t\t// The below is just to force the console window visible')
end=s.index('\t\tNString logFile', start)
s=s[:start]+s[end:]
needle = r'config.LoadConfig("C:\\Westwood\\RA2\\ra2-cd-compat.json");'
assert needle in s, "Injected DLL config path not found"
s=s.replace(needle, r'''bool configLoaded = config.LoadConfig("C:\\Westwood\\RA2\\ra2-cd-compat.json");
    FILE* stage = fopen("C:/RA2Mobile/safedisc-dll-stage.txt", "wt");
    if (stage) {
        fprintf(stage, "dll-entry=1\nconfig-loaded=%d\n", configLoaded ? 1 : 0);
        fclose(stage);
    }''', 1)
# Append stage markers rather than overwriting the DLL_PROCESS_ATTACH event.
old_stage = r'''bool configLoaded = config.LoadConfig("C:\\Westwood\\RA2\\ra2-cd-compat.json");
    FILE* stage = fopen("C:/RA2Mobile/safedisc-dll-stage.txt", "wt");
    if (stage) {
        fprintf(stage, "dll-entry=1\nconfig-loaded=%d\n", configLoaded ? 1 : 0);
        fclose(stage);
    }'''
new_stage = r'''FILE* entry = fopen("C:/RA2Mobile/safedisc-dll-stage.txt", "at");
    if (entry) { fprintf(entry, "load-entry\n"); fclose(entry); }
    bool configLoaded = config.LoadConfig("C:\\Westwood\\RA2\\ra2-cd-compat.json");
    FILE* stage = fopen("C:/RA2Mobile/safedisc-dll-stage.txt", "at");
    if (stage) { fprintf(stage, "config-loaded=%d\n", configLoaded ? 1 : 0); fclose(stage); }'''
assert old_stage in s
s=s.replace(old_stage, new_stage, 1)
hook_point = '\tlog("Hooks Complete!\\n");'
assert hook_point in s
s=s.replace(hook_point, '''\tFILE* hookmarker = fopen("C:/RA2Mobile/safedisc-dll-stage.txt", "at");
    if (hookmarker) { fprintf(hookmarker, "hooks-enabled\\n"); fclose(hookmarker); }
''' + hook_point, 1)
p.write_bytes(s.encode('cp1252'))
# DLL_PROCESS_ATTACH is independent of the Load()/CRT log setup.
p=root/'src/dllmain.cpp'
s=p.read_text()
needle='''    case DLL_PROCESS_ATTACH:
'''
assert needle in s
s=s.replace(needle, r'''    case DLL_PROCESS_ATTACH:
        {
            HANDLE diag = CreateFileA("C:\\RA2Mobile\\safedisc-dll-stage.txt",
                FILE_APPEND_DATA, FILE_SHARE_READ | FILE_SHARE_WRITE, NULL,
                OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
            if (diag != INVALID_HANDLE_VALUE) {
                const char marker[] = "dllmain-attach\n";
                DWORD written = 0;
                WriteFile(diag, marker, sizeof(marker) - 1, &written, NULL);
                CloseHandle(diag);
            }
        }
''', 1)
p.write_text(s)
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
