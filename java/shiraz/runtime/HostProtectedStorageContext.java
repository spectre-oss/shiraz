// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;

/** Separate desktop storage namespace; does not implement encryption or locked boot. */
final class HostProtectedStorageContext extends ContextWrapper implements java.io.Closeable {
 private final HostStorage storage;
 private final HostPreferences preferences;
 HostProtectedStorageContext(Context credential){
  super(credential);
  storage=new HostStorage(new File(credential.getDataDir(),"device-protected"),credential.getPackageName());
  preferences=new HostPreferences(new File(storage.data(),"preferences"));
 }
 @Override public boolean isDeviceProtectedStorage(){return true;}
 @Override public boolean isCredentialProtectedStorage(){return false;}
 @Override public Context createDeviceProtectedStorageContext(){return this;}
 @Override public Context createCredentialProtectedStorageContext(){return getBaseContext();}
 @Override public SharedPreferences getSharedPreferences(String name,int mode){return preferences.get(name,mode);}
 @Override public boolean deleteSharedPreferences(String name){return preferences.delete(name);}
 @Override public boolean moveSharedPreferencesFrom(Context source,String name){return HostPreferences.move(source.getSharedPreferences(name,MODE_PRIVATE),getSharedPreferences(name,MODE_PRIVATE));}
 @Override public File getDataDir(){return storage.data();}
 @Override public File getFilesDir(){return storage.files();}
 @Override public File getFileStreamPath(String name){return storage.filePath(name);}
 @Override public File getCacheDir(){return storage.cache();}
 @Override public File getCodeCacheDir(){return storage.codeCache();}
 @Override public File getNoBackupFilesDir(){return storage.noBackup();}
 @Override public File getDir(String name,int mode){return storage.named(name,mode);}
 @Override public File getDatabasePath(String name){return storage.databasePath(name);}
 @Override public String[] databaseList(){return storage.databases();}
 @Override public boolean deleteDatabase(String name){return SQLiteDatabase.deleteDatabase(getDatabasePath(name));}
 @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return openOrCreateDatabase(name,mode,factory,null);}
 @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,android.database.DatabaseErrorHandler handler){
  if((mode&~24)!=0)throw new SecurityException("Only private database modes are supported");
  int flags=SQLiteDatabase.CREATE_IF_NECESSARY;
  if((mode&MODE_ENABLE_WRITE_AHEAD_LOGGING)!=0)flags|=SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING;
  if((mode&MODE_NO_LOCALIZED_COLLATORS)!=0)flags|=SQLiteDatabase.NO_LOCALIZED_COLLATORS;
  return SQLiteDatabase.openDatabase(getDatabasePath(name).getPath(),factory,flags,handler);
 }
 @Override public void close(){preferences.close();}
}
