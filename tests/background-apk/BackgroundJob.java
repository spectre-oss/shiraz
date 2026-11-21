// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.backgroundfixture;
import android.app.job.JobService;
import android.app.job.JobParameters;
import android.os.Handler;
public final class BackgroundJob extends JobService {
    public boolean onStartJob(final JobParameters params) {
        final Handler handler=new Handler();
        handler.post(new Runnable(){
            int attempts;
            public void run(){
                // Registered jobs run in another process: static activity/service flags are not shared.
                java.io.File handoff=new java.io.File(getFilesDir(),"background-predecessors-finished");
                if(!handoff.isFile()){
                    if(++attempts>200)throw new AssertionError("background predecessors did not finish");
                    handler.postDelayed(this,50);return;
                }
                if("1".equals(System.getenv("SHIRAZ_BACKGROUND_LOCATION_TEST"))){
                    android.app.NotificationManager notifications=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                    android.service.notification.StatusBarNotification[] active=notifications.getActiveNotifications();
                    if(active.length!=1||active[0].getId()!=1)throw new AssertionError("Detached notification lost after service destruction");
                    if((active[0].getNotification().flags&android.app.Notification.FLAG_FOREGROUND_SERVICE)!=0)throw new AssertionError("Detached notification still foreground");
                    notifications.cancel(1);
                    if(notifications.getActiveNotifications().length!=0)throw new AssertionError("Explicit notification cancellation failed");
                    System.out.println("BACKGROUND_APK_DETACHED_NOTIFICATION_CLEANED");
                }
                if(!handoff.delete())throw new AssertionError("Cannot consume fixture handoff");
                System.out.println("BACKGROUND_APK_JOB_FINISHED");
                jobFinished(params,false);
            }
        });
        return true;
    }
    public boolean onStopJob(JobParameters params){return false;}
    public void onDestroy(){
        super.onDestroy();
        System.out.println("BACKGROUND_APK_JOB_DESTROYED");
    }
}
