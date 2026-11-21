// SPDX-License-Identifier: GPL-3.0-only
package android.accounts;
import android.os.*;

/** API-27 authenticator request wire contract. Authorization belongs to the service. */
public interface IAccountAuthenticator extends IInterface {
    void addAccount(IAccountAuthenticatorResponse response,String accountType,String authTokenType,String[] requiredFeatures,Bundle options) throws RemoteException;
    void confirmCredentials(IAccountAuthenticatorResponse response,Account account,Bundle options) throws RemoteException;
    void getAuthToken(IAccountAuthenticatorResponse response,Account account,String authTokenType,Bundle options) throws RemoteException;
    void getAuthTokenLabel(IAccountAuthenticatorResponse response,String authTokenType) throws RemoteException;
    void updateCredentials(IAccountAuthenticatorResponse response,Account account,String authTokenType,Bundle options) throws RemoteException;
    void editProperties(IAccountAuthenticatorResponse response,String accountType) throws RemoteException;
    void hasFeatures(IAccountAuthenticatorResponse response,Account account,String[] features) throws RemoteException;
    void getAccountRemovalAllowed(IAccountAuthenticatorResponse response,Account account) throws RemoteException;
    void getAccountCredentialsForCloning(IAccountAuthenticatorResponse response,Account account) throws RemoteException;
    void addAccountFromCredentials(IAccountAuthenticatorResponse response,Account account,Bundle accountCredentials) throws RemoteException;
    void startAddAccountSession(IAccountAuthenticatorResponse response,String accountType,String authTokenType,String[] requiredFeatures,Bundle options) throws RemoteException;
    void startUpdateCredentialsSession(IAccountAuthenticatorResponse response,Account account,String authTokenType,Bundle options) throws RemoteException;
    void finishSession(IAccountAuthenticatorResponse response,String accountType,Bundle sessionBundle) throws RemoteException;
    void isCredentialsUpdateSuggested(IAccountAuthenticatorResponse response,Account account,String statusToken) throws RemoteException;

