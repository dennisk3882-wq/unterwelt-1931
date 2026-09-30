import com.winlator.CabExtractor;
import java.io.*;import java.nio.file.*;import java.util.*;
public class CabExtractorTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  File root=new File("test-native/fixtures"),out=new File("test-native/output");out.mkdirs();
  CabExtractor.extractRequired(root,out,new String[]{"ra2.mix"},n->{});
  byte[] good=Files.readAllBytes(new File(root,"expected.bin").toPath());
  check(Arrays.equals(good,Files.readAllBytes(new File(out,"ra2.mix").toPath())),"MSZIP dictionary mismatch");
  try{CabExtractor.extractRequired(new File(root,"bad"),out,new String[]{"ra2.mix"},n->{});throw new AssertionError("truncated CAB accepted");}catch(IOException e){}
  check(Arrays.equals(good,Files.readAllBytes(new File(out,"ra2.mix").toPath())),"old data damaged");
  check(!new File(out,"ra2.mix.extracting").exists(),"staging remains");
  try{CabExtractor.extractRequired(root,out,new String[]{"language.mix"},n->{});throw new AssertionError("missing accepted");}catch(IOException e){}
  try{CabExtractor.extractRequired(root,out,new String[]{"../escape"},n->{});throw new AssertionError("unsafe name accepted");}catch(IOException e){}
  System.out.println("PASS: multiblock MSZIP, case/subfolder matching, truncation, atomic replacement, missing file, path rejection");
 }
}
