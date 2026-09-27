package uk.co.armedpineapple.innoextract.service;

/**
 * Minimal ABI-compatible output proxy expected by the bundled Android
 * innoextract runtime.
 */
public interface OutputFile extends AutoCloseable {
    String getPath();
    byte[] getPathUtf8();
    @Override
    void close();
}
