// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.accounts.Account;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class LocalSyncExecutionSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static final class Adapter extends ISyncAdapter.Stub {
        ISyncContext callback; int canceled; boolean fail;
        public void startSync(ISyncContext callback,String authority,Account account,Bundle extras)throws RemoteException {
            check(authority.equals("fixture.data")&&account.type.equals("fixture.accounts"),"dispatch identity");
            check(extras.getInt("n")==7,"extras were not snapshotted");
            this.callback=callback;callback.sendHeartbeat();
            if(fail)throw new RemoteException("delivery uncertain");
        }
        public void cancelSync(ISyncContext callback){check(callback==this.callback,"cancel callback identity");canceled++;}
    }
    static LocalSyncExecution operation(Adapter adapter,LocalSyncExecution.Listener listener){
        Bundle extras=new Bundle();extras.putInt("n",7);
        LocalSyncExecution result=new LocalSyncExecution(adapter,new Account("fixture","fixture.accounts"),"fixture.data",extras,listener);
        extras.putInt("n",99);return result;
    }
    public static void main(String[] args)throws Exception {
        Adapter adapter=new Adapter();final AtomicInteger completed=new AtomicInteger();final SyncResult[] captured={null};
        LocalSyncExecution execution=operation(adapter,new LocalSyncExecution.Listener(){public void finished(SyncResult result,boolean canceled){check(!canceled,"unexpected cancellation");captured[0]=result;completed.incrementAndGet();}});
        execution.start();check(execution.heartbeats()==1,"heartbeat");
        try{execution.start();throw new AssertionError("duplicate start");}catch(IllegalStateException expected){}
        final SyncResult result=new SyncResult();result.stats.numUpdates=3;
        Thread first=new Thread(new Runnable(){public void run(){try{adapter.callback.onFinished(result);}catch(RemoteException e){throw new RuntimeException(e);}}});
        Thread second=new Thread(new Runnable(){public void run(){try{adapter.callback.onFinished(result);}catch(RemoteException e){throw new RuntimeException(e);}}});
        first.start();second.start();first.join();second.join();result.stats.numUpdates=99;
        check(completed.get()==1&&captured[0].stats.numUpdates==3,"duplicate completion or mutable result");
        execution.cancel();check(adapter.canceled==0,"cancel after completion");
        Adapter canceled=new Adapter();final boolean[] canceledResult={false};
        LocalSyncExecution pending=operation(canceled,new LocalSyncExecution.Listener(){public void finished(SyncResult outcome,boolean wasCanceled){canceledResult[0]=wasCanceled;}});
        pending.start();pending.cancel();pending.cancel();
        check(canceled.canceled==1&&pending.state()==LocalSyncExecution.State.CANCEL_REQUESTED,"cancellation falsely completed work");
        canceled.callback.onFinished(new SyncResult());check(canceledResult[0],"late success lost cancellation");
        Adapter failed=new Adapter();failed.fail=true;
        LocalSyncExecution uncertain=operation(failed,new LocalSyncExecution.Listener(){public void finished(SyncResult outcome,boolean wasCanceled){throw new AssertionError("fabricated completion");}});
        try{uncertain.start();throw new AssertionError("delivery failure hidden");}catch(RemoteException expected){}
        check(uncertain.state()==LocalSyncExecution.State.DELIVERY_FAILED,"delivery uncertainty lost");
        System.out.println("LOCAL_SYNC_EXECUTION_OK");
    }
}
