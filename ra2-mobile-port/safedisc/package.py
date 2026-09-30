"""Package exact helper source and licenses alongside the two 32-bit binaries."""
from pathlib import Path
import subprocess, sys, zipfile, struct, shutil
root, output = map(Path, sys.argv[1:3])
output.mkdir(parents=True, exist_ok=True)
for name in ['VersionInjector.exe', 'version.dll']:
    data=(output/name).read_bytes()
    pe=struct.unpack_from('<I',data,0x3c)[0]
    assert data[pe:pe+4]==b'PE\0\0' and struct.unpack_from('<H',data,pe+4)[0]==0x14c, name
shutil.copyfile(root/'LICENSE',output/'SafeDiscLoader2-LICENSE')
with zipfile.ZipFile(output/'SafeDiscLoader2-source.zip','w',zipfile.ZIP_DEFLATED) as archive:
    for name in subprocess.check_output(['git','-C',str(root),'ls-files'],text=True).splitlines():
        archive.write(root/name,name)
    archive.write(Path(__file__).with_name('prepare.py'),'android-build/prepare.py')
    archive.write(Path(__file__),'android-build/package.py')
(output/'SafeDiscLoader2-NOTICE').write_text('SafeDiscLoader2 by nckstwrt and contributors. GPL-3.0.\nSource: https://github.com/nckstwrt/SafeDiscLoader2\nPinned revision: 91b6b89da8296276c36ff1a8fea2456c892ca865\nAndroid modifications: synchronous child lifetime and exit propagation; helper-relative DLL path; file-only logs; private config filename; static CRT; no elevation.\nExact corresponding modified source is included in SafeDiscLoader2-source.zip. Build Release|x86 with MSBuild v143. No commercial game files included.\n',encoding='utf-8')
