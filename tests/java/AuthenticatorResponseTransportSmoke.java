// SPDX-License-Identifier: GPL-3.0-only
import android.accounts.*;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Two ART processes exchange the actual authenticator response wire contract. */
public final class AuthenticatorResponseTransportSmoke {
    public static void main(String[] args)throws Exception{
        int fd=Integer.parseInt(args[1]);
        if("receive".equals(args[0])){
            final CountDownLatch delivered=new CountDownLatch(3);
            final AtomicInteger results=new AtomicInteger(),continued=new AtomicInteger(),errors=new AtomicInteger();
            IAccountAuthenticatorResponse.Stub callback=new IAccountAuthenticatorResponse.Stub(){
                private void origin(){if(Binder.getCallingPid()==android.os.Process.myPid())throw new AssertionError("Callback did not cross processes");}
                public void onResult(Bundle result){origin();if(result==null||!"fixture-result".equals(result.getString("value")))throw new AssertionError("Result payload");results.incrementAndGet();delivered.countDown();}
                public void onRequestContinued(){origin();continued.incrementAndGet();delivered.countDown();}
                public void onError(int code,String message){origin();if(code!=7||!"fixture-error".equals(message))throw new AssertionError("Error payload");errors.incrementAndGet();delivered.countDown();}
                protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
                    if(code!=4)return super.onTransact(code,data,reply,flags);
                    try{if(!delivered.await(5,TimeUnit.SECONDS))throw new AssertionError("Callback timeout");}catch(InterruptedException interrupted){throw new AssertionError(interrupted);}
                    reply.writeInt(results.get());reply.writeInt(continued.get());reply.writeInt(errors.get());reply.writeInt(android.os.Process.myPid());return true;
                }
            };
            try(BinderTransport transport=new BinderTransport(fd,callback)){transport.awaitClosed();}
            if(results.get()!=1||continued.get()!=1||errors.get()!=1)throw new AssertionError("Callback counts");
            System.out.println("AUTHENTICATOR_REMOTE_RECEIVER_OK");
        }else{
            try(BinderTransport transport=new BinderTransport(fd,null)){
                Parcel parcel=Parcel.obtain();
                try{
                    AccountAuthenticatorResponse original=new AccountAuthenticatorResponse(IAccountAuthenticatorResponse.Stub.asInterface(transport.remoteRoot()));
                    original.writeToParcel(parcel,0);parcel.setDataPosition(0);
                    AccountAuthenticatorResponse restored=AccountAuthenticatorResponse.CREATOR.createFromParcel(parcel);parcel.enforceNoDataAvail();
                    Bundle value=new Bundle();value.putString("value","fixture-result");
                    restored.onResult(value);restored.onRequestContinued();restored.onError(7,"fixture-error");
                }finally{parcel.recycle();}
                Parcel query=Parcel.obtain(),reply=Parcel.obtain();
                try{
                    if(!transport.remoteRoot().transact(4,query,reply,0))throw new AssertionError("Missing callback acknowledgment");
                    if(reply.readInt()!=1||reply.readInt()!=1||reply.readInt()!=1||reply.readInt()==android.os.Process.myPid())throw new AssertionError("Remote callback counts/identity");
                    reply.enforceNoDataAvail();
                }finally{query.recycle();reply.recycle();}
            }
            System.out.println("AUTHENTICATOR_REMOTE_SENDER_OK");
        }
    }
}