    abstract class Stub extends Binder implements IAccountAuthenticator {
        private static final String DESCRIPTOR="android.accounts.IAccountAuthenticator";
        public Stub(){attachInterface(this,DESCRIPTOR);}
        public IBinder asBinder(){return this;}
        public static IAccountAuthenticator asInterface(IBinder binder){
            if(binder==null)return null;
            IInterface local=binder.queryLocalInterface(DESCRIPTOR);
            return local instanceof IAccountAuthenticator?(IAccountAuthenticator)local:new Proxy(binder);
        }
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(code<1||code>14)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(DESCRIPTOR);
            IAccountAuthenticatorResponse response=IAccountAuthenticatorResponse.Stub.asInterface(data.readStrongBinder());
            switch(code){
                case 1:{
                    String accountType=data.readString();
                    String authTokenType=data.readString();
                    String[] requiredFeatures=data.createStringArray();
                    Bundle options=data.readTypedObject(Bundle.CREATOR);
                    data.enforceNoDataAvail();addAccount(response,accountType,authTokenType,requiredFeatures,options);return true;
                }
                case 2:{
                    Account account=data.readTypedObject(Account.CREATOR);
                    Bundle options=data.readTypedObject(Bundle.CREATOR);
                    data.enforceNoDataAvail();confirmCredentials(response,account,options);return true;
                }
                case 3:{
                    Account account=data.readTypedObject(Account.CREATOR);
                    String authTokenType=data.readString();
                    Bundle options=data.readTypedObject(Bundle.CREATOR);
                    data.enforceNoDataAvail();getAuthToken(response,account,authTokenType,options);return true;
                }
                case 4:{
                    String authTokenType=data.readString();
                    data.enforceNoDataAvail();getAuthTokenLabel(response,authTokenType);return true;
                }
                case 5:{
                    Account account=data.readTypedObject(Account.CREATOR);
                    String authTokenType=data.readString();
                    Bundle options=data.readTypedObject(Bundle.CREATOR);
                    data.enforceNoDataAvail();updateCredentials(response,account,authTokenType,options);return true;
                }
                case 6:{
                    String accountType=data.readString();
                    data.enforceNoDataAvail();editProperties(response,accountType);return true;
                }
                case 7:{
                    Account account=data.readTypedObject(Account.CREATOR);
                    String[] features=data.createStringArray();
                    data.enforceNoDataAvail();hasFeatures(response,account,features);return true;
                }
                case 8:{
                    Account account=data.readTypedObject(Account.CREATOR);
                    data.enforceNoDataAvail();getAccountRemovalAllowed(response,account);return true;
                }
                case 9:{
                    Account account=data.readTypedObject(Account.CREATOR);
                    data.enforceNoDataAvail();getAccountCredentialsForCloning(response,account);return true;
                }
                case 10:{
                    Account account=data.readTypedObject(Account.CREATOR);
                    Bundle accountCredentials=data.readTypedObject(Bundle.CREATOR);
                    data.enforceNoDataAvail();addAccountFromCredentials(response,account,accountCredentials);return true;
                }
                case 11:{
                    String accountType=data.readString();
                    String authTokenType=data.readString();
                    String[] requiredFeatures=data.createStringArray();
                    Bundle options=data.readTypedObject(Bundle.CREATOR);
                    data.enforceNoDataAvail();startAddAccountSession(response,accountType,authTokenType,requiredFeatures,options);return true;
                }
                case 12:{
                    Account account=data.readTypedObject(Account.CREATOR);
                    String authTokenType=data.readString();
                    Bundle options=data.readTypedObject(Bundle.CREATOR);
                    data.enforceNoDataAvail();startUpdateCredentialsSession(response,account,authTokenType,options);return true;
                }
                case 13:{
                    String accountType=data.readString();
                    Bundle sessionBundle=data.readTypedObject(Bundle.CREATOR);
                    data.enforceNoDataAvail();finishSession(response,accountType,sessionBundle);return true;
                }
                case 14:{
                    Account account=data.readTypedObject(Account.CREATOR);
                    String statusToken=data.readString();
                    data.enforceNoDataAvail();isCredentialsUpdateSuggested(response,account,statusToken);return true;
                }
                default:throw new AssertionError("Unreachable authenticator transaction");
            }
        }
        private static final class Proxy implements IAccountAuthenticator {
            private final IBinder remote;
            Proxy(IBinder remote){this.remote=remote;}
            public IBinder asBinder(){return remote;}
            private Parcel request(IAccountAuthenticatorResponse response){
                Parcel data=Parcel.obtain();data.writeInterfaceToken(DESCRIPTOR);
                data.writeStrongBinder(response==null?null:response.asBinder());return data;
            }
            private void send(int code,Parcel data)throws RemoteException{
                if(!remote.transact(code,data,null,IBinder.FLAG_ONEWAY))throw new RemoteException("Authenticator transaction unavailable");
            }
            public void addAccount(IAccountAuthenticatorResponse response,String accountType,String authTokenType,String[] requiredFeatures,Bundle options)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeString(accountType);
                    data.writeString(authTokenType);
                    data.writeStringArray(requiredFeatures);
                    data.writeTypedObject(options,0);
                    send(1,data);
                }finally{data.recycle();}
            }
            public void confirmCredentials(IAccountAuthenticatorResponse response,Account account,Bundle options)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeTypedObject(account,0);
                    data.writeTypedObject(options,0);
                    send(2,data);
                }finally{data.recycle();}
            }
            public void getAuthToken(IAccountAuthenticatorResponse response,Account account,String authTokenType,Bundle options)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeTypedObject(account,0);
                    data.writeString(authTokenType);
                    data.writeTypedObject(options,0);
                    send(3,data);
                }finally{data.recycle();}
            }
            public void getAuthTokenLabel(IAccountAuthenticatorResponse response,String authTokenType)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeString(authTokenType);
                    send(4,data);
                }finally{data.recycle();}
            }
            public void updateCredentials(IAccountAuthenticatorResponse response,Account account,String authTokenType,Bundle options)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeTypedObject(account,0);
                    data.writeString(authTokenType);
                    data.writeTypedObject(options,0);
                    send(5,data);
                }finally{data.recycle();}
            }
            public void editProperties(IAccountAuthenticatorResponse response,String accountType)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeString(accountType);
                    send(6,data);
                }finally{data.recycle();}
            }
            public void hasFeatures(IAccountAuthenticatorResponse response,Account account,String[] features)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeTypedObject(account,0);
                    data.writeStringArray(features);
                    send(7,data);
                }finally{data.recycle();}
            }
            public void getAccountRemovalAllowed(IAccountAuthenticatorResponse response,Account account)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeTypedObject(account,0);
                    send(8,data);
                }finally{data.recycle();}
            }
            public void getAccountCredentialsForCloning(IAccountAuthenticatorResponse response,Account account)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeTypedObject(account,0);
                    send(9,data);
                }finally{data.recycle();}
            }
            public void addAccountFromCredentials(IAccountAuthenticatorResponse response,Account account,Bundle accountCredentials)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeTypedObject(account,0);
                    data.writeTypedObject(accountCredentials,0);
                    send(10,data);
                }finally{data.recycle();}
            }
            public void startAddAccountSession(IAccountAuthenticatorResponse response,String accountType,String authTokenType,String[] requiredFeatures,Bundle options)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeString(accountType);
                    data.writeString(authTokenType);
                    data.writeStringArray(requiredFeatures);
                    data.writeTypedObject(options,0);
                    send(11,data);
                }finally{data.recycle();}
            }
            public void startUpdateCredentialsSession(IAccountAuthenticatorResponse response,Account account,String authTokenType,Bundle options)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeTypedObject(account,0);
                    data.writeString(authTokenType);
                    data.writeTypedObject(options,0);
                    send(12,data);
                }finally{data.recycle();}
            }
            public void finishSession(IAccountAuthenticatorResponse response,String accountType,Bundle sessionBundle)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeString(accountType);
                    data.writeTypedObject(sessionBundle,0);
                    send(13,data);
                }finally{data.recycle();}
            }
            public void isCredentialsUpdateSuggested(IAccountAuthenticatorResponse response,Account account,String statusToken)throws RemoteException{
                Parcel data=request(response);try{
                    data.writeTypedObject(account,0);
                    data.writeString(statusToken);
                    send(14,data);
                }finally{data.recycle();}
            }
        }
    }
}
