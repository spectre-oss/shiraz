// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.backgroundfixture;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
public final class BackgroundWorker extends Service {
    static volatile boolean destroyed;
    public IBinder onBind(Intent intent) { return null; }
    public int onStartCommand(Intent intent, int flags, int id) {
        final Handler handler = new Handler();
        final boolean location = "1".equals(System.getenv("SHIRAZ_BACKGROUND_LOCATION_TEST"));
        for(int type:new int[]{64,128,192}){
            try{promote(type);throw new AssertionError("Capture type accepted without runtime permission");}
            catch(SecurityException expected){}
            if(getForegroundServiceType()!=0||((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).getActiveNotifications().length!=0)throw new AssertionError("Denied capture promotion mutated state");
        }
        System.out.println("BACKGROUND_APK_CAPTURE_PROMOTION_DENIED");
        if(location)promote(8);
        handler.postDelayed(new Runnable() {
            int attempts;
            public void run() {
                if (!BackgroundActivity.destroyed) {
                    if (++attempts > 200) throw new AssertionError("activity did not finish");
                    handler.postDelayed(this, 50); return;
                }
                System.out.println("BACKGROUND_APK_WORK_AFTER_ACTIVITY");
                if(location)checkLocation(handler);else stopSelf();
            }
        }, 300);
        return START_NOT_STICKY;
    }
    private void promote(int type){
        android.app.NotificationManager manager=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new android.app.NotificationChannel("location","Test location",2));
        startForeground(1,new android.app.Notification.Builder(this,"location").setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("Mock location test").build(),type);
        if(getForegroundServiceType()!=type)throw new AssertionError("Active service type mismatch");
    }
    private void checkLocation(final Handler handler){
        final android.location.LocationManager manager=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
        manager.requestSingleUpdate("gps",new android.location.LocationListener(){
            int calls;
            public void onLocationChanged(android.location.Location fix){
                if(!BackgroundActivity.destroyed||!fix.isFromMockProvider()||fix.getLatitude()!=1||fix.getLongitude()!=2)throw new AssertionError("Windowless mock location evidence");
                if(++calls==1){
                    System.out.println("BACKGROUND_APK_LOCATION_AFTER_ACTIVITY");
                    stopForeground(false);
                    android.app.NotificationManager notifications=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                    if(notifications.getActiveNotifications().length!=1)throw new AssertionError("Legacy notification not retained");
                    stopForeground(true);
                    if(notifications.getActiveNotifications().length!=0)throw new AssertionError("Retained notification removal ignored");
                    System.out.println("BACKGROUND_APK_RETAINED_NOTIFICATION_REMOVED");
                    manager.requestSingleUpdate("gps",this,android.os.Looper.getMainLooper());
                    handler.postDelayed(new Runnable(){public void run(){
                        if(calls!=1)throw new AssertionError("Demoted service received location");
                        System.out.println("BACKGROUND_APK_LOCATION_DEMOTED");promote(1);
                        handler.postDelayed(new Runnable(){public void run(){
                            if(calls!=1)throw new AssertionError("Data-sync service received location");
                            System.out.println("BACKGROUND_APK_LOCATION_WRONG_TYPE_BLOCKED");promote(8);
                        }},1200);
                    }},1200);
                }else if(calls==2){
                    System.out.println("BACKGROUND_APK_LOCATION_RESUMED");
                    stopForeground(false);stopForeground(STOP_FOREGROUND_DETACH);stopForeground(true);
                    System.out.println("BACKGROUND_APK_NOTIFICATION_DETACHED");stopSelf();
                }
                else throw new AssertionError("Repeated single location fix");
            }
            public void onStatusChanged(String p,int s,android.os.Bundle b){}
            public void onProviderEnabled(String p){}
            public void onProviderDisabled(String p){}
        },android.os.Looper.getMainLooper());
    }
    public void onDestroy() {
        destroyed=true;
        if(BackgroundActivity.destroyed)try{
            if(!new java.io.File(getFilesDir(),"background-predecessors-finished").createNewFile())throw new AssertionError("Duplicate fixture handoff");
        }catch(java.io.IOException failure){throw new AssertionError(failure);}
        System.out.println("BACKGROUND_APK_SERVICE_DESTROYED");
    }
}
