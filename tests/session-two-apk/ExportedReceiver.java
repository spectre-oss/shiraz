// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.sessionfixture;
import android.content.*;
public final class ExportedReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){
  if("org.shiraz.CROSS_BROADCAST".equals(intent.getAction())){
   if(!"java-broadcast".equals(intent.getStringExtra("payload")))throw new AssertionError("Broadcast snapshot");
   final PendingResult pending=goAsync();
   new Thread(new Runnable(){public void run(){try{Thread.sleep(400);}catch(InterruptedException error){throw new AssertionError(error);}System.out.println("EXPORTED_RECEIVER_ASYNC_FINISHED");pending.finish();}}).start();
  }
  System.out.println("EXPORTED_RECEIVER_DELIVERED");
 }
}
