// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;

import android.content.Context;
import android.os.Environment;
import java.io.*;
import java.util.Arrays;

final class StorageFixture {
    static void check(Context context) throws Exception {
        File lookup=context.getFileStreamPath("path-only-probe");
        if(!lookup.equals(new File(context.getFilesDir(),"path-only-probe"))||lookup.exists())throw new AssertionError("Internal file lookup mapping or creation");
        Context wrapped=new android.content.ContextWrapper(context);if(!wrapped.getFileStreamPath("path-only-probe").equals(lookup))throw new AssertionError("Wrapped file lookup");
        File root = context.getDataDir().getCanonicalFile();
        File primary = Environment.getExternalStorageDirectory();
        if (!primary.equals(new File(root, "external")) || !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(new File(primary, "nonexistent-child")))
                || !Environment.MEDIA_UNKNOWN.equals(Environment.getStorageState(root))
                || !Environment.isExternalStorageEmulated() || Environment.isExternalStorageRemovable()
                || !Environment.isExternalStorageEmulated(primary) || Environment.isExternalStorageRemovable(primary))
            throw new AssertionError("Primary volume state/type");
        File publicPath = Environment.getExternalStoragePublicDirectory("fixture-public-not-created");
        if (!publicPath.equals(new File(primary, "fixture-public-not-created")) || publicPath.exists())
            throw new AssertionError("Public path getter must not create directories");
        if (!Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).equals(new File(primary, "Download"))
                || !context.getExternalFilesDir(null).equals(new File(primary, "Android/data/" + context.getPackageName() + "/files"))
                || !context.getObbDir().equals(new File(primary, "Android/obb/" + context.getPackageName())))
            throw new AssertionError("Environment/Context volume relationships");
        try { Environment.getExternalStoragePublicDirectory("../outside"); throw new AssertionError("Public volume traversal"); }
        catch (SecurityException expected) { }
        try { Environment.isExternalStorageRemovable(root); throw new AssertionError("Outside volume type"); }
        catch (IllegalArgumentException expected) { }
        File[] directories = {context.getFilesDir(), context.getCacheDir(), context.getCodeCacheDir(), context.getNoBackupFilesDir(),
            context.getDir("fixture", Context.MODE_PRIVATE), context.getExternalFilesDir(null), context.getExternalFilesDir("maps/tracks"),
            context.getExternalCacheDir(), context.getObbDir(), context.getExternalMediaDirs()[0]};
        for (File directory : directories) {
            if (directory == null || !directory.isDirectory() || !directory.canWrite()
                    || !directory.getCanonicalPath().startsWith(root.getPath() + File.separator))
                throw new AssertionError("App storage directory");
        }
        if (!root.getPath().equals(context.getApplicationInfo().dataDir) || !context.getFilesDir().equals(new File(root, "files"))
                || !context.getExternalFilesDir("maps/tracks").equals(new File(context.getExternalFilesDir(null), "maps/tracks")))
            throw new AssertionError("Stable app storage mapping");
        File[] files = context.getExternalFilesDirs(null), cache = context.getExternalCacheDirs(), obb = context.getObbDirs(), media = context.getExternalMediaDirs();
        if (files.length != 1 || !files[0].equals(context.getExternalFilesDir(null)) || cache.length != 1 || !cache[0].equals(context.getExternalCacheDir())
                || obb.length != 1 || !obb[0].equals(context.getObbDir()) || media.length != 1 || !media[0].equals(directories[9]))
            throw new AssertionError("Single host storage volume");
        files[0] = null; cache[0] = null; obb[0] = null; media[0] = null;
        if (context.getExternalFilesDirs(null)[0] == null || context.getExternalCacheDirs()[0] == null
                || context.getObbDirs()[0] == null || context.getExternalMediaDirs()[0] == null)
            throw new AssertionError("Storage array aliasing");
        if (!context.getExternalFilesDir(null).equals(context.getApplicationContext().getExternalFilesDir(null)))
            throw new AssertionError("Application storage wrapper");
        try { context.getExternalFilesDir("../../escape"); throw new AssertionError("Storage traversal accepted"); }
        catch (SecurityException expected) { }
        try { context.getExternalFilesDir("/absolute"); throw new AssertionError("Absolute storage type accepted"); }
        catch (IllegalArgumentException expected) { }
        try { context.getDir("../escape", 0); throw new AssertionError("Named directory traversal accepted"); }
        catch (IllegalArgumentException expected) { }
        try { context.getDir("fixture", 1); throw new AssertionError("Public directory mode accepted"); }
        catch (UnsupportedOperationException expected) { }
        File marker = new File(context.getExternalFilesDir("maps"), "shiraz-storage-marker.bin");
        byte[] expected = {0, 1, 2, -1};
        if ("read".equals(System.getenv("SHIRAZ_FIXTURE_PREFERENCES"))) {
            byte[] actual = new byte[4];
            try (DataInputStream input = new DataInputStream(new FileInputStream(marker))) {
                input.readFully(actual);
                if (input.read() != -1 || !Arrays.equals(actual, expected)) throw new AssertionError("Storage restart contents");
            }
            System.out.println("SHIRAZ_FIXTURE_STORAGE_PERSISTED");
        }
        try (FileOutputStream output = new FileOutputStream(marker)) { output.write(expected); }
        System.out.println("SHIRAZ_FIXTURE_STORAGE_OK");
    }
}
