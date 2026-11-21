// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.sessionfixture;
import android.content.*;
import android.os.*;
public final class LiveReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){
  if(MainActivity.current==null||MainActivity.destroyed||Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Not the existing activity process");
  if(!"org.shiraz.LIVE_BROADCAST".equals(intent.getAction()))throw new AssertionError("Live action");
  if(!new ComponentName(context.getPackageName(),LiveReceiver.class.getName()).equals(intent.getComponent())||!context.getPackageName().equals(intent.getPackage()))throw new AssertionError("Foreign manifest component not resolved");
  MainActivity.liveReceived=true;
  final PendingResult pending=goAsync();
  new Handler(Looper.getMainLooper()).postDelayed(new Runnable(){public void run(){System.out.println("SESSION_LIVE_RECEIVER_FINISHED "+android.os.Process.myPid());pending.finish();}},200);
  MainActivity.current.finishAfterLive();
 }
}
