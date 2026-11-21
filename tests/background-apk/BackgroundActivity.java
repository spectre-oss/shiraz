// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.backgroundfixture;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
public final class BackgroundActivity extends Activity {
    static volatile boolean destroyed;
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        android.os.PowerManager.WakeLock wake=getSystemService(android.os.PowerManager.class).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"declared-background");
        wake.acquire();if(!wake.isHeld())throw new AssertionError("Declared APK wake lock denied");
        try{
            android.os.WorkSource attribution=android.os.WorkSource.class.getConstructor(int.class).newInstance(android.os.Process.myUid());
            try{wake.setWorkSource(attribution);throw new AssertionError("APK wake attribution accepted without privilege");}catch(SecurityException expected){}
        }catch(ReflectiveOperationException error){throw new AssertionError("Cannot create fixture work attribution",error);}
        if(!wake.isHeld())throw new AssertionError("Denied attribution update released APK wake lock");wake.release();wake.acquire();wake.release();
        if(checkPermission("android.permission.WAKE_LOCK",android.os.Process.myPid(),android.os.Process.myUid()+1)!=android.content.pm.PackageManager.PERMISSION_DENIED)throw new AssertionError("Wake lock permission granted to other UID");
        java.io.File handoff=new java.io.File(getFilesDir(),"background-predecessors-finished");
        if(handoff.exists()&&!handoff.delete())throw new AssertionError("Cannot clear previous fixture handoff");
        if(checkSelfPermission("android.permission.FOREGROUND_SERVICE")!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new AssertionError("Declared foreground service permission denied");
        for(String permission:new String[]{"android.permission.FOREGROUND_SERVICE_DATA_SYNC","android.permission.FOREGROUND_SERVICE_LOCATION","android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK","android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING","android.permission.FOREGROUND_SERVICE_CAMERA","android.permission.FOREGROUND_SERVICE_MICROPHONE"}){
            if(checkSelfPermission(permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new AssertionError("Declared type permission denied: "+permission);
            if(checkPermission(permission,android.os.Process.myPid(),android.os.Process.myUid()+1)!=android.content.pm.PackageManager.PERMISSION_DENIED)throw new AssertionError("Type permission granted to another UID");
        }
        if(checkSelfPermission("android.permission.FOREGROUND_SERVICE_HEALTH")!=android.content.pm.PackageManager.PERMISSION_DENIED)throw new AssertionError("Undeclared type permission granted");
        if(checkSelfPermission("android.permission.FOREGROUND_SERVICE_UNKNOWN")!=android.content.pm.PackageManager.PERMISSION_DENIED)throw new AssertionError("Unknown foreground permission granted");
        System.out.println("BACKGROUND_APK_UID="+android.os.Process.myUid());
        String inbox=System.getenv("SHIRAZ_LAUNCHER_INBOX");
        if(inbox!=null)System.out.println("BACKGROUND_APK_INBOX="+inbox);
        View view = new View(this); view.setBackgroundColor(0xff123456); setContentView(view);
        startService(new Intent(this, BackgroundWorker.class));
        android.app.job.JobScheduler jobs=(android.app.job.JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
        int result=jobs.schedule(new android.app.job.JobInfo.Builder(1,
            new android.content.ComponentName(this,BackgroundJob.class)).setMinimumLatency(1500).build());
        if(result!=android.app.job.JobScheduler.RESULT_SUCCESS)throw new AssertionError("job schedule failed");
        new Handler().postDelayed(new Runnable() { public void run() { finish(); } }, 200);
    }
    protected void onDestroy() {
        super.onDestroy(); destroyed = true;
        System.out.println("BACKGROUND_APK_ACTIVITY_DESTROYED");
    }
}
