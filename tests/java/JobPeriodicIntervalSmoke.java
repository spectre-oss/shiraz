// SPDX-License-Identifier: GPL-3.0-only
import android.app.Application;
import android.app.job.*;
import android.content.*;
import android.os.*;
import java.util.Properties;
import shiraz.runtime.HostServices;

/** Deliberately waits the real platform minimum period. No clock overrides. */
public final class JobPeriodicIntervalSmoke {
 static int starts,destroys;static long first,second;
 public static final class Worker extends JobService {
  public boolean onStartJob(JobParameters params){
   long now=SystemClock.uptimeMillis();starts++;
   if(starts==1)first=now;else if(starts==2)second=now;else throw new AssertionError("Unexpected extra period");
   System.out.println("PERIODIC_INTERVAL_START count="+starts+" uptime_ms="+now);System.out.flush();return false;
  }
  public boolean onStopJob(JobParameters params){throw new AssertionError("Completed job was stopped");}
  public void onDestroy(){destroys++;System.out.println("PERIODIC_INTERVAL_DESTROY count="+destroys);System.out.flush();}
 }
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();
  Context context=new ContextWrapper(null){public String getPackageName(){return "org.shiraz.periodic";}public ClassLoader getClassLoader(){return JobPeriodicIntervalSmoke.class.getClassLoader();}};
  Properties metadata=new Properties();metadata.setProperty("service.count","1");metadata.setProperty("service.0.name",Worker.class.getName());metadata.setProperty("service.0.permission",JobService.PERMISSION_BIND);
  final HostServices host=new HostServices(context,new Application(),metadata);final JobScheduler scheduler=new JobScheduler(host);
  try{
   JobInfo job=new JobInfo.Builder(1,new ComponentName(context.getPackageName(),Worker.class.getName())).setPeriodic(JobInfo.getMinPeriodMillis()).build();
   if(scheduler.schedule(job)!=JobScheduler.RESULT_SUCCESS)throw new AssertionError("Periodic admission failed");
   new Handler(Looper.getMainLooper()).postDelayed(new Runnable(){public void run(){throw new AssertionError("Second period timed out");}},930000);
   Looper.getMainLooper().shirazLoopWhile(new java.util.function.BooleanSupplier(){public boolean getAsBoolean(){return starts<2||host.hasWork();}});
   long elapsed=second-first;
   if(starts!=2||destroys!=2||elapsed<900000||elapsed>930000||scheduler.getPendingJob(1)!=job)throw new AssertionError("Periodic recurrence/lifetime: "+elapsed);
   scheduler.cancelAll();if(scheduler.shirazHasWork()||host.hasWork())throw new AssertionError("Periodic cancellation leaked");
   System.out.println("PERIODIC_INTERVAL_ELAPSED_MS="+elapsed);
   System.out.println("JOB_PERIODIC_REAL_INTERVAL_OK");
  }finally{scheduler.shirazClose();host.close();Looper.shirazShutdownMain();}
 }
}
