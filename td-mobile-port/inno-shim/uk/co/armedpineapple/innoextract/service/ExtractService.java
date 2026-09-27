package uk.co.armedpineapple.innoextract.service;

import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Small compatibility shim for alanwoolley/innoextract-android.
 *
 * The bundled native library expects the original class/package name and
 * passes an Android file descriptor rather than a pathname.
 */
public final class ExtractService {
    private String currentFile;

    static {
        System.loadLibrary("innoextract");
    }

    private native void nativePrepare();
    private native int nativeExtract(int sourceFd, String extractDir);

    public int extract(File source, File outputDirectory) throws IOException {
        if (source == null || !source.isFile()) {
            throw new IOException("German installer package is missing");
        }
        if (!outputDirectory.exists()
                && !outputDirectory.mkdirs()
                && !outputDirectory.isDirectory()) {
            throw new IOException("Could not create extraction directory");
        }

        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                source, ParcelFileDescriptor.MODE_READ_ONLY)) {
            nativePrepare();
            return nativeExtract(descriptor.getFd(), outputDirectory.getAbsolutePath());
        }
    }

    // Called from the native compatibility layer. Logging/progress is optional
    // for our installer because the Android activity already shows its own UI.
    public void gotString(String value, int streamNo) {
    }

    public void updateProgress(long progress, long total) {
    }

    public void updateCurrentFile(String fileName) {
        currentFile = fileName;
    }

    public OutputFile newFile(byte[] path) {
        return new DirectAccessFile(new String(path, StandardCharsets.UTF_8));
    }

    public String getCurrentFile() {
        return currentFile;
    }
}
