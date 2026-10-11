package com.winlator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CD start forensic telemetry. No guessing from warning counts.
 * Preserve only relevant Wine debug output in a separate bounded log.
 */
public final class RA2CdForensics {
    private RA2CdForensics() {}
    public static boolean relevant(String line) {
        if (line == null) return false;
        String l = line.toLowerCase(Locale.ROOT);
        return l.contains("getvolumeinformation") || l.contains("getdrivetype") ||
            l.contains("getlogicaldrives") || l.contains("querydosdevice") ||
            l.contains("deviceiocontrol") || l.contains("ntdeviceiocontrolfile") ||
            l.contains("ioctl_cdrom") || l.contains("ioctl_scsi") ||
            l.contains("secdrv") || l.contains("safedisc") ||
            l.contains("version.dll") ||
            l.contains("messageboxa(") || l.contains("messageboxw(") ||
            l.contains("findfirstfilea(") || l.contains("findfirstfilew(") ||
            l.contains("x:\\\\") || l.contains("\\\\.\\\\x:") ||
            l.contains("drive_x") || l.contains("ra2.dsk");
    }
    public static final class Report {
        public String headline;
        public String stage;
        public final List<String> findings = new ArrayList<>();
        public final List<String> focusedTrace = new ArrayList<>();
    }
    public static Report analyze(String dllStages, String injectorLog, String dllLog,
                                 String cdPreflight, String focused, String metadata) {
        Report r = new Report();
        String d = safe(dllStages), i = safe(injectorLog), h = safe(dllLog);
        String p = safe(cdPreflight), f = safe(focused);
        String lo = d.toLowerCase(Locale.ROOT), hi = h.toLowerCase(Locale.ROOT);
        String li = i.toLowerCase(Locale.ROOT), lf = f.toLowerCase(Locale.ROOT);
        boolean helper = safe(metadata).contains("original-launcher-probe")
            || li.contains("versioninjector starting");
        boolean hasDisc = p.contains("RA2.DSK") && p.toLowerCase(Locale.ROOT).contains("volume in drive x is ra2");
        boolean attached = lo.contains("dllmain-attach");
        boolean entered = lo.contains("load-entry");
        boolean configured = lo.contains("config-loaded=1");
        boolean hooks = lo.contains("hooks-enabled") || hi.contains("hooks complete!");
        boolean injected = li.contains("injection-result=success");
        boolean failed = li.contains("injection-result=failure") ||
            li.contains("createprocess failed") || li.contains("could not create remote thread");
        boolean nativeDll = java.util.regex.Pattern.compile("(?im)Loaded[^\\r\\n]*version\\.dll[^\\r\\n]*: native").matcher(f).find();
        boolean builtinDll = java.util.regex.Pattern.compile("(?im)Loaded[^\\r\\n]*version\\.dll[^\\r\\n]*: builtin").matcher(f).find();
        boolean requestedNative = safe(metadata).contains("\\"nativeVersionDllRequested\\":true");
        boolean cdCalls = lf.contains("getvolumeinformation") || lf.contains("getdrivetype")
            || lf.contains("deviceiocontrol") || lf.contains("secdrv");
        r.stage = "unbekannt";
        if (helper) {
            if (hooks) {r.stage = "Hooks aktiv (protokolliert)"; r.headline="DLL-Hooks bestätigt; CD-Prüfung im Spiel gezielt auswerten.";}
            else if (configured) {r.stage = "DLL-Konfiguration gelesen"; r.headline="DLL erreicht Konfiguration; Hook-Initialisierung nicht bestätigt.";}
            else if (entered) {r.stage = "DLL-Load erreicht"; r.headline="DLL gestartet, Konfigurationsstatus ungeklärt.";}
            else if (attached) {r.stage = "DLL_PROCESS_ATTACH"; r.headline="DLL geladen, aber Load-Funktion nicht bestätigt.";}
            else if (failed) {r.stage = "Injector-Fehler"; r.headline="Injector meldet einen konkreten Prozess-/Injektionsfehler.";}
            else if (injected) {r.stage = "Injektion vom Injector gemeldet"; r.headline="Injector meldet Erfolg, aber DLL-Einstieg ist nicht bestätigt.";}
            else {r.stage = "Keine Initialisierungsmarker"; r.headline="Messlücke zwischen Injector, DLL-Initialisierung und CD-Prüfung.";}
        } else {
            r.headline="Kein eindeutig aktiver CD-Kompatibilitätshelfer in diesem Lauf.";
        }
        r.findings.add("CD in Wine: "+(hasDisc?"Verzeichnis und Name RA2 bestätigt":
            p.isEmpty()?"Kein Laufzeit-CD-Verzeichnisbericht":"CD-Inhalt/Volume nicht vollständig bestätigt"));
        r.findings.add("Injector: "+(failed?"FEHLER belegt":injected?"Injektion gemeldet":i.isEmpty()?"Kein Injector-Log":"Log vorhanden, kein Injektionsresultat"));
        r.findings.add("DLL angehängt: "+yes(attached)+"; Load betreten: "+yes(entered)+
            "; Konfiguration: "+yes(configured)+"; Hooks: "+yes(hooks));
        r.findings.add("Wine DLL-Modus: "+(nativeDll?"VERSION.DLL nativ geladen":builtinDll?"Wine-builtin VERSION.DLL belegt":"native/builtin noch nicht unterscheidbar"));
        if (requestedNative && !nativeDll)
            r.findings.add("MESSLÜCKE: Native DLL angefordert; Wine bestätigt sie im erfassten Trace noch nicht.");
        r.findings.add("CD-API-Aufrufe in eigener Wine-Spur: "+(cdCalls?"vorhanden":"nicht nachgewiesen"));
        if (!attached && !entered && !h.contains("Version.DLL Loaded!"))
            r.findings.add("MESSLÜCKE: Fehlende DLL-Marker dürfen nicht als definitiv fehlende Injektion gewertet werden.");
        if (hasDisc && !hooks)
            r.findings.add("NÄCHSTER TEST: DLL-/Injector-Stufen vor dem eigentlichen Spiel untersuchen; CD-Inhalt ist erreichbar.");
        if (hasDisc && hooks)
            r.findings.add("NÄCHSTER TEST: GetVolumeInformation/DeviceIoControl-Rückgaben, CDFS und Sektorzugriff prüfen.");
        for (String line: f.split("\\r?\\n")) {
            if (!relevant(line) || line.toLowerCase(Locale.ROOT).contains("serial")) continue;
            if (r.focusedTrace.size()==26) r.focusedTrace.remove(0);
            if (line.length()>500) line=line.substring(0,500);
            r.focusedTrace.add(line);
        }
        return r;
    }
    private static String safe(String s){return s==null?"":s;}
    private static String yes(boolean b){return b?"Ja":"nicht belegt";}
}