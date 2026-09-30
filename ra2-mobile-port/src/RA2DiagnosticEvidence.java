package com.winlator;

import java.util.*;
import java.util.regex.*;

/** Deterministic evidence analysis. No probability scores inferred from warning counts. */
public final class RA2DiagnosticEvidence {
    public static final class Finding {
        public final String severity, confidence, title, evidence, next;
        Finding(String s, String c, String t, String e, String n) {
            severity=s; confidence=c; title=t; evidence=e; next=n;
        }
    }
    public static final class Analysis {
        public String summary = "Keine eindeutige Ursache belegt.";
        public final List<Finding> findings = new ArrayList<>();
        public final Set<String> threads = new LinkedHashSet<>(), modules = new LinkedHashSet<>();
        public final List<String> errors = new ArrayList<>(), files = new ArrayList<>();
        public int beforeRpc, gameRpc, afterRpc;
        public boolean created, loaded, crashed, helperLoaded;
        void add(String s,String c,String t,String e,String n) { findings.add(new Finding(s,c,t,e,n)); }
    }
    private static final Pattern ACTOR=Pattern.compile("(?i)([0-9a-f]{4,}):(?:(?:trace|err|warn|fixme):|(?:Call|Ret) )");
    private static final Pattern MODULE=Pattern.compile("(?i)Loaded L\\\"([^\\\"]+)\\\"");
    private static String actor(String line) { Matcher m=ACTOR.matcher(line); return m.find()?m.group(1).toLowerCase(Locale.ROOT):""; }
    private static boolean target(String s) { return s.contains("\\game.exe\"") || s.contains("\\gamemd.exe\""); }
    private static boolean rpc(String s) { return s.contains("code=6ba") || s.contains("error 6be") || s.contains("rpc_s_server_unavailable") || s.contains("failed to start rpcss"); }
    public static Analysis analyze(String trace, String injector, String helper, String before, String after, Integer exit, boolean window) {
        Analysis a=new Analysis();
        trace=trace==null?"":trace; injector=injector==null?"":injector; helper=helper==null?"":helper;
        String[] lines=trace.split("\\r?\\n");
        int start=-1,end=-1;
        for(int i=0;i<lines.length;i++) if(lines[i].contains("stage=game-command-started")) start=i;
        for(int i=Math.max(0,start);i<lines.length;i++) if(lines[i].contains("stage=game-command-ended") || lines[i].contains("stage=game-exited")) { end=i;break; }
        // Identify the target's Wine thread, rather than blaming unrelated sc.exe/services warnings.
        for(int i=Math.max(0,start);i<lines.length && (end<0 || i<end);i++) {
            String l=lines[i].toLowerCase(Locale.ROOT);
            if(target(l) && (l.contains("init_peb") || l.contains("build_module loaded"))) {
                String id=actor(lines[i]); if(!id.isEmpty()) a.threads.add(id);
                if(l.contains("build_module loaded")) a.loaded=true;
            }
            if(target(l) && l.contains("createprocessinternalw started")) a.created=true;
            if(target(l) && l.contains("ntcreateuserprocess") && l.contains("image ")) a.created=true;
        }
        boolean fatalImport=false, targetRpc=false;
        for(int i=0;i<lines.length;i++) {
            String raw=lines[i], l=raw.toLowerCase(Locale.ROOT);
            boolean in=start>=0 && i>=start && (end<0 || i<end);
            if(rpc(l)) { if(start<0 || i<start) a.beforeRpc++; else if(in) a.gameRpc++; else a.afterRpc++; }
            if(!in || !a.threads.contains(actor(raw))) continue;
            Matcher m=MODULE.matcher(raw); if(m.find()) a.modules.add(m.group(1));
            targetRpc |= rpc(l);
            boolean fatal=l.contains("unhandled exception") || l.contains("unhandled page fault") || l.contains("backtrace:");
            a.crashed |= fatal;
            boolean missing=l.contains("import_dll") && (l.contains("not found") || l.contains("failed"));
            fatalImport |= missing;
            if(fatal || missing || l.contains(":err:")) { if(a.errors.size()<12) a.errors.add(raw); }
            if((l.contains(":file:") || l.contains("createfile")) &&
                (l.contains("c0000034") || l.contains("c000003a") || l.contains("c0000022") || l.contains("not found") || l.contains("access denied"))) {
                if(a.files.size()==12) a.files.remove(0); a.files.add(raw);
            }
        }
        String h=helper.toLowerCase(Locale.ROOT), inj=injector.toLowerCase(Locale.ROOT);
        a.helperLoaded=h.contains("version.dll loaded!");
        boolean helperRequested=trace.contains("cdCompatibility=true");
        a.add("INFO","BELEGT","Messgrundlage", "Wine-TIDs des Spiels: "+a.threads+"; Prozessstart angefordert: "+a.created+"; EXE geladen: "+a.loaded+"; Spielfenster: "+window+"; Spielcode: "+exit,
            "Wine-TIDs sind Threadkennungen, keine Android-Prozesskennungen. Ein geladenes Modul beweist noch keine erfolgreiche Initialisierung.");
        a.add("INFO","BELEGT","RPC nach Phase", "Vor Start: "+a.beforeRpc+"; während Start: "+a.gameRpc+"; nach Ende: "+a.afterRpc+"; davon dem Spiel-TID zugeordnet: "+targetRpc,
            "RpcSs vor Start: "+running(before)+"; nach Ende: "+running(after)+". Meldungen anderer Prozesse beweisen keinen Spielabsturz.");
        if(fatalImport) {
            a.summary="Fehlende DLL im Spielprozess protokolliert.";
            a.add("FAIL","BELEGT","DLL-Auflösung fehlgeschlagen",join(a.errors),"Genau die genannte DLL, Architektur und Wine-Override prüfen; keine Neuinstallation der MIX-Dateien auf Verdacht.");
        } else if(a.crashed || (exit!=null && (exit==0xc0000005 || exit==0xc000001d || exit==0xc000007b))) {
            a.summary="Absturz bzw. Windows-Fehlerstatus des Spiels belegt.";
            a.add("FAIL","BELEGT","Spielabsturz / NTSTATUS", decode(exit)+"\n"+join(a.errors),"Fehleradresse und zugehöriges Modul aus dem Backtrace untersuchen. Illegal Instruction: Box64/CPU-Pfad; Invalid Image: DLL-Architektur.");
        } else if(helperRequested && (inj.contains("could not") || inj.contains("cannot find") || inj.contains("createprocess failed") || inj.contains("cannot open") || inj.contains("timeout"))) {
            a.summary="CD-Kompatibilitätshelfer meldet einen konkreten Startfehler.";
            a.add("FAIL","BELEGT","Kompatibilitätshelfer fehlgeschlagen",tail(injector,2200),"Der Helferfehler ist vor dem eigentlichen Spieleinstieg zu beheben. SafeDisc-/Wine-Hook-Fehler im separaten Helferprotokoll prüfen.");
        } else if(helperRequested && (h.contains("unable to hook") || h.contains("minhook init failed") || h.contains("no offsets found"))) {
            a.summary="SafeDisc-Helfer kann einen benötigten Hook nicht einrichten.";
            a.add("FAIL","BELEGT","CD-Kompatibilität unvollständig",tail(helper,2200),"Konkreten Hook und gemeldete SafeDisc-Version prüfen. Ein vorhandener secdrv.sys-Treiber löst diesen Hook-Fehler nicht.");
        } else if(!window && exit!=null && exit==0) {
            a.summary="Frühes Spielende mit Code 0 belegt; die Ursache ist noch offen.";
            a.add("WARN","BELEGT","Kein Spielfenster trotz Spielende",decode(exit)+"; EXE geladen: "+a.loaded,"Exit 0 ist kein Absturzbeleg und ohne Spielfenster kein Spielbarkeitstest.");
            a.add("WARN","HYPOTHESE","CD-Prüfung oder frühe Initialisierung", "CD-Helfer angefordert: "+helperRequested+"; geladen: "+a.helperLoaded+"; "+safeVersion(helper),
                helperRequested?"Helferprotokoll auf erfolgreiche Hooks/Entschlüsselung prüfen. Wenn diese erfolgreich sind, den letzten Spiel-Dateizugriff und Grafik-/Audio-Initialisierung untersuchen.":"SafeDisc-Signatur und tatsächlich gewählten Startweg prüfen. Keine Ursache allein aus secdrv.sys ableiten.");
        } else if(!window && exit==null) {
            a.summary="Kein abgeschlossenes Spielergebnis erfasst; Hängen, manueller Abbruch und Messlücke bleiben unterscheidbar.";
            a.add("WARN","MESSLÜCKE","Spielende nicht erfasst","game-exit.txt fehlt oder ist unvollständig.","Sitzungsereignisse und Prozess-/Helfer-Snapshot vergleichen; keinen erfolgreichen Start oder Absturz behaupten.");
        } else if(window) {
            a.summary="Spielfenster belegt; tatsächliches Gameplay wurde nicht automatisch geprüft.";
            a.add("INFO","BELEGT","Fenster erreicht","Das RA2/Yuri-Fenster wurde erkannt.","Hauptmenü, Kampagne/Skirmish und Eingabe müssen im Spiel überprüft werden.");
        } else {
            a.add("WARN","MESSLÜCKE","Rückgabecode ohne eindeutige Ursache",decode(exit),"Letzte Spiel-TID-Fehler und Helferlog auswerten; Rückgabecode allein benennt keine Ursache.");
        }
        if(start<0) a.add("WARN","MESSLÜCKE","Startmarker fehlt","Spielphase kann zeitlich nicht sicher abgegrenzt werden.","Mit diesem Build erneut starten; ältere/rotierte Protokolle nicht mit neuen Ergebnissen vermischen.");
        if(a.threads.isEmpty()) a.add("WARN","MESSLÜCKE","Spiel-TID fehlt","Keine eindeutig zugeordnete GAME.EXE/GAMEMD.EXE-Zeile.","Wine-Trace oder Start des Kompatibilitätshelfers prüfen; Warnungen aus anderen Prozessen bleiben unzugeordnet.");
        if(helperRequested && !a.helperLoaded) a.add("WARN","MESSLÜCKE","Helferinitialisierung nicht bestätigt","Kein „Version.DLL Loaded!“ im aktuellen Helferlog.","Fehlendes Log beweist keinen Kopierschutzfehler; Injector-Log und Dateipfad prüfen.");
        List<String> exitCalls = new ArrayList<>();
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (a.threads.contains(actor(line)) && (lower.contains("exitprocess(") || lower.contains("exituserprocess(") || lower.contains("terminateprocess("))) {
                if (exitCalls.size()<6) exitCalls.add(line);
            }
        }
        if (!exitCalls.isEmpty()) a.add("INFO","BELEGT","Beendigungsaufrufe des Spiel-TIDs",join(exitCalls),
            "ret= bezeichnet die protokollierte Rücksprungadresse. Adresse dem geladenen Modul zuordnen; der Aufruf allein erklärt noch nicht den Auslöser.");
        else a.add("INFO","MESSLÜCKE","Beendigungsaufruf nicht protokolliert","Kein passender Relay-Aufruf im Spiel-TID.",
            "Ältere Builds hatten keine gezielte Relay-Aufzeichnung. Auch mit Relay bleiben direkte/unprotokollierte Aufrufe möglich.");
        if(!a.errors.isEmpty()) a.add("INFO","BELEGT","Fehler des Spiel-TIDs",join(a.errors),"Originalzeilen bleiben für die Ursachenprüfung erhalten.");
        if(!a.files.isEmpty()) a.add("WARN","BELEGT","Fehlgeschlagene Dateizugriffe des Spiels",join(a.files),"Auch optionale Suchpfade können fehlschlagen. Erst Zusammenhang mit benötigter Datei und anschließendem Spielende prüfen.");
        if(!a.modules.isEmpty()) a.add("INFO","BELEGT","Im Spiel-TID geladene Module",join(a.modules),"Native/builtin und Architektur im Rohprotokoll prüfen; Laden allein beweist keine funktionierende DLL.");
        return a;
    }
    public static String sha256(java.io.File file) {
        if (file == null || !file.isFile()) return "missing";
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[65536]; int count;
            while((count=input.read(buffer))!=-1) digest.update(buffer,0,count);
            StringBuilder value=new StringBuilder();
            for(byte b:digest.digest()) value.append(String.format(Locale.ROOT,"%02x",b & 255));
            return value.toString();
        } catch(Exception error) { return "unreadable: " + error.getClass().getSimpleName(); }
    }
    private static String running(String s) { if(s==null || s.isEmpty()) return "unbekannt"; return s.toLowerCase(Locale.ROOT).contains("running")?"RUNNING belegt":"RUNNING nicht belegt"; }
    private static String safeVersion(String s) { for(String l:s.split("\\r?\\n")) if(l.contains("SafeDisc Version:")) return l;return "SafeDisc-Version nicht im Helferlog belegt"; }
    public static String decode(Integer c) {
        if(c==null) return "kein Spielcode";
        String name=c==0?"normaler Rückgabecode":c==0xc0000005?"STATUS_ACCESS_VIOLATION":c==0xc000001d?"STATUS_ILLEGAL_INSTRUCTION":c==0xc000007b?"STATUS_INVALID_IMAGE_FORMAT":c==0xc0000135?"STATUS_DLL_NOT_FOUND":c==137?"137: mögliche SIGKILL-Konvention; Verursacher nicht belegt":"programmspezifischer Rückgabecode";
        return c+" / "+String.format(Locale.ROOT,"0x%08X",c)+" – "+name;
    }
    private static String tail(String s,int size) { return s.length()>size?s.substring(s.length()-size):s; }
    private static String join(Collection<String> s) { return String.join("\n",s); }
}
