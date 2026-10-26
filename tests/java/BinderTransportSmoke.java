// SPDX-License-Identifier: GPL-3.0-only
import android.os.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import shiraz.runtime.NativeIpc;
public final class BinderTransportSmoke {
 public static void main(String[] args)throws Exception{
  if(args.length!=1||!"child".equals(args[0]))throw new IllegalArgumentException("child");
  Binder service=new Binder(){protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
   if(code==2)Runtime.getRuntime().halt(23);
   if(code==3){android.content.Intent intent=data.readTypedObject(android.content.Intent.CREATOR);reply.writeNoException();reply.writeTypedObject(intent,0);return true;}
   data.enforceInterface("spawned.child");check(Binder.getCallingPid()!=android.os.Process.myPid()&&Binder.getCallingUid()==android.os.Process.myUid(),"Kernel-authenticated parent identity");
   long caller=Binder.clearCallingIdentity();check(Binder.getCallingPid()==android.os.Process.myPid(),"Cleared caller identity");Binder.restoreCallingIdentity(caller);
   IBinder callback=data.readStrongBinder();
   ParcelFileDescriptor descriptor=data.readFileDescriptor();Parcel input=Parcel.obtain(),output=Parcel.obtain();
   try{input.writeInt(android.os.Process.myPid());callback.transact(1,input,output,0);output.readException();
    reply.writeNoException();reply.writeInt(android.os.Process.myPid());reply.writeInt(output.readInt());reply.writeLong(descriptor.getStatSize());
   }catch(IOException error){throw new RemoteException(error.toString());}finally{input.recycle();output.recycle();try{descriptor.close();}catch(IOException ignored){}}
   return true;
  }};
  try(BinderTransport transport=new BinderTransport(3,service)){transport.awaitClosed();}
  System.out.println("BINDER_CHILD_EXIT_OK");
 }
 public static void crossProcess(String commandPath)throws Exception{
  String[] command;
  try(DataInputStream input=new DataInputStream(new FileInputStream(commandPath))){int count=input.readInt();command=new String[count];
   for(int i=0;i<count;i++){byte[] bytes=new byte[input.readInt()];input.readFully(bytes);command[i]=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}}
  int[] sockets=NativeIpc.pair();int pid;
  try{pid=NativeIpc.spawn(command,sockets[1]);}catch(Exception error){shiraz.runtime.NativeFd.closeRawQuietly(sockets[0]);throw error;}finally{shiraz.runtime.NativeFd.closeRawQuietly(sockets[1]);}
  check(pid!=android.os.Process.myPid(),"Spawned separate process");
  Binder callback=new Binder(){protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){
   check(data.readInt()==Binder.getCallingPid()&&Binder.getCallingPid()!=android.os.Process.myPid(),"Kernel-authenticated child callback origin");reply.writeNoException();reply.writeInt(android.os.Process.myPid());return true;
  }};
  File path=File.createTempFile("binder-process-", ".bin",new File(System.getenv("TMPDIR")));
  try(BinderTransport transport=new BinderTransport(sockets[0],null)){
   try(FileOutputStream stream=new FileOutputStream(path)){stream.write(new byte[]{4,3,2,1,0});}
   Parcel data=Parcel.obtain(),reply=Parcel.obtain();
   try{data.writeInterfaceToken("spawned.child");data.writeStrongBinder(callback);
    try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(path,ParcelFileDescriptor.MODE_READ_ONLY)){data.writeFileDescriptor(fd.getFileDescriptor());}
    check(transport.remoteRoot().transact(1,data,reply,0),"Cross-process transaction");reply.readException();
    check(reply.readInt()==pid&&reply.readInt()==android.os.Process.myPid()&&reply.readLong()==5,"Real child, callback and transferred descriptor");
   }finally{data.recycle();reply.recycle();}
   data=Parcel.obtain();reply=Parcel.obtain();try{
    Bundle nested=new Bundle();nested.putInt("count",17);nested.putStringArray("names",new String[]{"one","دو"});
    Bundle extras=new Bundle();extras.putBundle("nested",nested);extras.putBinder("callback",callback);extras.putBoolean("enabled",true);
    android.content.Intent intent=new android.content.Intent("test.action",android.net.Uri.parse("https://example.org/path"));
    intent.setClassName("test.pkg","test.pkg.Service").addCategory("test.category").setFlags(123).putExtras(extras);
    data.writeTypedObject(intent,0);transport.remoteRoot().transact(3,data,reply,0);reply.readException();
    android.content.Intent result=reply.readTypedObject(android.content.Intent.CREATOR);
    check(result.getFlags()==123&&result.hasCategory("test.category")&&"https://example.org/path".equals(result.getDataString()),"Cross-process Intent routing");
    Bundle received=result.getExtras();check(received.getBinder("callback")==callback&&received.getBundle("nested").getInt("count")==17&&"دو".equals(received.getBundle("nested").getStringArray("names")[1]),"Nested Bundle values and callback capability");
   }finally{data.recycle();reply.recycle();}
  }finally{path.delete();}
  check(NativeIpc.waitFor(pid)==0,"Child clean exit and reap");
  sockets=NativeIpc.pair();
  try{pid=NativeIpc.spawn(command,sockets[1]);}catch(Exception error){shiraz.runtime.NativeFd.closeRawQuietly(sockets[0]);throw error;}finally{shiraz.runtime.NativeFd.closeRawQuietly(sockets[1]);}
  try(BinderTransport endpoint=new BinderTransport(sockets[0],null)){
   IBinder remote=endpoint.remoteRoot();final CountDownLatch death=new CountDownLatch(1);
   remote.linkToDeath(new IBinder.DeathRecipient(){public void binderDied(){death.countDown();}},0);
   try{remote.transact(2,null,null,0);throw new AssertionError("Terminated child returned transaction");}catch(DeadObjectException expected){}
   check(death.await(5,TimeUnit.SECONDS),"OS child death delivered to Binder recipient");
  }
  check(NativeIpc.waitFor(pid)==23,"Terminated child exit and reap");
  System.out.println("BINDER_CROSS_PROCESS_OK");
 }
 static void malformedReply()throws Exception{
  int[] sockets=NativeIpc.pair();
  try(BinderTransport endpoint=new BinderTransport(sockets[0],null)){
   final IBinder remote=endpoint.remoteRoot();final Throwable[] failure={null};
   Thread waiting=new Thread(new Runnable(){public void run(){try{remote.transact(1,null,null,0);failure[0]=new AssertionError("Malformed reply accepted");}catch(DeadObjectException expected){}catch(Throwable error){failure[0]=error;}}});waiting.setDaemon(true);waiting.start();
   long request;
   try(NativeIpc.Packet packet=NativeIpc.receive(sockets[1])){DataInputStream input=new DataInputStream(new ByteArrayInputStream(packet.bytes));input.readInt();request=input.readLong();}
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream output=new DataOutputStream(bytes);
   output.writeInt(2);output.writeLong(request);output.writeLong(0);output.writeInt(0);output.writeInt(0);output.writeInt(-1);output.writeInt(4);output.writeInt(0);
   output.write(new byte[]{127,127,127,127}); // impossible Parcel string length
   NativeIpc.send(sockets[1],bytes.toByteArray(),new int[0]);waiting.join(5000);
   check(!waiting.isAlive()&&failure[0]==null&&!endpoint.isAlive(),"Malformed reply releases pending caller");
  }finally{shiraz.runtime.NativeFd.closeRawQuietly(sockets[1]);}
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void run()throws Exception{
  final CountDownLatch oneWayDone=new CountDownLatch(20),entered=new CountDownLatch(1),release=new CountDownLatch(1);
  final AtomicInteger order=new AtomicInteger();
  final Binder callback=new Binder(){protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){
   data.enforceInterface("callback");int input=data.readInt();data.enforceNoDataAvail();reply.writeNoException();reply.writeInt(input+7);return true;
  }};
  Binder service=new Binder(){protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
   if(code==IBinder.INTERFACE_TRANSACTION||code==IBinder.PING_TRANSACTION)return super.onTransact(code,data,reply,flags);
   data.enforceInterface("child");
   if(code==1){IBinder callback=data.readStrongBinder();Parcel input=Parcel.obtain(),output=Parcel.obtain();
    try{input.writeInterfaceToken("callback");input.writeInt(5);check(callback.queryLocalInterface("callback")==null,"Callback must be a proxy");
     check(callback.transact(1,input,output,0),"Nested callback handled");output.readException();
     reply.writeNoException();reply.writeInt(output.readInt());reply.writeStrongBinder(callback);
    }finally{input.recycle();output.recycle();}return true;
   }
   if(code==2){ParcelFileDescriptor file=data.readFileDescriptor();try{reply.writeNoException();file.writeToParcel(reply,Parcelable.PARCELABLE_WRITE_RETURN_VALUE);}finally{try{file.close();}catch(IOException ignored){}}return true;}
   if(code==3)throw new IllegalArgumentException("test remote validation");
   if(code==4){check((flags&IBinder.FLAG_ONEWAY)!=0,"One-way flag");check(data.readInt()==order.getAndIncrement(),"One-way ordering");oneWayDone.countDown();return true;}
   if(code==5){entered.countDown();try{release.await();}catch(InterruptedException ignored){}return true;}
   return false;
  }};service.attachInterface(null,"child");
  int[] sockets=NativeIpc.pair();BinderTransport left=new BinderTransport(sockets[0],null),right=new BinderTransport(sockets[1],service);
  try{
   final IBinder remote=left.remoteRoot();check(remote.queryLocalInterface("child")==null&&remote.pingBinder()&&"child".equals(remote.getInterfaceDescriptor()),"Remote metadata");
   Parcel data=Parcel.obtain(),reply=Parcel.obtain();
   try{data.writeInterfaceToken("child");data.writeStrongBinder(callback);check(remote.transact(1,data,reply,0),"RPC handled");reply.readException();check(reply.readInt()==12&&reply.readStrongBinder()==callback,"Callback result and returned local identity");}
   finally{data.recycle();reply.recycle();}
   File path=File.createTempFile("binder-rpc-", ".bin",new File(System.getenv("TMPDIR")));
   try{try(FileOutputStream out=new FileOutputStream(path)){out.write(new byte[]{1,2,3,4});}
    data=Parcel.obtain();reply=Parcel.obtain();try{
     data.writeInterfaceToken("child");try(ParcelFileDescriptor file=ParcelFileDescriptor.open(path,ParcelFileDescriptor.MODE_READ_ONLY)){data.writeFileDescriptor(file.getFileDescriptor());}
     remote.transact(2,data,reply,0);reply.readException();ParcelFileDescriptor returned=ParcelFileDescriptor.CREATOR.createFromParcel(reply);
     data.recycle();reply.recycle();try{check(returned.getStatSize()==4,"RPC descriptor survives parcel recycle");}finally{returned.close();}
    }finally{data.recycle();reply.recycle();}
   }finally{path.delete();}
   data=Parcel.obtain();reply=Parcel.obtain();try{data.writeInterfaceToken("child");remote.transact(3,data,reply,0);
    try{reply.readException();throw new AssertionError("Remote exception missing");}catch(IllegalArgumentException expected){check("test remote validation".equals(expected.getMessage()),"Remote error message");}
   }finally{data.recycle();reply.recycle();}
   for(int i=0;i<20;i++){data=Parcel.obtain();try{data.writeInterfaceToken("child");data.writeInt(i);remote.transact(4,data,null,IBinder.FLAG_ONEWAY);}finally{data.recycle();}}
   check(oneWayDone.await(5,TimeUnit.SECONDS),"One-way delivery deadline");
   final CountDownLatch death=new CountDownLatch(1);remote.linkToDeath(new IBinder.DeathRecipient(){public void binderDied(){death.countDown();}},0);
   final Throwable[] failure={null};Thread waiting=new Thread(new Runnable(){public void run(){Parcel input=Parcel.obtain(),output=Parcel.obtain();try{input.writeInterfaceToken("child");remote.transact(5,input,output,0);failure[0]=new AssertionError("Pending call survived peer death");}catch(DeadObjectException expected){}catch(Throwable error){failure[0]=error;}finally{input.recycle();output.recycle();}}});waiting.setDaemon(true);waiting.start();
   check(entered.await(5,TimeUnit.SECONDS),"Blocking transaction entered");right.close();waiting.join(5000);
   check(!waiting.isAlive()&&failure[0]==null&&death.await(5,TimeUnit.SECONDS)&&!remote.isBinderAlive(),"Peer death wakes pending call and death callback");
   try{remote.linkToDeath(new IBinder.DeathRecipient(){public void binderDied(){}},0);throw new AssertionError("Dead proxy accepted death subscription");}catch(DeadObjectException expected){}
  }finally{release.countDown();left.close();right.close();}
  Parcel invalid=Parcel.obtain();try{
   Bundle cyclic=new Bundle();cyclic.putBundle("self",cyclic);
   try{cyclic.writeToParcel(invalid,0);throw new AssertionError("Cyclic Bundle accepted");}catch(IllegalArgumentException expected){}
   invalid.setDataSize(0);invalid.writeInt(65537);invalid.setDataPosition(0);
   try{Bundle.CREATOR.createFromParcel(invalid);throw new AssertionError("Oversized Bundle accepted");}catch(IllegalArgumentException expected){}
   invalid.setDataSize(0);Bundle valid=new Bundle();valid.putString("after","failure");valid.writeToParcel(invalid,0);invalid.setDataPosition(0);
   check("failure".equals(Bundle.CREATOR.createFromParcel(invalid).getString("after")),"Bundle budget reset after malformed input");
  }finally{invalid.recycle();}
  malformedReply();
  System.out.println("BINDER_TRANSPORT_OK");
 }
}
