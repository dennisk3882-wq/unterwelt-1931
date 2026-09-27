package uk.co.armedpineapple.innoextract.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Direct filesystem output used when extracting into our app-private cache.
 */
public final class DirectAccessFile implements OutputFile {
    private final String path;
    private final byte[] pathUtf8;

    public DirectAccessFile(String outputPath) {
        File file = new File(outputPath).getAbsoluteFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new RuntimeException("Could not create extract directory: " + parent);
        }
        try {
            if (!file.exists() && !file.createNewFile()) {
                throw new IOException("Could not create " + file);
            }
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
        path = file.getAbsolutePath();
        pathUtf8 = path.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public byte[] getPathUtf8() {
        return pathUtf8;
    }

    @Override
    public void close() {
        // The native extractor opens/closes the returned path itself.
    }
}
