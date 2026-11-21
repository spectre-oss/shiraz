// SPDX-License-Identifier: GPL-3.0-only
import android.accounts.*;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import shiraz.runtime.*;

public final class AccountListenersSmoke {
 public static void run(ServiceBrokerClient client)throws Exception{
  java.lang.reflect.Field field=Class.forName("shiraz.runtime.RuntimeBroker").getDeclaredField("client");field.setAccessible(true);field.set(null,client);
  HandlerThread thread=new HandlerThread("account-listener-test");thread.start();
  Handler handler=new Handler(thread.getLooper());AccountManager manager=AccountManager.get(new android.content.ContextWrapper(null){
   public android.content.Context getApplicationContext(){return this;}
   public String getPackageName(){return "test.caller";}
   public android.content.SharedPreferences getSharedPreferences(String name,int mode){return null;}
  });
  final BlockingQueue<Account[]> updates=new LinkedBlockingQueue<Account[]>();
  final AtomicBoolean wrongThread=new AtomicBoolean();
  OnAccountsUpdateListener listener=new OnAccountsUpdateListener(){public void onAccountsUpdated(Account[] accounts){if(Looper.myLooper()!=handler.getLooper())wrongThread.set(true);updates.add(accounts);}};
  final AtomicInteger ignored=new AtomicInteger();OnAccountsUpdateListener filtered=new OnAccountsUpdateListener(){public void onAccountsUpdated(Account[] accounts){ignored.incrementAndGet();}};
  Account account=new Account("listener-user","test.accounts");
  try{
   manager.addOnAccountsUpdatedListener(listener,handler,true,null);
   if(take(updates).length!=0)throw new AssertionError("Initial accounts");
   try{manager.addOnAccountsUpdatedListener(listener,handler,false,null);throw new AssertionError("Duplicate accepted");}catch(IllegalStateException expected){}
   manager.addOnAccountsUpdatedListener(filtered,handler,false,new String[]{"other.type"});
   client.accountOperation("add",account,null,null,null);
   Account[] added=take(updates);if(added.length!=1||!added[0].equals(account))throw new AssertionError("Added account delivery");added[0]=null;
   client.accountOperation("remove",account,null,null,null);
   if(take(updates).length!=0||wrongThread.get()||ignored.get()!=0)throw new AssertionError("Removal/filter/handler");
   manager.removeOnAccountsUpdatedListener(listener);manager.removeOnAccountsUpdatedListener(filtered);
   CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
   handler.post(new Runnable(){public void run(){entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException error){throw new AssertionError(error);}}});
   if(!entered.await(3,TimeUnit.SECONDS))throw new AssertionError("Handler barrier");
   manager.addOnAccountsUpdatedListener(listener,handler,true,null);manager.removeOnAccountsUpdatedListener(listener);
   release.countDown();CountDownLatch drained=new CountDownLatch(1);handler.post(new Runnable(){public void run(){drained.countDown();}});
   if(!drained.await(3,TimeUnit.SECONDS)||!updates.isEmpty())throw new AssertionError("Removed callback delivered");
   manager.addOnAccountsUpdatedListener(listener,handler,true,new String[0]);
   if(take(updates).length!=0)throw new AssertionError("Empty filter");
   manager.removeOnAccountsUpdatedListener(listener);
   verifySlowHandlerAndRevocation(manager,client,handler,account);
   Looper.prepareMainLooper();
   final AtomicInteger mainCalls=new AtomicInteger();
   OnAccountsUpdateListener mainListener=new OnAccountsUpdateListener(){public void onAccountsUpdated(Account[] accounts){if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Default callback thread");mainCalls.incrementAndGet();}};
   try{
    manager.addOnAccountsUpdatedListener(mainListener,null,true);
    if(mainCalls.get()!=0)throw new AssertionError("Inline callback");
    Looper.getMainLooper().shirazDispatchDue(10);
    if(mainCalls.get()!=1)throw new AssertionError("Main callback missing");
   }finally{manager.removeOnAccountsUpdatedListener(mainListener);Looper.shirazShutdownMain();}
   Looper.prepareMainLooper();
   final AtomicInteger shutdownCalls=new AtomicInteger();
   OnAccountsUpdateListener shutdownListener=new OnAccountsUpdateListener(){public void onAccountsUpdated(Account[] accounts){shutdownCalls.incrementAndGet();}};
   try{
    manager.addOnAccountsUpdatedListener(shutdownListener,null,true);
    java.lang.reflect.Method close=Class.forName("shiraz.runtime.RuntimeBroker").getDeclaredMethod("close");close.setAccessible(true);close.invoke(null);
    Looper.getMainLooper().shirazDispatchDue(10);
    if(shutdownCalls.get()!=0||field.get(null)!=null)throw new AssertionError("Runtime shutdown retained callback or client");
    try{client.accountSnapshot();throw new AssertionError("Runtime shutdown retained broker channel");}catch(java.io.IOException expected){}
   }finally{manager.removeOnAccountsUpdatedListener(shutdownListener);Looper.shirazShutdownMain();}
   System.out.println("ART_ACCOUNT_LISTENER_RUNTIME_SHUTDOWN_OK");
   System.out.println("ART_ACCOUNT_LISTENERS_OK");
  }finally{
   manager.removeOnAccountsUpdatedListener(listener);manager.removeOnAccountsUpdatedListener(filtered);
   field.set(null,null);thread.quit();thread.join(3000);if(thread.isAlive())throw new AssertionError("Handler leak");
  }
 }
 private static void verifySlowHandlerAndRevocation(AccountManager manager,ServiceBrokerClient client,Handler fast,Account account)throws Exception{
  HandlerThread slowThread=new HandlerThread("slow-account-listener");slowThread.start();
  AtomicInteger posts=new AtomicInteger();
  Handler slow=new Handler(slowThread.getLooper()){
   public boolean sendMessageAtTime(Message message,long when){posts.incrementAndGet();return super.sendMessageAtTime(message,when);}
  };
  BlockingQueue<Account[]> fastUpdates=new LinkedBlockingQueue<Account[]>(),slowUpdates=new LinkedBlockingQueue<Account[]>();
  OnAccountsUpdateListener fastListener=new OnAccountsUpdateListener(){public void onAccountsUpdated(Account[] values){fastUpdates.add(values);}};
  OnAccountsUpdateListener slowListener=new OnAccountsUpdateListener(){public void onAccountsUpdated(Account[] values){slowUpdates.add(values);}};
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  slow.post(new Runnable(){public void run(){entered.countDown();try{if(!release.await(15,TimeUnit.SECONDS))throw new AssertionError("Slow handler barrier timeout");}catch(InterruptedException error){throw new AssertionError(error);}}});
  try{
   if(!entered.await(3,TimeUnit.SECONDS))throw new AssertionError("Slow handler unavailable");
   manager.addOnAccountsUpdatedListener(fastListener,fast,false);
   int baseline=posts.get();
   String[] types={account.type};manager.addOnAccountsUpdatedListener(slowListener,slow,true,types);types[0]="mutated.filter";
   for(int i=0;i<16;i++){
    boolean add=(i%2)==0;
    client.accountOperation(add?"add":"remove",account,null,null,null);
    Account[] observed=take(fastUpdates);
    if(observed.length!=(add?1:0))throw new AssertionError("Fast observer lost mutation");
   }
   if(posts.get()-baseline!=1)throw new AssertionError("Slow handler queue grew with account changes");
   release.countDown();if(take(slowUpdates).length!=0)throw new AssertionError("Slow observer did not receive latest state");
   CountDownLatch drained=new CountDownLatch(1);slow.post(new Runnable(){public void run(){drained.countDown();}});
   if(!drained.await(3,TimeUnit.SECONDS)||!slowUpdates.isEmpty())throw new AssertionError("Slow handler replayed old snapshots");
   manager.removeOnAccountsUpdatedListener(slowListener);manager.removeOnAccountsUpdatedListener(fastListener);
   // Queue a main-thread delivery without dispatching it, then revoke the stream.
   Looper.prepareMainLooper();
   AtomicInteger stale=new AtomicInteger();
   OnAccountsUpdateListener revoked=new OnAccountsUpdateListener(){public void onAccountsUpdated(Account[] values){stale.incrementAndGet();}};
   try{
    manager.addOnAccountsUpdatedListener(revoked,null,true);
    java.lang.reflect.Field watchField=HostAccountListeners.class.getDeclaredField("watch");watchField.setAccessible(true);
    java.lang.reflect.Field lockField=HostAccountListeners.class.getDeclaredField("lock");lockField.setAccessible(true);
    Object lock=lockField.get(null);
    ServiceBrokerClient.AccountWatch watch;synchronized(lock){watch=(ServiceBrokerClient.AccountWatch)watchField.get(null);}
    java.lang.reflect.Field endpointField=watch.getClass().getDeclaredField("endpoint");endpointField.setAccessible(true);
    NativeIpc.shutdown(((ParcelFileDescriptor)endpointField.get(watch)).getFd());
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
    while(true){synchronized(lock){if(watchField.get(null)==null)break;}if(System.nanoTime()>deadline)throw new AssertionError("Revoked account watch retained");Thread.sleep(5);}
    Looper.getMainLooper().shirazDispatchDue(10);
    if(stale.get()!=0)throw new AssertionError("Revoked listener delivered queued state");
    manager.addOnAccountsUpdatedListener(revoked,null,true);
    Looper.getMainLooper().shirazDispatchDue(10);
    if(stale.get()!=1)throw new AssertionError("Listener could not re-register after revocation");
   }finally{manager.removeOnAccountsUpdatedListener(revoked);Looper.shirazShutdownMain();}
   System.out.println("ART_ACCOUNT_LISTENER_BACKPRESSURE_REVOCATION_OK");
  }finally{
   release.countDown();manager.removeOnAccountsUpdatedListener(slowListener);manager.removeOnAccountsUpdatedListener(fastListener);
   slowThread.quit();slowThread.join(3000);if(slowThread.isAlive())throw new AssertionError("Slow callback thread leaked");
  }
 }
 private static Account[] take(BlockingQueue<Account[]> updates)throws Exception{Account[] value=updates.poll(5,TimeUnit.SECONDS);if(value==null)throw new AssertionError("Account callback missing");return value;}
}
