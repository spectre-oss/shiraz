// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.sessionfixture;
import android.content.*;
import android.os.*;
public final class RemoteReceiver extends BroadcastReceiver {
 private static final java.util.concurrent.CountDownLatch arrivals=new java.util.concurrent.CountDownLatch(2);
 public void onReceive(Context context,Intent intent){
  if(Looper.myLooper()!=Looper.getMainLooper()||MainActivity.current!=null)throw new AssertionError("Secondary receiver ran in activity process");
  if(!"org.shiraz.sessiontwo:receiver".equals(android.app.Application.getProcessName()))throw new AssertionError("Secondary process name");
  if(intent.getIntExtra("pid",-1)==android.os.Process.myPid()||intent.getIntExtra("uid",-1)!=android.os.Process.myUid())throw new AssertionError("Secondary process identity");
  if(!"secondary-snapshot".equals(intent.getStringExtra("payload")))throw new AssertionError("Secondary snapshot");
  if(!new ComponentName(context.getPackageName(),RemoteReceiver.class.getName()).equals(intent.getComponent()))throw new AssertionError("Manifest component was not resolved");
  arrivals.countDown();
  final PendingResult pending=goAsync();
  new Thread(new Runnable(){public void run(){try{if(!arrivals.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Consecutive broadcasts did not share receiver process");}catch(InterruptedException error){throw new AssertionError(error);}System.out.println("SESSION_SECONDARY_RECEIVER_FINISHED");pending.finish();}}).start();
 }
}
