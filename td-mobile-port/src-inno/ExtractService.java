package uk.co.armedpineapple.innoextract.service;

import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

/**
 * Minimal in-process adapter for the Android innoextract native library.
 * The library expects the original ExtractService JNI class name and a
 * Linux file descriptor instead of a setup path.
 */
public final class ExtractService {
    static {
        System.loadLibrary("innoextract");
    }

    private native void nativePrepare();
    private native int nativeExtract(int sourceFd, String extractDir);

    public int extract(File installer, File outputDirectory)
            throws FileNotFoundException {
        if (installer == null || !installer.isFile()) {
            throw new FileNotFoundException("Installer not found");
        }
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()
                && !outputDirectory.isDirectory()) {
            throw new RuntimeException("Could not create extraction directory");
        }

        nativePrepare();
        try (ParcelFileDescriptor descriptor =
                 ParcelFileDescriptor.open(installer, ParcelFileDescriptor.MODE_READ_ONLY)) {
            return nativeExtract(descriptor.getFd(), outputDirectory.getAbsolutePath());
        } catch (java.io.IOException error) {
            throw new RuntimeException("Could not open German installer", error);
        }
    }

    // JNI callbacks expected by alanwoolley/innoextract-android.
    public void gotString(String value, int streamNumber) { }
    public void updateProgress(long progress, long total) { }
    public void updateCurrentFile(String fileName) { }

    public OutputFile newFile(byte[] path) {
        return new DirectAccessFile(new String(path, StandardCharsets.UTF_8));
    }
}
