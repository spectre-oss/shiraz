// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.pm.PackageInstaller;
import android.os.*;
import java.io.*;
public final class InstallerSessionLifecycleSmoke {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();final File cache=java.nio.file.Files.createTempDirectory(new File("build").toPath(),"installer-lifecycle-").toFile();
  final PackageInstaller installer=new PackageInstaller(new ContextWrapper(null){public File getCacheDir(){return cache;}});
  final java.util.List<String> events=new java.util.ArrayList<String>();
  PackageInstaller.SessionCallback callback=new PackageInstaller.SessionCallback(){
   public void onCreated(int id){events.add("created:"+id);}
   public void onBadgingChanged(int id){}public void onActiveChanged(int id,boolean active){}
   public void onProgressChanged(int id,float progress){check(progress==0.5f,"progress value");events.add("progress:"+id);}
   public void onFinished(int id,boolean success){check(!success&&installer.getSessionInfo(id)==null,"abandon terminal state");events.add("finished:"+id);}
  };
  installer.registerSessionCallback(callback);
  try{
   for(int n=0;n<40;n++){
    int id=installer.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
    PackageInstaller.Session session=installer.openSession(id);
    try(OutputStream stream=session.openWrite("base.apk",0,3)){stream.write(new byte[]{1,2,3});}
    session.setStagingProgress(0.5f);
    try{session.setStagingProgress(Float.NaN);throw new AssertionError("NaN progress accepted");}catch(IllegalArgumentException expected){}
    if(n%2==0)session.abandon();else installer.abandonSession(id);
    session.abandon();check(installer.getMySessions().isEmpty(),"abandoned session consumed capacity");
    try{session.setStagingProgress(0.5f);throw new AssertionError("abandoned progress accepted");}catch(IllegalStateException expected){}
    Looper.myLooper().shirazDispatchDue(256);
    check(events.size()==3*(n+1),"missing or duplicate callback");
    check(events.get(n*3).equals("created:"+id)&&events.get(n*3+1).equals("progress:"+id)&&events.get(n*3+2).equals("finished:"+id),"callback order");
    check(cache.list().length==0,"abandon left staging files");
   }
   events.clear();
   for(int n=0;n<40;n++){
    int id=installer.createSession(new PackageInstaller.SessionParams(1));
    PackageInstaller.Session session=installer.openSession(id);
    OutputStream stream=session.openWrite("base.apk",0,3);stream.write(1);
    try{stream.close();throw new AssertionError("truncated stream accepted");}catch(IOException expected){}
    check(installer.getSessionInfo(id)==null&&installer.getMySessions().isEmpty(),"truncated session consumed capacity");
    check(cache.list().length==0,"truncated session left staging files");
    try{stream.write(2);throw new AssertionError("failed stream writable");}catch(IOException expected){}
    try{session.commit(null);throw new AssertionError("failed session committed");}catch(IllegalStateException expected){}
    stream.close();session.abandon();installer.abandonSession(id);
    Looper.myLooper().shirazDispatchDue(256);
    check(events.size()==2*(n+1),"truncated callback missing or duplicated");
    check(events.get(2*n).equals("created:"+id)&&events.get(2*n+1).equals("finished:"+id),"truncated callback order");
   }
   installer.unregisterSessionCallback(callback);
   int id=installer.createSession(new PackageInstaller.SessionParams(1));installer.abandonSession(id);Looper.myLooper().shirazDispatchDue(256);check(events.size()==80,"unregistered callback invoked");
  }finally{for(PackageInstaller.SessionInfo info:installer.getMySessions())installer.abandonSession(info.getSessionId());cache.delete();Looper.shirazShutdownMain();}
  System.out.println("INSTALLER_SESSION_LIFECYCLE_OK");
 }
}
