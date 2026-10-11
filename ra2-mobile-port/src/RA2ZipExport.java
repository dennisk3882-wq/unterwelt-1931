package com.winlator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Validate ZIP before exposing it to Android's Storage Access Framework. */
public final class RA2ZipExport {
    private RA2ZipExport() {}

    /**
     * Capture a bounded, consistent file snapshot. For large logs retain both
     * the initial startup evidence and the final exit/error evidence.
     * No unbounded readLine() loop against a concurrently growing Wine trace.
     */
    public static void appendSnapshot(java.util.zip.ZipOutputStream zip,
                                      File file, String entryName,
                                      String[] secrets, int byteLimit) throws IOException {
        if (!file.isFile()) return;
        int cap = Math.max(4096, Math.min(byteLimit, 1024 * 1024));
        String contents;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long length = raf.length();
            if (length <= cap) {
                byte[] all = new byte[(int) length];
                raf.readFully(all);
                contents = new String(all, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                int beginning = Math.min(65536, cap / 4);
                int ending = cap - beginning;
                byte[] head = new byte[beginning], tail = new byte[ending];
                raf.readFully(head);
                raf.seek(length - ending);
                raf.readFully(tail);
                contents = new String(head, java.nio.charset.StandardCharsets.UTF_8)
                    + "\n\n[RA2-DIAGNOSE: " + (length - cap)
                    + " Bytes im Mittelteil ausgelassen; Anfang und Ende der Datei erhalten]\n\n"
                    + new String(tail, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        // Redact before appending bytes, never export ISO images, registry or CD keys.
        String safe = RA2SerialPolicy.redact(contents, secrets);
        zip.putNextEntry(new java.util.zip.ZipEntry(entryName));
        zip.write(safe.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    public static long verify(File zip) throws IOException {
        if (zip == null || !zip.isFile() || zip.length() < 150)
            throw new IOException("ZIP-Datei ist leer oder fehlt");
        try (FileInputStream header = new FileInputStream(zip)) {
            byte[] magic = new byte[4];
            if (header.read(magic) != 4 ||
                    magic[0] != 0x50 || magic[1] != 0x4b ||
                    magic[2] != 0x03 || magic[3] != 0x04)
                throw new IOException("ZIP-Kopf ungueltig");
        }
        try (ZipFile contents = new ZipFile(zip)) {
            ZipEntry report = contents.getEntry("Diagnosebericht.txt");
            if (report == null || report.getSize() < 32)
                throw new IOException("Diagnosebericht fehlt in ZIP");
            if (contents.size() < 2)
                throw new IOException("ZIP enthaelt keine Sitzungsdaten");
        }
        return zip.length();
    }
}