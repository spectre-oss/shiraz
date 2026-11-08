// SPDX-License-Identifier: GPL-3.0-only
package android.app.backup;
import android.content.Context;
import android.os.ParcelFileDescriptor;
/** Registers preference names without reading or exporting preference values. */
public class SharedPreferencesBackupHelper implements BackupHelper {
    private final String[] preferenceGroups;
    public SharedPreferencesBackupHelper(Context context,String... prefGroups){if(context==null||prefGroups==null)throw new NullPointerException("preferences backup");if(prefGroups.length>1024)throw new IllegalArgumentException("Preference group limit");preferenceGroups=prefGroups.clone();for(String name:preferenceGroups)if(name==null||name.isEmpty()||name.length()>4096||name.indexOf('/')>=0)throw new IllegalArgumentException("Preference group name");}
    public void performBackup(ParcelFileDescriptor oldState,BackupDataOutput data,ParcelFileDescriptor newState){throw new UnsupportedOperationException("Android backup transport unavailable");}
    public void restoreEntity(BackupDataInputStream data){throw new UnsupportedOperationException("Android backup transport unavailable");}
    public void writeNewStateDescription(ParcelFileDescriptor newState){throw new UnsupportedOperationException("Android backup transport unavailable");}
}
