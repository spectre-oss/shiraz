// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import java.io.File;
import java.util.Properties;

/** System resource context available before the application's constructor. */
public final class ContextImpl extends Context {
    private final PackageManager packages;
    private Resources.Theme theme;

    ContextImpl(Properties metadata) {
        packages = new shiraz.runtime.HostPackageManager(metadata, this);
    }
    @Override public String getPackageName() { return "android"; }
    @Override public String getOpPackageName() { return ActivityThread.currentPackageName(); }
    @Override public Resources getResources() { return Resources.getSystem(); }
    @Override public PackageManager getPackageManager() { return packages; }
    @Override public Context getApplicationContext() { return ActivityThread.currentApplication(); }
    @Override public ApplicationInfo getApplicationInfo() {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = "android";
        info.sourceDir = System.getProperty("shiraz.frameworkResources");
        info.publicSourceDir = info.sourceDir;
        info.uid = android.os.Process.myUid();
        info.targetSdkVersion = android.os.Build.VERSION.SDK_INT;
        return info;
    }
    @Override public synchronized Resources.Theme getTheme() {
        if (theme == null) theme = getResources().newTheme();
        return theme;
    }
    @Override public void setTheme(int id) { getTheme().applyStyle(id, true); }
    @Override public Object getSystemService(String name) {
        Application app = ActivityThread.currentApplication();
        if (app == null) throw new UnsupportedOperationException("Service unavailable before application binding: " + name);
        return app.getSystemService(name);
    }
    private UnsupportedOperationException noStorage() {
        return new UnsupportedOperationException("Application storage is unavailable in the system context");
    }
    @Override public SharedPreferences getSharedPreferences(String name,int mode) { throw noStorage(); }
    @Override public File getDatabasePath(String name) { throw noStorage(); }
    @Override public String[] databaseList() { throw noStorage(); }
    @Override public File getFilesDir() { throw noStorage(); }
    @Override public File getCacheDir() { throw noStorage(); }
    @Override public File getDataDir() { throw noStorage(); }
    @Override public File getCodeCacheDir() { throw noStorage(); }
    @Override public File getNoBackupFilesDir() { throw noStorage(); }
    @Override public File getDir(String name,int mode) { throw noStorage(); }
    @Override public File getExternalFilesDir(String type) { throw noStorage(); }
    @Override public File getExternalCacheDir() { throw noStorage(); }
    @Override public File getObbDir() { throw noStorage(); }
    @Override public File[] getExternalMediaDirs() { throw noStorage(); }
}
