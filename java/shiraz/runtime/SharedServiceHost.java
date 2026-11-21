// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.Service;
import android.content.Intent;
import android.content.ComponentName;
import android.os.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
/** One service instance, with broker-issued, separately identified Binder endpoints. */
final class SharedServiceHost implements Closeable {
 private final ParcelFileDescriptor control;
 private final Service service;private final HostServices services;private final String pkg,component;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ArrayList<Client> clients=new ArrayList<Client>();
 private final ArrayList<Admission> pending=new ArrayList<Admission>();
 private final ArrayList<Group> groups=new ArrayList<Group>();
 private final ArrayList<ParcelFileDescriptor> pendingSyncs=new ArrayList<ParcelFileDescriptor>();
 private final ArrayList<SyncDispatchSession> syncs=new ArrayList<SyncDispatchSession>();
 private boolean closed,stickyRequested;
 private static final class Group {final Intent intent;IBinder binder;int references;boolean rebind;Group(Intent intent){this.intent=intent;}}
 private static final class Client {BinderTransport transport;Group group;AddAccountEndpoint accountAdd;boolean closed;}
 SharedServiceHost(int ownedControl,Service service,HostServices services,String pkg,String component){
  control=ParcelFileDescriptor.adoptFd(ownedControl);this.service=service;this.services=services;this.pkg=pkg;this.component=component;
  Thread reader=new Thread(new Runnable(){public void run(){receive();}},"shiraz-shared-service-control");reader.setDaemon(true);reader.start();
  System.out.println("SHIRAZ_SHARED_SERVICE_READY");System.out.flush();
 }
 private void receive(){
  try{
   while(true)try(NativeIpc.Packet packet=NativeIpc.receive(control.getFd())){
    if(packet.bytes.length>=4&&ByteBuffer.wrap(packet.bytes).getInt()==4){
     if(packet.descriptorCount()!=1)throw new IOException("Account-add admission descriptor count");
     AddAccountAdmission add=AddAccountAdmission.decode(packet.bytes);
     ParcelFileDescriptor endpoint=packet.takeDescriptor(0);
     synchronized(this){
      if(closed||clients.size()+pending.size()>=64){endpoint.close();throw new IOException("Shared service client limit");}
      Admission admission=new Admission(endpoint,add.uid,false,0,add);pending.add(admission);
      if(!main.post(admission)){pending.remove(admission);endpoint.close();throw new IOException("Shared service main queue stopped");}
     }
     continue;
    }
    if(packet.bytes.length>=4&&ByteBuffer.wrap(packet.bytes).getInt()==3&&packet.descriptorCount()==1){
     final SyncDispatchRequest request=SyncDispatchRequest.decode(packet.bytes,pkg,component);
     final ParcelFileDescriptor endpoint=packet.takeDescriptor(0);
     synchronized(this){
      if(closed||pendingSyncs.size()+syncs.size()>=32){endpoint.close();throw new IOException("Sync admission limit");}
      pendingSyncs.add(endpoint);
      if(!main.post(new Runnable(){public void run(){startSync(request,endpoint);}})){pendingSyncs.remove(endpoint);endpoint.close();throw new IOException("Sync main queue stopped");}
     }
     NativeIpc.send(control.getFd(),"{\"sync_admitted\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8),new int[0]);
     continue;
    }
    if(packet.bytes.length==4&&packet.descriptorCount()==0&&ByteBuffer.wrap(packet.bytes).getInt()==1){
     if(!main.post(new Runnable(){public void run(){try{
      synchronized(SharedServiceHost.this){if(closed||stickyRequested)throw new IllegalStateException("Shared service restart unavailable");stickyRequested=true;RuntimeBroker.client().serviceRestartAllowed();services.restartStickyWorker(new ComponentName(pkg,component));}
      NativeIpc.send(control.getFd(),"{\"restart_queued\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8),new int[0]);
     }catch(IOException|RuntimeException error){try{NativeIpc.send(control.getFd(),"{\"error\":\"Sticky restart rejected\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),new int[0]);}catch(IOException ignored){}}}}))throw new IOException("Shared service main queue stopped");
     continue;
    }
    if(packet.bytes.length>=8&&packet.descriptorCount()==0&&ByteBuffer.wrap(packet.bytes).getInt()==2){
     final ServiceRedelivery redelivery=ServiceRedelivery.decode(packet.bytes,pkg,component);
     if(!main.post(new Runnable(){public void run(){try{
      synchronized(SharedServiceHost.this){if(closed||stickyRequested)throw new IllegalStateException("Shared service restart unavailable");stickyRequested=true;RuntimeBroker.client().serviceRestartAllowed();services.restartRedeliveryWorker(new ComponentName(pkg,component),redelivery);}
      NativeIpc.send(control.getFd(),"{\"restart_queued\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8),new int[0]);
     }catch(IOException|RuntimeException error){try{NativeIpc.send(control.getFd(),"{\"error\":\"Redelivery restart rejected\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),new int[0]);}catch(IOException ignored){}}}}))throw new IOException("Shared service main queue stopped");
     continue;
    }
    if((packet.bytes.length!=4&&packet.bytes.length!=8&&packet.bytes.length!=16)||packet.descriptorCount()!=1)throw new IOException("Shared service admission frame");
    ByteBuffer metadata=ByteBuffer.wrap(packet.bytes);int uid=metadata.getInt();boolean authenticator=packet.bytes.length==8;
    if(authenticator&&metadata.getInt()!=1)throw new IOException("Shared service admission kind");
    final long startTicket=packet.bytes.length==16?readStartTicket(metadata):0;
    if(uid<10000||uid>19999)throw new IOException("Shared service caller UID");
    ParcelFileDescriptor endpoint=packet.takeDescriptor(0);
    synchronized(this){
     if(closed||clients.size()+pending.size()>=64){endpoint.close();throw new IOException("Shared service client limit");}
     Admission admission=new Admission(endpoint,uid,authenticator,startTicket);pending.add(admission);
     if(!main.post(admission)){pending.remove(admission);endpoint.close();throw new IOException("Shared service main queue stopped");}
    }
   }
  }catch(IOException|RuntimeException failure){synchronized(this){if(!closed)System.err.println("SHIRAZ_SHARED_SERVICE_CONTROL_CLOSED "+failure.getMessage());}}
  finally{Looper.shirazRequestMainExit();}
 }
 private static long readStartTicket(ByteBuffer metadata)throws IOException{
  if(metadata.getInt()!=2)throw new IOException("Shared service start admission kind");
  long ticket=metadata.getLong();if(ticket<=0)throw new IOException("Shared service start ticket");return ticket;
 }
 private synchronized void startSync(SyncDispatchRequest request,ParcelFileDescriptor endpoint){
  if(!pendingSyncs.remove(endpoint))return;
  final Client client=new Client();final SyncDispatchSession[] holder={null};
  try{
   if(closed||syncs.size()>=32)throw new IllegalStateException("Sync service unavailable");
   clients.add(client);
   IBinder binder=bind(client,new Intent("android.content.SyncAdapter").setClassName(pkg,component));
   holder[0]=new SyncDispatchSession(endpoint,binder,request,main,new Runnable(){public void run(){synchronized(SharedServiceHost.this){detach(client);syncs.remove(holder[0]);}}});
   syncs.add(holder[0]);holder[0].start();
  }catch(RemoteException|RuntimeException failure){
   detach(client);
   try{NativeIpc.send(endpoint.getFd(),"{\"ok\":false,\"delivery_uncertain\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8),new int[0]);endpoint.close();}catch(IOException ignored){}
  }
 }
 private final class Admission implements Runnable {
  final ParcelFileDescriptor endpoint;final int uid;final boolean authenticator;final long startTicket;final AddAccountAdmission add;
  Admission(ParcelFileDescriptor endpoint,int uid,boolean authenticator,long startTicket){this(endpoint,uid,authenticator,startTicket,null);}
  Admission(ParcelFileDescriptor endpoint,int uid,boolean authenticator,long startTicket,AddAccountAdmission add){this.endpoint=endpoint;this.uid=uid;this.authenticator=authenticator;this.startTicket=startTicket;this.add=add;}
  public void run(){synchronized(SharedServiceHost.this){
   if(!pending.remove(this))return;
   try{if(closed){endpoint.close();return;}attach(endpoint,uid,authenticator,startTicket,add);NativeIpc.send(control.getFd(),("{\"uid\":"+uid+(authenticator?",\"authenticator\":true":"")+(add!=null?",\"account_add\":true":"")+(startTicket!=0?",\"start_ticket\":"+startTicket:"")+"}").getBytes(java.nio.charset.StandardCharsets.UTF_8),new int[0]);}
   catch(IOException|RuntimeException error){try{endpoint.close();NativeIpc.send(control.getFd(),"{\"error\":\"Service admission failed\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),new int[0]);}catch(IOException ignored){}System.err.println("SHIRAZ_SHARED_SERVICE_ADMISSION_FAILED");}
  }}
 }
 private void attach(ParcelFileDescriptor endpoint,int uid,boolean authenticator,final long startTicket,AddAccountAdmission add)throws IOException{
  final boolean[] startUsed={false};
  final Client client=new Client();
  Binder root=new Binder(){protected boolean onTransact(int code,Parcel input,Parcel output,int flags)throws RemoteException{
   if(code<1||code>4)return super.onTransact(code,input,output,flags);
   input.enforceInterface("shiraz.service.control");final Intent intent=input.readTypedObject(Intent.CREATOR);input.enforceNoDataAvail();
   if(intent==null||intent.getComponent()==null||!pkg.equals(intent.getComponent().getPackageName())||!component.equals(intent.getComponent().getClassName())||(intent.getPackage()!=null&&!pkg.equals(intent.getPackage())))throw new SecurityException("Service command component mismatch");
   final int operation=code;final boolean[] stopped={false};
   FutureTask<IBinder> task=new FutureTask<IBinder>(new Callable<IBinder>(){public IBinder call()throws RemoteException{
    if((operation==2||operation==3)!=(startTicket!=0))throw new SecurityException("Service command admission mismatch");
    if(startTicket!=0){if(startUsed[0])throw new SecurityException("Service start ticket already used");startUsed[0]=true;}
    if(operation==1)return bind(client,intent);
    synchronized(SharedServiceHost.this){if(closed||client.closed||!client.transport.isAlive())throw new DeadObjectException("Service connection closed");}
    if(operation==4)stopped[0]=services.stop(intent);else if(operation==3)services.startForeground(intent);else services.start(intent);
    if(startTicket!=0)try{RuntimeBroker.client().serviceStartAccepted(startTicket,services.workerStartId(new ComponentName(pkg,component)));}catch(IOException failure){throw new IllegalStateException("Service start acceptance failed",failure);}
    return null;
   }});
   if(!main.post(task))throw new DeadObjectException("Service main queue stopped");
   try{IBinder binder=task.get();output.writeNoException();if(operation==1)output.writeStrongBinder(binder);else output.writeInt(operation==4&&!stopped[0]?0:1);return true;}
   catch(InterruptedException error){Thread.currentThread().interrupt();throw new DeadObjectException("Service bind interrupted");}
   catch(ExecutionException error){Throwable cause=error.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new RemoteException("Service binding failed");}
  }};
  try{
   if(authenticator||add!=null){
    Intent intent=new Intent(android.accounts.AccountManager.ACTION_AUTHENTICATOR_INTENT).setClassName(pkg,component);
    IBinder target=bind(client,intent);
    if(add!=null){client.accountAdd=new AddAccountEndpoint(target,add.type,add.caller,pkg,uid,service.getPackageManager());root=client.accountAdd;}
    else root=new AuthenticatorEndpoint(target);
   }
   client.transport=new BinderTransport(endpoint.detachFd(),root,uid);clients.add(client);
  }catch(RemoteException failure){detach(client);throw new IOException("Authenticator admission failed");}
  catch(IOException|RuntimeException failure){detach(client);throw failure;}
  Thread death=new Thread(new Runnable(){public void run(){try{client.transport.awaitClosed();}catch(InterruptedException ignored){}main.post(new Runnable(){public void run(){detach(client);}});}},"shiraz-shared-service-client");death.setDaemon(true);death.start();
 }
 private static boolean same(Intent a,Intent b){
  return Objects.equals(a.getAction(),b.getAction())&&Objects.equals(a.getDataString(),b.getDataString())&&Objects.equals(a.getType(),b.getType())&&Objects.equals(a.getPackage(),b.getPackage())&&Objects.equals(a.getComponent(),b.getComponent())&&Objects.equals(a.getCategories(),b.getCategories());
 }
 private synchronized IBinder bind(Client client,Intent intent)throws RemoteException{
  if(closed||client.closed||client.transport!=null&&!client.transport.isAlive())throw new DeadObjectException("Service connection closed");
  if(client.group!=null){if(!same(client.group.intent,intent))throw new UnsupportedOperationException("One binding filter per endpoint");return client.group.binder;}
  Group group=null;for(Group candidate:groups)if(same(candidate.intent,intent)){group=candidate;break;}
  if(group==null){if(groups.size()>=64)throw new IllegalStateException("Service intent group limit");group=new Group(intent.shirazBroadcastCopy());group.binder=service.onBind(intent);groups.add(group);}
  else if(group.references==0&&group.rebind){service.onRebind(intent);group.rebind=false;}
  group.references++;client.group=group;return group.binder;
 }
 private synchronized void detach(Client client){
  if(client.closed)return;client.closed=true;clients.remove(client);if(client.accountAdd!=null)client.accountAdd.close();if(client.transport!=null)client.transport.close();
  Group group=client.group;client.group=null;
  if(group!=null&&--group.references==0){group.rebind=service.onUnbind(group.intent);if(!group.rebind)groups.remove(group);}
 }
 public synchronized void close()throws IOException{
  if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Shared service teardown thread");
  if(closed)return;closed=true;NativeIpc.shutdown(control.getFd());control.close();
  for(Admission admission:pending){main.removeCallbacks(admission);admission.endpoint.close();}pending.clear();
  for(ParcelFileDescriptor endpoint:pendingSyncs)endpoint.close();pendingSyncs.clear();
  Throwable first=null;
  for(SyncDispatchSession sync:new ArrayList<SyncDispatchSession>(syncs))try{sync.close();}catch(IOException|RuntimeException failure){if(first==null)first=failure;}
  syncs.clear();
  for(Client client:new ArrayList<Client>(clients))try{detach(client);}catch(RuntimeException|Error failure){if(first==null)first=failure;}
  groups.clear();if(first instanceof Error)throw (Error)first;if(first instanceof IOException)throw (IOException)first;if(first!=null)throw (RuntimeException)first;
 }
}
