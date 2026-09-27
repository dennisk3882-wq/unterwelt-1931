package uk.co.armedpineapple.innoextract.service;

/**
 * Minimal compatibility interface expected by the bundled Android innoextract
 * native library.
 */
public interface OutputFile extends AutoCloseable {
    String getPath();
    byte[] getPathUtf8();
    @Override
    void close();
}
