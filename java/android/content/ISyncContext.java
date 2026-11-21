// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.os.*;

/** Synchronous Android sync completion wire contract over Shiraz Binder. */
public interface ISyncContext extends IInterface {
    void sendHeartbeat() throws RemoteException;
    void onFinished(SyncResult result) throws RemoteException;
    abstract class Stub extends Binder implements ISyncContext {
        private static final String DESCRIPTOR="android.content.ISyncContext";
        public Stub(){attachInterface(this,DESCRIPTOR);}
        public IBinder asBinder(){return this;}
        public static ISyncContext asInterface(IBinder binder){
            if(binder==null)return null;
            IInterface local=binder.queryLocalInterface(DESCRIPTOR);
            return local instanceof ISyncContext?(ISyncContext)local:new Proxy(binder);
        }
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(code!=1&&code!=2)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(DESCRIPTOR);
            SyncResult result=code==2?data.readTypedObject(SyncResult.CREATOR):null;
            data.enforceNoDataAvail();
            if(code==1)sendHeartbeat();else onFinished(result);
            if(reply!=null)reply.writeNoException();
            return true;
        }
        private static final class Proxy implements ISyncContext {
            private final IBinder remote;
            Proxy(IBinder remote){this.remote=remote;}
            public IBinder asBinder(){return remote;}
            private void invoke(int code,SyncResult result)throws RemoteException{
                Parcel data=Parcel.obtain(),reply=Parcel.obtain();
                try{
                    data.writeInterfaceToken(DESCRIPTOR);
                    if(code==2)data.writeTypedObject(result,0);
                    if(!remote.transact(code,data,reply,0))throw new RemoteException("Sync context transaction unavailable");
                    reply.readException();
                }finally{reply.recycle();data.recycle();}
            }
            public void sendHeartbeat()throws RemoteException{invoke(1,null);}
            public void onFinished(SyncResult result)throws RemoteException{invoke(2,result);}
        }
    }
}
