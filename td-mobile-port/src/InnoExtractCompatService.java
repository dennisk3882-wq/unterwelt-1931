package org.tiberiandawn.android;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import uk.co.armedpineapple.innoextract.service.DirectAccessFile;
import uk.co.armedpineapple.innoextract.service.OutputFile;

/**
 * Compatibility object for alanwoolley/innoextract-android's native library.
 *
 * The upstream Android port expects callback methods normally provided by its
 * Android Service. We provide the same tiny surface here and restrict all
 * extracted output to the caller-selected private app directory.
 */
public final class InnoExtractCompatService {
    private File outputRoot;
    private volatile long extractedBytes;
    private volatile long totalBytes;
    private volatile String currentFile;

    public synchronized void setOutputRoot(String root) throws IOException {
        if (root == null || root.trim().isEmpty()) {
            throw new IOException("German extraction output directory is empty");
        }
        outputRoot = new File(root).getCanonicalFile();
        if (!outputRoot.exists() && !outputRoot.mkdirs() && !outputRoot.isDirectory()) {
            throw new IOException("Could not create German extraction directory");
        }
    }

    public void gotString(String value, int streamNumber) {
        // Native stdout/stderr is intentionally ignored. Progress is reported
        // through updateProgress/updateCurrentFile.
    }

    public void updateProgress(long progress, long total) {
        extractedBytes = progress;
        totalBytes = total;
    }

    public void updateCurrentFile(String fileName) {
        currentFile = fileName;
    }

    public synchronized OutputFile newFile(byte[] pathBytes) {
        try {
            if (outputRoot == null) {
                throw new IOException("German extraction output directory is not configured");
            }
            String requested = new String(pathBytes, StandardCharsets.UTF_8);
            File candidate = new File(requested);
            if (!candidate.isAbsolute()) {
                candidate = new File(outputRoot, requested);
            }
            candidate = candidate.getCanonicalFile();

            String rootPath = outputRoot.getCanonicalPath();
            String candidatePath = candidate.getCanonicalPath();
            if (!candidatePath.equals(rootPath)
                    && !candidatePath.startsWith(rootPath + File.separator)) {
                throw new IOException("German package attempted to write outside the private extraction directory");
            }
            return new DirectAccessFile(candidate);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    public long getExtractedBytes() {
        return extractedBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public String getCurrentFile() {
        return currentFile;
    }
}
