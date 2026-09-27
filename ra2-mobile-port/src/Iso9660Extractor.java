package com.winlator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Small ISO-9660 extractor for original game CDs.
 *
 * It intentionally supports the plain ISO-9660 directory tree used by the
 * Red Alert 2 / Yuri media. This keeps disc preparation in native Android
 * storage and avoids starting Wine merely to unpack an ISO.
 */
public final class Iso9660Extractor {
    public interface ProgressListener {
        void onProgress(long extractedBytes, long imageBytes, String currentName);
    }

    private static final int SECTOR = 2048;
    private static final int FIRST_DESCRIPTOR = 16;
    private static final int LAST_DESCRIPTOR = 64;

    private final RandomAccessFile iso;
    private final File destination;
    private final ProgressListener listener;
    private final long imageBytes;
    private final Set<String> visitedDirectories = new HashSet<>();
    private long extractedBytes;

    private Iso9660Extractor(File image, File destination, ProgressListener listener) throws IOException {
        this.iso = new RandomAccessFile(image, "r");
        this.destination = destination;
        this.listener = listener;
        this.imageBytes = iso.length();
    }

    public static void extract(File image, File destination, ProgressListener listener) throws IOException {
        if (image == null || !image.isFile()) throw new IOException("ISO file not found");
        if (destination == null) throw new IOException("Destination missing");
        if (!destination.isDirectory() && !destination.mkdirs()) {
            throw new IOException("Could not create destination");
        }

        Iso9660Extractor extractor = new Iso9660Extractor(image, destination, listener);
        try {
            extractor.extractInternal();
        }
        finally {
            extractor.iso.close();
        }
    }

    private void extractInternal() throws IOException {
        byte[] descriptor = new byte[SECTOR];
        boolean found = false;
        int rootExtent = 0;
        long rootLength = 0;

        for (int sector = FIRST_DESCRIPTOR; sector <= LAST_DESCRIPTOR; sector++) {
            iso.seek((long) sector * SECTOR);
            iso.readFully(descriptor);

            int type = descriptor[0] & 0xff;
            String id = new String(descriptor, 1, 5, StandardCharsets.US_ASCII);
            int version = descriptor[6] & 0xff;

            if ("CD001".equals(id) && version == 1 && type == 1) {
                int root = 156;
                int recordLength = descriptor[root] & 0xff;
                if (recordLength < 34) throw new IOException("Invalid ISO root directory record");
                rootExtent = le32(descriptor, root + 2);
                rootLength = uint32(descriptor, root + 10);
                found = true;
                break;
            }

            if ("CD001".equals(id) && type == 255) break;
        }

        if (!found) {
            throw new IOException("No ISO-9660 primary volume descriptor found");
        }

        extractDirectory(rootExtent, rootLength, destination, 0);
    }

    private void extractDirectory(int extent, long dataLength, File outDir, int depth) throws IOException {
        if (depth > 32) throw new IOException("ISO directory nesting is too deep");

        String visitKey = extent + ":" + dataLength;
        if (!visitedDirectories.add(visitKey)) return;

        long start = (long) extent * SECTOR;
        long cursor = start;
        long end = start + dataLength;
        byte[] record = new byte[256];

        while (cursor < end) {
            iso.seek(cursor);
            int recordLength = iso.readUnsignedByte();

            if (recordLength == 0) {
                long nextSector = ((cursor / SECTOR) + 1) * SECTOR;
                if (nextSector <= cursor) break;
                cursor = nextSector;
                continue;
            }

            if (recordLength < 34 || recordLength > record.length) {
                throw new IOException("Invalid ISO directory record");
            }

            iso.seek(cursor);
            iso.readFully(record, 0, recordLength);

            int childExtent = le32(record, 2);
            long childLength = uint32(record, 10);
            int flags = record[25] & 0xff;
            int nameLength = record[32] & 0xff;

            if (33 + nameLength > recordLength) {
                throw new IOException("Invalid ISO file identifier");
            }

            if (!(nameLength == 1 && ((record[33] & 0xff) == 0 || (record[33] & 0xff) == 1))) {
                String rawName = new String(record, 33, nameLength, StandardCharsets.US_ASCII);
                String name = normalizeName(rawName);
                if (!name.isEmpty()) {
                    File child = safeChild(outDir, name);
                    boolean directory = (flags & 0x02) != 0;

                    if (directory) {
                        if (!child.isDirectory() && !child.mkdirs()) {
                            throw new IOException("Could not create directory: " + child.getName());
                        }
                        extractDirectory(childExtent, childLength, child, depth + 1);
                    }
                    else {
                        extractFile(childExtent, childLength, child, name);
                    }
                }
            }

            cursor += recordLength;
        }
    }

    private void extractFile(int extent, long dataLength, File output, String name) throws IOException {
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create output directory");
        }

        long remaining = dataLength;
        iso.seek((long) extent * SECTOR);

        try (FileOutputStream out = new FileOutputStream(output, false)) {
            byte[] buffer = new byte[1024 * 1024];
            while (remaining > 0) {
                int wanted = (int)Math.min(buffer.length, remaining);
                int read = iso.read(buffer, 0, wanted);
                if (read < 0) throw new IOException("Unexpected end of ISO while extracting " + name);
                out.write(buffer, 0, read);
                remaining -= read;
                extractedBytes += read;
                if (listener != null) listener.onProgress(extractedBytes, imageBytes, name);
            }
            out.flush();
        }
    }

    private static File safeChild(File parent, String name) throws IOException {
        if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) {
            throw new IOException("Unsafe ISO path");
        }

        File child = new File(parent, name);
        String parentPath = parent.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        if (!childPath.startsWith(parentPath + File.separator)) {
            throw new IOException("ISO path escaped destination");
        }
        return child;
    }

    private static String normalizeName(String raw) {
        String name = raw;
        int semicolon = name.indexOf(';');
        if (semicolon >= 0) name = name.substring(0, semicolon);
        while (name.endsWith(".")) name = name.substring(0, name.length() - 1);
        return name.trim();
    }

    private static int le32(byte[] data, int offset) {
        return (data[offset] & 0xff)
            | ((data[offset + 1] & 0xff) << 8)
            | ((data[offset + 2] & 0xff) << 16)
            | ((data[offset + 3] & 0xff) << 24);
    }

    private static long uint32(byte[] data, int offset) {
        return Integer.toUnsignedLong(le32(data, offset));
    }
}
