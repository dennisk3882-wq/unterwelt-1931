package com.winlator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Separate community client: never replace the user's original RA2 executables. */
public final class RA2XwisClient {
    private RA2XwisClient() {}
    public static final String RA2_INSTALLER = "Red-Alert-2-Multiplayer.exe";
    public static final String YURI_INSTALLER = "Yuris-Revenge-Multiplayer.exe";
    public static final String RA2_URL = "https://xwis.net/downloads/Red-Alert-2-Multiplayer.exe";
    public static final String YURI_URL = "https://xwis.net/downloads/Yuris-Revenge-Multiplayer.exe";
    public static final String RA2_ARCHIVE = "https://archive.org/download/red-alert-2-multiplayer/Red-Alert-2-Multiplayer.exe";
    public static final String YURI_ARCHIVE = "https://archive.org/download/yuris-revenge-multiplayer/Yuris-Revenge-Multiplayer.exe";
    public static final long MAX_INSTALLER = 700L * 1024 * 1024;

    public static String dosInstaller(boolean yuri) {
        return "C:\\RA2Mobile\\XWISPackages\\" + (yuri ? YURI_INSTALLER : RA2_INSTALLER);
    }
    public static String installerArgs() {
        // Both original XWIS packages use NSIS. /D must be the final parameter.
        return "/S /D=C:\\RA2Mobile\\XWISClient";
    }
    public static boolean isPeInstaller(File file) throws IOException {
        if (file == null || !file.isFile() || file.length() < 1024 * 1024 ||
                file.length() > MAX_INSTALLER) return false;
        try (java.io.FileInputStream stream = new java.io.FileInputStream(file)) {
            return stream.read() == 'M' && stream.read() == 'Z';
        }
    }
    public static File findClient(File dir, boolean yuri) {
        if (dir == null || !dir.isDirectory()) return null;
        String[] names = yuri ?
            new String[]{"gamemd.exe", "Yuri's Revenge.exe", "ra2md.exe", "yuri.exe"} :
            new String[]{"game.exe", "Red Alert 2.exe"};
        for (String name : names) {
            File[] entries = dir.listFiles();
            if (entries == null) return null;
            for (File item : entries)
                if (item.isFile() && item.length() > 300000 && name.equalsIgnoreCase(item.getName()))
                    return item;
        }
        return null;
    }
    /**
     * The multiplayer installers omit campaign movies/maps. Restore missing
     * *MIX files from the user's own ISO installation, without touching EXEs
     * or changing original files. Returns the number of assets added.
     */
    public static int addOwnedResources(File original, File client) throws IOException {
        if (!client.isDirectory()) throw new IOException("XWIS-Client ist noch nicht installiert");
        if (original == null || !original.isDirectory()) throw new IOException("Original-ISO-Daten fehlen");
        File[] files = original.listFiles();
        if (files == null) throw new IOException("Spieldateien konnten nicht gelesen werden");
        int added = 0;
        long total = 0;
        for (File src : files) {
            if (!src.isFile()) continue;
            String name=src.getName().toLowerCase(Locale.ROOT);
            if (!name.matches("[a-z0-9_.-]{1,80}\\.mix")) continue;
            if (src.length() <= 0 || src.length() > 1200L * 1024 * 1024) continue;
            File[] already = client.listFiles();
            boolean present=false;
            if (already != null) for (File a : already)
                if (a.getName().equalsIgnoreCase(src.getName())) {present=true;break;}
            if (present) continue;
            total += src.length();
            if (total > 4L * 1024 * 1024 * 1024) throw new IOException("Grenze für Missionsdaten überschritten");
            File target = new File(client,src.getName());
            File temp = new File(client,src.getName()+".ra2partial");
            try {
                Files.copy(src.toPath(),temp.toPath(),StandardCopyOption.REPLACE_EXISTING);
                if (!temp.renameTo(target)) throw new IOException("Missionsdatei konnte nicht übernommen werden: "+src.getName());
            } finally {temp.delete();}
            added++;
        }
        return added;
    }
    public static String dosClientPath(File exe) {
        if (exe == null) throw new IllegalArgumentException("No XWIS client");
        String name = exe.getName();
        if (!name.matches("(?i)(game|gamemd|ra2md|yuri|red alert 2|yuri's revenge)\\.exe"))
            throw new IllegalArgumentException("Unexpected XWIS executable");
        return "C:\\RA2Mobile\\XWISClient\\" + name;
    }
}