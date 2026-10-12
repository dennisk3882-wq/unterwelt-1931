package com.winlator;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only union of installed original game files and already extracted,
 * user-owned ISO9660 media (disc 2 first). No copies and no ISO parser needed.
 * The RA2 VM normalizes D:\RA2.DSK to ra2.dsk, so expose disc files in
 * the same virtual game namespace while advertising D: as DRIVE_CDROM.
 */
public final class RA2VmMediaOverlay {
    private final File game;
    private final File ra2Media;
    private final File yuriMedia;

    public RA2VmMediaOverlay(File originalGame) {
        game=originalGame;
        File westwood=game.getParentFile();
        File driveC=westwood==null?null:westwood.getParentFile();
        File wine=driveC==null?null:driveC.getParentFile();
        ra2Media=wine==null?new File("missing-ra2-cd"):new File(wine,"drive_ra2_media");
        yuriMedia=wine==null?new File("missing-yuri-cd"):new File(wine,"drive_yuri_media");
    }

    public File ra2Disc(){return ra2Media;}
    public File yuriDisc(){return yuriMedia;}
    public boolean ra2DiscReady() { return RA2VmGameDiscovery.directChildIgnoreCase(ra2Media,"RA2.DSK").isFile(); }
    public int discFileCount() {
        File[] files=ra2Media.listFiles();
        return files==null?0:files.length;
    }

    public File resolve(String relative) throws IOException {
        if(relative==null || relative.contains("..") || relative.startsWith("/") || relative.indexOf('\\')>=0)
            throw new IOException("Invalid virtual file path");
        File found=within(game,relative);
        if(found.exists())return found;
        File cd=within(ra2Media,relative);
        if(cd.isFile() || cd.isDirectory())return cd;
        File yuri=within(yuriMedia,relative);
        if(yuri.isFile() || yuri.isDirectory())return yuri;
        return found;
    }

    public String[] listing(String relative)throws IOException {
        if(relative==null || relative.contains("..") || relative.startsWith("/") || relative.indexOf('\\')>=0)
            throw new IOException("Invalid virtual directory path");
        LinkedHashMap<String,String> merged=new LinkedHashMap<>();
        for(File root:new File[]{game,ra2Media,yuriMedia}) {
            File folder=within(root,relative);
            File[] entries=folder.listFiles();
            if(entries!=null) for(File entry:entries) {
                String name=entry.getName();
                if(name.startsWith(".") || name.length()>190)continue;
                merged.putIfAbsent(name.toLowerCase(Locale.ROOT),name);
            }
        }
        return merged.values().toArray(new String[0]);
    }

    private static File within(File root,String relative)throws IOException {
        File current=root;
        for(String name:relative.split("/")) {
            if(name.isEmpty())continue;
            if(name.equals(".") || name.equals(".."))throw new IOException("Invalid component");
            current=RA2VmGameDiscovery.directChildIgnoreCase(current,name);
        }
        String prefix=root.getCanonicalPath(), actual=current.getCanonicalPath();
        if(!actual.equals(prefix) && !actual.startsWith(prefix+File.separator))
            throw new IOException("Virtual file is outside original ISO/game directory");
        return current;
    }
}
