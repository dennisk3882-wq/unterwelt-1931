package com.winlator;
import java.util.Locale;
/** Input hygiene only; this does not authenticate an original CD key. */
public final class RA2SerialPolicy {
    public static String normalize(String raw) {
        return raw==null?"":raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
    public static boolean acceptableInput(String raw) {
        return normalize(raw).matches("[A-Z0-9]{10,40}");
    }
    public static String resolve(String input, String view32, String view64) {
        if(acceptableInput(input)) return normalize(input);
        if(present(view32)) return view32;
        if(present(view64)) return view64;
        return "0";
    }
    public static boolean present(String value) {
        return value!=null && !value.trim().isEmpty() && !value.trim().equals("0");
    }
    public static String probeState(String probe) {
        if(probe==null || probe.isEmpty()) return "INCOMPLETE";
        boolean incomplete=false, failed=false;
        for(String field : new String[]{"installQueryExit", "folderQueryExit", "serialQueryExit"}) {
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?m)^"+field+"=(-?[0-9]+)\\r?$").matcher(probe);
            if(!m.find()) { incomplete=true; continue; }
            int code;
            try { code=Integer.parseInt(m.group(1)); } catch(NumberFormatException e) { return "INCOMPLETE"; }
            if(code==9009) return "TOOL_UNAVAILABLE";
            if(code!=0) failed=true;
        }
        return incomplete?"INCOMPLETE":failed?"QUERY_FAILED":"READABLE";
    }
    public static String redact(String text, String... secrets) {
        if(text==null) return "";
        for(String secret : secrets) {
            if(!acceptableInput(secret)) continue;
            String key=normalize(secret);
            StringBuilder pattern=new StringBuilder("(?i)");
            for(int i=0;i<key.length();i++) {
                if(i>0) pattern.append("[\\s-]*");
                pattern.append(java.util.regex.Pattern.quote(String.valueOf(key.charAt(i))));
            }
            text=text.replaceAll(pattern.toString(),"[CD-KEY AUSGEBLENDET]");
        }
        return text;
    }
    public static String state(String value) {
        return present(value)?"Vorhanden; Echtheit nicht geprüft":"Fehlt oder Platzhalter 0";
    }
}
