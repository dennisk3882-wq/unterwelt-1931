package com.winlator;

/** A synchronous launch: launcher lifetime is independent of game lifetime. */
public final class RA2LaunchBatch {
    private RA2LaunchBatch() {}

    public static Integer readGameExit(java.io.File file) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line = reader.readLine();
            if (line == null) return null;
            long code = Long.parseLong(line.trim());
            return code >= Integer.MIN_VALUE && code <= 0xffffffffL ? (int) code : null;
        }
        catch (Exception ignored) { return null; }
    }

    public static boolean isGameWindow(String className) {
        String cls = className == null ? "" : className.toLowerCase(java.util.Locale.ROOT);
        return cls.equals("red alert 2") || cls.equals("yuri's revenge");
    }

    public static String gamePhase(String log) {
        if (log == null) return "";
        int start = log.lastIndexOf("stage=game-command-started");
        if (start < 0) return "";
        int end = log.indexOf("stage=game-command-ended", start);
        if (end < 0) end = log.indexOf("stage=game-exited", start);
        return log.substring(start, end < 0 ? log.length() : end);
    }

    /** Exact on-disk SafeDisc version signatures; never infer from a stray driver. */
    public static boolean isSafeDisc(java.io.File exe) {
        if (exe == null || !exe.isFile() || exe.length() < 64 || exe.length() > 32 * 1024 * 1024) return false;
        try (java.io.FileInputStream input = new java.io.FileInputStream(exe)) {
            byte[] data = new byte[(int) exe.length()];
            int count = 0, read;
            while (count < data.length && (read = input.read(data, count, data.length - count)) > 0) count += read;
            if (count != data.length) return false;
            byte[][] markers = {"BoG_ *90.0&!!  Yy>".getBytes("US-ASCII"), "000001_!!!".getBytes("US-ASCII")};
            int[] offsets = {32, 11};
            for (int m = 0; m < markers.length; m++) {
                byte[] marker = markers[m];
                for (int i = 0; i + offsets[m] + 12 <= data.length; i++) {
                    boolean matches = true;
                    for (int j = 0; j < marker.length; j++) if (data[i+j] != marker[j]) { matches = false; break; }
                    if (!matches) continue;
                    for (int j = marker.length; j < offsets[m]; j++) if (data[i+j] != 0) { matches = false; break; }
                    int pos = i + offsets[m];
                    int major = data[pos] & 255;
                    if (matches && major >= 2 && major <= 4 && data[pos+1] == 0 && data[pos+2] == 0 && data[pos+3] == 0) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static String create(String exe) { return create(exe, false); }

    public static String create(String exe, boolean cdCompatibility) {
        if (exe == null || !exe.matches("(?i)C:\\\\Westwood\\\\RA2\\\\(?:game|gamemd)\\.exe")) {
            throw new IllegalArgumentException("Unexpected game path: " + exe);
        }
        String command = cdCompatibility ? "\"C:\\RA2Mobile\\SafeDisc\\VersionInjector.exe\" \"" + exe + "\"" : "\"" + exe + "\"";
        String batch = "@echo off\r\n"
            + "echo stage=rpc-preflight-started>>C:\\RA2Mobile\\ra2-live.log\r\n"
            + "C:\\windows\\system32\\sc.exe start RpcSs >C:\\RA2Mobile\\rpcss-preflight.txt 2>&1\r\n"
            + "C:\\windows\\system32\\sc.exe query RpcSs >>C:\\RA2Mobile\\rpcss-preflight.txt 2>&1\r\n"
            + "set RA2_REGSVR=C:\\windows\\system32\\regsvr32.exe\r\n"
            + "if exist C:\\windows\\syswow64\\regsvr32.exe set RA2_REGSVR=C:\\windows\\syswow64\\regsvr32.exe\r\n"
            + "%RA2_REGSVR% /s C:\\Westwood\\RA2\\blowfish.dll >C:\\RA2Mobile\\blowfish-register.txt 2>&1\r\n"
            + "echo registerExit=%ERRORLEVEL%>>C:\\RA2Mobile\\blowfish-register.txt\r\n"
            + "echo stage=game-command-started>>C:\\RA2Mobile\\ra2-live.log\r\n"
            + "cd /d C:\\Westwood\\RA2\r\n"
            + "start /wait \"\" " + command + "\r\n"
            + "set RA2_GAME_EXIT=%ERRORLEVEL%\r\n"
            + "echo stage=game-command-ended>>C:\\RA2Mobile\\ra2-live.log\r\n"
            + "C:\\windows\\system32\\sc.exe query RpcSs >C:\\RA2Mobile\\rpcss-after-game.txt 2>&1\r\n"
            + "echo %RA2_GAME_EXIT%>C:\\RA2Mobile\\game-exit.tmp\r\n"
            + "move /y C:\\RA2Mobile\\game-exit.tmp C:\\RA2Mobile\\game-exit.txt >nul\r\n"
            + "exit /b %RA2_GAME_EXIT%\r\n";
        StringBuilder mirrored = new StringBuilder();
        for (String line : batch.split("\r\n")) {
            mirrored.append(line).append("\r\n");
            if (line.startsWith("echo stage=")) {
                mirrored.append(line.replace("ra2-live.log", "ra2-evidence.log")).append("\r\n");
                mirrored.append(line.replace("ra2-live.log", "events.log")).append("\r\n");
            }
        }
        return mirrored.toString();
    }
}
