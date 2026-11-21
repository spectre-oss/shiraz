// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.*;
import android.os.Looper;
import java.io.File;
import java.nio.file.*;

public final class ProtectedStorageSmoke {
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();final Path root=Files.createTempDirectory(Paths.get("build"),"protected-storage-");
  final HostPreferences prefs=new HostPreferences(root.resolve("preferences").toFile());
  Context credential=new ContextWrapper(null){
   public String getPackageName(){return "test.storage";}
   public File getDataDir(){return root.toFile();}
   public SharedPreferences getSharedPreferences(String name,int mode){return prefs.get(name,mode);}
   public boolean isCredentialProtectedStorage(){return true;}
   public boolean isDeviceProtectedStorage(){return false;}
  };
  try{
   try(HostProtectedStorageContext device=new HostProtectedStorageContext(credential)){
    Context wrapped=new ContextWrapper(device);
    if(!wrapped.isDeviceProtectedStorage()||wrapped.isCredentialProtectedStorage()||wrapped.createDeviceProtectedStorageContext()!=device||wrapped.createCredentialProtectedStorageContext()!=credential)throw new AssertionError("Storage identity/delegation");
    if(device.getDataDir().equals(credential.getDataDir()))throw new AssertionError("Aliased storage roots");
    for(File path:new File[]{device.getFilesDir(),device.getCacheDir(),device.getCodeCacheDir(),device.getNoBackupFilesDir(),device.getDir("module",0),device.getDatabasePath("state.db")})if(!path.toPath().startsWith(device.getDataDir().toPath()))throw new AssertionError("Storage path escaped namespace");
    prefs.get("same",0).edit().putString("value","credential").commit();
    SharedPreferences dp=device.getSharedPreferences("same",0);
    if(dp.contains("value"))throw new AssertionError("Credential preferences visible in device namespace");
    if(!dp.edit().putString("value","device").commit())throw new AssertionError("Device preference write");
    if(!"credential".equals(prefs.get("same",0).getString("value",null)))throw new AssertionError("Device write changed credential storage");
    prefs.get("move",0).edit().putInt("value",7).commit();
    if(!device.moveSharedPreferencesFrom(credential,"move")||device.getSharedPreferences("move",0).getInt("value",0)!=7||prefs.get("move",0).contains("value"))throw new AssertionError("Preference namespace migration");
    if(!device.deleteSharedPreferences("same")||!"credential".equals(prefs.get("same",0).getString("value",null)))throw new AssertionError("Deletion crossed namespace");
   }
   try(HostProtectedStorageContext reopened=new HostProtectedStorageContext(credential)){if(reopened.getSharedPreferences("move",0).getInt("value",0)!=7)throw new AssertionError("Device namespace persistence");}
   System.out.println("PROTECTED_STORAGE_OK");
  }finally{prefs.close();Looper.shirazShutdownMain();try(java.util.stream.Stream<Path> stream=Files.walk(root)){java.util.ArrayList<Path> paths=new java.util.ArrayList<Path>();java.util.Iterator<Path> it=stream.iterator();while(it.hasNext())paths.add(it.next());java.util.Collections.reverse(paths);for(Path path:paths)Files.delete(path);}}
 }
}
