// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.servicefixture;
import android.app.IntentService;
import android.content.Intent;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
public final class IntentQueue extends IntentService {
 private final CountDownLatch secondQueued=new CountDownLatch(1);
 private int handled;
 public IntentQueue(){super("shared-queue");}
 public int onStartCommand(Intent intent,int flags,int id){
  super.onStartCommand(intent,flags,id);
  if("second".equals(intent.getAction()))secondQueued.countDown();
  return START_REDELIVER_INTENT;
 }
 protected void onHandleIntent(Intent intent){
  if(Looper.myLooper()==Looper.getMainLooper())throw new AssertionError("Main-thread work");
  try{if(!secondQueued.await(20,TimeUnit.SECONDS))throw new AssertionError("Second start missing");}
  catch(InterruptedException failure){throw new AssertionError(failure);}
  String expected=handled==0?"first":"second";
  if(handled>=2||!expected.equals(intent.getAction()))throw new AssertionError("Intent order");
  handled++;
  System.out.println("INTENT_QUEUE_HANDLED "+expected);System.out.flush();
 }
 public void onDestroy(){
  super.onDestroy();
  if(handled!=2)throw new AssertionError("Premature destroy");
  System.out.println("INTENT_QUEUE_DESTROYED");System.out.flush();
 }
}
