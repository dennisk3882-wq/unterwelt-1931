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
        int end = log.indexOf("stage=game-exited", start);
        return log.substring(start, end < 0 ? log.length() : end);
    }

    public static String create(String exe) {
        if (exe == null || !exe.matches("(?i)C:\\\\Westwood\\\\RA2\\\\(?:game|gamemd)\\.exe")) {
            throw new IllegalArgumentException("Unexpected game path: " + exe);
        }
        return "@echo off\r\n"
            + "echo stage=rpc-preflight-started>>C:\\RA2Mobile\\ra2-live.log\r\n"
            + "C:\\windows\\system32\\sc.exe start RpcSs >C:\\RA2Mobile\\rpcss-preflight.txt 2>&1\r\n"
            + "C:\\windows\\system32\\sc.exe query RpcSs >>C:\\RA2Mobile\\rpcss-preflight.txt 2>&1\r\n"
            + "set RA2_REGSVR=C:\\windows\\system32\\regsvr32.exe\r\n"
            + "if exist C:\\windows\\syswow64\\regsvr32.exe set RA2_REGSVR=C:\\windows\\syswow64\\regsvr32.exe\r\n"
            + "%RA2_REGSVR% /s C:\\Westwood\\RA2\\blowfish.dll >C:\\RA2Mobile\\blowfish-register.txt 2>&1\r\n"
            + "echo registerExit=%ERRORLEVEL%>>C:\\RA2Mobile\\blowfish-register.txt\r\n"
            + "echo stage=game-command-started>>C:\\RA2Mobile\\ra2-live.log\r\n"
            + "cd /d C:\\Westwood\\RA2\r\n"
            + "start /wait \"\" \"" + exe + "\"\r\n"
            + "set RA2_GAME_EXIT=%ERRORLEVEL%\r\n"
            + "C:\\windows\\system32\\sc.exe query RpcSs >C:\\RA2Mobile\\rpcss-after-game.txt 2>&1\r\n"
            + "echo %RA2_GAME_EXIT%>C:\\RA2Mobile\\game-exit.tmp\r\n"
            + "move /y C:\\RA2Mobile\\game-exit.tmp C:\\RA2Mobile\\game-exit.txt >nul\r\n"
            + "exit /b %RA2_GAME_EXIT%\r\n";
    }
}
