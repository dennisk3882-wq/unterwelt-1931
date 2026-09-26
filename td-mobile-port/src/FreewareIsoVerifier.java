package org.tiberiandawn.android;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Integrity checks for the known public game-data packages. */
public final class FreewareIsoVerifier {
    public enum Result { NOT_KNOWN_FREEWARE, VALID, INVALID_SIZE, INVALID_HASH }

    private FreewareIsoVerifier() {}

    public static Result verify(File stagedFile, String originalDisplayName)
            throws IOException {
        if (stagedFile == null || originalDisplayName == null) {
            return Result.NOT_KNOWN_FREEWARE;
        }

        if (FreewareCatalog.GDI_FILENAME.equalsIgnoreCase(originalDisplayName)) {
            return verifyKnownFile(stagedFile, FreewareCatalog.GDI_SIZE,
                FreewareCatalog.GDI_MD5);
        } else if (FreewareCatalog.NOD_FILENAME.equalsIgnoreCase(originalDisplayName)) {
            return verifyKnownFile(stagedFile, FreewareCatalog.NOD_SIZE,
                FreewareCatalog.NOD_MD5);
        }
        return Result.NOT_KNOWN_FREEWARE;
    }

    public static Result verifyKnownFile(File file, long expectedSize,
                                         String expectedMd5)
            throws IOException {
        if (file == null || !file.isFile()) return Result.INVALID_SIZE;
        if (file.length() != expectedSize) return Result.INVALID_SIZE;
        return expectedMd5.equalsIgnoreCase(md5(file))
            ? Result.VALID : Result.INVALID_HASH;
    }

    public static String md5(File file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("MD5 unavailable", error);
        }

        byte[] buffer = new byte[256 * 1024];
        try (BufferedInputStream input =
                 new BufferedInputStream(new FileInputStream(file))) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Verification cancelled");
                }
                digest.update(buffer, 0, count);
            }
        }

        StringBuilder result = new StringBuilder(32);
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }
}
