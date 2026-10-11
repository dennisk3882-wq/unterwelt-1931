import com.winlator.RA2ZipExport;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
public class RA2ZipExportTest {
    public static void main(String[] args) throws Exception {
        File file=File.createTempFile("ra2-export-check-", ".zip");
        try {
            try (ZipOutputStream z=new ZipOutputStream(new FileOutputStream(file))) {
                z.putNextEntry(new ZipEntry("Diagnosebericht.txt"));
                z.write(("RA2-Diagnose\n"+"X".repeat(40)).getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
                z.putNextEntry(new ZipEntry("aktueller-lauf/session.json"));
                z.write("{\"version\":\"0.32\"}".getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
            if (RA2ZipExport.verify(file) < 150)
                throw new AssertionError("zip size validation failed");
            try (FileOutputStream f=new FileOutputStream(file)) {}
            boolean failed=false;
            try {RA2ZipExport.verify(file);} catch(java.io.IOException expected) {failed=true;}
            if(!failed) throw new AssertionError("empty zip accepted");
        } finally {file.delete();}
        File input=File.createTempFile("ra2-giant-log", ".log");
        File bounded=File.createTempFile("ra2-bounded", ".zip");
        String key="TESTCDKEY123456";
        try {
            try (FileOutputStream f=new FileOutputStream(input)) {
                f.write(("START\n"+key+"\n").getBytes(StandardCharsets.UTF_8));
                byte[] pad=new byte[8192]; java.util.Arrays.fill(pad,(byte)'X');
                for(int i=0;i<1600;i++) f.write(pad); // 12.5 MiB, tests log truncation
                f.write(("\nLAST CD-API EVENT\n"+key+"\n").getBytes(StandardCharsets.UTF_8));
            }
            try (ZipOutputStream z=new ZipOutputStream(new FileOutputStream(bounded))) {
                RA2ZipExport.appendSnapshot(z,input,"aktueller-lauf/ra2-live.log",new String[]{key},128*1024);
            }
            if(bounded.length()>160000)throw new AssertionError("Log was not capped");
            try(java.util.zip.ZipFile z=new java.util.zip.ZipFile(bounded)) {
                java.util.zip.ZipEntry entry=z.getEntry("aktueller-lauf/ra2-live.log");
                byte[] data=z.getInputStream(entry).readAllBytes();
                String sampled=new String(data,StandardCharsets.UTF_8);
                if(!sampled.contains("START") || !sampled.contains("LAST CD-API EVENT"))
                    throw new AssertionError("head/tail evidence not retained");
                if(!sampled.contains("Bytes im Mittelteil ausgelassen"))
                    throw new AssertionError("missing truncation disclosure");
                if(sampled.contains(key))
                    throw new AssertionError("CD key escaped redaction");
            }
        } finally {input.delete();bounded.delete();}
        System.out.println("RA2 ZIP validation tests passed");
    }
}