// SPDX-License-Identifier: GPL-3.0-only
package android.app.backup;
import android.content.Context;
/** Android backup transport is absent; change requests never export app data. */
public class BackupManager {
    private static final java.util.concurrent.atomic.AtomicBoolean warned=new java.util.concurrent.atomic.AtomicBoolean();
    public BackupManager(Context context){if(context==null)throw new NullPointerException("context");}
    public void dataChanged(){unavailable();}
    public static void dataChanged(String packageName){if(packageName==null)throw new NullPointerException("packageName");unavailable();}
    private static void unavailable(){if(warned.compareAndSet(false,true))android.util.Log.w("BackupManager","SHIRAZ_UNSUPPORTED backup: no host backup transport");}
    public boolean isBackupEnabled(){return false;}
    public void setBackupEnabled(boolean enabled){if(enabled)throw new UnsupportedOperationException("Android backup transport unavailable");}
    public void backupNow(){throw new UnsupportedOperationException("Android backup transport unavailable");}
}
