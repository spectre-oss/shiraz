// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import java.io.File;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

/** Initial host context subset. Unsupported services/resources are not stubbed. */
public abstract class Context {
    public static final String ACCOUNT_SERVICE="account";
    public static final String NFC_SERVICE="nfc";
    public static final String VIBRATOR_SERVICE="vibrator";
    public static final String UI_MODE_SERVICE="uimode";
    public static final String AUDIO_SERVICE="audio";
    private ContentResolver contentResolver;
    public synchronized ContentResolver getContentResolver(){if(contentResolver==null)contentResolver=new ContentResolver(this);return contentResolver;}
    public static final int MODE_PRIVATE = 0, MODE_ENABLE_WRITE_AHEAD_LOGGING = 8, MODE_NO_LOCALIZED_COLLATORS = 16;
    // No Android permission grants or remote Binder callers are registered yet.
    public int checkPermission(String permission,int pid,int uid){if(permission==null)throw new IllegalArgumentException("permission");return PackageManager.PERMISSION_DENIED;}
    public int checkSelfPermission(String permission){return checkPermission(permission,android.os.Process.myPid(),android.os.Process.myUid());}
    public int checkCallingPermission(String permission){if(permission==null)throw new IllegalArgumentException("permission");return PackageManager.PERMISSION_DENIED;}
    public int checkCallingOrSelfPermission(String permission){return checkSelfPermission(permission);}
    public void enforcePermission(String permission,int pid,int uid,String message){if(checkPermission(permission,pid,uid)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException((message==null?"Permission denied":message)+": "+permission);}
    public void enforceCallingPermission(String permission,String message){if(checkCallingPermission(permission)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException((message==null?"Permission denied":message)+": "+permission);}
    public void enforceCallingOrSelfPermission(String permission,String message){if(checkCallingOrSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException((message==null?"Permission denied":message)+": "+permission);}
    public boolean isRestricted() { return false; }
    public abstract SharedPreferences getSharedPreferences(String name, int mode);
    public boolean moveSharedPreferencesFrom(Context sourceContext, String name) {
        if (sourceContext == null) throw new NullPointerException("sourceContext");
        return shiraz.runtime.HostPreferences.move(sourceContext.getSharedPreferences(name, MODE_PRIVATE),
                getSharedPreferences(name, MODE_PRIVATE));
    }
    public abstract void setTheme(int id);
    public abstract android.content.res.Resources.Theme getTheme();
    public final android.content.res.TypedArray obtainStyledAttributes(int[] attrs){return getTheme().obtainStyledAttributes(attrs);}
    public final android.content.res.TypedArray obtainStyledAttributes(int style,int[] attrs){return getTheme().obtainStyledAttributes(style,attrs);}
    public final android.content.res.TypedArray obtainStyledAttributes(android.util.AttributeSet set,int[] attrs){return getTheme().obtainStyledAttributes(set,attrs,0,0);}
    public final android.content.res.TypedArray obtainStyledAttributes(android.util.AttributeSet set,int[] attrs,int defStyleAttr,int defStyleRes){return getTheme().obtainStyledAttributes(set,attrs,defStyleAttr,defStyleRes);}
    public final android.graphics.drawable.Drawable getDrawable(int id) { return getResources().getDrawable(id,getTheme()); }
    public final int getColor(int id) { return getResources().getColor(id, getTheme()); }
    public final android.content.res.ColorStateList getColorStateList(int id) { return getResources().getColorStateList(id, getTheme()); }
    public ClassLoader getClassLoader(){return getClass().getClassLoader();}
    public static final String INPUT_SERVICE="input";
    public static final String INPUT_METHOD_SERVICE="input_method";
    public static final String AUTOFILL_SERVICE="autofill";
    public static final String LOCATION_SERVICE="location";
    public static final String TELEPHONY_SERVICE="phone";
    public static final String APP_OPS_SERVICE="appops";
    public static final String WIFI_SERVICE="wifi";
    public static final String CONNECTIVITY_SERVICE="connectivity";
    public static final String SHORTCUT_SERVICE="shortcut";
    public static final String CLIPBOARD_SERVICE="clipboard";
    public static final String TELECOM_SERVICE="telecom";
    public static final String USER_SERVICE="user";
    public static final String JOB_SCHEDULER_SERVICE="jobscheduler";
    public static final String ALARM_SERVICE="alarm";
    public static final String ACCESSIBILITY_SERVICE="accessibility";
    public static final String DOWNLOAD_SERVICE="download";
    public static final String LAYOUT_INFLATER_SERVICE="layout_inflater";
    public static final String ACTIVITY_SERVICE = "activity", WINDOW_SERVICE = "window", SENSOR_SERVICE = "sensor", NOTIFICATION_SERVICE = "notification", DISPLAY_SERVICE = "display", POWER_SERVICE = "power", KEYGUARD_SERVICE = "keyguard";
    public final <T> T getSystemService(Class<T> serviceClass) {
        if (serviceClass == null) throw new NullPointerException("serviceClass");
        String name = getSystemServiceName(serviceClass);
        if (name == null) throw new UnsupportedOperationException("System service class is not implemented: " + serviceClass.getName());
        return serviceClass.cast(getSystemService(name));
    }
    public String getSystemServiceName(Class<?> serviceClass) {
        if (serviceClass == android.nfc.NfcManager.class) return NFC_SERVICE;
        if (serviceClass == android.accounts.AccountManager.class) return ACCOUNT_SERVICE;
        if (serviceClass == android.hardware.input.InputManager.class) return INPUT_SERVICE;
        if (serviceClass == android.app.DownloadManager.class) return DOWNLOAD_SERVICE;
        if (serviceClass == android.os.Vibrator.class) return VIBRATOR_SERVICE;
        if (serviceClass == android.app.UiModeManager.class) return UI_MODE_SERVICE;
        if (serviceClass == android.media.AudioManager.class) return AUDIO_SERVICE;
        if (serviceClass == android.view.textservice.TextServicesManager.class) return "textservices";
        if (serviceClass == null) throw new NullPointerException("serviceClass");
        if (serviceClass == android.view.inputmethod.InputMethodManager.class) return INPUT_METHOD_SERVICE;
        if (serviceClass == android.view.autofill.AutofillManager.class) return AUTOFILL_SERVICE;
        if (serviceClass == android.view.accessibility.AccessibilityManager.class) return ACCESSIBILITY_SERVICE;
        if (serviceClass == android.view.LayoutInflater.class) return LAYOUT_INFLATER_SERVICE;
        if (serviceClass == android.content.pm.ShortcutManager.class) return SHORTCUT_SERVICE;
        if (serviceClass == android.hardware.display.DisplayManager.class) return DISPLAY_SERVICE;
        if (serviceClass == android.app.NotificationManager.class) return NOTIFICATION_SERVICE;
        if (serviceClass == android.os.PowerManager.class) return POWER_SERVICE;
        if (serviceClass == android.app.KeyguardManager.class) return KEYGUARD_SERVICE;
        if (serviceClass == android.app.ActivityManager.class) return ACTIVITY_SERVICE;
        if (serviceClass == android.location.LocationManager.class) return LOCATION_SERVICE;
        if (serviceClass == android.telephony.TelephonyManager.class) return TELEPHONY_SERVICE;
        if (serviceClass == android.app.AppOpsManager.class) return APP_OPS_SERVICE;
        if (serviceClass == android.net.wifi.WifiManager.class) return WIFI_SERVICE;
        if (serviceClass == android.net.ConnectivityManager.class) return CONNECTIVITY_SERVICE;
        if (serviceClass == android.hardware.SensorManager.class) return SENSOR_SERVICE;
        if (serviceClass == android.view.WindowManager.class) return WINDOW_SERVICE;
        if (serviceClass == android.content.ClipboardManager.class) return CLIPBOARD_SERVICE;
        if (serviceClass == android.telecom.TelecomManager.class) return TELECOM_SERVICE;
        if (serviceClass == android.os.UserManager.class) return USER_SERVICE;
        if (serviceClass == android.app.AlarmManager.class) return ALARM_SERVICE;
        if (serviceClass == android.app.job.JobScheduler.class) return JOB_SCHEDULER_SERVICE;
        return null;
    }
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        Context application=getApplicationContext();
        if(application==null||application==this) throw new IllegalStateException("No Application callback registry");
        application.registerComponentCallbacks(callback);
    }
    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        Context application=getApplicationContext();
        if(application==null||application==this) throw new IllegalStateException("No Application callback registry");
        application.unregisterComponentCallbacks(callback);
    }
    public static final int MODE_APPEND = 0x8000, CONTEXT_INCLUDE_CODE = 1, CONTEXT_IGNORE_SECURITY = 2;
    public boolean isDeviceProtectedStorage() { return false; }
    public Context createDeviceProtectedStorageContext() { return this; }
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        if (getPackageName().equals(packageName)) return this;
        throw new PackageManager.NameNotFoundException(packageName);
    }
    public boolean deleteFile(String name) { return new File(getFilesDir(), name).delete(); }
    public java.io.FileInputStream openFileInput(String name) throws java.io.FileNotFoundException { return new java.io.FileInputStream(new File(getFilesDir(), name)); }
    public java.io.FileOutputStream openFileOutput(String name, int mode) throws java.io.FileNotFoundException {
        if (name.indexOf('/') >= 0) throw new IllegalArgumentException("File " + name + " contains a path separator");
        return new java.io.FileOutputStream(new File(getFilesDir(), name), (mode & MODE_APPEND) != 0);
    }
    public java.util.concurrent.Executor getMainExecutor() {
        final android.os.Handler handler = new android.os.Handler(getMainLooper());
        return new java.util.concurrent.Executor() { public void execute(Runnable command) { handler.post(command); } };
    }
    public String getAttributionTag() { return null; }
    public int getDeviceId() { return 0; }
    public String getOpPackageName() { return getPackageName(); }
    public android.os.Looper getMainLooper() { return android.os.Looper.getMainLooper(); }
    public static final int RECEIVER_VISIBLE_TO_INSTANT_APPS = 1, RECEIVER_EXPORTED = 2, RECEIVER_NOT_EXPORTED = 4;
    protected shiraz.runtime.ActivityLaunchQueue shirazActivityLaunches(){throw new UnsupportedOperationException("Activity launch sessions are unavailable");}
    public void startActivity(Intent intent){startActivity(intent,null);}
    public void startActivity(Intent intent,android.os.Bundle options){throw new UnsupportedOperationException("Activity launch from a non-activity context requires task support");}
    protected shiraz.runtime.HostServices shirazServices(){throw new UnsupportedOperationException("Service sessions are not available in this context");}
    public static final int BIND_AUTO_CREATE=1,BIND_WAIVE_PRIORITY=32,BIND_IMPORTANT=64;
    public boolean bindService(Intent intent,ServiceConnection connection,int flags){return shirazServices().bindUnavailable(intent,connection,flags);}
    public void unbindService(ServiceConnection connection){shirazServices().unbindUnavailable(connection);}
    public ComponentName startService(Intent intent){return shirazServices().start(intent);}
    public boolean stopService(Intent intent){return shirazServices().stop(intent);}
    public ComponentName startForegroundService(Intent intent){return shirazServices().startForeground(intent);}
    protected shiraz.runtime.HostBroadcasts shirazBroadcasts() { throw new UnsupportedOperationException("Broadcast sessions are not available in this context"); }
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) { return registerReceiver(receiver, filter, null, null, 0); }
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) { return registerReceiver(receiver, filter, null, null, flags); }
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String permission, android.os.Handler handler) { return registerReceiver(receiver, filter, permission, handler, 0); }
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String permission, android.os.Handler handler, int flags) { return shirazBroadcasts().register(this, receiver, filter, permission, handler, flags); }
    public void unregisterReceiver(BroadcastReceiver receiver) { shirazBroadcasts().unregister(this, receiver); }
    public void sendBroadcast(Intent intent) { sendBroadcast(intent, null); }
    public void sendBroadcast(Intent intent, String permission) { shirazBroadcasts().send(intent, permission); }
    public void sendOrderedBroadcast(Intent intent, String permission) { throw new UnsupportedOperationException("Ordered broadcasts are not implemented"); }
    public void sendStickyBroadcast(Intent intent) { throw new UnsupportedOperationException("Sticky broadcast publication is not implemented"); }
    public abstract Object getSystemService(String name);
    public Context createConfigurationContext(android.content.res.Configuration overrideConfiguration) {
        return new shiraz.runtime.ConfigurationContext(this,overrideConfiguration);
    }
    public abstract android.content.res.Resources getResources();
    public android.content.res.AssetManager getAssets() { return getResources().getAssets(); }
    public final CharSequence getText(int id) { return getResources().getText(id); }
    public final String getString(int id) { return getResources().getString(id); }
    public final String getString(int id, Object... args) { return getResources().getString(id, args); }
    public abstract PackageManager getPackageManager();
    public abstract ApplicationInfo getApplicationInfo();
    public String getPackageCodePath() { return getApplicationInfo().sourceDir; }
    public String getPackageResourcePath() { return getApplicationInfo().publicSourceDir; }
    public abstract String getPackageName();
    public abstract Context getApplicationContext();
    public abstract File getDatabasePath(String name);
    public abstract String[] databaseList();
    public boolean deleteDatabase(String name) { return android.database.sqlite.SQLiteDatabase.deleteDatabase(getDatabasePath(name)); }
    public android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String name, int mode, android.database.sqlite.SQLiteDatabase.CursorFactory factory) { return openOrCreateDatabase(name, mode, factory, null); }
    public android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String name, int mode, android.database.sqlite.SQLiteDatabase.CursorFactory factory, android.database.DatabaseErrorHandler handler) {
        if ((mode & ~24) != 0) throw new SecurityException("Only private database modes are supported");
        int flags = android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY;
        if ((mode & MODE_ENABLE_WRITE_AHEAD_LOGGING) != 0) flags |= android.database.sqlite.SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING;
        if ((mode & MODE_NO_LOCALIZED_COLLATORS) != 0) flags |= android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS;
        return android.database.sqlite.SQLiteDatabase.openDatabase(getDatabasePath(name).getPath(), factory, flags, handler);
    }
    public abstract File getFilesDir();
    public File getFileStreamPath(String name){throw new UnsupportedOperationException("Context file storage is not configured");}
    public abstract File getCacheDir();
    public abstract File getDataDir();
    public abstract File getCodeCacheDir();
    public abstract File getNoBackupFilesDir();
    public abstract File getDir(String name, int mode);
    public abstract File getExternalFilesDir(String type);
    public abstract File getExternalCacheDir();
    public abstract File getObbDir();
    public File[] getExternalFilesDirs(String type) { return new File[]{getExternalFilesDir(type)}; }
    public File[] getExternalCacheDirs() { return new File[]{getExternalCacheDir()}; }
    public File[] getObbDirs() { return new File[]{getObbDir()}; }
    public abstract File[] getExternalMediaDirs();
}
