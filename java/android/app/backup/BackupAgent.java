// SPDX-License-Identifier: GPL-3.0-only
package android.app.backup;
import android.content.ContextWrapper;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
/** App-owned lifecycle. No backup agent is attached without a transport. */
public abstract class BackupAgent extends ContextWrapper {
    public BackupAgent(){super(null);}
    public void onCreate(){}
    public void onDestroy(){}
    public abstract void onBackup(ParcelFileDescriptor oldState,BackupDataOutput data,ParcelFileDescriptor newState)throws IOException;
    public abstract void onRestore(BackupDataInput data,int appVersionCode,ParcelFileDescriptor newState)throws IOException;
    public void onRestore(BackupDataInput data,long appVersionCode,ParcelFileDescriptor newState)throws IOException{onRestore(data,(int)appVersionCode,newState);}
    public void onRestoreFinished(){}
}
