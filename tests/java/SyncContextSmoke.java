// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.os.*;

public final class SyncContextSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        final int[] beats={0},finishes={0};final SyncResult[] received={null};
        final ISyncContext.Stub endpoint=new ISyncContext.Stub(){
            public void sendHeartbeat(){beats[0]++;}
            public void onFinished(SyncResult result){finishes[0]++;received[0]=result;}
        };
        check(ISyncContext.Stub.asInterface(null)==null&&ISyncContext.Stub.asInterface(endpoint)==endpoint,"local interface identity");
        Binder bridge=new Binder(){protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            check(flags==0&&reply!=null,"sync callback must use synchronous reply");return endpoint.transact(code,data,reply,flags);
        }};
        ISyncContext proxy=ISyncContext.Stub.asInterface(bridge);
        proxy.sendHeartbeat();check(beats[0]==1,"heartbeat dispatch");
        SyncResult result=new SyncResult();result.stats.numIoExceptions=3;result.delayUntil=456;
        proxy.onFinished(result);check(finishes[0]==1&&received[0]!=result&&received[0].stats.numIoExceptions==3&&received[0].delayUntil==456,"result parcel dispatch");
        proxy.onFinished(null);check(finishes[0]==2&&received[0]==null,"nullable completion");
        for(boolean wrongDescriptor:new boolean[]{true,false}){
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();
            try{
                data.writeInterfaceToken(wrongDescriptor?"invalid":"android.content.ISyncContext");
                if(!wrongDescriptor)data.writeInt(99);
                try{endpoint.transact(1,data,reply,0);throw new AssertionError("malformed callback accepted");}
                catch(SecurityException|IllegalArgumentException expected){}
                check(beats[0]==1,"malformed payload invoked callback");
            }finally{data.recycle();reply.recycle();}
        }
        SyncContext context=new SyncContext(proxy);check(context.getSyncContextBinder()==bridge,"callback binder identity");
        context.setStatusText("progress");int after=beats[0];context.setStatusText("progress again");check(beats[0]==after,"heartbeat throttle");
        Thread.sleep(1050);context.setStatusText("later");check(beats[0]==after+1,"heartbeat resumes");
        context.onFinished(result);check(finishes[0]==3,"context completion");
        ISyncContext absent=ISyncContext.Stub.asInterface(new Binder());
        try{absent.onFinished(result);throw new AssertionError("unsupported transaction accepted");}catch(RemoteException expected){}
        new SyncContext(absent).onFinished(result);
        SyncContext none=new SyncContext(null);none.onFinished(result);none.setStatusText("none");check(none.getSyncContextBinder()==null,"null context binder");
        System.out.println("SYNC_CONTEXT_WIRE_OK");
    }
}
