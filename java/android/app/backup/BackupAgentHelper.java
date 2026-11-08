// SPDX-License-Identifier: GPL-3.0-only
package android.app.backup;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
/** Bounded helper registration; transport dispatch remains explicitly unavailable. */
public class BackupAgentHelper extends BackupAgent {
    private final java.util.TreeMap<String,BackupHelper> helpers=new java.util.TreeMap<String,BackupHelper>();
    public BackupAgentHelper(){super();}
    public void addHelper(String keyPrefix,BackupHelper helper){if(keyPrefix==null||helper==null)throw new NullPointerException("backup helper");if(keyPrefix.length()>4096||keyPrefix.indexOf(':')>=0)throw new IllegalArgumentException("Backup helper prefix");if(!helpers.containsKey(keyPrefix)&&helpers.size()>=1024)throw new IllegalStateException("Backup helper limit");helpers.put(keyPrefix,helper);}
    public void onBackup(ParcelFileDescriptor oldState,BackupDataOutput data,ParcelFileDescriptor newState)throws IOException{throw new IOException("Android backup transport unavailable");}
    public void onRestore(BackupDataInput data,int appVersionCode,ParcelFileDescriptor newState)throws IOException{throw new IOException("Android backup transport unavailable");}
}
