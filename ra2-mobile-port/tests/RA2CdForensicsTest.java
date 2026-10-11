import com.winlator.RA2CdForensics;
public class RA2CdForensicsTest {
    public static void main(String[] args) {
        String disc="Volume in drive X is RA2\nRA2.DSK\n";
        RA2CdForensics.Report missing=RA2CdForensics.analyze("",
             "VersionInjector Starting...\n","",disc,"","original-launcher-probe");
        require(missing.headline.contains("Messlücke"),"Do not infer DLL failure from absent marker");
        require(missing.findings.toString().contains("CD in Wine: Verzeichnis"),"Disc preflight");
        RA2CdForensics.Report partial=RA2CdForensics.analyze("dllmain-attach\nload-entry\nconfig-loaded=1\n",
             "injection-result=success\n","",disc,
             "123:Call KERNEL32.GetDriveTypeA(01) ret=0123\n","original-launcher-probe");
        require(partial.stage.equals("DLL-Konfiguration gelesen"),"Stages are distinct");
        require(partial.findings.toString().contains("vorhanden"),"CD-API evidence");
        RA2CdForensics.Report ready=RA2CdForensics.analyze("dllmain-attach\nload-entry\nconfig-loaded=1\nhooks-enabled\n",
             "injection-result=success\n","Hooks Complete!\n",disc,"","original-launcher-probe");
        require(ready.headline.contains("DLL-Hooks bestätigt"),"Hook conclusion");
        RA2CdForensics.Report dllMode=RA2CdForensics.analyze("",
            "injection-result=success", "", disc,
            "wineMs=99:trace:loaddll:build_module Loaded L\"C:\\RA2Mobile\\SafeDisc\\version.dll\" at 0000: native\n",
            "{\"launchMode\":\"original-launcher-probe\",\"nativeVersionDllRequested\":true}");
        require(dllMode.findings.toString().contains("VERSION.DLL nativ geladen"),
            "Native DLL distinguishable from Wine builtin");
        require(!RA2CdForensics.relevant("wineMs=1 00:Call kernel32.ReadFile()"),"Noise dropped");
        require(RA2CdForensics.relevant("Call KERNEL32.GetVolumeInformationA()"),"Volume call retained");
        System.out.println("RA2 CD forensic stage/capture tests passed");
    }
    private static void require(boolean x,String why) {if(!x)throw new AssertionError(why);}
}