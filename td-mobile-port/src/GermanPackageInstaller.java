package org.tiberiandawn.android;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Applies the verified German C&C95 community resources on top of a valid
 * baseline Tiberian Dawn installation.
 *
 * The Windows Inno Setup packages are extracted locally on the device; none of
 * their contents are bundled in the APK.
 */
public final class GermanPackageInstaller {
    public interface Progress {
        void onStage(String stage);
    }

    private GermanPackageInstaller() {}

    public static boolean isInstalled(Context context) {
        if (context == null) return false;
        File game = new File(new File(context.getFilesDir(), "TiberianDawn"), "vanillatd");
        File marker = new File(game, "GERMAN_DATA.txt");
        File speech = new File(game, "SPEECGER.MIX");
        File talk = new File(game, "TALKGER.MIX");
        File movies = new File(game, "MOVIESGER.MIX");
        return marker.isFile()
            && speech.isFile() && speech.length() > 0
            && talk.isFile() && talk.length() > 0
            && movies.isFile() && movies.length() > 100_000_000L;
    }

    private static native String nativeExtractInno(String installerPath,
                                                    String outputDirectory);

    public static void install(Context context,
                               File coreInstaller,
                               File videoInstaller,
                               Progress progress)
            throws IOException {
        if (context == null) throw new IOException("Android context unavailable");
        File product = new File(context.getFilesDir(), "TiberianDawn");
        File game = new File(product, "vanillatd");
        if (!AndroidGameData.isInstalled(context)) {
            throw new IOException("Baseline game data is not installed");
        }

        GameDataImportActivity.ensureNativeLibrariesForDataTools();

        File extractRoot = new File(context.getCacheDir(), "german-data-extract");
        deleteTree(extractRoot);
        File coreOut = new File(extractRoot, "core");
        File videoOut = new File(extractRoot, "video");
        ensureDirectory(coreOut);
        ensureDirectory(videoOut);

        try {
            stage(progress, "extract-core");
            extract(coreInstaller, coreOut);

            stage(progress, "extract-video");
            extract(videoInstaller, videoOut);

            File coreApp = new File(coreOut, "app");
            File videoApp = new File(videoOut, "app");

            File staging = new File(product, "german-overlay.importing");
            File backup = new File(product, "german-overlay.backup");
            deleteTree(staging);
            deleteTree(backup);
            ensureDirectory(staging);
            ensureDirectory(backup);

            stage(progress, "prepare");
            copyRequired(coreApp, "cclocal.mix", new File(staging, "CCLOCAL.MIX"));
            copyRequired(coreApp, "conquer.mix", new File(staging, "CONQUER.MIX"));
            copyRequired(coreApp, "updatec.mix", new File(staging, "UPDATEC.MIX"));
            copyRequired(coreApp, "speecger.mix", new File(staging, "SPEECGER.MIX"));
            copyRequired(coreApp, "talkger.mix", new File(staging, "TALKGER.MIX"));
            copyOptional(coreApp, "deseiger.mix", new File(staging, "DESEIGER.MIX"));
            copyOptional(coreApp, "tempiger.mix", new File(staging, "TEMPIGER.MIX"));
            copyOptional(coreApp, "wintiger.mix", new File(staging, "WINTIGER.MIX"));
            copyOptional(coreApp, "snowiger.mix", new File(staging, "SNOWIGER.MIX"));

            File movies = findIgnoreCase(videoApp, "movies.mix");
            if (movies == null || !movies.isFile() || movies.length() < 100_000_000L) {
                throw new IOException("German MOVIES.MIX is missing from the video package");
            }
            moveOrCopy(movies, new File(staging, "MOVIESGER.MIX"));

            String[] replaceNames = {"CCLOCAL.MIX", "CONQUER.MIX", "UPDATEC.MIX"};
            String[] germanNames = {
                "SPEECGER.MIX", "TALKGER.MIX", "DESEIGER.MIX",
                "TEMPIGER.MIX", "WINTIGER.MIX", "SNOWIGER.MIX",
                "MOVIESGER.MIX"
            };

            boolean committed = false;
            try {
                stage(progress, "commit");
                for (String name : replaceNames) {
                    File current = new File(game, name);
                    if (current.isFile()) {
                        moveOrCopy(current, new File(backup, name));
                    }
                }
                for (String name : replaceNames) {
                    moveRequired(new File(staging, name), new File(game, name));
                }
                for (String name : germanNames) {
                    File source = new File(staging, name);
                    if (source.isFile()) moveRequired(source, new File(game, name));
                }

                validateInstalled(game);
                writeMarker(game);
                committed = true;
            } finally {
                if (!committed) {
                    for (String name : replaceNames) {
                        File target = new File(game, name);
                        if (target.exists()) target.delete();
                        File old = new File(backup, name);
                        if (old.isFile()) moveOrCopy(old, target);
                    }
                    for (String name : germanNames) {
                        File target = new File(game, name);
                        if (target.exists()) target.delete();
                    }
                }
                deleteTree(staging);
                deleteTree(backup);
            }
        } finally {
            deleteTree(extractRoot);
        }
    }

