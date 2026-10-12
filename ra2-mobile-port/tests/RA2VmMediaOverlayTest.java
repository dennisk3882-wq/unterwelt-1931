import com.winlator.RA2VmGameDiscovery;
import com.winlator.RA2VmMediaOverlay;
import java.nio.file.*;
import java.io.File;
import java.util.Arrays;

public class RA2VmMediaOverlayTest {
  private static void check(boolean ok,String error){if(!ok)throw new AssertionError(error);}
  public static void main(String[] args)throws Exception {
    Path root=Files.createTempDirectory("ra2-vm-cd");
    try {
      File game=root.resolve("container/.wine/drive_c/Westwood/RA2").toFile();
      File media=root.resolve("container/.wine/drive_ra2_media").toFile();
      File yuri=root.resolve("container/.wine/drive_yuri_media").toFile();
      check(game.mkdirs()&&media.mkdirs()&&yuri.mkdirs(),"dirs");
      Files.write(new File(game,"GAME.EXE").toPath(),new byte[]{'M','Z'});
      Files.write(new File(media,"RA2.DSK").toPath(),new byte[]{1,2,3});
      Files.write(new File(media,"AUTORUN.INF").toPath(),new byte[]{9});
      Files.write(new File(yuri,"YURI.DSK").toPath(),new byte[]{7});
      RA2VmMediaOverlay overlay=new RA2VmMediaOverlay(game);
      check(overlay.ra2DiscReady(),"Original RA2 CD2 marker missing");
      check(overlay.discFileCount()==2,"Disc inventory");
      check(overlay.resolve("ra2.dsk").getName().equals("RA2.DSK"),"case insensitive CD root lookup");
      check(overlay.resolve("game.exe").getName().equals("GAME.EXE"),"original executable must be first");
      check(overlay.resolve("yuri.dsk").getName().equals("YURI.DSK"),"Yuri disc fallback");
      check(Arrays.asList(overlay.listing("")).contains("RA2.DSK"),"CD file in merged directory");
      check(Arrays.asList(overlay.listing("")).contains("GAME.EXE"),"game file in merged directory");
      boolean denied=false;
      try {overlay.resolve("../../etc/passwd");} catch(java.io.IOException ex){denied=true;}
      check(denied,"Must reject traversal");
      System.out.println("RA2/Yuri virtual CD2 media overlay tests passed");
    } finally {
      try(java.util.stream.Stream<Path> walk=Files.walk(root)){
        walk.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});
      }
    }
  }
}
