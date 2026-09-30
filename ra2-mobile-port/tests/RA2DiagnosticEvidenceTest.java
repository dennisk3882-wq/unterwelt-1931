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
        System.out.println("PASS: diagnostic phase attribution, unrelated process suppression, handled exceptions, crashes, helper failure, measurement gaps");
    }
}
