package org.tiberiandawn.android;

public final class FreewareDownloadClientTest {
    public static void main(String[] args) throws Exception {
        String relative =
            "<html><a href=\"/downloads/mirror/110505/122/token123\">download</a></html>";
        String resolved = FreewareDownloadClient.findMirrorUrl(relative, 110505);
        if (!"https://www.moddb.com/downloads/mirror/110505/122/token123".equals(resolved)) {
            throw new AssertionError("relative mirror resolution failed: " + resolved);
        }

        String absolute =
            "<a data-url='https://www.moddb.com/downloads/mirror/110507/77/token456'>x</a>";
        resolved = FreewareDownloadClient.findMirrorUrl(absolute, 110507);
        if (!"https://www.moddb.com/downloads/mirror/110507/77/token456".equals(resolved)) {
            throw new AssertionError("absolute mirror resolution failed: " + resolved);
        }

        if (FreewareDownloadClient.findMirrorUrl(relative, 110507) != null) {
            throw new AssertionError("wrong file id unexpectedly matched");
        }

        boolean rejected = false;
        try {
            FreewareDownloadClient.findMirrorUrl(
                "<a href='https://evil.example/downloads/mirror/110505/1/x'>x</a>",
                110505);
        } catch (java.io.IOException expected) {
            rejected = true;
        }
        if (!rejected) throw new AssertionError("foreign mirror host was not rejected");

        System.out.println("FreewareDownloadClient parser tests passed.");
    }
}
