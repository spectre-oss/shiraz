// SPDX-License-Identifier: GPL-3.0-only
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class HandlerShutdownSmoke {
 public static void run()throws Exception{
  HandlerThread worker=new HandlerThread("resource-owner-fixture");worker.start();
  Handler handler=new Handler(worker.getLooper());
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  AtomicBoolean closed=new AtomicBoolean(),read=new AtomicBoolean();AtomicInteger queued=new AtomicInteger();
  AtomicReference<Throwable> failure=new AtomicReference<Throwable>();
  handler.post(new Runnable(){public void run(){synchronized(worker){entered.countDown();try{release.await();if(closed.get())throw new AssertionError("Resource closed during callback");read.set(true);}catch(Throwable error){failure.set(error);}}}});
  if(!entered.await(3,TimeUnit.SECONDS))throw new AssertionError("Worker not entered");
  handler.post(new Runnable(){public void run(){queued.incrementAndGet();}});
  long timeoutStarted=System.nanoTime();
  Thread.currentThread().interrupt();
  try{HandlerThread.shirazShutdown(50);throw new AssertionError("Stuck worker reported cleanup success");}
  catch(IllegalStateException expected){if(!expected.getMessage().contains("timed out"))throw expected;}
  finally{if(!Thread.interrupted())throw new AssertionError("Shutdown lost interrupt status");}
  long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-timeoutStarted);
  if(elapsed<40||elapsed>2000||!worker.isAlive()||read.get()||closed.get())throw new AssertionError("Timeout ownership/deadline: "+elapsed);
  System.out.println("ART_HANDLER_SHUTDOWN_TIMEOUT_OK elapsed_ms="+elapsed);
  Thread closer=new Thread(new Runnable(){public void run(){try{HandlerThread.shirazShutdown(3000);closed.set(true);}catch(Throwable error){failure.set(error);}}});
  closer.start();
  try{
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
   while(handler.post(new Runnable(){public void run(){}})){if(System.nanoTime()>deadline)throw new AssertionError("Queue did not stop accepting");Thread.sleep(1);}
   if(closed.get())throw new AssertionError("Shutdown did not wait for callback");
   try{new HandlerThread("late-fixture").start();throw new AssertionError("Late worker admitted");}catch(IllegalStateException expected){}
  }finally{release.countDown();closer.join(4000);}
  if(closer.isAlive()||worker.isAlive()||!closed.get()||!read.get()||queued.get()!=0||failure.get()!=null)throw new AssertionError("Handler shutdown ownership",failure.get());
  HandlerThread.shirazShutdown(1000);
  System.out.println("ART_HANDLER_SHUTDOWN_OK");
 }
}
