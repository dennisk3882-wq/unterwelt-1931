package com.winlator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Validate ZIP before exposing it to Android's Storage Access Framework. */
public final class RA2ZipExport {
    private RA2ZipExport() {}

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