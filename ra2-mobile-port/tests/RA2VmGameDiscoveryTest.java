import com.winlator.RA2VmGameDiscovery;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public class RA2VmGameDiscoveryTest {
  private static void check(boolean v,String error) {if(!v)throw new AssertionError(error);}
  public static void main(String[] args)throws Exception{
    File root=Files.createTempDirectory("ra2vm-android").toFile();
    File notRoot=Files.createTempDirectory("ra2vm-empty").toFile();
    try{
      File dir=new File(root,".wine/drive_c/Westwood/RA2");
      check(dir.mkdirs(),"directory");
      Files.write(new File(dir,"GAME.EXE").toPath(),new byte[]{'M','Z'});
      Files.write(new File(dir,"RA2.MIX").toPath(),new byte[]{1});
      Files.write(new File(dir,"GAMEMD.EXE").toPath(),new byte[]{'M','Z'});
      Files.write(new File(dir,"RA2MD.MIX").toPath(),new byte[]{1});
      RA2VmGameDiscovery.Result result=RA2VmGameDiscovery.find(Arrays.asList(notRoot,root));
      check(result.ra2Ready,"case-insensitive RA2 files are not found");
      check(result.yuriReady,"case-insensitive Yuri files are not found");
      check(result.directory.getCanonicalFile().equals(dir.getCanonicalFile()),"wrong base directory");
      check(result.status.contains("GAME.EXE=true")&&result.status.contains("RA2MD.MIX=true"),
        "diagnostics should report all matching names");
      check(RA2VmGameDiscovery.directChildIgnoreCase(dir,"game.exe").isFile(),"uppercase GAME.EXE");
      boolean invalid=false;
      try{RA2VmGameDiscovery.directChildIgnoreCase(dir,"../game.exe");}catch(IllegalArgumentException expected){invalid=true;}
      check(invalid,"traversal paths not rejected");
      check(!RA2VmGameDiscovery.inspect(notRoot).ra2Ready,"must not falsely detect uninstalled game");
      System.out.println("RA2 VM case-insensitive original game discovery passed");
    }finally {
      for(File base:Arrays.asList(root,notRoot))
        try(java.util.stream.Stream<java.nio.file.Path> walk=Files.walk(base.toPath())) {
          walk.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
            try{Files.deleteIfExists(p);}catch(Exception ignored){}
          });
        }
    }
  }
}
