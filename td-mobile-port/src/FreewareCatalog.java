package org.tiberiandawn.android;

/** Metadata for the publicly distributed C&C Gold freeware ISO images. */
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
}
