// SPDX-License-Identifier: GPL-3.0-only
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import shiraz.runtime.HostPreferences;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class MultiProcessPreferencesSmoke {
 static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 static void child(Path root,String action)throws Exception{
  // Separate OpenJDK writer, also when the observing parent is running on ART.
  String base="build/java-framework-smoke/MultiProcessPreferencesSmoke/";
  String cp=base+"classes:"+base+"framework-classes:context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar";
  Process process=new ProcessBuilder("java","-Djava.library.path="+(new File("build/macos/jvm-native").isDirectory()?"build/macos/jvm-native":"build"),"-cp",cp,"MultiProcessPreferencesSmoke",root.toString(),action).inheritIO().start();
  require(process.waitFor(15,TimeUnit.SECONDS),"Writer process timeout");
  require(process.exitValue()==0,"Writer process failed");
 }
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();
  if(args.length==2){
   try(HostPreferences prefs=new HostPreferences(new File(args[0]))){
    if(args[1].equals("delete"))require(prefs.delete("state"),"Child delete");
    else require(prefs.get("state",0).edit().putString("value",args[1]).commit(),"Child write");
   }finally{Looper.shirazShutdownMain();}
   return;
  }
  Path root=Files.createTempDirectory("shiraz-multiprocess-prefs-");
  try(HostPreferences prefs=new HostPreferences(root.toFile())){
   SharedPreferences cached=prefs.get("state",0);
   require(cached.edit().putString("value","local").commit(),"Initial write");
   child(root,"remote");
   require(prefs.get("state",0)==cached,"Cache identity");
   require(cached.getString("value","").equals("local"),"Private mode should retain cache");
   require(prefs.get("state",Context.MODE_MULTI_PROCESS)==cached,"Refresh changed object identity");
   require(cached.getString("value","").equals("remote"),"External write not reloaded");
   child(root,"delete");
   prefs.get("state",4);require(cached.getAll().isEmpty(),"External deletion not reloaded");
   cached.edit().putString("value","applied").apply();
   require(prefs.get("state",4).getString("value","").equals("applied"),"Refresh lost pending apply");
   require(cached.edit().putString("value","local").commit(),"Drain apply");
   child(root,"remote-again");
   require(cached.edit().putString("value","local").commit(),"Unchanged commit");
   try(HostPreferences disk=new HostPreferences(root.toFile())){
    require(disk.get("state",0).getString("value","").equals("local"),"Durability cache ignored external replacement");
   }
   for(int bad:new int[]{1,2,5,8,-1}){
    try{prefs.get("state",bad);throw new AssertionError("Unsupported mode accepted");}catch(UnsupportedOperationException expected){}
   }
   System.out.println("MULTIPROCESS_PREFERENCES_OK");
  }finally{
   Looper.shirazShutdownMain();
   try(java.util.stream.Stream<Path> paths=Files.walk(root)){
    List<Path> all=new ArrayList<Path>();Iterator<Path> it=paths.iterator();while(it.hasNext())all.add(it.next());
    Collections.sort(all,Collections.reverseOrder());for(Path p:all)Files.delete(p);
   }
  }
 }
}
