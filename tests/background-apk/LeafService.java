// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.backgroundfixture;
import android.app.Service;
import android.content.Intent;
import android.os.*;
public final class LeafService extends Service {
 private int calls;
 public IBinder onBind(Intent intent){return new Binder(){
  protected boolean onTransact(int code,Parcel input,Parcel output,int flags)throws RemoteException{
   if(code!=1)return super.onTransact(code,input,output,flags);
   input.enforceInterface("shiraz.fixture.leaf");input.enforceNoDataAvail();
   synchronized(LeafService.this){output.writeNoException();output.writeInt(++calls);output.writeInt(android.os.Process.myPid());output.writeInt(Binder.getCallingUid());output.writeInt(android.os.Process.myUid());}return true;
  }
 };}
 public void onDestroy(){System.out.println("LEAF_SERVICE_DESTROYED");}
}
