// SPDX-License-Identifier: GPL-3.0-only
import android.accounts.Account;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;

public final class SyncOwnerSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static class Adapter extends AbstractThreadedSyncAdapter {
        Adapter(Context context){super(context,false);}
        public void onPerformSync(Account a,Bundle b,String authority,ContentProviderClient provider,SyncResult result){}
    }
    public static void main(String[] args)throws Exception{
        Context root=new ContextWrapper(null){public ContentResolver getContentResolver(){return new ContentResolver(this);}};Context wrapped=new ContextWrapper(new ContextWrapper(root));
        check(root.shirazSyncAdapters()==wrapped.shirazSyncAdapters(),"wrapper split owner");
        Adapter adapter=new Adapter(wrapped);
        final CountDownLatch callbackEntered=new CountDownLatch(1),callbackRelease=new CountDownLatch(1);final Thread[] worker={null};
        ISyncContext callback=new ISyncContext.Stub(){
            public void sendHeartbeat(){}
            public void onFinished(SyncResult result){
                check(result.databaseError,"missing provider result");worker[0]=Thread.currentThread();callbackEntered.countDown();
                boolean done=false;while(!done)try{callbackRelease.await();done=true;}catch(InterruptedException ignored){}
            }
        };
        try{
            // Missing-provider completion still runs on a worker. Keep that callback alive
            // after its request slot is freed to prove teardown tracks the whole lifetime.
            ISyncAdapter.Stub.asInterface(adapter.getSyncAdapterBinder()).startSync(callback,"missing.owner.provider",new Account("fixture","sync"),new Bundle());
            check(callbackEntered.await(3,TimeUnit.SECONDS),"completion callback not entered");
            long start=System.nanoTime();
            try{root.shirazSyncAdapters().close(20);throw new AssertionError("shutdown ignored active completion callback");}catch(IllegalStateException expected){}
            check(System.nanoTime()-start<1000000000L,"unbounded owner shutdown");
            try{new Adapter(wrapped);throw new AssertionError("closed owner accepted adapter");}catch(IllegalStateException expected){}
            try{ISyncAdapter.Stub.asInterface(adapter.getSyncAdapterBinder()).startSync(callback,"missing",new Account("fixture","sync"),new Bundle());throw new AssertionError("closed adapter accepted work");}catch(IllegalStateException expected){}
            callbackRelease.countDown();root.shirazSyncAdapters().close(3000);worker[0].join(3000);check(!worker[0].isAlive(),"completion worker leaked");
            root.shirazSyncAdapters().close(0);
            Context separate=new ContextWrapper(null);Adapter fresh=new Adapter(separate);check(fresh.getContext()==separate,"separate owner unusable");separate.shirazSyncAdapters().close(100);
            System.out.println("SYNC_OWNER_SHUTDOWN_OK");
        }finally{callbackRelease.countDown();adapter.shirazClose();if(worker[0]!=null)worker[0].join(3000);}
    }
}
