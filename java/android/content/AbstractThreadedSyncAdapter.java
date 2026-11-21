// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.accounts.Account;
import android.os.*;
import java.util.HashMap;

/** Bounded sync workers. Host sync discovery, authorization and scheduling are separate. */
public abstract class AbstractThreadedSyncAdapter {
    @Deprecated public static final int LOG_SYNC_DETAILS=2743;
    private final Context context;
    private final boolean autoInitialize,parallel;
    private final Object lock=new Object();
    private final shiraz.runtime.HostSyncAdapters owner;
    private final java.util.HashSet<Worker> running=new java.util.HashSet<Worker>();
    private final HashMap<Account,Worker> workers=new HashMap<Account,Worker>();
    private final ThreadLocal<Worker> cancellation=new ThreadLocal<Worker>();
    private boolean closed;
    private long sequence;
    public AbstractThreadedSyncAdapter(Context context,boolean autoInitialize){this(context,autoInitialize,false);}
    public AbstractThreadedSyncAdapter(Context context,boolean autoInitialize,boolean allowParallelSyncs){
        if(context==null)throw new NullPointerException("context");
        this.context=context;this.autoInitialize=autoInitialize;parallel=allowParallelSyncs;
        owner=context.shirazSyncAdapters();owner.register(this);
    }
    public Context getContext(){return context;}
    public final IBinder getSyncAdapterBinder(){return binder;}
    private void requireLocalCoordinator(){
        if(Binder.getCallingPid()!=android.os.Process.myPid())throw new UnsupportedOperationException("Remote sync requires host coordinator authorization");
    }
    private final ISyncAdapter.Stub binder=new ISyncAdapter.Stub(){
        public void startSync(ISyncContext callback,String authority,Account account,Bundle extras){
            requireLocalCoordinator();
            if(callback==null||account==null||authority==null||authority.isEmpty()||extras==null)throw new IllegalArgumentException("Sync callback, account, authority and extras required");
            Bundle snapshot=extras.shirazBroadcastCopy();
            ContentResolver.validateSyncExtrasBundle(snapshot);
            Account key=parallel?account:null;
            synchronized(lock){
                if(closed)throw new IllegalStateException("Sync adapter closed");
                if(!workers.containsKey(key)){
                    if(autoInitialize&&snapshot.getBoolean("initialize",false))throw new UnsupportedOperationException("Syncability initialization requires host sync state");
                    if(running.size()>=32)throw new IllegalStateException("Sync worker limit");
                    Worker worker=new Worker(key,account,authority,snapshot,callback,++sequence);workers.put(key,worker);running.add(worker);
                    try{worker.start();}catch(RuntimeException|Error failure){workers.remove(key);running.remove(worker);throw failure;}
                    return;
                }
            }
            new SyncContext(callback).onFinished(SyncResult.ALREADY_IN_PROGRESS);
        }
        public void cancelSync(ISyncContext callback){
            requireLocalCoordinator();if(callback==null)throw new IllegalArgumentException("Sync callback required");
            Worker target=null;
            synchronized(lock){for(Worker worker:workers.values())if(worker.callback.getSyncContextBinder()==callback.asBinder()){target=worker;worker.canceled=true;break;}}
            if(target!=null)cancelWorker(target);
        }
    };
    private void cancelWorker(Worker worker){
        cancellation.set(worker);
        try{if(parallel)onSyncCanceled(worker);else onSyncCanceled();}finally{cancellation.remove();}
    }
    public abstract void onPerformSync(Account account,Bundle extras,String authority,ContentProviderClient provider,SyncResult result);
    public void onSecurityException(Account account,Bundle extras,String authority,SyncResult result){}
    public void onSyncCanceled(){
        Worker worker=cancellation.get();
        if(worker==null)synchronized(lock){worker=workers.get(null);}
        if(worker!=null)worker.interrupt();
    }
    public void onSyncCanceled(Thread thread){thread.interrupt();}
    /** Session owner must close adapters before releasing their provider context. */
    public final void shirazClose(){
        Worker[] active;
        synchronized(lock){closed=true;active=running.toArray(new Worker[0]);for(Worker worker:active)worker.canceled=true;}
        for(Worker worker:active)worker.interrupt();
        if(active.length==0)owner.released(this);
    }
    public final boolean shirazAwaitTermination(long deadlineNanos){
        synchronized(lock){
            while(!running.isEmpty()){
                if(running.contains(Thread.currentThread()))return false;
                long remaining=deadlineNanos-System.nanoTime();if(remaining<=0)return false;
                try{lock.wait(Math.max(1,remaining/1000000L));}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();return false;}
            }
            return true;
        }
    }
    private final class Worker extends Thread {
        final Account key,account;final String authority;final Bundle extras;final SyncContext callback;
        volatile boolean canceled;
        Worker(Account key,Account account,String authority,Bundle extras,ISyncContext callback,long id){
            super("ShirazSync-"+id);this.key=key;this.account=account;this.authority=authority;this.extras=extras;this.callback=new SyncContext(callback);
        }
        public void run(){
            SyncResult result=new SyncResult();ContentProviderClient provider=null;
            try{
                if(canceled||isInterrupted())return;
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                provider=context.getContentResolver().acquireContentProviderClient(authority);
                if(provider==null)result.databaseError=true;
                else if(!canceled&&!isInterrupted())onPerformSync(account,extras,authority,provider,result);
            }catch(SecurityException denied){result.databaseError=true;onSecurityException(account,extras,authority,result);}
            catch(RuntimeException|Error failure){result.databaseError=true;throw failure;}
            finally{
                try{if(provider!=null)provider.release();}
                finally{
                    synchronized(lock){if(workers.get(key)==this)workers.remove(key);}
                    try{if(!canceled&&!isInterrupted())callback.onFinished(result);}
                    finally{
                        boolean release;
                        synchronized(lock){running.remove(this);release=closed&&running.isEmpty();lock.notifyAll();}
                        if(release)owner.released(AbstractThreadedSyncAdapter.this);
                    }
                }
            }
        }
    }
}
