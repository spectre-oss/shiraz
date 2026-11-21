// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.os.*;

/** Host-created wrapper for a broker-authorized authenticator connection. */
final class AuthenticatorEndpoint extends Binder {
    private static final String DESCRIPTOR="android.accounts.IAccountAuthenticator";
    private static final ThreadLocal<long[]> CALLER=new ThreadLocal<long[]>();
    private final IBinder target;
    AuthenticatorEndpoint(IBinder target)throws RemoteException{
        if(target==null||!DESCRIPTOR.equals(target.getInterfaceDescriptor()))throw new SecurityException("Invalid authenticator service Binder");
        this.target=target;
    }
    static boolean permits(int pid,int uid){
        long[] caller=CALLER.get();
        return caller!=null&&caller[0]==pid&&caller[1]==uid&&Binder.getCallingPid()==pid&&Binder.getCallingUid()==uid;
    }
    @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
        if(code==IBinder.INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(DESCRIPTOR);return true;}
        if(code<1||code>14||flags!=IBinder.FLAG_ONEWAY)throw new SecurityException("Invalid authenticator endpoint transaction");
        int position=data.dataPosition();data.enforceInterface(DESCRIPTOR);data.setDataPosition(position);
        long[] previous=CALLER.get();CALLER.set(new long[]{Binder.getCallingPid(),Binder.getCallingUid()});
        try{return target.transact(code,data,reply,flags);}
        catch(RuntimeException failure){
            // BinderTransport logs uncaught dispatch exceptions; never forward a
            // provider's arbitrary message or cause into that diagnostic path.
            throw new IllegalStateException("Authenticator dispatch failed");
        }finally{if(previous==null)CALLER.remove();else CALLER.set(previous);}
    }
}
