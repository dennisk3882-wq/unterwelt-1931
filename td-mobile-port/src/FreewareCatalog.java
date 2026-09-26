package org.tiberiandawn.android;

/** Metadata for the public/freeware game-data sources used by the Android installer. */
public final class FreewareCatalog {
    private FreewareCatalog() {}

    public static final String GDI_PAGE =
        "https://www.moddb.com/games/cc-gold/downloads/command-conquer-gold-free-game-gdi-iso";
    public static final String NOD_PAGE =
        "https://www.moddb.com/games/cc-gold/downloads/command-conquer-gold-free-game-nod-iso";

    public static final int GDI_MODDB_FILE_ID = 110505;
    public static final int NOD_MODDB_FILE_ID = 110507;
    public static final String GDI_START =
        "https://www.moddb.com/downloads/start/" + GDI_MODDB_FILE_ID;
    public static final String NOD_START =
        "https://www.moddb.com/downloads/start/" + NOD_MODDB_FILE_ID;

    public static final String GDI_FILENAME = "CnC_GDI95.iso";
    public static final String NOD_FILENAME = "CnC_NOD95.iso";
    public static final long GDI_SIZE = 608_987_136L;
    public static final long NOD_SIZE = 592_476_160L;
    public static final String GDI_MD5 = "1cb7c6290718c5e729ae1a4a146aa793";
    public static final String NOD_MD5 = "1dfb2ccae81117b053b60d70e8381250";
    public static final long TOTAL_DOWNLOAD_SIZE = GDI_SIZE + NOD_SIZE;

    // C&C95 1.06c r3 update 6 full-game/community installer.
    // Used only as the source for the German language resources.
    public static final int GERMAN_CORE_MODDB_FILE_ID = 41773;
    public static final String GERMAN_CORE_START =
        "https://www.moddb.com/downloads/start/" + GERMAN_CORE_MODDB_FILE_ID;
    public static final String GERMAN_CORE_FILENAME = "cc95v106c_r3_u6_full.exe";
    public static final long GERMAN_CORE_SIZE = 105_755_776L;
    public static final String GERMAN_CORE_MD5 =
        "41270fdafc0c177b7c557ccb688a5d67";

    // C&C95 1.06 German Videos Base Pack.
    public static final int GERMAN_VIDEO_MODDB_FILE_ID = 71455;
    public static final String GERMAN_VIDEO_START =
        "https://www.moddb.com/downloads/start/" + GERMAN_VIDEO_MODDB_FILE_ID;
    public static final String GERMAN_VIDEO_FILENAME = "cc95v106_videobase_ger.1.exe";
    public static final long GERMAN_VIDEO_SIZE = 517_670_926L;
    public static final String GERMAN_VIDEO_MD5 =
        "098501debb7e51717ca7cc0d1b30aaf3";

    public static final long GERMAN_EXTRA_DOWNLOAD_SIZE =
        GERMAN_CORE_SIZE + GERMAN_VIDEO_SIZE;
    public static final long GERMAN_TOTAL_DOWNLOAD_SIZE =
        TOTAL_DOWNLOAD_SIZE + GERMAN_EXTRA_DOWNLOAD_SIZE;
}
