// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.os.*;
/** Opaque identity of a local host window. No remote service or parcel transport. */
final class WindowToken implements IBinder {
 public String getInterfaceDescriptor(){return null;}
 public boolean pingBinder(){return true;}
 public boolean isBinderAlive(){return true;}
 public IInterface queryLocalInterface(String descriptor){return null;}
 public boolean transact(int code,Parcel data,Parcel reply,int flags){throw new UnsupportedOperationException("Window tokens have no Binder transport");}
 public void dump(java.io.FileDescriptor fd,String[] args){throw new UnsupportedOperationException("Window token dump is not implemented");}
 public void dumpAsync(java.io.FileDescriptor fd,String[] args){dump(fd,args);}
 // Local objects cannot die independently of the process retaining them.
 public void linkToDeath(DeathRecipient recipient,int flags){}
 public boolean unlinkToDeath(DeathRecipient recipient,int flags){return true;}
}
