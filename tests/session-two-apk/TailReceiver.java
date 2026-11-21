// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.sessionfixture;
import android.content.*;
public final class TailReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){
  final PendingResult pending=goAsync();
  new Thread(new Runnable(){public void run(){
   try{Thread.sleep(500);}catch(InterruptedException error){throw new AssertionError(error);}
   if(!MainActivity.destroyed)throw new AssertionError("Receiver must outlive activity");
   System.out.println("SESSION_MANIFEST_ASYNC_AFTER_ACTIVITY_OK");pending.finish();
  }},"fixture-receiver-tail").start();
 }
}