    private static void extract(File installer, File output) throws IOException {
        if (installer == null || !installer.isFile()) {
            throw new IOException("German installer package is missing");
        }
        String error;
        try {
            error = nativeExtractInno(installer.getAbsolutePath(), output.getAbsolutePath());
        } catch (UnsatisfiedLinkError problem) {
            throw new IOException("German package extractor is unavailable", problem);
        }
        if (error != null && !error.trim().isEmpty()) {
            throw new IOException(error.trim());
        }
    }

    private static void validateInstalled(File game) throws IOException {
        String[] required = {
            "CCLOCAL.MIX", "CONQUER.MIX", "UPDATEC.MIX",
            "SPEECGER.MIX", "TALKGER.MIX", "MOVIESGER.MIX"
        };
        for (String name : required) {
            File file = new File(game, name);
            if (!file.isFile() || file.length() <= 0) {
                throw new IOException("German installation is incomplete: " + name);
            }
        }
    }

    private static void writeMarker(File game) throws IOException {
        File marker = new File(game, "GERMAN_DATA.txt");
        try (FileOutputStream out = new FileOutputStream(marker, false)) {
            out.write(("German language pack installed\n"
                + "core-md5=" + FreewareCatalog.GERMAN_CORE_MD5 + "\n"
                + "video-md5=" + FreewareCatalog.GERMAN_VIDEO_MD5 + "\n")
                .getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }

    private static void copyRequired(File directory, String name, File destination)
            throws IOException {
        File source = findIgnoreCase(directory, name);
        if (source == null || !source.isFile() || source.length() <= 0) {
            throw new IOException("German package file is missing: " + name);
        }
        copy(source, destination);
    }

    private static void copyOptional(File directory, String name, File destination)
            throws IOException {
        File source = findIgnoreCase(directory, name);
        if (source != null && source.isFile() && source.length() > 0) {
            copy(source, destination);
        }
    }

    private static File findIgnoreCase(File directory, String name) {
        if (directory == null || !directory.isDirectory()) return null;
        File[] children = directory.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.getName().equalsIgnoreCase(name)) return child;
        }
        return null;
    }

    private static void moveRequired(File source, File destination) throws IOException {
        if (!source.isFile()) throw new IOException("Staged German file is missing: " + source.getName());
        if (destination.exists() && !destination.delete()) {
            throw new IOException("Could not replace " + destination.getName());
        }
        moveOrCopy(source, destination);
    }

    private static void moveOrCopy(File source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null) ensureDirectory(parent);
        if (destination.exists() && !destination.delete()) {
            throw new IOException("Could not replace " + destination.getName());
        }
        if (source.renameTo(destination)) return;
        copy(source, destination);
        if (!source.delete()) {
            // The copy is already durable; cleanup failure is non-fatal.
        }
    }

    private static void copy(File source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null) ensureDirectory(parent);
        File partial = new File(destination.getParentFile(), destination.getName() + ".part");
        if (partial.exists()) partial.delete();

        byte[] buffer = new byte[256 * 1024];
        try (BufferedInputStream input =
                 new BufferedInputStream(new FileInputStream(source), buffer.length);
             FileOutputStream fileOut = new FileOutputStream(partial);
             BufferedOutputStream output =
                 new BufferedOutputStream(fileOut, buffer.length)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("German installation cancelled");
                }
                output.write(buffer, 0, count);
            }
            output.flush();
            fileOut.getFD().sync();
        }
        if (destination.exists() && !destination.delete()) {
            partial.delete();
            throw new IOException("Could not replace " + destination.getName());
        }
        if (!partial.renameTo(destination)) {
            partial.delete();
            throw new IOException("Could not finalize " + destination.getName());
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create " + directory);
        }
    }

    private static void deleteTree(File target) {
        if (target == null || !target.exists()) return;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) deleteTree(child);
            }
        }
        target.delete();
    }

    private static void stage(Progress progress, String value) {
        if (progress != null) progress.onStage(value);
    }
}
