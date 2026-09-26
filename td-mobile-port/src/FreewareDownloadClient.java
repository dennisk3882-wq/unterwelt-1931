package org.tiberiandawn.android;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small dependency-free downloader for ModDB's public freeware download pages.
 *
 * ModDB mirror URLs contain short-lived tokens, so the app resolves a fresh
 * mirror URL from /downloads/start/<id> instead of hard-coding a stale mirror.
 */
public final class FreewareDownloadClient {
    public interface ProgressListener {
        void onProgress(String fileName, long fileBytes, long fileTotal,
                        long overallBytes, long overallTotal);
    }

    private static final String USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/140 Mobile Safari/537.36 "
        + "TiberianDawnAndroid/0.3";
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 45_000;
    private static final int MAX_HTML_BYTES = 512 * 1024;
    private static final int BUFFER_SIZE = 256 * 1024;

    private FreewareDownloadClient() {}

    public static File downloadKnownIso(String startUrl,
                                        int modDbFileId,
                                        String fileName,
                                        long expectedSize,
                                        long alreadyCompleted,
                                        long overallTotal,
                                        File destinationDirectory,
                                        ProgressListener progress)
            throws IOException {
        if (!destinationDirectory.exists()
            && !destinationDirectory.mkdirs()
            && !destinationDirectory.isDirectory()) {
            throw new IOException("Could not create download directory");
        }

        File destination = new File(destinationDirectory, fileName);
        File partial = new File(destinationDirectory, fileName + ".part");

        if (destination.isFile() && destination.length() == expectedSize) {
            if (progress != null) {
                progress.onProgress(fileName, expectedSize, expectedSize,
                    alreadyCompleted + expectedSize, overallTotal);
            }
            return destination;
        }
        if (destination.exists()) destination.delete();

        IOException lastError = null;
        for (int attempt = 1; attempt <= 3; ++attempt) {
            try {
                String mirror = resolveMirrorUrl(startUrl, modDbFileId);
                downloadToFile(mirror, startUrl, fileName, expectedSize,
                    partial, alreadyCompleted, overallTotal, progress);
                if (partial.length() != expectedSize) {
                    throw new IOException("Downloaded file size does not match expected size");
                }
                if (!partial.renameTo(destination)) {
                    throw new IOException("Could not finalize downloaded ISO");
                }
                return destination;
            } catch (IOException error) {
                lastError = error;
                if (attempt == 3) break;
                // Keep a partial file for HTTP Range resume, but always resolve
                // a fresh mirror because the old mirror token may have expired.
            }
        }
        throw lastError == null ? new IOException("Download failed") : lastError;
    }

    public static String resolveMirrorUrl(String startUrl, int modDbFileId)
            throws IOException {
        HttpURLConnection connection = open(new URL(startUrl), startUrl);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Download page returned HTTP " + code);
            }
            String html = readLimited(connection.getInputStream(), MAX_HTML_BYTES);
            String resolved = findMirrorUrl(html, modDbFileId);
            if (resolved == null) {
                throw new IOException("No ModDB mirror link found");
            }
            return resolved;
        } finally {
            connection.disconnect();
        }
    }

    static String findMirrorUrl(String html, int modDbFileId) throws IOException {
        if (html == null) return null;
        String id = Integer.toString(modDbFileId);
        Pattern pattern = Pattern.compile(
            "(?:href|data-url)=[\"']([^\"']*/downloads/mirror/"
            + Pattern.quote(id) + "/[^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) return null;

        String value = matcher.group(1)
            .replace("&amp;", "&")
            .replace("&#38;", "&");
        URI base = URI.create("https://www.moddb.com/");
        URI resolved = base.resolve(value);
        if (!"https".equalsIgnoreCase(resolved.getScheme())
            || resolved.getHost() == null
            || !resolved.getHost().toLowerCase(Locale.US).endsWith("moddb.com")) {
            throw new IOException("Unexpected ModDB mirror URL");
        }
        return resolved.toString();
    }

    private static void downloadToFile(String mirrorUrl,
                                       String referer,
                                       String fileName,
                                       long expectedSize,
                                       File partial,
                                       long alreadyCompleted,
                                       long overallTotal,
                                       ProgressListener progress)
            throws IOException {
        long existing = partial.isFile() ? partial.length() : 0L;
        if (existing < 0 || existing > expectedSize) {
            partial.delete();
            existing = 0L;
        }

        HttpURLConnection connection = open(new URL(mirrorUrl), referer);
        if (existing > 0) {
            connection.setRequestProperty("Range", "bytes=" + existing + "-");
        }

        int code = connection.getResponseCode();
        boolean append = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL;
        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            connection.disconnect();
            throw new IOException("Download mirror returned HTTP " + code);
        }
        if (!append) existing = 0L;

        try (InputStream raw = connection.getInputStream();
             BufferedInputStream input = new BufferedInputStream(raw, BUFFER_SIZE);
             FileOutputStream fileOutput = new FileOutputStream(partial, append);
             BufferedOutputStream output = new BufferedOutputStream(fileOutput, BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long downloaded = existing;
            long lastPublished = -1;
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                downloaded += count;
                if (downloaded > expectedSize) {
                    throw new IOException("Downloaded file is larger than expected");
                }
                if (progress != null
                    && (lastPublished < 0 || downloaded - lastPublished >= 2L * 1024L * 1024L)) {
                    lastPublished = downloaded;
                    progress.onProgress(fileName, downloaded, expectedSize,
                        alreadyCompleted + downloaded, overallTotal);
                }
            }
            output.flush();
            fileOutput.getFD().sync();
            if (progress != null) {
                progress.onProgress(fileName, downloaded, expectedSize,
                    alreadyCompleted + downloaded, overallTotal);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(URL url, String referer)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept-Encoding", "identity");
        if (referer != null) connection.setRequestProperty("Referer", referer);
        return connection;
    }

    private static String readLimited(InputStream input, int limit)
            throws IOException {
        try (InputStream source = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = source.read(buffer)) != -1) {
                total += count;
                if (total > limit) throw new IOException("Download page is unexpectedly large");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
