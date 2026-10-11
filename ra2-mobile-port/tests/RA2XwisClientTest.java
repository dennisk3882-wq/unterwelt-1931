import com.winlator.RA2XwisClient;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

public class RA2XwisClientTest {
    public static void main(String[] args) throws Exception {
        File base=Files.createTempDirectory("ra2-xwis-test").toFile();
        try {
            File orig=new File(base,"original"), client=new File(base,"client");
            orig.mkdirs();client.mkdirs();
            byte[] movie=new byte[]{1,2,3,4};
            Files.write(new File(orig,"movies01.mix").toPath(),movie);
            Files.write(new File(orig,"game.exe").toPath(),"original".getBytes(StandardCharsets.UTF_8));
            Files.write(new File(client,"game.exe").toPath(),new byte[500000]);
            int n=RA2XwisClient.addOwnedResources(orig,client);
            if(n!=1 || !new File(client,"movies01.mix").isFile())throw new AssertionError("Campaign assets missing");
            if(RA2XwisClient.addOwnedResources(orig,client)!=0)throw new AssertionError("Idempotence");
            if(Files.size(new File(client,"game.exe").toPath())!=500000)throw new AssertionError("EXE overwritten");
            if(RA2XwisClient.findClient(client,false)==null)throw new AssertionError("Client not detected");
            if(!RA2XwisClient.dosClientPath(new File(client,"game.exe")).equals("C:\\RA2Mobile\\XWISClient\\game.exe"))
                throw new AssertionError("Win32 path");
            if(!RA2XwisClient.installerArgs().endsWith("XWISClient"))throw new AssertionError("NSIS /D not last");
            boolean rejected=false;
            try{RA2XwisClient.dosClientPath(new File(client,"setup.exe"));}catch(IllegalArgumentException expected){rejected=true;}
            if(!rejected)throw new AssertionError("Unsafe launch file accepted");
        } finally {
            java.nio.file.Files.walk(base.toPath()).sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});
        }
        System.out.println("XWIS alternate-client staging and original data tests passed");
    }
}