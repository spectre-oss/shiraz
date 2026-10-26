// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Owns one declared non-isolated service child and its private Binder connection. */
final class ServiceProcess implements Closeable {
 private static String[] base,prefix;
 final int pid;final BinderTransport transport;final IBinder binder;
 static synchronized void configure(String[] args)throws IOException{
  if(args.length!=9)throw new IllegalArgumentException("Application launch arguments");
  byte[] bytes;try(InputStream in=new FileInputStream("/proc/self/cmdline")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[4096];int n;
   while((n=in.read(block))>0){if(out.size()+n>1024*1024)throw new IOException("Runtime command limit");out.write(block,0,n);}bytes=out.toByteArray();}
  ArrayList<String> command=new ArrayList<String>();int start=0;boolean found=false;
  for(int i=0;i<bytes.length;i++)if(bytes[i]==0){String value=new String(bytes,start,i-start,StandardCharsets.UTF_8);start=i+1;
   if(value.equals("shiraz.runtime.ActivityRunner")||value.equals("shiraz.runtime.ServiceRunner")){found=true;break;}command.add(value);}
  if(!found||command.isEmpty()||!command.get(0).startsWith("/"))throw new IOException("Cannot identify ART child command");
  prefix=command.toArray(new String[command.size()]);base=args.clone();
 }
 static synchronized boolean available(){return base!=null;}
 private ServiceProcess(int pid,BinderTransport transport,IBinder binder){this.pid=pid;this.transport=transport;this.binder=binder;}
 static ServiceProcess start(Context context,String service,String process,Intent intent)throws Exception{
  ArrayList<String> command;String[] args;
  synchronized(ServiceProcess.class){if(base==null)throw new UnsupportedOperationException("Service process launcher is unavailable");command=new ArrayList<String>(Arrays.asList(prefix));args=base.clone();}
  android.util.DisplayMetrics metrics=context.getResources().getDisplayMetrics();
  args[2]=service;args[3]=Integer.toString(metrics.widthPixels);args[4]=Integer.toString(metrics.heightPixels);args[5]=Integer.toString(metrics.densityDpi);args[7]=process;
  command.add("shiraz.runtime.ServiceRunner");command.addAll(Arrays.asList(args));
  int[] pair=NativeIpc.pair();int pid;
  try{pid=NativeIpc.spawn(command.toArray(new String[command.size()]),pair[1]);}
  catch(Exception error){NativeFd.closeRawQuietly(pair[0]);throw error;}finally{NativeFd.closeRawQuietly(pair[1]);}
  System.err.println("SHIRAZ_SERVICE_SPAWN pid="+pid+" component="+service+" process="+process);
  BinderTransport transport=new BinderTransport(pair[0],null);
  Thread reap=new Thread(new Runnable(){public void run(){try{int status=NativeIpc.waitFor(pid);System.err.println("SHIRAZ_SERVICE_EXIT pid="+pid+" status="+status);}catch(IOException error){System.err.println("W/ServiceProcess: child reap failed");}finally{transport.close();}}},"shiraz-service-reaper");reap.setDaemon(true);reap.start();
  Parcel input=Parcel.obtain(),output=Parcel.obtain();
  try{input.writeInterfaceToken("shiraz.service.control");input.writeTypedObject(intent,0);
   transport.remoteRoot().transact(1,input,output,0);output.readException();IBinder binder=output.readStrongBinder();
   System.err.println("SHIRAZ_SERVICE_BOUND pid="+pid+" component="+service);
   return new ServiceProcess(pid,transport,binder);
  }catch(Exception|Error error){transport.close();throw error;}finally{input.recycle();output.recycle();}
 }
 public void close(){transport.close();}
}
