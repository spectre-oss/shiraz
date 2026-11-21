// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.servicefixture;
import android.app.IntentService;
import android.content.Intent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
public final class Redelivery extends IntentService {
 private final CountDownLatch secondQueued=new CountDownLatch(1),finish=new CountDownLatch(1);
 private volatile boolean replayed;
 public Redelivery(){super("redelivery-test");setIntentRedelivery(true);}
 public int onStartCommand(Intent intent,int flags,int id){
  if(intent==null||!"original-extra".equals(intent.getStringExtra("payload")))throw new AssertionError("Lost retained intent");
  if(flags==START_FLAG_REDELIVERY){
   if(id!=2||!"second".equals(intent.getAction()))throw new AssertionError("Wrong work redelivered");
   replayed=true;System.out.println("REDELIVERY_RESTORED_ID_2");System.out.flush();
  }else if(flags!=0)throw new AssertionError("Wrong flags");
  int result=super.onStartCommand(intent,flags,id);
  if("second".equals(intent.getAction()))secondQueued.countDown();
  if("finish".equals(intent.getAction())){if(!replayed||id!=3)throw new AssertionError("New start sequence");finish.countDown();}
  intent.putExtra("payload","mutated-by-callback");
  return result;
 }
 private static void await(CountDownLatch latch){try{if(!latch.await(30,TimeUnit.SECONDS))throw new AssertionError("Test coordination timeout");}catch(InterruptedException error){throw new AssertionError(error);}}
 protected void onHandleIntent(Intent intent){
  if("first".equals(intent.getAction())){if(replayed)throw new AssertionError("Finished work replayed");await(secondQueued);}
  else if("second".equals(intent.getAction()))await(finish);
  else if(!"finish".equals(intent.getAction()))throw new AssertionError("Unexpected action");
  System.out.println("REDELIVERY_HANDLED "+intent.getAction());System.out.flush();
 }
 public void onDestroy(){super.onDestroy();System.out.println("REDELIVERY_DESTROYED");System.out.flush();}
}
