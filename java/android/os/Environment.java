// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.io.File;
import java.nio.charset.StandardCharsets;
import shiraz.runtime.HostStorage;

/** One app-scoped emulated primary volume, backed by ordinary Linux files. */
public class Environment {
    static { System.loadLibrary("shiraz-host"); }
    public static final String DIRECTORY_MUSIC = "Music", DIRECTORY_PODCASTS = "Podcasts",
        DIRECTORY_RINGTONES = "Ringtones", DIRECTORY_ALARMS = "Alarms", DIRECTORY_NOTIFICATIONS = "Notifications",
        DIRECTORY_PICTURES = "Pictures", DIRECTORY_MOVIES = "Movies", DIRECTORY_DOWNLOADS = "Download",
        DIRECTORY_DCIM = "DCIM", DIRECTORY_DOCUMENTS = "Documents";
    public static final String MEDIA_UNKNOWN = "unknown", MEDIA_REMOVED = "removed", MEDIA_UNMOUNTED = "unmounted",
        MEDIA_CHECKING = "checking", MEDIA_NOFS = "nofs", MEDIA_MOUNTED = "mounted", MEDIA_MOUNTED_READ_ONLY = "mounted_ro",
        MEDIA_SHARED = "shared", MEDIA_BAD_REMOVAL = "bad_removal", MEDIA_UNMOUNTABLE = "unmountable";
    private static volatile HostStorage storage;
    public Environment() { }
    public static synchronized void shirazInitialize(HostStorage value) {
        if (value == null) throw new NullPointerException("Primary storage");
        if (storage != null) throw new IllegalStateException("Primary storage already configured");
        value.preparePrimary(); storage = value;
    }
    public static synchronized void shirazShutdown() { storage = null; }
    private static HostStorage storage() {
        HostStorage value = storage;
        if (value == null) throw new IllegalStateException("Primary storage is not configured");
        return value;
    }
    public static File getDownloadCacheDirectory(){return storage().cache();}
    public static File getDataDirectory(){return storage().data();}
    public static File getExternalStorageDirectory() { return storage().primaryPath(null); }
    public static File getExternalStoragePublicDirectory(String type) { return storage().primaryPath(type); }
    public static String getExternalStorageState() { return state(storage()); }
    public static String getExternalStorageState(File path) {
        HostStorage value = storage(); return value.onPrimary(path) ? state(value) : MEDIA_UNKNOWN;
    }
    public static String getStorageState(File path) { return getExternalStorageState(path); }
    private static String state(HostStorage value) {
        File root = value.primaryPath(null);
        if (!root.isDirectory()) return MEDIA_UNMOUNTED;
        return nativeIsReadOnly(root.getPath().getBytes(StandardCharsets.UTF_8)) ? MEDIA_MOUNTED_READ_ONLY : MEDIA_MOUNTED;
    }
    private static native boolean nativeIsReadOnly(byte[] path);
    public static boolean isExternalStorageEmulated() { storage(); return true; }
    public static boolean isExternalStorageRemovable() { storage(); return false; }
    public static boolean isExternalStorageEmulated(File path) { requirePrimary(path); return true; }
    public static boolean isExternalStorageRemovable(File path) { requirePrimary(path); return false; }
    private static void requirePrimary(File path) {
        if (!storage().onPrimary(path)) throw new IllegalArgumentException("Path is outside the primary volume");
    }
}
