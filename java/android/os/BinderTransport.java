// SPDX-License-Identifier: GPL-3.0-only
package android.os;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import shiraz.runtime.NativeIpc;

/** Private bidirectional Binder RPC over an owned Unix socketpair endpoint.
 * This is not the kernel Binder protocol. Capability tables live until channel
 * death (bounded to 4096 objects); a reader never executes application callbacks.
 */
public final class BinderTransport implements Closeable {
 private static final int REQUEST=1,REPLY=2,MAX_PENDING=128,MAX_OBJECTS=4096;
 private final ParcelFileDescriptor socket;
 private final Object writer=new Object();
 private final Map<Long,IBinder> local=new HashMap<Long,IBinder>();
 private final IdentityHashMap<IBinder,Long> exported=new IdentityHashMap<IBinder,Long>();
 private final Map<Long,Proxy> proxies=new HashMap<Long,Proxy>();
 private final Map<Long,Pending> pending=new HashMap<Long,Pending>();
 private long nextObject=2,nextRequest=1;
 private boolean closed;
 private long incomingBytes;
 private final ThreadPoolExecutor calls;
 private final ThreadPoolExecutor oneWay;
 private final Thread reader;
 private static ThreadFactory threads(final String name){return new ThreadFactory(){public Thread newThread(Runnable run){Thread t=new Thread(run,name);t.setDaemon(true);return t;}};}
 public BinderTransport(int ownedSocket,IBinder root)throws IOException{
  socket=ParcelFileDescriptor.adoptFd(ownedSocket);
  if(root!=null){local.put(1L,root);exported.put(root,1L);}
  calls=new ThreadPoolExecutor(0,16,30,TimeUnit.SECONDS,new SynchronousQueue<Runnable>(),threads("shiraz-binder-call"));
  oneWay=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(128),threads("shiraz-binder-oneway"));
  reader=threads("shiraz-binder-reader").newThread(new Runnable(){public void run(){readLoop();}});reader.start();
 }
 public synchronized IBinder remoteRoot()throws RemoteException{return proxy(1);}
 public synchronized boolean isAlive(){return !closed;}
 public synchronized void awaitClosed()throws InterruptedException{while(!closed)wait();}
 private void live()throws DeadObjectException{if(closed)throw new DeadObjectException("Binder channel is closed");}
 private synchronized Proxy proxy(long id)throws RemoteException{
  live();if(id<=0)throw new RemoteException("Invalid Binder object ID");
  Proxy value=proxies.get(id);if(value==null){if(proxies.size()>=MAX_OBJECTS)throw new RemoteException("Binder proxy limit");value=new Proxy(id);proxies.put(id,value);}return value;
 }
 private synchronized long encodeBinder(IBinder binder)throws RemoteException{
  live();if(binder instanceof BinderTransport.Proxy){Proxy p=(Proxy)binder;if(p.connection()==this)return -p.id;}
  Long id=exported.get(binder);if(id!=null)return id;
  if(local.size()>=MAX_OBJECTS||nextObject==Long.MAX_VALUE)throw new RemoteException("Binder export limit");
  long result=nextObject++;local.put(result,binder);exported.put(binder,result);return result;
 }
 private synchronized IBinder decodeBinder(long id)throws RemoteException{
  if(id>0)return proxy(id);
  IBinder value=id==Long.MIN_VALUE?null:local.get(-id);
  if(value==null)throw new RemoteException("Unknown returned Binder capability");return value;
 }
 private static final class Pending {Parcel reply;boolean handled;RemoteException failure;boolean done;}
 private static final class Message implements Runnable {
  final BinderTransport owner;final long request,target;final int kind,code,flags,status,pid,uid;final Parcel data;int charge;
  Message(BinderTransport owner,long request,long target,int kind,int code,int flags,int status,int pid,int uid,Parcel data){this.pid=pid;this.uid=uid;this.owner=owner;this.request=request;this.target=target;this.kind=kind;this.code=code;this.flags=flags;this.status=status;this.data=data;}
  public void run(){owner.dispatch(this);}
  void release(){data.recycle();if(charge!=0)synchronized(owner){owner.incomingBytes-=charge;charge=0;}}

 }
 private void send(int kind,long request,long target,int code,int flags,int status,Parcel data)throws IOException,RemoteException{
  byte[] bytes=data==null?new byte[0]:data.transportBytes();
  Map<Integer,Object> objects=data==null?Collections.<Integer,Object>emptyMap():data.transportObjects();
  long total=40L+bytes.length+16L*objects.size();if(total>16*1024*1024)throw new RemoteException("Binder transaction byte limit");
  ByteArrayOutputStream buffer=new ByteArrayOutputStream((int)total);DataOutputStream out=new DataOutputStream(buffer);
  out.writeInt(kind);out.writeLong(request);out.writeLong(target);out.writeInt(code);out.writeInt(flags);out.writeInt(status);out.writeInt(bytes.length);out.writeInt(objects.size());out.write(bytes);
  ArrayList<Integer> descriptors=new ArrayList<Integer>();
  for(Map.Entry<Integer,Object> entry:objects.entrySet()){
   out.writeInt(entry.getKey());Object object=entry.getValue();
   if(object instanceof IBinder){out.writeInt(1);out.writeLong(encodeBinder((IBinder)object));}
   else{if(descriptors.size()>=128)throw new RemoteException("Binder descriptor limit");out.writeInt(2);out.writeLong(descriptors.size());descriptors.add(((ParcelFileDescriptor)object).getFd());}
  }
  int[] fds=new int[descriptors.size()];for(int i=0;i<fds.length;i++)fds[i]=descriptors.get(i);
  synchronized(writer){ParcelFileDescriptor output;synchronized(this){live();output=socket.dup();}
   try{NativeIpc.send(output.getFd(),buffer.toByteArray(),fds);}finally{output.close();}}
 }
 private Message decode(NativeIpc.Packet packet)throws IOException,RemoteException{
  DataInputStream in=new DataInputStream(new ByteArrayInputStream(packet.bytes));
  int kind=in.readInt();long request=in.readLong(),target=in.readLong();int code=in.readInt(),flags=in.readInt(),status=in.readInt(),length=in.readInt(),count=in.readInt();
  if((kind!=REQUEST&&kind!=REPLY)||request<=0||count<0||count>1024||length<0||length>16*1024*1024||40L+length+16L*count!=packet.bytes.length||(flags&~IBinder.FLAG_ONEWAY)!=0)
   throw new IOException("Malformed Binder packet");
  if(kind==REQUEST&&(target<=0||status!=0)||kind==REPLY&&(target!=0||flags!=0||code!=0||status< -1||status>1))throw new IOException("Invalid Binder packet metadata");
  byte[] bytes=new byte[length];in.readFully(bytes);Parcel data=Parcel.obtain();boolean success=false;
  try{
   data.unmarshall(bytes,0,bytes.length);int fdCount=0;
   for(int i=0;i<count;i++){
    int offset=in.readInt(),type=in.readInt();long value=in.readLong();
    if(type==1)data.transportAttach(offset,decodeBinder(value));
    else if(type==2){if(value!=fdCount||fdCount>=packet.descriptorCount())throw new IOException("Invalid descriptor index");
     ParcelFileDescriptor fd=packet.takeDescriptor(fdCount++);boolean attached=false;
     try{data.transportAttach(offset,fd);attached=true;}finally{if(!attached)fd.close();}
    }else throw new IOException("Unknown Binder attachment");
   }
   if(fdCount!=packet.descriptorCount())throw new IOException("Unused Binder descriptors");
   success=true;return new Message(this,request,target,kind,code,flags,status,packet.senderPid,packet.senderUid,data);
  }finally{if(!success)data.recycle();}
 }
 private void readLoop(){
  try(ParcelFileDescriptor input=socket.dup()){
   while(isAlive())try(NativeIpc.Packet packet=NativeIpc.receive(input.getFd())){
    Message message=decode(packet);
    if(message.kind==REPLY){Pending call;RemoteException failure=null;
     if(message.status== -1){try{failure=new RemoteException(message.data.readString());message.data.enforceNoDataAvail();}catch(RuntimeException error){message.data.recycle();throw error;}}
     synchronized(this){call=pending.remove(message.request);}
     if(call==null){message.data.recycle();throw new IOException("Unexpected Binder reply");}
     synchronized(call){call.handled=message.status==1;
      if(message.status== -1){call.failure=failure;message.data.recycle();}else call.reply=message.data;
      call.done=true;call.notifyAll();}
    }else{
     synchronized(this){int charge=packet.bytes.length;if(closed||incomingBytes+charge>32*1024*1024){message.data.recycle();throw new IOException("Binder incoming byte budget");}incomingBytes+=charge;message.charge=charge;}
     try{((message.flags&IBinder.FLAG_ONEWAY)!=0?oneWay:calls).execute(message);}
     catch(RejectedExecutionException error){message.release();throw new IOException("Binder dispatch capacity",error);}
    }
   }
  }catch(IOException error){if(isAlive())System.err.println("SHIRAZ_BINDER_CHANNEL_CLOSED pid="+Process.myPid()+" reason="+error.getMessage());}
  catch(RemoteException|RuntimeException error){System.err.println("SHIRAZ_BINDER_DECODE_FAILED");error.printStackTrace();}
  finally{close();}
 }
 private void dispatch(Message message){
  Parcel reply=Parcel.obtain();int status=0;
  try{
   IBinder target;synchronized(this){target=local.get(message.target);}
   if(target==null)throw new RemoteException("Unknown Binder target");
   Long previous=Binder.enterCaller(message.pid,message.uid);
   try{status=target.transact(message.code,message.data,reply,message.flags)?1:0;}
   catch(RuntimeException error){System.err.println("SHIRAZ_BINDER_CALL_EXCEPTION code="+message.code+" type="+error.getClass().getName());error.printStackTrace();reply.setDataSize(0);reply.writeException(error);status=1;}
   finally{Binder.leaveCaller(previous);}
  }catch(RemoteException error){reply.setDataSize(0);reply.writeString(error.getMessage());status= -1;}
  catch(Throwable error){System.err.println("SHIRAZ_BINDER_DISPATCH_FAILED code="+message.code+" target="+message.target);error.printStackTrace();close();}
  finally{
   try{if((message.flags&IBinder.FLAG_ONEWAY)==0&&isAlive())send(REPLY,message.request,0,0,0,status,reply);}
   catch(IOException|RemoteException|RuntimeException error){close();}
   finally{message.release();reply.recycle();}
  }
 }
 private boolean transact(long target,int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if((flags&~IBinder.FLAG_ONEWAY)!=0)throw new IllegalArgumentException("Unsupported Binder flags");
  Pending call=new Pending();long request;
  synchronized(this){live();if(pending.size()>=MAX_PENDING||nextRequest==Long.MAX_VALUE)throw new RemoteException("Binder pending transaction limit");request=nextRequest++;if((flags&IBinder.FLAG_ONEWAY)==0)pending.put(request,call);}
  try{send(REQUEST,request,target,code,flags,0,data);}
  catch(IOException|RuntimeException error){close();throw new DeadObjectException("Binder send failed: "+error.getClass().getSimpleName());}
  catch(RemoteException error){synchronized(this){pending.remove(request);}throw error;}
  if((flags&IBinder.FLAG_ONEWAY)!=0)return true;
  boolean interrupted=false;
  synchronized(call){while(!call.done)try{call.wait();}catch(InterruptedException ignored){interrupted=true;}}
  if(interrupted)Thread.currentThread().interrupt();
  if(call.failure!=null)throw call.failure;
  try{if(reply!=null&&call.reply!=null)reply.transportMoveFrom(call.reply);return call.handled;}
  finally{if(call.reply!=null)call.reply.recycle();}
 }
 @Override public void close(){
  List<Pending> waiting;List<Proxy> dead;
  synchronized(this){if(closed)return;closed=true;notifyAll();NativeIpc.shutdown(socket.getFd());try{socket.close();}catch(IOException ignored){}
   waiting=new ArrayList<Pending>(pending.values());pending.clear();dead=new ArrayList<Proxy>(proxies.values());proxies.clear();local.clear();exported.clear();}
  for(Pending call:waiting)synchronized(call){call.failure=new DeadObjectException("Binder peer disconnected");call.done=true;call.notifyAll();}
  for(Runnable run:oneWay.shutdownNow())if(run instanceof Message)((Message)run).release();calls.shutdownNow();
  for(Proxy proxy:dead)proxy.died();
 }
 private final class Proxy implements IBinder {
  final long id;final ArrayList<DeathRecipient> deaths=new ArrayList<DeathRecipient>();
  Proxy(long id){this.id=id;}
  BinderTransport connection(){return BinderTransport.this;}
  public IInterface queryLocalInterface(String descriptor){return null;}
  public boolean isBinderAlive(){return isAlive();}
  public boolean pingBinder(){try{return transact(PING_TRANSACTION,null,null,0);}catch(RemoteException error){return false;}}
  public String getInterfaceDescriptor()throws RemoteException{Parcel reply=Parcel.obtain();try{transact(INTERFACE_TRANSACTION,null,reply,0);return reply.readString();}finally{reply.recycle();}}
  public boolean transact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{return BinderTransport.this.transact(id,code,data,reply,flags);}
  public void dump(java.io.FileDescriptor fd,String[] args)throws RemoteException{throw new UnsupportedOperationException("Remote Binder dump is not implemented");}
  public void dumpAsync(java.io.FileDescriptor fd,String[] args)throws RemoteException{dump(fd,args);}
  public void linkToDeath(DeathRecipient recipient,int flags)throws RemoteException{
   if(recipient==null)throw new NullPointerException("recipient");if(flags!=0)throw new IllegalArgumentException("Death flags");
   synchronized(BinderTransport.this){live();synchronized(deaths){if(deaths.size()>=128)throw new RemoteException("Death recipient limit");deaths.add(recipient);}}
  }
  public boolean unlinkToDeath(DeathRecipient recipient,int flags){synchronized(deaths){return deaths.remove(recipient);}}
  void died(){List<DeathRecipient> copy;synchronized(deaths){copy=new ArrayList<DeathRecipient>(deaths);deaths.clear();}
   for(DeathRecipient recipient:copy)try{recipient.binderDied();}catch(RuntimeException error){System.err.println("W/BinderTransport: death recipient threw "+error.getClass().getName());}}
 }
}
