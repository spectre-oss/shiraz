// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.backgroundfixture;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
public final class HeadlessReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){
  if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Receiver main thread");
  if(!"org.shiraz.backgroundfixture:events".equals(Application.getProcessName()))throw new AssertionError("Receiver process");
  if("throw".equals(intent.getAction()))throw new IllegalStateException("RECEIVER_EXPECTED_FAILURE");
  final PendingResult pending=goAsync();
  if("timeout".equals(intent.getAction()))return;
  new Thread(new Runnable(){public void run(){
   try{Thread.sleep(100);}catch(InterruptedException failure){throw new AssertionError(failure);}
   System.out.println("HEADLESS_RECEIVER_ASYNC_FINISHED");pending.finish();
  }}).start();
 }
}
