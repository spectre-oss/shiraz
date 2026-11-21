// SPDX-License-Identifier: GPL-3.0-only
import android.app.Application;
import android.app.job.*;
import android.content.*;
import android.os.*;
import java.util.Properties;
import shiraz.runtime.HostServices;

public final class JobLocalDeadlineSmoke {
 static JobParameters latest;static Worker worker;static int starts;
 public static final class Worker extends JobService {
  public boolean onStartJob(JobParameters params){latest=params;worker=this;starts++;return true;}
  public boolean onStopJob(JobParameters params){return false;}
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void drain(){for(int i=0;i<8;i++)Looper.myLooper().shirazDispatchDue(100);}
 static void complete(){worker.jobFinished(latest,false);drain();}
 public static void main(String[] args)throws Exception {
  Looper.prepareMainLooper();
  Context context=new ContextWrapper(null){public String getPackageName(){return "test.localdeadline";}public ClassLoader getClassLoader(){return JobLocalDeadlineSmoke.class.getClassLoader();}};
  Properties metadata=new Properties();metadata.setProperty("service.count","1");metadata.setProperty("service.0.name",Worker.class.getName());metadata.setProperty("service.0.permission",JobService.PERMISSION_BIND);
  HostServices host=new HostServices(context,new Application(),metadata);JobScheduler scheduler=new JobScheduler(host);
  ComponentName service=new ComponentName(context.getPackageName(),Worker.class.getName());
  try{
   check(scheduler.schedule(new JobInfo.Builder(1,service).build())==1,"no deadline schedule");drain();check(!latest.isOverrideDeadlineExpired(),"absent deadline");complete();
   scheduler.schedule(new JobInfo.Builder(2,service).setOverrideDeadline(Long.MAX_VALUE).build());drain();check(!latest.isOverrideDeadlineExpired(),"future deadline overflow");complete();
   scheduler.schedule(new JobInfo.Builder(3,service).setOverrideDeadline(30).build());
   // Hold the main queue past the deadline: state must be captured at execution,
   // rather than when the entry was enqueued.
   SystemClock.sleep(80);drain();check(latest.isOverrideDeadlineExpired(),"queued deadline missed");complete();
   JobInfo retry=new JobInfo.Builder(4,service).setOverrideDeadline(0).setBackoffCriteria(10000,JobInfo.BACKOFF_POLICY_LINEAR).build();
   scheduler.schedule(retry);drain();check(latest.isOverrideDeadlineExpired(),"zero deadline");
   JobParameters first=latest;long requested=SystemClock.uptimeMillis();int before=starts;
   worker.jobFinished(first,true);drain();check(starts==before,"retry bypassed backoff");
   while(starts==before&&SystemClock.uptimeMillis()-requested<15000){SystemClock.sleep(10);drain();}
   check(starts==before+1&&SystemClock.uptimeMillis()-requested>=10000,"retry timing");
   check(!latest.isOverrideDeadlineExpired()&&first.isOverrideDeadlineExpired(),"retry inherited deadline or mutated old params");
   complete();check(!scheduler.shirazHasWork()&&!host.hasWork(),"job leaked");
   System.out.println("JOB_LOCAL_DEADLINE_OK");
  }finally{scheduler.shirazClose();host.close();Looper.shirazShutdownMain();}
 }
}
