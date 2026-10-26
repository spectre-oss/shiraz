// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import java.io.File;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

public class ContextWrapper extends Context {
    private Context base;
    public ContextWrapper(Context base) { this.base = base; }
    protected void attachBaseContext(Context base) {
        if (base == null) throw new NullPointerException("base context");
        if (this.base != null) throw new IllegalStateException("Context is already attached");
        this.base = base;
    }
    @Override public int checkPermission(String p,int pid,int uid){return requireBase().checkPermission(p,pid,uid);}
    @Override public int checkSelfPermission(String p){return requireBase().checkSelfPermission(p);}
    @Override public int checkCallingPermission(String p){return requireBase().checkCallingPermission(p);}
    @Override public int checkCallingOrSelfPermission(String p){return requireBase().checkCallingOrSelfPermission(p);}
    @Override public void enforcePermission(String p,int pid,int uid,String message){requireBase().enforcePermission(p,pid,uid,message);}
    @Override public void enforceCallingPermission(String p,String message){requireBase().enforceCallingPermission(p,message);}
    @Override public void enforceCallingOrSelfPermission(String p,String message){requireBase().enforceCallingOrSelfPermission(p,message);}
    @Override public boolean isRestricted() { return requireBase().isRestricted(); }
    @Override public ContentResolver getContentResolver(){return requireBase().getContentResolver();}
    public Context getBaseContext() { return base; }
    private Context requireBase() {
        if (base == null) throw new IllegalStateException("Context is not attached");
        return base;
    }
    @Override public void startActivity(Intent intent){requireBase().startActivity(intent);}
    @Override public void startActivity(Intent intent,android.os.Bundle options){requireBase().startActivity(intent,options);}
    @Override protected shiraz.runtime.ActivityLaunchQueue shirazActivityLaunches(){return requireBase().shirazActivityLaunches();}
    @Override protected shiraz.runtime.HostServices shirazServices(){return requireBase().shirazServices();}
    @Override protected shiraz.runtime.HostBroadcasts shirazBroadcasts() { return requireBase().shirazBroadcasts(); }
    @Override public void registerComponentCallbacks(ComponentCallbacks callback) { requireBase().registerComponentCallbacks(callback); }
    @Override public void unregisterComponentCallbacks(ComponentCallbacks callback) { requireBase().unregisterComponentCallbacks(callback); }
    @Override public Object getSystemService(String name) { return requireBase().getSystemService(name); }
    @Override public void setTheme(int id) { requireBase().setTheme(id); }
    @Override public android.content.res.Resources.Theme getTheme() { return requireBase().getTheme(); }
    @Override public SharedPreferences getSharedPreferences(String name, int mode) { return requireBase().getSharedPreferences(name, mode); }
    @Override public Context createConfigurationContext(android.content.res.Configuration configuration) { return requireBase().createConfigurationContext(configuration); }
    @Override public android.content.res.Resources getResources() { return requireBase().getResources(); }
    @Override public PackageManager getPackageManager() { return requireBase().getPackageManager(); }
    @Override public ApplicationInfo getApplicationInfo() { return requireBase().getApplicationInfo(); }
    @Override public String getPackageName() { return requireBase().getPackageName(); }
    @Override public Context getApplicationContext() { return requireBase().getApplicationContext(); }
    @Override public File getDatabasePath(String name) { return requireBase().getDatabasePath(name); }
    @Override public String[] databaseList() { return requireBase().databaseList(); }
    @Override public boolean deleteDatabase(String name) { return requireBase().deleteDatabase(name); }
    @Override public android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String name, int mode, android.database.sqlite.SQLiteDatabase.CursorFactory factory) { return requireBase().openOrCreateDatabase(name, mode, factory); }
    @Override public android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String name, int mode, android.database.sqlite.SQLiteDatabase.CursorFactory factory, android.database.DatabaseErrorHandler handler) { return requireBase().openOrCreateDatabase(name, mode, factory, handler); }
    @Override public File getFileStreamPath(String name){return requireBase().getFileStreamPath(name);}
    @Override public File getFilesDir() { return requireBase().getFilesDir(); }
    @Override public File getCacheDir() { return requireBase().getCacheDir(); }
    @Override public File getDataDir() { return requireBase().getDataDir(); }
    @Override public File getCodeCacheDir() { return requireBase().getCodeCacheDir(); }
    @Override public File getNoBackupFilesDir() { return requireBase().getNoBackupFilesDir(); }
    @Override public File getDir(String name, int mode) { return requireBase().getDir(name, mode); }
    @Override public File getExternalFilesDir(String type) { return requireBase().getExternalFilesDir(type); }
    @Override public File[] getExternalFilesDirs(String type) { return requireBase().getExternalFilesDirs(type); }
    @Override public File getExternalCacheDir() { return requireBase().getExternalCacheDir(); }
    @Override public File[] getExternalCacheDirs() { return requireBase().getExternalCacheDirs(); }
    @Override public File getObbDir() { return requireBase().getObbDir(); }
    @Override public File[] getObbDirs() { return requireBase().getObbDirs(); }
    @Override public File[] getExternalMediaDirs() { return requireBase().getExternalMediaDirs(); }
}
