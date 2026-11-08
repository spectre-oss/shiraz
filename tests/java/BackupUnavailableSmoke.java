// SPDX-License-Identifier: GPL-3.0-only
import android.app.backup.*;
import android.content.ContextWrapper;
public final class BackupUnavailableSmoke {
    public static void main(String[] args)throws Exception{
        ContextWrapper context=new ContextWrapper(null){public String getPackageName(){return "org.shiraz.backup";}};
        BackupAgentHelper agent=new BackupAgentHelper();SharedPreferencesBackupHelper helper=new SharedPreferencesBackupHelper(context,"settings");
        agent.addHelper("prefs",helper);agent.addHelper("prefs",helper);
        BackupManager manager=new BackupManager(context);manager.dataChanged();
        if(manager.isBackupEnabled())throw new AssertionError("absent transport advertised");
        try{manager.backupNow();throw new AssertionError("backup falsely succeeded");}catch(UnsupportedOperationException expected){}
        try{agent.onBackup(null,null,null);throw new AssertionError("agent falsely succeeded");}catch(java.io.IOException expected){}
        try{helper.performBackup(null,null,null);throw new AssertionError("preferences exported without transport");}catch(UnsupportedOperationException expected){}
        System.out.println("BACKUP_UNAVAILABLE_OK");
    }
}
