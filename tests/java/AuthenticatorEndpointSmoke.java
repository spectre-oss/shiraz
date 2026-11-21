// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.accounts.*;
import android.os.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Scope isolation without creating any production broker grants. */
public final class AuthenticatorEndpointSmoke {
    public static void run()throws Exception{
        final int pid=android.os.Process.myPid(),uid=android.os.Process.myUid();
        final int[] calls={0};final boolean[] fail={false};
        Binder provider=new Binder(){
            public String getInterfaceDescriptor(){return "android.accounts.IAccountAuthenticator";}
            protected boolean onTransact(int code,Parcel data,Parcel reply,int flags){
                if(!AuthenticatorEndpoint.permits(pid,uid)||AuthenticatorEndpoint.permits(pid+1,uid)||AuthenticatorEndpoint.permits(pid,uid+1))throw new AssertionError("Scoped caller identity");
                data.enforceInterface("android.accounts.IAccountAuthenticator");data.readStrongBinder();data.readString();data.enforceNoDataAvail();calls[0]++;
                final AtomicBoolean leaked=new AtomicBoolean();Thread child=new Thread(new Runnable(){public void run(){leaked.set(AuthenticatorEndpoint.permits(pid,uid));}});
                child.start();try{child.join(2000);}catch(InterruptedException failure){throw new AssertionError(failure);}
                if(child.isAlive()||leaked.get())throw new AssertionError("Authorization inherited by another thread");
                if(fail[0])throw new IllegalArgumentException("private-fixture");return true;
            }
        };
        if(AuthenticatorEndpoint.permits(pid,uid))throw new AssertionError("Ambient account manager permission");
        AuthenticatorEndpoint endpoint=new AuthenticatorEndpoint(provider);
        IAccountAuthenticator client=IAccountAuthenticator.Stub.asInterface(endpoint);
        client.getAuthTokenLabel(null,"fixture");
        if(calls[0]!=1||AuthenticatorEndpoint.permits(pid,uid))throw new AssertionError("Permission persisted after return");
        fail[0]=true;try{client.getAuthTokenLabel(null,"fixture");throw new AssertionError("Provider failure missing");}catch(IllegalStateException expected){if(expected.getMessage().contains("private-fixture")||expected.getCause()!=null)throw new AssertionError("Provider exception exposed");}
        if(AuthenticatorEndpoint.permits(pid,uid))throw new AssertionError("Permission persisted after failure");
        for(int code:new int[]{0,15}){
            Parcel data=Parcel.obtain();try{data.writeInterfaceToken("android.accounts.IAccountAuthenticator");
                try{endpoint.transact(code,data,null,IBinder.FLAG_ONEWAY);throw new AssertionError("Unscoped transaction accepted");}catch(SecurityException expected){}
            }finally{data.recycle();}
        }
        Parcel data=Parcel.obtain();try{data.writeInterfaceToken("wrong.interface");
            try{endpoint.transact(4,data,null,IBinder.FLAG_ONEWAY);throw new AssertionError("Wrong interface accepted");}catch(SecurityException expected){}
        }finally{data.recycle();}
        data=Parcel.obtain();try{data.writeInterfaceToken("android.accounts.IAccountAuthenticator");
            try{endpoint.transact(4,data,null,0);throw new AssertionError("Synchronous request accepted");}catch(SecurityException expected){}
        }finally{data.recycle();}
        if(calls[0]!=2||AuthenticatorEndpoint.permits(pid,uid))throw new AssertionError("Rejected request invoked provider");
        try{new AuthenticatorEndpoint(new Binder());throw new AssertionError("Wrong service accepted");}catch(SecurityException expected){}
    }
}
