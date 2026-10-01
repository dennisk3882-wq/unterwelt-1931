import com.winlator.RA2DiagnosticEvidence;
public class RA2DiagnosticEvidenceTest {
    static final String START="stage=game-command-started\n";
    static final String GAME="wineMs=230 0160:err:environ:init_peb starting L\"C:\\Westwood\\RA2\\GAME.EXE\" in experimental wow64 mode\n"+
        "0160:trace:loaddll:build_module Loaded L\"C:\\Westwood\\RA2\\GAME.EXE\" at 00400000: native\n";
    static RA2DiagnosticEvidence.Analysis analyze(String s,String inj,String h,Integer exit,boolean window) {
        return RA2DiagnosticEvidence.analyze(s,inj,h,"STATE : 4 RUNNING","STATE : 4 RUNNING",exit,window);
    }
    public static void main(String[] args) {
        RA2DiagnosticEvidence.Analysis a=analyze("0084:trace:seh:dispatch_exception code=6ba\n"+START+GAME+
            "0168:warn:rpc:RPCRT4_io_thread receive failed with error 6be\n"+
            "stage=game-command-ended\n016c:warn:rpc:RPCRT4_io_thread receive failed with error 6be\n","","",0,false);
        if(a.beforeRpc!=1 || a.gameRpc!=1 || a.afterRpc!=1 || a.crashed || !a.loaded || !a.threads.contains("0160")) throw new AssertionError("phase / thread attribution");
        if(a.summary.contains("RPC") || a.findings.stream().anyMatch(f->f.severity.equals("FAIL"))) throw new AssertionError("speculation mislabeled confirmed");
        a=analyze(START+GAME+"0160:err:module:import_dll Library required.dll not found\n","","",0,false);
        if(!a.summary.contains("Fehlende DLL")) throw new AssertionError("target missing DLL not prioritized");
        a=analyze(START+GAME+"0180:err:module:import_dll Library unrelated.dll not found\n","","",0,false);
        if(a.summary.contains("Fehlende DLL")) throw new AssertionError("another process blamed");
        a=analyze(START+GAME+"0160:trace:seh:dispatch_exception code=6ba\n","","",0,false);
        if(a.crashed) throw new AssertionError("handled exception counted as crash");
        a=analyze(START+GAME+"0160:err:seh:Unhandled exception: page fault\n","","",-1073741819,false);
        if(!a.crashed || !a.summary.contains("Absturz")) throw new AssertionError("unhandled crash missed");
        a=analyze("cdCompatibility=true\n"+START+GAME,"Could not create remote thread", "",-1,false);
        if(!a.summary.contains("Kompatibilitätshelfer")) throw new AssertionError("injector failure missed");
        a=analyze("cdCompatibility=true\n"+START+GAME,"", "Version.DLL Loaded!\nUnable to hook CreateFileA",0,false);
        if(!a.summary.contains("Hook")) throw new AssertionError("hook failure missed");
        a=analyze("","","",null,false);
        if(a.findings.stream().noneMatch(f->f.confidence.equals("MESSLÜCKE"))) throw new AssertionError("missing data hidden");
        a=analyze(START+GAME,"","",0,true);
        if(!a.summary.contains("Gameplay wurde nicht")) throw new AssertionError("window treated as gameplay test");
        if(!RA2DiagnosticEvidence.decode(0xc000007b).contains("INVALID_IMAGE")) throw new AssertionError("NTSTATUS decode");
        String realFormat = "cdCompatibility=false\n" + START +
            "wineMs=5681 100280.113:0160:err:environ:init_peb starting L\"C:\\Westwood\\RA2\\GAME.EXE\" in experimental wow64 mode\n" +
            "wineMs=5758 100280.190:0160:trace:loaddll:build_module Loaded L\"C:\\Westwood\\RA2\\GAME.EXE\" at 00400000: native\n" +
            "wineMs=6739 100281.169:0160:warn:file:NtCreateFile L\"C:\\windows\\AcGenral.dll\" not found (c0000034)\n" +
            "wineMs=6740 100281.170:0160:Call KERNEL32.ExitProcess(00000000) ret=00401010\n" +
            "stage=game-command-ended\n016c:warn:rpc:receive failed with error 6be\n";
        a=analyze(realFormat,"","",0,false);
        if (!a.loaded || a.files.isEmpty() || a.afterRpc!=1 || a.findings.stream().noneMatch(f->f.title.equals("Beendigungsaufrufe des Spiel-TIDs"))) throw new AssertionError("Actual uploaded log format / relay not handled");
        String relay = START + GAME +
            "wineMs=1 1.000:0160:trace:module:map_image_into_view mapping PE file L\"C:\\Westwood\\RA2\\GAME.EXE\" at 0x400000-0xb29000\n" +
            "wineMs=2 1.001:0160:Call KERNEL32.GetDriveTypeA(0021feb4 \"x:\\\\\" ) ret=004d1f0b\n" +
            "wineMs=3 1.002:0160:Call kernelbase.GetDriveTypeA(0021feb4 \"x:\\\\\" ) ret=7ae2cc54\n" +
            "wineMs=4 1.003:0160:Ret  kernelbase.GetDriveTypeA() retval=00000005 ret=7ae2cc54\n" +
            "wineMs=5 1.004:0160:Ret  KERNEL32.GetDriveTypeA() retval=00000005 ret=004d1f0b\n" +
            "wineMs=6 1.005:0160:Call KERNEL32.ExitProcess(00000000) ret=0077050f\n" +
            "wineMs=7 1.006:0160:warn:file:NtCreateFile AcGenral.dll not found (c0000034)\n" +
            "stage=game-command-ended\n";
        a=analyze(relay,"","",0,false);
        if(a.findings.stream().noneMatch(f->f.title.equals("Tatsächliche Laufwerkstypen im Spiel") && f.evidence.contains("X:=00000005"))) throw new AssertionError("Nested forwarded drive calls mismatched");
        if(a.findings.stream().noneMatch(f->f.title.equals("Spiel ruft ExitProcess selbst auf") && f.evidence.contains("0x37050f"))) throw new AssertionError("Exit caller RVA not mapped");
        if(!a.files.isEmpty()) throw new AssertionError("Post-exit cleanup blamed for startup");
        String otherThread=relay.replace(":Ret  KERNEL32.GetDriveTypeA()",":Ret  KERNEL32.GetDriveTypeA()").replace("1.004:0160:","1.004:0180:");
        a=analyze(otherThread,"","",0,false);
        if(a.findings.stream().anyMatch(f->f.title.equals("Tatsächliche Laufwerkstypen im Spiel"))) throw new AssertionError("Return paired across unrelated thread");
        if (!RA2DiagnosticEvidence.sha256(null).equals("missing")) throw new AssertionError("Missing case-insensitive file lookup crashed");
        System.out.println("PASS: diagnostic phase attribution, unrelated process suppression, handled exceptions, crashes, helper failure, measurement gaps");
    }
}
