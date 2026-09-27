package uk.co.armedpineapple.innoextract.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class DirectAccessFile implements OutputFile {
    private final String path;
    private final byte[] pathUtf8;

    public DirectAccessFile(String outputPath) {
        File file = new File(outputPath).getAbsoluteFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try {
            if (!file.exists()) file.createNewFile();
        } catch (IOException error) {
            throw new RuntimeException("Could not create extracted file: " + file, error);
        }
        path = file.getAbsolutePath();
        pathUtf8 = path.getBytes(StandardCharsets.UTF_8);
    }

    @Override public String getPath() { return path; }
    @Override public byte[] getPathUtf8() { return pathUtf8; }
    @Override public void close() { }
}
