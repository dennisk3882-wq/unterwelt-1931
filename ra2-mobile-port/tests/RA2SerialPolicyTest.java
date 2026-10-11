import com.winlator.RA2SerialPolicy;
public class RA2SerialPolicyTest {
 public static void main(String[] args) {
  String synthetic="AB12-CD34-EF56-GH78";
  if(!RA2SerialPolicy.acceptableInput(synthetic) || !RA2SerialPolicy.normalize(synthetic).equals("AB12CD34EF56GH78")) throw new AssertionError("Grouped pasted key");
  if(RA2SerialPolicy.acceptableInput("0") || RA2SerialPolicy.acceptableInput("AB12;echo hacked") || RA2SerialPolicy.acceptableInput("AB12/CD34EF56")) throw new AssertionError("Unsafe/incomplete input accepted");
  if(!RA2SerialPolicy.resolve("", "legacy-value", "64bit-value").equals("legacy-value")) throw new AssertionError("Existing 32-bit serial lost");
  if(!RA2SerialPolicy.resolve("", "0", "legacy-value").equals("legacy-value")) throw new AssertionError("Placeholder overwrote existing key");
  if(!RA2SerialPolicy.resolve(synthetic,"old","old").equals("AB12CD34EF56GH78")) throw new AssertionError("New input not applied");
  if(!RA2SerialPolicy.resolve("",null,null).equals("0")) throw new AssertionError("Unset state");
  if(RA2SerialPolicy.state(synthetic).contains(synthetic)) throw new AssertionError("Key exposed by diagnostic state");
  String batch=com.winlator.RA2LaunchBatch.create("C:\\Westwood\\RA2\\GAME.EXE");
  if(!batch.contains("/v Serial >nul 2>&1") || !batch.contains("serialQueryExit=%ERRORLEVEL%")) throw new AssertionError("Serial probe leaks/misses status");
  if(!batch.contains("syswow64\\reg.exe") || !batch.contains("HKLM\\Software\\Westwood\\Red Alert 2")) throw new AssertionError("Wrong view/product");
  if(!com.winlator.RA2LaunchBatch.create("C:\\Westwood\\RA2\\GAMEMD.EXE").contains("HKLM\\Software\\Westwood\\Yuri's Revenge")) throw new AssertionError("Yuri not separated");
  String probe="installQueryExit=0\r\nfolderQueryExit=0\r\nserialQueryExit=0\r\n";
  if(!RA2SerialPolicy.probeState(probe).equals("READABLE")) throw new AssertionError("Complete runtime proof");
  if(!RA2SerialPolicy.probeState(probe.replace("serialQueryExit=0","serialQueryExit=1")).equals("QUERY_FAILED")) throw new AssertionError("Failed query hidden");
  if(!RA2SerialPolicy.probeState("installQueryExit=0\n").equals("INCOMPLETE")) throw new AssertionError("Incomplete probe treated as proof");
  if(!RA2SerialPolicy.probeState(probe.replace("installQueryExit=0","installQueryExit=9009")).equals("TOOL_UNAVAILABLE")) throw new AssertionError("Tool failure blamed on registry");
  String masked=RA2SerialPolicy.redact("value="+synthetic+" plain=AB12CD34EF56GH78 remains=OK",synthetic);
  if(masked.contains(synthetic) || masked.contains("AB12CD34EF56GH78") || !masked.contains("remains=OK")) throw new AssertionError("Key redaction failed");
  System.out.println("PASS: key normalization, preservation, separate products, private serial probe");
 }
}
