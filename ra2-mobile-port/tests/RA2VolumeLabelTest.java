import com.winlator.Iso9660Extractor;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class RA2VolumeLabelTest {
    public static void main(String[] args) throws Exception {
        byte[] image = new byte[17 * 2048];
        int sector = 16 * 2048;
        image[sector] = 1;
        System.arraycopy("CD001".getBytes(StandardCharsets.US_ASCII), 0, image, sector + 1, 5);
        image[sector + 6] = 1;
        Arrays.fill(image, sector + 40, sector + 72, (byte)' ');
        byte[] label = "RA2_DISC2".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(label, 0, image, sector + 40, label.length);
        String actual = Iso9660Extractor.readVolumeId(new ByteArrayInputStream(image));
        if (!"RA2_DISC2".equals(actual)) throw new AssertionError("Original disc label lost: " + actual);
        image[sector + 40] = (byte)'\n';
        if (!Iso9660Extractor.readVolumeId(new ByteArrayInputStream(image)).isEmpty())
            throw new AssertionError("Unsafe label accepted");
        System.out.println("RA2 ISO-9660 volume label regression checks passed");
    }
}
