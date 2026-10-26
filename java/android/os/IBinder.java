// SPDX-License-Identifier: GPL-3.0-only
package android.os;
/** Binder contract only; no fabricated transport or remote liveness. */
public interface IBinder {
 int FIRST_CALL_TRANSACTION=1,LAST_CALL_TRANSACTION=0x00ffffff,FLAG_ONEWAY=1;
 int INTERFACE_TRANSACTION=0x5f4e5446,DUMP_TRANSACTION=0x5f444d50,PING_TRANSACTION=0x5f504e47;
 interface DeathRecipient {void binderDied();}
 String getInterfaceDescriptor() throws RemoteException;boolean pingBinder();boolean isBinderAlive();IInterface queryLocalInterface(String descriptor);
 void dump(java.io.FileDescriptor descriptor,String[] args) throws RemoteException;void dumpAsync(java.io.FileDescriptor descriptor,String[] args) throws RemoteException;
 boolean transact(int code,Parcel data,Parcel reply,int flags) throws RemoteException;void linkToDeath(DeathRecipient recipient,int flags) throws RemoteException;boolean unlinkToDeath(DeathRecipient recipient,int flags);
}
