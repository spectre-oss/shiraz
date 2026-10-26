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
 public static void main(final String[] args){DedicatedProcess.run(new DedicatedProcess.Task(){public void run()throws Throwable{HostTlsProvider.install();Looper.prepareMainLooper();runService(args);}},new DedicatedProcess.Task(){public void run(){android.os.AsyncTask.shirazShutdown();android.database.sqlite.SQLiteDatabase.shirazShutdown();android.os.Environment.shirazShutdown();Looper.shirazShutdownMain();}});}
 private static void runService(String[] args)throws Exception{
  if(args.length!=9)throw new IllegalArgumentException("Service launch arguments");
  String pkg=args[0],process=args[7].startsWith(":")?args[0]+args[7]:args[7];Application.shirazSetProcessName(process);ServiceProcess.configure(args);
  Properties metadata=new Properties();try(Reader reader=new InputStreamReader(new FileInputStream(args[8]),StandardCharsets.UTF_8)){metadata.load(reader);}
  if(!pkg.equals(metadata.getProperty("package")))throw new IllegalArgumentException("Service package mismatch");
  System.err.println("SHIRAZ_SERVICE_START pid="+android.os.Process.myPid()+" process="+process+" component="+args[2]);
  boolean declared=false;int count=Integer.parseInt(metadata.getProperty("service.count","0"));if(count<0||count>256)throw new IllegalArgumentException("Service declaration limit");
  for(int i=0;i<count;i++)if(args[2].equals(metadata.getProperty("service."+i+".name"))){String key="service."+i+".";String expected=metadata.getProperty(key+"process",pkg);if(expected.startsWith(":"))expected=pkg+expected;
   if(!expected.equals(process)||!"false".equals(metadata.getProperty(key+"isolated","false"))||"false".equals(metadata.getProperty(key+"enabled")))throw new SecurityException("Unsupported service process declaration");declared=true;break;}
  if(!declared)throw new SecurityException("Undeclared service process");
  // Cache the executable command above before exposing Android process identity.
  android.os.Process.setArgV0(process);
  int width=Integer.parseInt(args[3]),height=Integer.parseInt(args[4]),density=Integer.parseInt(args[5]);
  if(width<1||height<1||width>8192||height>8192||density<1||density>4096)throw new IllegalArgumentException("Service display configuration");
  android.content.res.Resources.shirazSetSystemDisplay(width,height,density);
  HostStorage storage=new HostStorage(new File(args[6]),pkg);Environment.shirazInitialize(storage);
  Application app=Class.forName(args[1]).asSubclass(Application.class).newInstance();
  ActivityRunner.HostContext context=new ActivityRunner.HostContext(pkg,new File(args[6]),storage,app,metadata,width,height,density);
  Service service=null;BinderTransport transport=null;final Intent[] bound={null};
  try{
   ActivityRunner.attach(app,context);if(context.getApplicationInfo().theme!=0)context.setTheme(context.getApplicationInfo().theme);
   ProviderHost.attach(context,metadata);app.onCreate();
   service=Class.forName(args[2]).asSubclass(Service.class).newInstance();service.shirazAttach(context,app,context.services);service.onCreate();
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
   transport=new BinderTransport(3,control);final BinderTransport connection=transport;
   Thread death=new Thread(new Runnable(){public void run(){try{connection.awaitClosed();}catch(InterruptedException ignored){}Looper.shirazRequestMainExit();}},"shiraz-service-parent");death.setDaemon(true);death.start();
   Looper.loop();
  }finally{
   if(transport!=null)transport.close();
   try{if(service!=null){if(bound[0]!=null)service.onUnbind(bound[0]);service.onDestroy();}}finally{context.close();}
  }
 }
}
