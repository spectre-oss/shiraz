// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.*;
/** Headless ART entry point for a manifest-declared service in its own process. */
public final class ServiceRunner {
 public static void main(final String[] args){launch(args,false);}
 static void launch(final String[] args,final boolean receiver){launch(args,receiver,false);}
 static void launch(final String[] args,final boolean receiver,final boolean job){DedicatedProcess.run(new DedicatedProcess.Task(){public void run()throws Throwable{HostTlsProvider.install();Looper.prepareMainLooper();if(args.length>=10)RuntimeBroker.attach();try{runService(args,receiver,job);}finally{RuntimeBroker.close();}}},new DedicatedProcess.Task(){public void run(){ActivityThread.shirazClear();android.os.AsyncTask.shirazShutdown();android.database.sqlite.SQLiteDatabase.shirazShutdown();android.os.Environment.shirazShutdown();Looper.shirazShutdownMain();}});}
 private static void validateDeclaration(Properties metadata,String pkg,String component,String process,boolean receiver){
  String kind=receiver?"receiver":"service";
  boolean declared=false;int count=Integer.parseInt(metadata.getProperty(kind+".count","0"));if(count<0||count>(receiver?256:512))throw new IllegalArgumentException("Service declaration limit");
  for(int i=0;i<count;i++)if(component.equals(metadata.getProperty(kind+"."+i+".name"))){String key=kind+"."+i+".";String expected=metadata.getProperty(key+"process",pkg);if(expected.startsWith(":"))expected=pkg+expected;
   if(!expected.equals(process)||!"false".equals(metadata.getProperty(key+"isolated","false"))||"false".equals(metadata.getProperty(key+"enabled")))throw new SecurityException("Unsupported service process declaration");declared=true;break;}
  if(!declared)throw new SecurityException("Undeclared service process");
 }
 private static void runService(String[] supplied,boolean receiver,boolean job)throws Exception{
  if((receiver||job)&&supplied.length!=10||receiver&&job)throw new IllegalArgumentException("Dedicated worker launch arguments");
  // NativeIpc.spawn uses fd 3. A host launcher can pass an explicit private fd;
  // strip it before ServiceProcess.configure so nested children use their own fd 3.
  final boolean sharedMode=supplied.length==11&&"shared".equals(supplied[10]);
  if(supplied.length==11&&!sharedMode)throw new IllegalArgumentException("Service launch mode");
  final int channel;
  final String[] args;
  if(supplied.length==10||sharedMode){channel=Integer.parseInt(supplied[9]);if(channel<3)throw new IllegalArgumentException("Service channel descriptor");args=java.util.Arrays.copyOf(supplied,9);}
  else{channel=3;args=supplied;}
  if(args.length!=9)throw new IllegalArgumentException("Service launch arguments");
  String pkg=args[0],process=args[7].startsWith(":")?args[0]+args[7]:args[7];Application.shirazSetProcessName(process);ServiceProcess.configure(args);
  Properties metadata=new Properties();try(Reader reader=new InputStreamReader(new FileInputStream(args[8]),StandardCharsets.UTF_8)){metadata.load(reader);}
  if(!pkg.equals(metadata.getProperty("package")))throw new IllegalArgumentException("Service package mismatch");
  HostNetworkPolicy.install(metadata);
  System.err.println((job?"SHIRAZ_JOB_START pid=":receiver?"SHIRAZ_RECEIVER_START pid=":"SHIRAZ_SERVICE_START pid=")+android.os.Process.myPid()+" process="+process+" component="+args[2]);
  validateDeclaration(metadata,pkg,args[2],process,receiver);
  final JobDefinition.Launch admittedJob=job?JobRunner.read(channel,pkg,args[2]):null;
  // Cache the executable command above before exposing Android process identity.
  android.os.Process.setArgV0(process);
  int width=Integer.parseInt(args[3]),height=Integer.parseInt(args[4]),density=Integer.parseInt(args[5]);
  if(width<1||height<1||width>8192||height>8192||density<1||density>4096)throw new IllegalArgumentException("Service display configuration");
  android.content.res.Resources.shirazSetSystemDisplay(width,height,density);
  HostStorage storage=new HostStorage(new File(args[6]),pkg);Environment.shirazInitialize(storage);
  ActivityThread.shirazPrepare(pkg,metadata);
  Application app=Class.forName(args[1]).asSubclass(Application.class).newInstance();
  ActivityRunner.HostContext context=new ActivityRunner.HostContext(pkg,new File(args[6]),storage,app,metadata,width,height,density);
  Service service=null;BinderTransport transport=null;SharedServiceHost shared=null;final Intent[] bound={null};Boolean jobResult=null;JobControl jobControl=job?new JobControl(channel):null;
  try{
   // A dedicated service/receiver/job process has no visible activity. Set this
   // before app/provider callbacks can request capture or location services.
   context.foreground(false);
   ActivityThread.shirazBind(app,pkg);
   ActivityRunner.attach(app,context);if(context.getApplicationInfo().theme!=0)context.setTheme(context.getApplicationInfo().theme);
   ProviderHost.attach(context,metadata);app.onCreate();
   if(job){context.startReceiverHost();jobResult=AdmittedJob.run(context.services,admittedJob.job,jobControl,admittedJob.deadlineExpired);jobControl.check();return;}
   if(receiver){context.startReceiverHost();ReceiverRunner.deliver(channel,pkg,args[2],context);return;}
   service=context.services.createWorkerService(new ComponentName(pkg,args[2]),sharedMode?new HostServices.StartedListener(){public void changed(boolean started){
    try{RuntimeBroker.client().serviceStarted(started);}catch(IOException failure){throw new IllegalStateException("Worker started-state update failed",failure);}
   }public void completed(int startId,int restartMode,Intent original){
    try{RuntimeBroker.client().serviceStartCompleted(startId,restartMode,original);}catch(IOException failure){throw new IllegalStateException("Worker start-completion update failed",failure);}
   }public void finished(int startId){
    try{RuntimeBroker.client().serviceStartFinished(startId);}catch(IOException failure){throw new IllegalStateException("Worker work-completion update failed",failure);}
   }}:null);
   if(sharedMode){context.startReceiverHost();shared=new SharedServiceHost(channel,service,context.services,pkg,args[2]);Looper.loop();return;}
   final Service instance=service;final Handler main=new Handler(Looper.getMainLooper());
   Binder control=new Binder(){protected boolean onTransact(int code,Parcel input,Parcel output,int flags)throws RemoteException{
    if(code!=1)return super.onTransact(code,input,output,flags);input.enforceInterface("shiraz.service.control");final Intent intent=input.readTypedObject(Intent.CREATOR);input.enforceNoDataAvail();
    if(intent==null||intent.getComponent()==null||!args[2].equals(intent.getComponent().getClassName())||!pkg.equals(intent.getComponent().getPackageName()))throw new SecurityException("Service bind component mismatch");
    FutureTask<IBinder> bind=new FutureTask<IBinder>(new Callable<IBinder>(){public IBinder call(){bound[0]=intent;return instance.onBind(intent);}});
    if(!main.post(bind))throw new DeadObjectException("Service main queue stopped");
    try{IBinder result=bind.get();output.writeNoException();output.writeStrongBinder(result);return true;}
    catch(InterruptedException error){Thread.currentThread().interrupt();throw new DeadObjectException("Service binding interrupted");}
    catch(ExecutionException error){Throwable cause=error.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new RemoteException("Service binding failed");}
   }};
   int peerUid=-1;
   if(System.getenv("SHIRAZ_PACKAGE_UID")!=null)peerUid=android.os.Process.myUid();
   if(supplied.length==10&&System.getenv("SHIRAZ_SERVICE_CALLER_UID")!=null){
    String value=System.getenv("SHIRAZ_SERVICE_CALLER_UID");if(!value.matches("1[0-9]{4}"))throw new SecurityException("Invalid service caller UID");peerUid=Integer.parseInt(value);
   }
   transport=new BinderTransport(channel,control,peerUid);final BinderTransport connection=transport;
   System.out.println("SHIRAZ_SERVICE_CHANNEL_READY");System.out.flush();
   Thread death=new Thread(new Runnable(){public void run(){try{connection.awaitClosed();}catch(InterruptedException ignored){}Looper.shirazRequestMainExit();}},"shiraz-service-parent");death.setDaemon(true);death.start();
   Looper.loop();
  }catch(Exception|Error failure){
   // App background crash handlers may terminate the process while cleanup
   // tears down its context. Preserve the initiating failure before that race.
   System.err.println("SHIRAZ_SERVICE_FAILURE_BEFORE_CLEANUP");
   failure.printStackTrace(System.err);System.err.flush();
   throw failure;
  }finally{
   try{
   try{if(shared!=null)shared.close();if(transport!=null)transport.close();}
   finally{try{if(service!=null){if(bound[0]!=null)service.onUnbind(bound[0]);}}finally{context.close();}}
   if(jobResult!=null)JobRunner.complete(channel,jobResult,jobControl.stopReason());
   }finally{if(jobControl!=null)jobControl.close();}
  }
 }
}
