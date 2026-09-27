package uk.co.armedpineapple.innoextract.service;

public interface OutputFile extends AutoCloseable {
    String getPath();
    byte[] getPathUtf8();
    @Override void close();
}
