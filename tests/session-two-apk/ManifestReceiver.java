// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.sessionfixture;
import android.content.*;
import android.os.*;
public final class ManifestReceiver extends BroadcastReceiver {
 public static int delivered;
 public void onReceive(Context context,Intent intent){
  if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Receiver main thread");
  if(!"local-manifest".equals(intent.getStringExtra("payload")))throw new AssertionError("Receiver payload");
  IntentFilter filter=new IntentFilter("test.NEVER");
  if(context.registerReceiver(null,filter)!=null)throw new AssertionError("Fabricated sticky broadcast");
  try{context.registerReceiver(new ManifestReceiver(),filter);throw new AssertionError("Manifest receiver registered a receiver");}
  catch(ReceiverCallNotAllowedException expected){}
  try{context.bindService(new Intent(),new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder binder){}public void onServiceDisconnected(ComponentName name){}},Context.BIND_AUTO_CREATE);throw new AssertionError("Manifest receiver bound a service");}
  catch(ReceiverCallNotAllowedException expected){}
  if(context.getApplicationContext()==context)throw new AssertionError("Application context unavailable");
  delivered++;
  PendingResult pending=goAsync();pending.finish();
  System.out.println("SESSION_MANIFEST_RECEIVER_OK");
 }
}
