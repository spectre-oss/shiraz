// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import java.io.File;
public final class NotificationSharedPolicySmoke {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  if(args.length==1){NotificationManager child=NotificationManager.shirazForProcess("fixture",new File(args[0]));try{child.createNotificationChannel(new NotificationChannel("main","Other process",0));}finally{child.shirazClose();}System.out.println("NOTIFICATION_CHILD_POLICY_OK");return;}
  File policy=File.createTempFile("notification-shared-", ".db",new File("build"));
  final NotificationManager first=NotificationManager.shirazForProcess("fixture",policy),second=NotificationManager.shirazForProcess("fixture",policy);
  try{
   check(first.getNotificationChannels().isEmpty()&&second.getNotificationChannels().isEmpty(),"initial snapshots");
   first.createNotificationChannel(new NotificationChannel("main","Initial",4));
   check(second.getNotificationChannel("main").getImportance()==4,"other manager missed creation");
   second.createNotificationChannel(new NotificationChannel("main","Lower",2));
   check(first.getNotificationChannel("main").getImportance()==2,"stale importance");
   first.createNotificationChannel(new NotificationChannel("main","Must retain",5));
   check(second.getNotificationChannel("main").getImportance()==2,"stale writer raised importance");
   second.deleteNotificationChannel("main");check(first.getNotificationChannel("main")==null,"stale deletion");
   first.createNotificationChannel(new NotificationChannel("main","Restored",5));
   check(second.getNotificationChannel("main").getImportance()==2,"restore lost prior policy");
   final java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
   final java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
   Thread[] writers=new Thread[2];
   for(int i=0;i<2;i++){final int index=i;writers[i]=new Thread(new Runnable(){public void run(){try{start.await();for(int n=0;n<12;n++)(index==0?first:second).createNotificationChannel(new NotificationChannel("main","Writer "+index,index==0?1:5));}catch(Throwable error){failure.set(error);}}});writers[i].start();}
   start.countDown();for(Thread writer:writers){writer.join(10000);check(!writer.isAlive(),"policy writer stalled");}
   if(failure.get()!=null)throw new AssertionError(failure.get());
   check(first.getNotificationChannel("main").getImportance()==1&&second.getNotificationChannel("main").getImportance()==1,"concurrent update raised policy");
   File root=new File(System.getProperty("user.dir"));String classpath=new File(root,"build/java-framework-smoke/NotificationSharedPolicySmoke/classes").getPath()+File.pathSeparator+new File(root,"build/framework/classes").getPath()+File.pathSeparator+new File(root,"context/art_standalone/out/host/common/obj/JAVA_LIBRARIES/core-libart-hostdex_intermediates/classes.jar").getPath();
   String natives=new File(root,shiraz.runtime.HostOs.darwin()?"build/macos/jvm-native":"build").getPath();
   Process child=new ProcessBuilder("java","-Xcheck:jni","-Djava.library.path="+natives,"-cp",classpath,"NotificationSharedPolicySmoke",policy.getAbsolutePath()).inheritIO().start();
   if(!child.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor();throw new AssertionError("Policy child timeout");}
   check(child.exitValue()==0,"Policy child failed");
   check(first.getNotificationChannel("main").getImportance()==0&&second.getNotificationChannel("main").getImportance()==0,"separate process policy change invisible");
   first.createNotificationChannel(new NotificationChannel("main","Cannot unblock",5));check(second.getNotificationChannel("main").getImportance()==0,"stale manager unblocked channel");
  }finally{first.shirazClose();second.shirazClose();policy.delete();}
  System.out.println("NOTIFICATION_SHARED_POLICY_OK");
 }
}
