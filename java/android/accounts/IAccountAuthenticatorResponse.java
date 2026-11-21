// SPDX-License-Identifier: GPL-3.0-only
package android.accounts;
import android.os.*;

/** API-27 authenticator callback wire contract, implemented over Shiraz Binder. */
public interface IAccountAuthenticatorResponse extends IInterface {
    void onResult(Bundle value) throws RemoteException;
    void onRequestContinued() throws RemoteException;
    void onError(int code,String message) throws RemoteException;

    abstract class Stub extends Binder implements IAccountAuthenticatorResponse {
        private static final String DESCRIPTOR="android.accounts.IAccountAuthenticatorResponse";
        public Stub(){attachInterface(this,DESCRIPTOR);}
        public IBinder asBinder(){return this;}
        public static IAccountAuthenticatorResponse asInterface(IBinder binder){
            if(binder==null)return null;
            IInterface local=binder.queryLocalInterface(DESCRIPTOR);
            return local instanceof IAccountAuthenticatorResponse?(IAccountAuthenticatorResponse)local:new Proxy(binder);
        }
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(code<1||code>3)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(DESCRIPTOR);
            if(code==1){Bundle result=data.readTypedObject(Bundle.CREATOR);data.enforceNoDataAvail();onResult(result);}
            else if(code==2){data.enforceNoDataAvail();onRequestContinued();}
            else {int error=data.readInt();String message=data.readString();data.enforceNoDataAvail();onError(error,message);}
            return true;
        }
        private static final class Proxy implements IAccountAuthenticatorResponse {
            private final IBinder remote;
            Proxy(IBinder remote){this.remote=remote;}
            public IBinder asBinder(){return remote;}
            private void send(int code,Bundle result,int error,String message)throws RemoteException{
                Parcel data=Parcel.obtain();
                try{
                    data.writeInterfaceToken(DESCRIPTOR);
                    if(code==1)data.writeTypedObject(result,0);
                    else if(code==3){data.writeInt(error);data.writeString(message);}
                    if(!remote.transact(code,data,null,IBinder.FLAG_ONEWAY))throw new RemoteException("Authenticator callback transaction unavailable");
                }finally{data.recycle();}
            }
            public void onResult(Bundle result)throws RemoteException{send(1,result,0,null);}
            public void onRequestContinued()throws RemoteException{send(2,null,0,null);}
            public void onError(int error,String message)throws RemoteException{send(3,null,error,message);}
        }
    }
}
