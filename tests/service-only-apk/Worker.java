// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.servicefixture;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
public final class Worker extends Service {
 private volatile int lastStart;
 public int onStartCommand(Intent intent,int flags,int id){lastStart=id;if(intent==null){if(flags!=0||id!=1)throw new AssertionError("Sticky restart arguments");System.out.println("SERVICE_ONLY_STICKY_NULL");}if(intent!=null&&intent.getBooleanExtra("foreground",false)){
  android.app.NotificationManager manager=getSystemService(android.app.NotificationManager.class);manager.createNotificationChannel(new android.app.NotificationChannel("worker","Worker",android.app.NotificationManager.IMPORTANCE_LOW));
  startForeground(29,new android.app.Notification.Builder(this,"worker").setSmallIcon(1).setContentTitle("Fixture worker").build());System.out.println("SERVICE_ONLY_FOREGROUND");
 }System.out.println("SERVICE_ONLY_STARTED "+id);return START_NOT_STICKY;}
 private int binds,calls,unbinds;private boolean coexistVerified;
 private final java.util.HashSet<Integer> sessionCallers=new java.util.HashSet<Integer>();
 public void onCreate(){
  if(!"org.shiraz.servicefixture".equals(getPackageName()))throw new AssertionError("wrong package");
  if(!"org.shiraz.servicefixture:worker".equals(android.app.Application.getProcessName()))throw new AssertionError("wrong process");
  android.app.ActivityManager manager=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
  java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes=manager.getRunningAppProcesses();
  if(processes.size()!=1||processes.get(0).importance==android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)throw new AssertionError("Headless worker inherited activity foreground state");
  android.app.ActivityManager.RunningAppProcessInfo own=new android.app.ActivityManager.RunningAppProcessInfo();
  android.app.ActivityManager.getMyMemoryState(own);
  if(own.importance!=android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE||own.importance!=processes.get(0).importance||own.uid!=android.os.Process.myUid())throw new AssertionError("Headless service importance mismatch");
  System.out.println("SERVICE_ONLY_NO_ACTIVITY_FOREGROUND");
  new java.io.File(getFilesDir(),"coexist-ready").delete();new java.io.File(getFilesDir(),"coexist-done").delete();
  System.out.println("SERVICE_ONLY_CREATED");
 }
 public IBinder onBind(Intent intent){
  if("org.shiraz.servicefixture.TYPED".equals(intent.getAction())){
   if(!"content://fixture/item".equals(intent.getDataString())||!"text/plain".equals(intent.getType())||!intent.getCategories().contains("org.shiraz.servicefixture.CLIENT"))throw new AssertionError("Typed binding payload changed");
   System.out.println("SERVICE_TYPED_BIND_PAYLOAD_OK");
  }
  final String bindingAction=intent.getAction(),bindingPayload=intent.getStringExtra("payload");
  binds++;System.out.println("SERVICE_ONLY_BOUND");return new Binder(){
  protected boolean onTransact(int code,android.os.Parcel input,android.os.Parcel output,int flags)throws android.os.RemoteException{
   if(code==9){input.enforceInterface("shiraz.fixture.service");input.enforceNoDataAvail();output.writeNoException();output.writeString(bindingAction);output.writeString(bindingPayload);output.writeInt(android.os.Process.myPid());output.writeInt(System.identityHashCode(Worker.this));output.writeInt(System.identityHashCode(this));return true;}
   if(code==8){input.enforceInterface("shiraz.fixture.service");input.enforceNoDataAvail();output.writeNoException();output.writeInt(android.os.Process.myPid());output.writeInt(System.identityHashCode(Worker.this));output.writeInt(lastStart);return true;}
   if(code==7){
    input.enforceInterface("shiraz.fixture.service");input.enforceNoDataAvail();
    java.util.concurrent.FutureTask<Integer> task=new java.util.concurrent.FutureTask<Integer>(new java.util.concurrent.Callable<Integer>(){public Integer call(){int id=lastStart;if(id!=2||stopSelfResult(id-1)||!stopSelfResult(id))throw new AssertionError("Started ID lifecycle");return id;}});
    new android.os.Handler(android.os.Looper.getMainLooper()).post(task);
    try{int id=task.get(10,java.util.concurrent.TimeUnit.SECONDS);output.writeNoException();output.writeInt(id);return true;}catch(Exception error){throw new AssertionError(error);}
   }
   if(code==5||code==6){
    input.enforceInterface("shiraz.fixture.service");input.enforceNoDataAvail();int caller=android.os.Binder.getCallingUid();
    synchronized(Worker.this){sessionCallers.add(caller);Worker.this.notifyAll();long deadline=System.nanoTime()+20000000000L;
     while(sessionCallers.size()<2){long remaining=deadline-System.nanoTime();if(remaining<=0)throw new AssertionError("Second app did not share service");try{Worker.this.wait(Math.max(1,remaining/1000000));}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}}
     if(code==6&&!coexistVerified){
      java.io.File ready=new java.io.File(getFilesDir(),"coexist-ready");
      while(!ready.isFile()){if(System.nanoTime()>=deadline)throw new AssertionError("Activity did not coexist");try{Worker.this.wait(20);}catch(InterruptedException error){throw new AssertionError(error);}}
      try(java.io.FileInputStream stream=new java.io.FileInputStream(ready)){
       byte[] bytes=new byte[32];int size=stream.read(bytes);int activityPid=Integer.parseInt(new String(bytes,0,size,"UTF-8"));
       if(activityPid==android.os.Process.myPid())throw new AssertionError("Separate service process required");
       new java.io.File(getFilesDir(),"coexist-done").createNewFile();coexistVerified=true;
      }catch(java.io.IOException error){throw new AssertionError(error);}
     }
     output.writeNoException();output.writeInt(android.os.Process.myPid());output.writeInt(System.identityHashCode(Worker.this));output.writeInt(sessionCallers.size());output.writeInt(caller);return true;
    }
   }
   if(code==4){input.enforceInterface("shiraz.fixture.service");input.enforceNoDataAvail();int[] result=nested();output.writeNoException();for(int value:result)output.writeInt(value);return true;}
   if(code==3){input.enforceInterface("shiraz.fixture.service");input.enforceNoDataAvail();synchronized(Worker.this){
    output.writeNoException();output.writeInt(android.os.Process.myPid());output.writeInt(System.identityHashCode(Worker.this));output.writeInt(binds);output.writeInt(++calls);output.writeInt(android.os.Binder.getCallingUid());output.writeInt(unbinds);return true;
   }}
   if(code==2){
    input.enforceInterface("shiraz.fixture.service");IBinder callback=input.readStrongBinder();input.enforceNoDataAvail();
    int caller=android.os.Binder.getCallingUid(),own=android.os.Process.myUid();
    String normal="org.shiraz.servicefixture.NORMAL",signature="org.shiraz.servicefixture.SIGNATURE";
    if(checkCallingPermission(normal)!=0||checkCallingOrSelfPermission(normal)!=0||checkCallingPermission(signature)!=-1||checkCallingOrSelfPermission(signature)!=-1)throw new AssertionError("Caller permission identity");
    enforceCallingPermission(normal,"Normal grant");
    try{enforceCallingPermission(signature,"Signature denied");throw new AssertionError("Missing signature denial");}catch(SecurityException expected){}
    if(checkSelfPermission(signature)!=0||getPackageManager().checkPermission(signature,"org.shiraz.backgroundfixture")!=0||getPackageManager().checkPermission(signature,"org.shiraz.brokercaller")!=-1||getPackageManager().checkPermission(normal,"missing.package")!=-1||checkSelfPermission("org.shiraz.servicefixture.DANGEROUS")!=-1)throw new AssertionError("Verified permission grants");
    long token=android.os.Binder.clearCallingIdentity();int cleared;
    try{cleared=android.os.Binder.getCallingUid();if(checkCallingOrSelfPermission(signature)!=0||checkCallingPermission(signature)!=-1)throw new AssertionError("Cleared caller permission");}finally{android.os.Binder.restoreCallingIdentity(token);}
    int restored=android.os.Binder.getCallingUid();
    if(checkCallingPermission(signature)!=-1)throw new AssertionError("Restored caller permission");
    System.out.println("REGISTERED_CALLER_PERMISSIONS_OK");
    if(getPackageManager().checkSignatures(caller,own)!=android.content.pm.PackageManager.SIGNATURE_NO_MATCH)throw new AssertionError("Service caller signer comparison");
    String[] packages=getPackageManager().getPackagesForUid(caller);
    if(packages==null||packages.length!=1)throw new AssertionError("Caller package lookup");
    try{if(getPackageManager().getPackageUid(packages[0],0)!=caller)throw new AssertionError("Caller package UID mismatch");}
    catch(android.content.pm.PackageManager.NameNotFoundException error){throw new AssertionError(error);}
    android.os.Parcel request=android.os.Parcel.obtain(),reply=android.os.Parcel.obtain();int callbackOwn;
    try{if(!callback.transact(1,request,reply,0))throw new AssertionError("Callback rejected");reply.readException();callbackOwn=reply.readInt();reply.enforceNoDataAvail();}
    finally{request.recycle();reply.recycle();}
    output.writeNoException();output.writeInt(caller);output.writeInt(own);output.writeInt(cleared);output.writeInt(restored);output.writeInt(callbackOwn);output.writeString(packages[0]);return true;
   }
   if(code!=1)return super.onTransact(code,input,output,flags);
   input.enforceInterface("shiraz.fixture.service");int value=input.readInt();input.enforceNoDataAvail();
   output.writeNoException();output.writeInt(value+1);output.writeString(getPackageName());return true;
  }
 };}
 private int[] nested(){
  final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
  final int[] result=new int[4];final Throwable[] failure={null};
  new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable(){public void run(){
   android.content.ServiceConnection connection=new android.content.ServiceConnection(){
    public void onServiceConnected(android.content.ComponentName name,IBinder binder){
     android.os.Parcel input=android.os.Parcel.obtain(),output=android.os.Parcel.obtain();
     try{input.writeInterfaceToken("shiraz.fixture.leaf");if(!binder.transact(1,input,output,0))throw new AssertionError("Leaf method");output.readException();for(int i=0;i<4;i++)result[i]=output.readInt();output.enforceNoDataAvail();}
     catch(Throwable error){failure[0]=error;}finally{input.recycle();output.recycle();unbindService(this);done.countDown();}
    }
    public void onServiceDisconnected(android.content.ComponentName name){}
    public void onBindingDied(android.content.ComponentName name){failure[0]=new AssertionError("Leaf binding died");unbindService(this);done.countDown();}
    public void onNullBinding(android.content.ComponentName name){onBindingDied(name);}
   };
   try{if(!bindService(new Intent().setComponent(new android.content.ComponentName("org.shiraz.backgroundfixture","org.shiraz.backgroundfixture.LeafService")),connection,BIND_AUTO_CREATE))throw new AssertionError("Leaf bind rejected");}
   catch(Throwable error){failure[0]=error;done.countDown();}
  }});
  try{if(!done.await(20,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Leaf bind timeout");}
  catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
  if(failure[0]!=null)throw new AssertionError(failure[0]);return result;
 }
 public boolean onUnbind(Intent intent){unbinds++;System.out.println("SERVICE_ONLY_UNBOUND");return false;}
 public void onDestroy(){System.out.println("SERVICE_ONLY_DESTROYED");}
}
