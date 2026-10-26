// SPDX-License-Identifier: GPL-3.0-only
package android.os;
/** Local Binder object and direct interface dispatch. No kernel or remote transport. */
public class Binder implements IBinder {
 private IInterface owner;private String descriptor;
 public Binder(){}
 public void attachInterface(IInterface owner,String descriptor){this.owner=owner;this.descriptor=descriptor;}
 public String getInterfaceDescriptor(){return descriptor;}
 public IInterface queryLocalInterface(String requested){return descriptor!=null&&descriptor.equals(requested)?owner:null;}
 public boolean pingBinder(){return true;}public boolean isBinderAlive(){return true;}
 private static final ThreadLocal<Long> CALLER=new ThreadLocal<Long>();
 private static long identity(){Long value=CALLER.get();return value==null?((long)Process.myPid()<<32)|(Process.myUid()&0xffffffffL):value;}
 public static int getCallingPid(){return (int)(identity()>>>32);}public static int getCallingUid(){return (int)identity();}
 public static long clearCallingIdentity(){long value=identity();CALLER.remove();return value;}
 public static void restoreCallingIdentity(long token){CALLER.set(token);}
 static Long enterCaller(int pid,int uid){Long previous=CALLER.get();CALLER.set(((long)pid<<32)|(uid&0xffffffffL));return previous;}
 static void leaveCaller(Long previous){if(previous==null)CALLER.remove();else CALLER.set(previous);}
 public static void flushPendingCommands(){}
 public final boolean transact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(data!=null)data.setDataPosition(0);
  try{return onTransact(code,data,reply,flags);}
  finally{if(reply!=null)reply.setDataPosition(0);}
 }
 protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(code==PING_TRANSACTION)return true;
  if(code==INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(descriptor);return true;}
  if(code==DUMP_TRANSACTION)throw new UnsupportedOperationException("Binder descriptor dumping is unavailable");
  return false;
 }
 public void dump(java.io.FileDescriptor fd,String[] args){throw new UnsupportedOperationException("Binder descriptor dumping is unavailable");}
 public void dumpAsync(java.io.FileDescriptor fd,String[] args){dump(fd,args);}
 // A retained local Binder does not die independently of its process.
 public void linkToDeath(DeathRecipient recipient,int flags){}
 public boolean unlinkToDeath(DeathRecipient recipient,int flags){return true;}
}
