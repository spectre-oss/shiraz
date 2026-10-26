// SPDX-License-Identifier: GPL-3.0-only
import android.os.*;
public final class BinderSmoke {
 interface LocalService extends IInterface {int sum(int first,int second);}
 private static void parcelTransactions() throws Exception {
  final Binder callback=new Binder();
  final java.io.File file=java.io.File.createTempFile("shiraz-binder-", ".bin", new java.io.File(System.getenv("TMPDIR")==null?System.getProperty("java.io.tmpdir"):System.getenv("TMPDIR")));
  try(java.io.FileOutputStream stream=new java.io.FileOutputStream(file)){stream.write(new byte[]{3,7,11});}
  try(ParcelFileDescriptor warmup=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)){check(warmup.getStatSize()==3,"Descriptor warmup");}
  int baseline=new java.io.File("/proc/self/fd").list().length;
  try {
   for(int iteration=0;iteration<40;iteration++){
    Parcel request=Parcel.obtain(),reply=Parcel.obtain();
    ParcelFileDescriptor original=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
    try {
     request.writeInterfaceToken("test.child");request.writeStrongBinder(callback);
     request.writeTypedArray(new ParcelFileDescriptor[]{original,null},0);
     original.close(); // Parcel must already own the duplication.
     check(request.hasFileDescriptors(),"Descriptor attachment tracked");
     try{request.marshall();throw new AssertionError("Capability serialized as raw bytes");}catch(IllegalStateException expected){}
     Binder target=new Binder(){protected boolean onTransact(int code,Parcel data,Parcel output,int flags)throws RemoteException{
      data.enforceInterface("test.child");check(data.readStrongBinder()==callback,"Callback identity retained");
      ParcelFileDescriptor[] descriptors=data.createTypedArray(ParcelFileDescriptor.CREATOR);
      data.enforceNoDataAvail();check(descriptors.length==2&&descriptors[1]==null,"Typed descriptor array");
      try{check(descriptors[0].getStatSize()==3,"Descriptor usable after sender closes");
       output.writeNoException();descriptors[0].writeToParcel(output,Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
       try{descriptors[0].getFd();throw new AssertionError("Returned descriptor not closed");}catch(IllegalStateException expected){}
      }catch(java.io.IOException error){throw new RemoteException(error.toString());}
      return true;
     }};
     check(target.transact(1,request,reply,0),"Parcel transaction handled");reply.readException();
     ParcelFileDescriptor received=ParcelFileDescriptor.CREATOR.createFromParcel(reply);
     request.recycle();reply.recycle();
     try{check(received.getStatSize()==3,"Receiver survives both parcels recycled");}finally{received.close();}
    }finally{original.close();request.recycle();reply.recycle();}
   }
   check(new java.io.File("/proc/self/fd").list().length==baseline,"Parcel transaction descriptor leak baseline="+baseline);
   Parcel p=Parcel.obtain();
   try{
    p.writeStrongBinder(callback);p.setDataPosition(0);p.writeInt(0x42494e44);p.setDataPosition(0);
    try{p.readStrongBinder();throw new AssertionError("Forged object token accepted");}catch(IllegalArgumentException expected){}
    p.setDataSize(0);p.writeInterfaceToken("wrong");p.setDataPosition(0);
    try{p.enforceInterface("expected");throw new AssertionError("Interface mismatch accepted");}catch(SecurityException expected){}
    p.setDataSize(0);p.writeException(new IllegalArgumentException("bad value"));p.setDataPosition(0);
    try{p.readException();throw new AssertionError("Remote error swallowed");}catch(IllegalArgumentException expected){check("bad value".equals(expected.getMessage()),"Exception message");}
    p.unmarshall(new byte[]{1,0,0,0,65,0},0,6);
    try{p.readString();throw new AssertionError("Truncated aligned string accepted");}catch(RuntimeException expected){}
    p.setDataSize(0);p.writeInt(1);p.setDataPosition(0);
    try{p.enforceNoDataAvail();throw new AssertionError("Trailing data accepted");}catch(IllegalArgumentException expected){}
    try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)){
     p.setDataSize(0);p.writeFileDescriptor(fd.getFileDescriptor());check(p.hasFileDescriptors(),"FD attached");
     p.setDataSize(0);check(!p.hasFileDescriptors(),"Truncate releases attachment");
     p.writeFileDescriptor(fd.getFileDescriptor());p.unmarshall(new byte[0],0,0);check(!p.hasFileDescriptors(),"Unmarshall releases attachment");
    }
   }finally{p.recycle();}
   check(new java.io.File("/proc/self/fd").list().length==baseline,"Discarded attachment descriptor leak");
   int[] channel=shiraz.runtime.NativeIpc.pair();
   try{
    try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)){
     shiraz.runtime.NativeIpc.send(channel[0],new byte[]{9,8,7},new int[]{fd.getFd()});
    }
    ParcelFileDescriptor received;
    try(shiraz.runtime.NativeIpc.Packet packet=shiraz.runtime.NativeIpc.receive(channel[1])){
     check(packet.bytes.length==3&&packet.bytes[1]==8&&packet.descriptorCount()==1,"JNI IPC payload");
     received=packet.takeDescriptor(0);
     try{packet.takeDescriptor(0);throw new AssertionError("Packet descriptor transferred twice");}catch(IllegalStateException expected){}
    }
    try{check(received.getStatSize()==3,"JNI IPC received ownership");}finally{received.close();}
    final int peer=channel[1];final Throwable[] problem={null};
    final java.util.concurrent.CountDownLatch entering=new java.util.concurrent.CountDownLatch(1);
    Thread waiting=new Thread(new Runnable(){public void run(){entering.countDown();
     try(shiraz.runtime.NativeIpc.Packet unexpected=shiraz.runtime.NativeIpc.receive(peer)){problem[0]=new AssertionError("Closed channel returned packet");}
     catch(java.io.IOException expected){}catch(Throwable error){problem[0]=error;}
    }},"ipc-shutdown-reader");waiting.setDaemon(true);waiting.start();entering.await();
    shiraz.runtime.NativeIpc.shutdown(channel[0]);waiting.join(2000);
    check(!waiting.isAlive()&&problem[0]==null,"Channel shutdown releases receiver");
   }finally{for(int socket:channel)shiraz.runtime.NativeFd.closeRawQuietly(socket);}
   check(new java.io.File("/proc/self/fd").list().length==baseline,"JNI IPC descriptor leak");
  }finally{file.delete();}
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  check(Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"activity")==null&&ServiceManager.checkService("package")==null&&ServiceManager.listServices().length==0,"Absent system Binder services are discoverable as unavailable");
  final Binder binder=new Binder();check(binder.getInterfaceDescriptor()==null&&binder.queryLocalInterface("absent")==null&&binder.pingBinder()&&binder.isBinderAlive(),"Local liveness without interface");
  LocalService owner=new LocalService(){public IBinder asBinder(){return binder;}public int sum(int first,int second){return first+second;}};binder.attachInterface(owner,"test.local");
  check(binder.queryLocalInterface("test.local")==owner&&binder.queryLocalInterface("other")==null&&owner.asBinder()==binder&&((LocalService)binder.queryLocalInterface("test.local")).sum(4,7)==11,"Typed local interface invocation");
  check(Binder.getCallingPid()==android.os.Process.myPid()&&Binder.getCallingUid()==android.os.Process.myUid(),"Local caller is host process");check(!android.os.Process.isIsolated(),"Host process is not an Android isolated UID");long token=Binder.clearCallingIdentity();Binder.restoreCallingIdentity(token);
  final int[] calls={0};Binder dispatch=new Binder(){protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{calls[0]++;if(code==17)throw new RemoteException("local failure");return code==16&&flags==IBinder.FLAG_ONEWAY;}};
  check(dispatch.transact(16,null,null,IBinder.FLAG_ONEWAY)&&calls[0]==1&&!dispatch.transact(15,null,null,0),"Synchronous local dispatch and result");try{dispatch.transact(17,null,null,0);throw new AssertionError("Exception swallowed");}catch(RemoteException expected){check(expected.getMessage().equals("local failure"),"Exception identity");}
  final int[] deaths={0};IBinder.DeathRecipient recipient=new IBinder.DeathRecipient(){public void binderDied(){deaths[0]++;}};binder.linkToDeath(recipient,0);check(binder.unlinkToDeath(recipient,0)&&deaths[0]==0,"No synthetic local death");
  binder.attachInterface(null,null);check(binder.queryLocalInterface("test.local")==null,"Interface replacement");check(!binder.transact(42,null,null,0),"Unknown transaction rejected");
  parcelTransactions();
  BinderTransportSmoke.run();
  if(args.length==1)BinderTransportSmoke.crossProcess(args[0]);
  System.out.println("LOCAL_BINDER_OK");
 }
}
