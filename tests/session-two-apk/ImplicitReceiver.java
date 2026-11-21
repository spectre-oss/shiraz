// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.sessionfixture;
import android.content.*;
import android.os.*;
public final class ImplicitReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){
  if(Looper.myLooper()!=Looper.getMainLooper()||MainActivity.current!=null)throw new AssertionError("Implicit receiver process");
  if(!"org.shiraz.sessiontwo:implicit".equals(android.app.Application.getProcessName()))throw new AssertionError("Implicit process name");
  if(!new ComponentName(context.getPackageName(),ImplicitReceiver.class.getName()).equals(intent.getComponent())||!context.getPackageName().equals(intent.getPackage())||!intent.hasCategory("org.shiraz.SECONDARY_CATEGORY"))throw new AssertionError("Resolved manifest routing");
  if(intent.getIntExtra("pid",-1)==android.os.Process.myPid()||intent.getIntExtra("uid",-1)!=android.os.Process.myUid())throw new AssertionError("Implicit identity");
  if(!"secondary-snapshot".equals(intent.getStringExtra("payload")))throw new AssertionError("Implicit snapshot");
  final PendingResult pending=goAsync();
  new Thread(new Runnable(){public void run(){System.out.println("SESSION_COLD_IMPLICIT_RECEIVER_OK");pending.finish();}}).start();
 }
}
