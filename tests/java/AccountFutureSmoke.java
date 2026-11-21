// SPDX-License-Identifier: GPL-3.0-only
import android.accounts.*;
import android.accounts.OperationCanceledException;
import android.os.*;
import shiraz.runtime.HostAccountFuture;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class AccountFutureSmoke {
 public static void run()throws Exception{
  HandlerThread callbacks=new HandlerThread("account-future-fixture");callbacks.start();
  try{
   final Handler handler=new Handler(callbacks.getLooper());
   final AtomicInteger closed=new AtomicInteger(),called=new AtomicInteger();final CountDownLatch delivered=new CountDownLatch(1);
   HostAccountFuture<String> success=new HostAccountFuture<String>(new AccountManagerCallback<String>(){public void run(AccountManagerFuture<String> future){
    try{if(Looper.myLooper()!=handler.getLooper()||!"fixture".equals(future.getResult()))throw new AssertionError("Callback thread/result");called.incrementAndGet();}catch(Exception failure){throw new AssertionError(failure);}finally{delivered.countDown();}
   }},handler);
   success.attach(new Closeable(){public void close(){closed.incrementAndGet();}});success.complete("fixture");success.complete("duplicate");success.fail(new IOException("late"));
   if(!delivered.await(3,TimeUnit.SECONDS)||called.get()!=1||closed.get()!=1||success.cancel(true)||!success.isDone()||success.isCancelled())throw new AssertionError("Single terminal result");
   HostAccountFuture<String> canceled=new HostAccountFuture<String>(null,null);if(!canceled.cancel(false))throw new AssertionError("Cancel failed");
   final AtomicInteger lateClose=new AtomicInteger();canceled.attach(new Closeable(){public void close(){lateClose.incrementAndGet();}});
   canceled.complete("late");if(lateClose.get()!=1||!canceled.isCancelled())throw new AssertionError("Late connection not revoked");
   try{canceled.getResult();throw new AssertionError("Canceled result returned");}catch(OperationCanceledException expected){}
   final AtomicInteger effects=new AtomicInteger();
   canceled.completeUsing(new Callable<String>(){public String call(){effects.incrementAndGet();return "late";}});
   success.completeUsing(new Callable<String>(){public String call(){effects.incrementAndGet();return "duplicate";}});
   if(effects.get()!=0)throw new AssertionError("Terminal future ran result side effects");
   final HostAccountFuture<String> committing=new HostAccountFuture<String>(null,null);
   final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
   final AtomicBoolean canceledCommit=new AtomicBoolean(true);
   Thread commit=new Thread(new Runnable(){public void run(){committing.completeUsing(new Callable<String>(){public String call(){entered.countDown();await(release);effects.incrementAndGet();return "committed";}});}});
   Thread cancelCommit=new Thread(new Runnable(){public void run(){canceledCommit.set(committing.cancel(true));}});
   commit.start();if(!entered.await(3,TimeUnit.SECONDS))throw new AssertionError("Completion did not start");
   cancelCommit.start();release.countDown();commit.join(3000);cancelCommit.join(3000);
   if(commit.isAlive()||cancelCommit.isAlive()||canceledCommit.get()||effects.get()!=1||!"committed".equals(committing.getResult()))throw new AssertionError("Completion/cancel ownership");
   HostAccountFuture<String> decodeFailure=new HostAccountFuture<String>(null,null);
   decodeFailure.completeUsing(new Callable<String>(){public String call()throws IOException{throw new IOException("fixture");}});
   try{decodeFailure.getResult();throw new AssertionError("Completion failure lost");}catch(IOException expected){}
   final AtomicInteger timedClose=new AtomicInteger();HostAccountFuture<String> timed=new HostAccountFuture<String>(null,null);
   timed.attach(new Closeable(){public void close(){timedClose.incrementAndGet();}});
   try{timed.getResult(1,TimeUnit.MILLISECONDS);throw new AssertionError("Timeout missing");}catch(OperationCanceledException expected){}
   if(!timed.isCancelled()||timedClose.get()!=1)throw new AssertionError("Timeout cleanup");
   for(Throwable failure:new Throwable[]{new IOException("fixture"),new AuthenticatorException("fixture"),new OperationCanceledException("fixture"),new SecurityException("fixture")}){
    HostAccountFuture<String> failed=new HostAccountFuture<String>(null,null);failed.fail(failure);
    try{failed.getResult();throw new AssertionError("Failure returned result");}catch(Exception actual){if(actual!=failure)throw new AssertionError("Failure type changed");}
   }
   final HostAccountFuture<String> interrupted=new HostAccountFuture<String>(null,null);final AtomicBoolean interruptPreserved=new AtomicBoolean();
   Thread waiter=new Thread(new Runnable(){public void run(){Thread.currentThread().interrupt();try{interrupted.getResult();}catch(OperationCanceledException expected){interruptPreserved.set(Thread.currentThread().isInterrupted());}catch(Exception failure){throw new AssertionError(failure);}}});
   waiter.start();waiter.join(3000);if(waiter.isAlive()||!interrupted.isCancelled()||!interruptPreserved.get())throw new AssertionError("Interrupted wait cleanup");
   for(int i=0;i<8;i++){
    final AtomicInteger releases=new AtomicInteger(),callbacksSeen=new AtomicInteger();final CountDownLatch callbackDone=new CountDownLatch(1),go=new CountDownLatch(1);
    final HostAccountFuture<String> racing=new HostAccountFuture<String>(new AccountManagerCallback<String>(){public void run(AccountManagerFuture<String> future){callbacksSeen.incrementAndGet();callbackDone.countDown();}},handler);
    Thread attach=new Thread(new Runnable(){public void run(){await(go);racing.attach(new Closeable(){public void close(){releases.incrementAndGet();}});}});
    Thread complete=new Thread(new Runnable(){public void run(){await(go);racing.complete("fixture");}});
    Thread cancel=new Thread(new Runnable(){public void run(){await(go);racing.cancel(true);}});
    attach.start();complete.start();cancel.start();go.countDown();attach.join(3000);complete.join(3000);cancel.join(3000);
    if(attach.isAlive()||complete.isAlive()||cancel.isAlive()||!callbackDone.await(3,TimeUnit.SECONDS)||releases.get()!=1||callbacksSeen.get()!=1)throw new AssertionError("Terminal/admission race");
   }
   Looper.prepareMainLooper();try{
    HostAccountFuture<String> main=new HostAccountFuture<String>(null,null);
    try{main.getResult();throw new AssertionError("Main thread blocked");}catch(IllegalStateException expected){}
    main.complete("ready");if(!"ready".equals(main.getResult()))throw new AssertionError("Ready result on main");
   }finally{Looper.shirazShutdownMain();}
  }finally{callbacks.quit();callbacks.join(3000);if(callbacks.isAlive())throw new AssertionError("Callback thread leak");}
 }
 private static void await(CountDownLatch latch){try{latch.await();}catch(InterruptedException failure){throw new AssertionError(failure);}}
}
