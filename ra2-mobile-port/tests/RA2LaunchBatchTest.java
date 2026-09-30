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
        java.io.File result = java.io.File.createTempFile("ra2-game-exit", ".txt");
        for (String value : new String[]{"0", "4294967295", "-1073741819"}) {
            java.nio.file.Files.write(result.toPath(), (value + "\r\n").getBytes());
            if (RA2LaunchBatch.readGameExit(result) != (int) Long.parseLong(value)) throw new AssertionError("Exit code lost");
        }
        java.nio.file.Files.write(result.toPath(), new byte[0]);
        if (RA2LaunchBatch.readGameExit(result) != null) throw new AssertionError("Partial write treated as success");
        result.delete();
        if (RA2LaunchBatch.isGameWindow("ConsoleWindowClass") || RA2LaunchBatch.isGameWindow("explorer")) throw new AssertionError("Helper detected as game");
        if (!RA2LaunchBatch.isGameWindow("Red Alert 2")) throw new AssertionError("Game window missed");
        String timeline = "RPC_S_SERVER_UNAVAILABLE\nstage=game-command-started\ngame output\nstage=game-exited\nshutdown error";
        String phase = RA2LaunchBatch.gamePhase(timeline);
        if (phase.contains("RPC_S") || phase.contains("shutdown") || !phase.contains("game output")) throw new AssertionError("Wrong RPC phase");
        System.out.println("RA2/Yuri launch batch regression checks passed");
    }
}
