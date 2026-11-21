// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.syncdispatchfixture;
import android.accounts.Account;
import android.app.Service;
import android.content.*;
import android.os.*;
public final class SyncService extends Service {
 public IBinder onBind(Intent intent){
  final Binder binder=new Binder(){
   protected boolean onTransact(int code,Parcel input,Parcel output,int flags)throws RemoteException{
    if(code!=1&&code!=2)return super.onTransact(code,input,output,flags);
    input.enforceInterface("android.content.ISyncAdapter");final IBinder callback=input.readStrongBinder();
    if(code==2){System.out.println("SYNC_DISPATCH_APK_CANCELED");return true;}
    String authority=input.readString();Account account=input.readInt()==0?null:Account.CREATOR.createFromParcel(input);
    final Bundle extras=input.readInt()==0?null:Bundle.CREATOR.createFromParcel(input);
    if(!authority.equals("org.shiraz.syncdispatchfixture.data")||account==null||!account.type.equals("org.shiraz.syncdispatchfixture.account"))throw new SecurityException("Fixture identity mismatch");
    new Thread(new Runnable(){public void run(){
     Parcel request=Parcel.obtain(),reply=Parcel.obtain();
     try{SyncResult result=new SyncResult();result.stats.numUpdates=1;
      if(extras.getBoolean("retry",false))result.stats.numIoExceptions=1;
      System.out.println("SYNC_DISPATCH_APK_EXECUTED");
      request.writeInterfaceToken("android.content.ISyncContext");request.writeInt(1);result.writeToParcel(request,0);
      if(!callback.transact(2,request,reply,0))throw new AssertionError("Sync completion rejected");reply.readException();
     }catch(RemoteException failure){throw new RuntimeException(failure);}finally{request.recycle();reply.recycle();}
    }}).start();return true;
   }
  };
  // The runtime requires a local ISyncAdapter interface. Reflection uses the platform
  // implementation while keeping this fixture compilable against the public SDK.
  try{
   Class<?> stub=Class.forName("android.content.ISyncAdapter$Stub");
   final Object adapter=stub.getMethod("asInterface",IBinder.class).invoke(null,binder);
   binder.attachInterface((IInterface)adapter,"android.content.ISyncAdapter");
  }catch(Exception failure){throw new RuntimeException(failure);}
  return binder;
 }
 public boolean onUnbind(Intent intent){System.out.println("SYNC_DISPATCH_APK_UNBOUND");return false;}
}
