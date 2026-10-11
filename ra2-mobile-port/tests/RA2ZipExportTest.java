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
        System.out.println("RA2 ZIP validation tests passed");
    }
}