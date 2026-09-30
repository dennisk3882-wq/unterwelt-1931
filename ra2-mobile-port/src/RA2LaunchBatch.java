package com.winlator;

/** A synchronous launch: launcher lifetime is independent of game lifetime. */
public final class RA2LaunchBatch {
    private RA2LaunchBatch() {}

    public static String create(String exe) {
        if (exe == null || !exe.matches("(?i)C:\\\\Westwood\\\\RA2\\\\(?:game|gamemd)\\.exe")) {
            throw new IllegalArgumentException("Unexpected game path: " + exe);
        }
        return "@echo off\r\n"
            + "echo stage=rpc-preflight-started>>C:\\RA2Mobile\\ra2-live.log\r\n"
            + "C:\\windows\\system32\\sc.exe start RpcSs >C:\\RA2Mobile\\rpcss-preflight.txt 2>&1\r\n"
            + "C:\\windows\\system32\\sc.exe query RpcSs >>C:\\RA2Mobile\\rpcss-preflight.txt 2>&1\r\n"
            + "echo stage=game-command-started>>C:\\RA2Mobile\\ra2-live.log\r\n"
            + "cd /d C:\\Westwood\\RA2\r\n"
            + "start /wait \"\" \"" + exe + "\"\r\n"
            + "set RA2_GAME_EXIT=%ERRORLEVEL%\r\n"
            + "echo %RA2_GAME_EXIT%>C:\\RA2Mobile\\game-exit.txt\r\n"
            + "exit /b %RA2_GAME_EXIT%\r\n";
    }
}
