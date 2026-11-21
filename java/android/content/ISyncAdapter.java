// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.accounts.Account;
import android.os.*;

/** API-27 sync adapter request ABI; discovery and authorization belong to the host. */
public interface ISyncAdapter extends IInterface {
    void startSync(ISyncContext callback,String authority,Account account,Bundle extras)throws RemoteException;
    void cancelSync(ISyncContext callback)throws RemoteException;
    abstract class Stub extends Binder implements ISyncAdapter {
        private static final String DESCRIPTOR="android.content.ISyncAdapter";
        public Stub(){attachInterface(this,DESCRIPTOR);}
        public IBinder asBinder(){return this;}
        public static ISyncAdapter asInterface(IBinder binder){
            if(binder==null)return null;
            IInterface local=binder.queryLocalInterface(DESCRIPTOR);
            return local instanceof ISyncAdapter?(ISyncAdapter)local:new Proxy(binder);
        }
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(code!=1&&code!=2)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(DESCRIPTOR);
            ISyncContext callback=ISyncContext.Stub.asInterface(data.readStrongBinder());
            if(code==1){
                String authority=data.readString();Account account=data.readTypedObject(Account.CREATOR);Bundle extras=data.readTypedObject(Bundle.CREATOR);
                data.enforceNoDataAvail();startSync(callback,authority,account,extras);
            }else{data.enforceNoDataAvail();cancelSync(callback);}
            return true;
        }
        private static final class Proxy implements ISyncAdapter {
            private final IBinder remote;
            Proxy(IBinder remote){this.remote=remote;}
            public IBinder asBinder(){return remote;}
            private void invoke(int code,ISyncContext callback,String authority,Account account,Bundle extras)throws RemoteException{
                Parcel data=Parcel.obtain();
                try{
                    data.writeInterfaceToken(DESCRIPTOR);data.writeStrongBinder(callback==null?null:callback.asBinder());
                    if(code==1){data.writeString(authority);data.writeTypedObject(account,0);data.writeTypedObject(extras,0);}
                    if(!remote.transact(code,data,null,IBinder.FLAG_ONEWAY))throw new RemoteException("Sync adapter transaction unavailable");
                }finally{data.recycle();}
            }
            public void startSync(ISyncContext callback,String authority,Account account,Bundle extras)throws RemoteException{invoke(1,callback,authority,account,extras);}
            public void cancelSync(ISyncContext callback)throws RemoteException{invoke(2,callback,null,null,null);}
        }
    }
}
