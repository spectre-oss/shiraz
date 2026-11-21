// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Intent;
import android.os.Looper;
import android.os.Parcel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
/** One admitted manifest receiver invocation on a private host-owned channel. */
public final class ReceiverRunner {
 private ReceiverRunner() {}
 public static void main(String[] args){ServiceRunner.launch(args,true);}
 static void deliver(int channel,String pkg,String component,ActivityRunner.HostContext context)throws IOException {
  // The host must authorize and pin the generation before spawning this entry point.
  // The channel carries only a typed Intent, never Binder or descriptor attachments.
  final Intent intent;
  try(NativeIpc.Packet packet=NativeIpc.receive(channel)){
   if(packet.descriptorCount()!=0||packet.bytes.length>4*1024*1024)throw new IOException("Receiver payload limit or attachments");
   Parcel parcel=Parcel.obtain();
   try{parcel.unmarshall(packet.bytes,0,packet.bytes.length);intent=parcel.readTypedObject(Intent.CREATOR);parcel.enforceNoDataAvail();}
   finally{parcel.recycle();}
  }
  if(intent==null||intent.getComponent()!=null&&(!pkg.equals(intent.getComponent().getPackageName())
     ||!component.equals(intent.getComponent().getClassName()))||intent.getPackage()!=null&&!pkg.equals(intent.getPackage()))
   throw new SecurityException("Receiver component mismatch");
  if((intent.getFlags()&Intent.FLAG_RECEIVER_REGISTERED_ONLY)!=0)throw new IllegalArgumentException("Manifest receiver excluded by flags");
  final HostBroadcasts broadcasts=context.shirazBroadcasts();
  java.util.concurrent.Future<Void> completion=broadcasts.receiveAdmitted(intent,new android.content.ComponentName(pkg,component));
  if(!broadcasts.hasWork())throw new IOException("Receiver was not enqueued");
  Looper.myLooper().shirazLoopWhile(new java.util.function.BooleanSupplier(){public boolean getAsBoolean(){return broadcasts.hasWork()||context.hasInboundReceiverWork();}});
  if(broadcasts.hasWork()||context.hasInboundReceiverWork())throw new IOException("Receiver loop stopped before completion");
  try{completion.get();}
  catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("Receiver completion interrupted",error);}
  catch(java.util.concurrent.ExecutionException|java.util.concurrent.CancellationException error){throw new IOException("Receiver completion failed",error);}
  NativeIpc.send(channel,"{\"ok\":true}".getBytes(StandardCharsets.UTF_8),new int[0]);
 }
}
