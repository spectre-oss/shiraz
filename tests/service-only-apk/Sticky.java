// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.servicefixture;
public final class Sticky extends android.app.Service {
 public android.os.IBinder onBind(android.content.Intent intent){return null;}
 public int onStartCommand(android.content.Intent intent,int flags,int id){
  if(intent==null){if(flags!=0||id!=1)throw new AssertionError("Sticky restart arguments");System.out.println("STICKY_AUTOMATIC_NULL");}
  System.out.println("STICKY_STARTED "+id);return START_STICKY;
 }
}
