// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Intent;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
/** Private inbound host channel dispatching into this existing application's main queue. */
final class ReceiverHost implements Closeable {
 private final ParcelFileDescriptor control;
 private final HostBroadcasts broadcasts;
 private final String pkg;
 private final Handler wake=new Handler(Looper.getMainLooper());
 private final Set<ParcelFileDescriptor> clients=new HashSet<ParcelFileDescriptor>();
 private volatile boolean closed;
 ReceiverHost(ServiceBrokerClient broker,String pkg,HostBroadcasts broadcasts)throws IOException {
  this.pkg=pkg;this.broadcasts=broadcasts;control=broker.registerReceiverHost(android.app.Application.getProcessName());
  try{Thread reader=new Thread(new Runnable(){public void run(){receive();}},"shiraz-receiver-host");reader.setDaemon(true);reader.start();}
  catch(RuntimeException|Error error){control.close();throw error;}
 }
 private void receive(){
  try{
   while(!closed)try(NativeIpc.Packet packet=NativeIpc.receive(control.getFd())){
    if(packet.descriptorCount()!=1||packet.bytes.length<1||packet.bytes.length>1024)throw new IOException("Inbound receiver admission frame");
    final String component=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString();
    if(!component.matches("dynamic:[0-9a-f]{32}")&&!component.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))throw new IOException("Inbound receiver component");
    final ParcelFileDescriptor endpoint=packet.takeDescriptor(0);
    synchronized(this){
     if(closed||clients.size()>=16){endpoint.close();throw new IOException("Inbound receiver capacity");}
     clients.add(endpoint);
     try{Thread worker=new Thread(new Runnable(){public void run(){deliver(endpoint,component);}},"shiraz-inbound-receiver");worker.setDaemon(true);worker.start();}
     catch(RuntimeException|Error error){clients.remove(endpoint);endpoint.close();throw error;}
    }
   }
  }catch(IOException|RuntimeException error){if(!closed)System.err.println("SHIRAZ_RECEIVER_HOST_FAILED "+error);}
  finally{close();try{control.close();}catch(IOException ignored){}}
 }
 private void deliver(final ParcelFileDescriptor endpoint,String component){
  try{
   final Intent intent;
   try(NativeIpc.Packet packet=NativeIpc.receive(endpoint.getFd())){
    if(packet.descriptorCount()!=0||packet.bytes.length>4*1024*1024)throw new IOException("Inbound receiver payload");
    Parcel parcel=Parcel.obtain();try{parcel.unmarshall(packet.bytes,0,packet.bytes.length);intent=parcel.readTypedObject(Intent.CREATOR);parcel.enforceNoDataAvail();}finally{parcel.recycle();}
   }
   boolean dynamic=component.startsWith("dynamic:");
   if(intent==null||dynamic&&intent.getComponent()!=null||!dynamic&&intent.getComponent()!=null&&(!pkg.equals(intent.getComponent().getPackageName())||!component.equals(intent.getComponent().getClassName())))throw new IOException("Inbound receiver capability mismatch");
   if(closed)throw new IOException("Receiver host closed");
   // receiveAdmitted only snapshots/resolves/enqueues under its lock; class
   // construction and onReceive remain on the existing main-thread handler.
   if(dynamic)broadcasts.receiveDynamicAdmitted(intent,component.substring(8)).get(12,TimeUnit.SECONDS);
   else broadcasts.receiveAdmitted(intent,new android.content.ComponentName(pkg,component)).get(12,TimeUnit.SECONDS);
   NativeIpc.send(endpoint.getFd(),"{\"ok\":true}".getBytes(StandardCharsets.UTF_8),new int[0]);
  }catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();if(!closed)System.err.println("SHIRAZ_INBOUND_RECEIVER_FAILED "+error);}
  finally{synchronized(this){clients.remove(endpoint);try{endpoint.close();}catch(IOException ignored){}if(!closed)wake.post(new Runnable(){public void run(){}});}}
 }
 synchronized boolean hasWork(){return !closed&&!clients.isEmpty();}
 public synchronized void close(){if(closed)return;closed=true;NativeIpc.shutdown(control.getFd());for(ParcelFileDescriptor client:clients)NativeIpc.shutdown(client.getFd());}
}
