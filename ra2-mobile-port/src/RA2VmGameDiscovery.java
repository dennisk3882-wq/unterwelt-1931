package com.winlator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Isolated filesystem discovery for original RA2/Yuri ISO data.
 * The source installation is read-only; no files are moved or replaced.
 * Windows filenames are matched case-insensitively on Android/Linux.
 */
public final class RA2VmGameDiscovery {
    private RA2VmGameDiscovery() {}

    public static final class Result {
        public final File directory;
        public final int score;
        public final boolean ra2Ready;
        public final boolean yuriReady;
        public final String status;
        Result(File dir,int points,boolean ra2,boolean yuri,String details) {
            directory=dir;score=points;ra2Ready=ra2;yuriReady=yuri;status=details;
        }
    }

    /** Roots are real container homes or their imagefs fallback. Never scan arbitrary storage. */
    public static Result find(List<File> containerRoots) {
        Result strongest=null;
        for(File root:containerRoots) {
            if(root==null)continue;
            File dir=childIgnoreCase(root,".wine","drive_c","Westwood","RA2");
            Result r=inspect(dir);
            if(strongest==null || r.score>strongest.score)strongest=r;
        }
        if(strongest==null)strongest=inspect(new File("RA2-not-configured"));
        return strongest;
    }

    public static File childIgnoreCase(File parent,String...segments) {
        File current=parent;
        for(String part:segments)current=directChildIgnoreCase(current,part);
        return current;
    }

    public static File directChildIgnoreCase(File parent,String name) {
        if(name==null || name.isEmpty() || name.equals(".") || name.equals("..") ||
           name.indexOf('/')>=0 || name.indexOf('\\')>=0)
            throw new IllegalArgumentException("Not a child filename");
        File direct=new File(parent,name);
        if(direct.exists())return direct;
        File[] entries=parent.listFiles();
        if(entries!=null)for(File entry:entries)if(entry.getName().equalsIgnoreCase(name))return entry;
        return direct;
    }

    public static Result inspect(File dir) {
        boolean game=directChildIgnoreCase(dir,"game.exe").isFile();
        boolean mix=directChildIgnoreCase(dir,"ra2.mix").isFile();
        boolean yuri=directChildIgnoreCase(dir,"gamemd.exe").isFile();
        boolean yuriMix=directChildIgnoreCase(dir,"ra2md.mix").isFile();
        boolean langs=directChildIgnoreCase(dir,"language.mix").isFile();
        boolean yrLang=directChildIgnoreCase(dir,"langmd.mix").isFile();
        int score=(game?4:0)+(mix?4:0)+(yuri?3:0)+(yuriMix?3:0)+(langs?1:0)+(yrLang?1:0);
        String[] names=dir.list();
        StringBuilder sample=new StringBuilder();
        if(names!=null)for(String name:names){
            String lower=name.toLowerCase(Locale.ROOT);
            if(lower.endsWith(".exe") || lower.endsWith(".mix")) {
                if(sample.length()>0)sample.append(", ");
                sample.append(name);
                if(sample.length()>190){sample.append(" …");break;}
            }
        }
        String status="path="+dir.getAbsolutePath()+
            " dir="+dir.isDirectory()+" entries="+(names==null?"unreadable":names.length)+
            " GAME.EXE="+game+" RA2.MIX="+mix+
            " GAMEMD.EXE="+yuri+" RA2MD.MIX="+yuriMix+
            " matching="+sample;
        return new Result(dir,score,game&&mix,yuri&&yuriMix,status);
    }
}
