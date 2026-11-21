// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.os.Looper;
import java.nio.file.*;
import java.util.stream.Stream;
import shiraz.runtime.HostPreferences;

public final class DeletePreferencesSmoke {
 private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();Path root=Files.createTempDirectory(Paths.get("build"),"delete-prefs-");
  try{
   try(final HostPreferences prefs=new HostPreferences(root.toFile())){
    Context context=new ContextWrapper(new ContextWrapper(null){public boolean deleteSharedPreferences(String name){return prefs.delete(name);}});
    require(context.deleteSharedPreferences("missing"),"Missing is success");
    SharedPreferences old=prefs.get("state",0);require(old.edit().putInt("value",1).commit(),"Initial commit");
    Path stored;try(Stream<Path> files=Files.walk(root)){stored=null;java.util.Iterator<Path> it=files.iterator();while(it.hasNext()){Path candidate=it.next();if(Files.isRegularFile(candidate)){stored=candidate;break;}}require(stored!=null,"Stored preference missing");}
    old.edit().putInt("value",2).apply();
    require(context.deleteSharedPreferences("state"),"Delete after pending apply");
    require(!Files.exists(stored),"Pending write resurrected file");
    SharedPreferences fresh=prefs.get("state",0);
    require(fresh!=old&&!fresh.contains("value"),"Cached store not evicted");
    require(old.getInt("value",0)==2,"Retained reference memory was cleared");
    require(old.edit().putInt("value",2).commit(),"Retained reference can recreate file");
    require(Files.exists(stored),"Durable cache incorrectly skipped recreation");
    require(context.deleteSharedPreferences("state"),"Delete recreated file");
    require(old.edit().putInt("value",2).commit()&&Files.exists(stored),"Repeated deletion invalidates retained durable shortcut");
    require(context.deleteSharedPreferences("state"),"Delete again");
    Files.write(Paths.get(stored.toString()+".tmp"),new byte[]{1});
    require(context.deleteSharedPreferences("state")&&!Files.exists(Paths.get(stored.toString()+".tmp")),"Temporary file cleanup");
    Files.createDirectory(stored);Files.write(stored.resolve("child"),new byte[]{1});
    require(!context.deleteSharedPreferences("state"),"Deletion failure reported success");
    Files.delete(stored.resolve("child"));Files.delete(stored);
    try{prefs.delete(null);throw new AssertionError("Null name accepted");}catch(NullPointerException expected){}
    Thread.currentThread().interrupt();require(prefs.delete("missing"),"Interrupted delete");require(Thread.interrupted(),"Interrupt lost");
   }
   try(HostPreferences reopened=new HostPreferences(root.toFile())){require(reopened.get("state",0).getAll().isEmpty(),"Deleted values returned after restart");}
   HostPreferences closed=new HostPreferences(root.toFile());closed.close();
   try{closed.delete("state");throw new AssertionError("Closed deletion accepted");}catch(IllegalStateException expected){}
   System.out.println("DELETE_PREFERENCES_OK");
  }finally{Looper.shirazShutdownMain();try(Stream<Path> files=Files.walk(root)){java.util.ArrayList<Path> paths=new java.util.ArrayList<Path>();java.util.Iterator<Path> it=files.iterator();while(it.hasNext())paths.add(it.next());java.util.Collections.reverse(paths);for(Path p:paths)Files.delete(p);}}
 }
}
