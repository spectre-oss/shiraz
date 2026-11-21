// SPDX-License-Identifier: GPL-3.0-only
import android.accounts.Account;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.util.concurrent.*;

public final class ThreadedSyncSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void await(CountDownLatch latch,String message)throws Exception{check(latch.await(3,TimeUnit.SECONDS),message);}
    static final class Callback extends ISyncContext.Stub {
        final CountDownLatch done=new CountDownLatch(1);volatile SyncResult result;
        public void sendHeartbeat(){}
        public void onFinished(SyncResult result){this.result=result;done.countDown();}
    }
    static final class Provider extends ContentProvider {
        public boolean onCreate(){return true;}
        public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
        public String getType(Uri u){return null;}public Uri insert(Uri u,ContentValues v){return null;}
        public int delete(Uri u,String s,String[] a){return 0;}public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
        public Bundle call(String method,String arg,Bundle extras){Bundle result=new Bundle();result.putInt("value",9);return result;}
    }
    static final class Adapter extends AbstractThreadedSyncAdapter {
        final BlockingQueue<Thread> entered=new LinkedBlockingQueue<Thread>();
        final CountDownLatch release=new CountDownLatch(1);volatile boolean security;volatile ContentProviderClient lastProvider;
        Adapter(Context c,boolean parallel){super(c,true,parallel);}
        public void onPerformSync(Account account,Bundle extras,String authority,ContentProviderClient provider,SyncResult result){
            lastProvider=provider;entered.add(Thread.currentThread());
            if(extras.getBoolean("deny"))throw new SecurityException("fixture denial");
            check(provider.call("probe",null,null).getInt("value")==9,"provider acquisition");
            if(extras.getBoolean("block"))try{release.await();}catch(InterruptedException expected){/* Deliberately clears interrupt: cancellation must remain sticky. */}
            result.stats.numUpdates=extras.getInt("updates",1);
        }
        public void onSecurityException(Account a,Bundle b,String authority,SyncResult result){security=true;}
    }
    public static void main(String[] args)throws Exception{
        final Context context=new ContextWrapper(null){public ContentResolver getContentResolver(){return new ContentResolver(this);}};
        ProviderInfo info=new ProviderInfo();info.authority="fixture.sync";new Provider().attachInfo(context,info);
        Account a=new Account("a","fixture"),b=new Account("b","fixture");
        Adapter serial=new Adapter(context,false),parallel=new Adapter(context,true);
        try{
            ISyncAdapter api=ISyncAdapter.Stub.asInterface(serial.getSyncAdapterBinder());
            Bundle invalid=new Bundle();invalid.putStringArray("invalid",new String[]{"value"});
            Callback rejected=new Callback();
            try{api.startSync(rejected,info.authority,a,invalid);throw new AssertionError("Invalid sync extra admitted");}
            catch(IllegalArgumentException expected){}
            check(serial.entered.isEmpty()&&rejected.done.getCount()==1,"Invalid extras caused work or completion");
            Bundle block=new Bundle();block.putBoolean("block",true);block.putInt("updates",4);
            Callback first=new Callback();api.startSync(first,info.authority,a,block);
            Thread worker=serial.entered.poll(3,TimeUnit.SECONDS);check(worker!=null&&worker!=Thread.currentThread(),"worker dispatch");
            Callback duplicate=new Callback();api.startSync(duplicate,info.authority,b,new Bundle());await(duplicate.done,"duplicate result");check(duplicate.result.syncAlreadyInProgress,"serial duplicate exclusion");
            api.cancelSync(new Callback());check(worker.isAlive(),"wrong callback canceled worker");
            api.cancelSync(first);worker.join(3000);check(!worker.isAlive()&&first.done.getCount()==1,"canceled worker reported completion");
            try{serial.lastProvider.call("probe",null,null);throw new AssertionError("canceled worker retained provider");}catch(IllegalStateException expected){}
            Callback next=new Callback();api.startSync(next,info.authority,a,new Bundle());await(next.done,"slot not released");check(next.result.stats.numUpdates==1,"normal completion");
            try{serial.lastProvider.call("probe",null,null);throw new AssertionError("completed worker retained provider");}catch(IllegalStateException expected){}
            Callback missing=new Callback();api.startSync(missing,"missing.provider",a,new Bundle());await(missing.done,"missing provider completion");check(missing.result.databaseError,"missing provider reported success");
            Callback denied=new Callback();Bundle deny=new Bundle();deny.putBoolean("deny",true);api.startSync(denied,info.authority,a,deny);await(denied.done,"denied completion");check(serial.security&&denied.result.databaseError,"security callback");
            Bundle init=new Bundle();init.putBoolean("initialize",true);
            try{api.startSync(new Callback(),info.authority,a,init);throw new AssertionError("unimplemented initialization accepted");}catch(UnsupportedOperationException expected){}
            ISyncAdapter concurrent=ISyncAdapter.Stub.asInterface(parallel.getSyncAdapterBinder());
            Callback one=new Callback(),two=new Callback();concurrent.startSync(one,info.authority,a,block);concurrent.startSync(two,info.authority,b,block);
            Thread w1=parallel.entered.poll(3,TimeUnit.SECONDS),w2=parallel.entered.poll(3,TimeUnit.SECONDS);check(w1!=null&&w2!=null&&w1!=w2,"parallel accounts");
            Callback same=new Callback();concurrent.startSync(same,info.authority,a,new Bundle());await(same.done,"parallel same account rejection");check(same.result.syncAlreadyInProgress,"same account concurrent work");
            block.putInt("updates",99);parallel.release.countDown();await(one.done,"first parallel completion");await(two.done,"second parallel completion");check(one.result.stats.numUpdates==4&&two.result.stats.numUpdates==4,"extras snapshot");
            w1.join(3000);w2.join(3000);check(!w1.isAlive()&&!w2.isAlive(),"parallel worker leak");
            serial.shirazClose();try{api.startSync(new Callback(),info.authority,a,new Bundle());throw new AssertionError("closed adapter restarted");}catch(IllegalStateException expected){}
            Adapter bounded=new Adapter(context,true);
            try{
                ISyncAdapter limited=ISyncAdapter.Stub.asInterface(bounded.getSyncAdapterBinder());
                Thread[] held=new Thread[32];Callback[] callbacks=new Callback[32];
                for(int i=0;i<32;i++){
                    callbacks[i]=new Callback();limited.startSync(callbacks[i],info.authority,new Account("bounded"+i,"fixture"),block);
                    held[i]=bounded.entered.poll(3,TimeUnit.SECONDS);check(held[i]!=null,"bounded worker startup");
                }
                try{limited.startSync(new Callback(),info.authority,new Account("overflow","fixture"),block);throw new AssertionError("worker cap bypassed");}catch(IllegalStateException expected){}
                bounded.shirazClose();
                for(int i=0;i<32;i++){held[i].join(3000);check(!held[i].isAlive()&&callbacks[i].done.getCount()==1,"close leaked worker or sent completion");}
            }finally{bounded.release.countDown();bounded.shirazClose();}
            System.out.println("THREADED_SYNC_LIFECYCLE_OK");
        }finally{serial.release.countDown();parallel.release.countDown();serial.shirazClose();parallel.shirazClose();}
    }
}
