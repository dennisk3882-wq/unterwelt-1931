import com.winlator.RA2LaunchBatch;
public class RA2LaunchBatchTest {
    public static void main(String[] args) throws Exception {
        for (String target : new String[]{"GAME.EXE", "gamemd.exe"}) {
            String path = "C:\\Westwood\\RA2\\" + target;
            String batch = RA2LaunchBatch.create(path);
            if (!batch.contains("start /wait \"\" \"" + path + "\"\r\n")) throw new AssertionError("GUI child must be awaited");
            if (batch.indexOf("sc.exe query RpcSs") > batch.indexOf("start /wait")) throw new AssertionError("RPC preflight order");
            if (batch.indexOf("set RA2_GAME_EXIT=%ERRORLEVEL%") < batch.indexOf("start /wait")) throw new AssertionError("Capture child exit");
            if (batch.contains("\n") && batch.replace("\r\n", "").contains("\n")) throw new AssertionError("Windows line endings");
        }
        for (String invalid : new String[]{"C:\\Westwood\\RA2\\game.exe & calc.exe", "C:\\Westwood\\RA2\\%PATH%.exe", "C:\\elsewhere\\game.exe"}) {
            try { RA2LaunchBatch.create(invalid); throw new AssertionError("Unsafe path accepted"); }
            catch (IllegalArgumentException expected) {}
        }
        String mirrored = RA2LaunchBatch.create("C:\\Westwood\\RA2\\GAME.EXE");
        if (!mirrored.contains("echo stage=game-command-started>>C:\\RA2Mobile\\ra2-evidence.log") ||
            !mirrored.contains("echo stage=game-command-ended>>C:\\RA2Mobile\\events.log")) throw new AssertionError("Batch stages lost from evidence/export");
        java.io.File result = java.io.File.createTempFile("ra2-game-exit", ".txt");
        for (String value : new String[]{"0", "4294967295", "-1073741819"}) {
            java.nio.file.Files.write(result.toPath(), (value + "\r\n").getBytes());
            if (RA2LaunchBatch.readGameExit(result) != (int) Long.parseLong(value)) throw new AssertionError("Exit code lost");
        }
        java.nio.file.Files.write(result.toPath(), new byte[0]);
        if (RA2LaunchBatch.readGameExit(result) != null) throw new AssertionError("Partial write treated as success");
        byte[] protectedExe = new byte[66000];
        byte[] marker = "BoG_ *90.0&!!  Yy>".getBytes("US-ASCII");
        System.arraycopy(marker, 0, protectedExe, 65530, marker.length);
        protectedExe[65530 + 32] = 2;
        java.nio.file.Files.write(result.toPath(), protectedExe);
        if (!RA2LaunchBatch.isSafeDisc(result)) throw new AssertionError("SafeDisc signature missed");
        protectedExe[65530 + 32] = 0;
        java.nio.file.Files.write(result.toPath(), protectedExe);
        if (RA2LaunchBatch.isSafeDisc(result)) throw new AssertionError("Invalid version accepted");
        byte[] alt = new byte[80];
        System.arraycopy("000001_!!!".getBytes("US-ASCII"), 0, alt, 2, 10);
        alt[13] = 2;
        java.nio.file.Files.write(result.toPath(), alt);
        if (!RA2LaunchBatch.isSafeDisc(result)) throw new AssertionError("Alternate signature missed");
        result.delete();
        String compatibility = RA2LaunchBatch.create("C:\\Westwood\\RA2\\GAME.EXE", true);
        if (!compatibility.contains("start /wait \"\" \"C:\\RA2Mobile\\SafeDisc\\VersionInjector.exe\" \"C:\\Westwood\\RA2\\GAME.EXE\"")) throw new AssertionError("Helper or target lost");
        if (RA2LaunchBatch.isGameWindow("ConsoleWindowClass") || RA2LaunchBatch.isGameWindow("explorer")) throw new AssertionError("Helper detected as game");
        if (!RA2LaunchBatch.isGameWindow("Red Alert 2")) throw new AssertionError("Game window missed");
        String timeline = "RPC_S_SERVER_UNAVAILABLE\nstage=game-command-started\ngame output\nstage=game-command-ended\nreceive failed with error 6be\nstage=game-exited\nshutdown error";
        String phase = RA2LaunchBatch.gamePhase(timeline);
        if (phase.contains("RPC_S") || phase.contains("shutdown") || phase.contains("6be") || !phase.contains("game output")) throw new AssertionError("Wrong RPC phase");
        System.out.println("RA2/Yuri launch batch regression checks passed");
    }
}
