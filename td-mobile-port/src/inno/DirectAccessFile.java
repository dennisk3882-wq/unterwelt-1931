package uk.co.armedpineapple.innoextract.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Direct app-private filesystem target used by the compatibility extractor.
 */
public final class DirectAccessFile implements OutputFile {
    private final File file;

    public DirectAccessFile(File file) throws IOException {
        this.file = file.getCanonicalFile();
        File parent = this.file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create extraction directory");
        }
        if (!this.file.exists() && !this.file.createNewFile()) {
            throw new IOException("Could not create extraction file");
        }
    }

    @Override
    public String getPath() {
        return file.getAbsolutePath();
    }

    @Override
    public byte[] getPathUtf8() {
        return getPath().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        // innoextract opens the path itself; there is no Java stream to close.
    }
}
