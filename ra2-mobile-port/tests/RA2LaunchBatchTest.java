import com.winlator.RA2LaunchBatch;
public class RA2LaunchBatchTest {
    public static void main(String[] args) {
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
        System.out.println("RA2/Yuri launch batch regression checks passed");
    }
}
