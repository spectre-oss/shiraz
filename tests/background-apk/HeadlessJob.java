// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.backgroundfixture;
import android.app.job.JobService;
import android.app.job.JobParameters;
import android.os.Handler;
public final class HeadlessJob extends JobService {
 private String action;
 public boolean onStartJob(final JobParameters params){
  action=params.getExtras().getString("action");
  System.out.println("HEADLESS_JOB_STARTED");
  if(action.startsWith("deadline-")){
   if(params.isOverrideDeadlineExpired()!=action.equals("deadline-true"))throw new AssertionError("Deadline launch state");
   System.out.println("APK_DEADLINE_STATE_OK "+params.isOverrideDeadlineExpired());
  }
  if("submit".equals(action)||"submit-transient".equals(action)||action.startsWith("submit-storage")){
   android.app.job.JobScheduler scheduler=(android.app.job.JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
   android.os.PersistableBundle extras=new android.os.PersistableBundle();extras.putString("action","submit-storage-deadline".equals(action)?"storage-deadline-retry":"submit-storage-retry".equals(action)?"storage-retry":"public-complete");extras.putString("recovery_key",java.util.UUID.randomUUID().toString().replace("-",""));extras.putLong("precision",Long.MAX_VALUE);
   android.app.job.JobInfo.Builder childBuilder=new android.app.job.JobInfo.Builder(18,new android.content.ComponentName(this,HeadlessJob.class))
       .setPersisted(!"submit-transient".equals(action)).setRequiresStorageNotLow(action.startsWith("submit-storage")).setBackoffCriteria(10000,android.app.job.JobInfo.BACKOFF_POLICY_EXPONENTIAL).setMinimumLatency(250).setExtras(extras);
   if("submit-storage-deadline".equals(action))childBuilder.setOverrideDeadline(2000);
   android.app.job.JobInfo child=childBuilder.build();
   if(scheduler.schedule(child)!=android.app.job.JobScheduler.RESULT_SUCCESS)throw new AssertionError("Public submission failed");
   android.app.job.JobInfo saved=scheduler.getPendingJob(18);
   if(saved==null||saved.isRequireStorageNotLow()!=action.startsWith("submit-storage")||saved.getExtras().getLong("precision")!=Long.MAX_VALUE)throw new AssertionError("Public query failed");
   System.out.println("APK_PUBLIC_JOB_SUBMITTED");
  }
  if("public-complete".equals(action)){
   if(params.getJobId()!=18||params.getExtras().getLong("precision")!=Long.MAX_VALUE)throw new AssertionError("Public delivery mismatch");
   System.out.println("APK_PUBLIC_JOB_EXECUTED");
  }
  if("storage-deadline-retry".equals(action)){
   String key=params.getExtras().getString("recovery_key");
   if(key==null||!key.matches("[0-9a-f]{32}"))throw new AssertionError("Deadline retry key");
   try{
    boolean first=new java.io.File(getFilesDir(),"job-storage-deadline-"+key).createNewFile();
    if(params.isOverrideDeadlineExpired()!=first)throw new AssertionError("Deadline reused or missing");
    if(first){
     System.out.println("APK_STORAGE_DEADLINE_FIRST_EXPIRED");
     new Handler().post(new Runnable(){public void run(){jobFinished(params,true);}});return true;
    }
    System.out.println("APK_STORAGE_DEADLINE_RETRY_NOT_EXPIRED");
   }catch(java.io.IOException error){throw new IllegalStateException(error);}
  }
  if("storage-retry".equals(action)){
   if(params.isOverrideDeadlineExpired())throw new AssertionError("Unexpected retry deadline");
   String key=params.getExtras().getString("recovery_key");
   if(key==null||!key.matches("[0-9a-f]{32}"))throw new AssertionError("Storage retry key");
   try{
    if(new java.io.File(getFilesDir(),"job-storage-"+key).createNewFile()){
     System.out.println("APK_STORAGE_RETRY_FIRST");return true;
    }
    System.out.println("APK_STORAGE_RETRY_COMPLETE");
   }catch(java.io.IOException error){throw new IllegalStateException(error);}
  }
  if("crash-retry".equals(action)){
   String key=params.getExtras().getString("recovery_key");
   if(key==null||!key.matches("[0-9a-f]{32}"))throw new AssertionError("Recovery key");
   try{
    java.io.File marker=new java.io.File(getFilesDir(),"job-crash-"+key);
    if(marker.createNewFile()){
     System.out.println("APK_CRASH_RETRY_FIRST");return true;
    }
    System.out.println("APK_CRASH_RETRY_COMPLETE");
   }catch(java.io.IOException error){throw new IllegalStateException(error);}
  }
  if("throw".equals(action))throw new IllegalStateException("JOB_EXPECTED_START_FAILURE");
  if("hang".equals(action)||"stop-true".equals(action)||"stop-false".equals(action)||"stop-hang".equals(action))return true;
  new Handler().post(new Runnable(){public void run(){jobFinished(params,"retry".equals(action));}});
  return true;
 }
 public boolean onStopJob(JobParameters params){
  System.out.println("HEADLESS_JOB_STOPPED reason="+params.getStopReason());
  if(action.startsWith("stop-")||"storage-retry".equals(action)){
   if(params.getStopReason()!=9||android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new AssertionError("Storage stop callback");
   System.out.println("APK_STORAGE_STOP_CALLBACK_OK");
  }
  if("stop-hang".equals(action)){
   System.out.println("APK_STORAGE_STOP_HANG_ENTERED");System.out.flush();
   for(;;)try{Thread.sleep(1000);}catch(InterruptedException ignored){}
  }
  return "stop-true".equals(action)||"storage-retry".equals(action);
 }
 public void onDestroy(){System.out.println("HEADLESS_JOB_DESTROYED");if("destroy".equals(action))throw new IllegalStateException("JOB_EXPECTED_DESTROY_FAILURE");}
}
