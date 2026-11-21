// SPDX-License-Identifier: GPL-3.0-only
import android.app.Application;
import android.app.job.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.HostServices;
import java.util.Properties;
public final class JobExecutionSmoke {
 static int starts,stops,destroys,lastStopReason;static Worker worker;static JobParameters latest;
 public static final class Worker extends JobService {
  public void onCreate(){worker=this;}
  public boolean onStartJob(JobParameters params){
   check(params.getStopReason()==JobParameters.STOP_REASON_UNDEFINED,"new job stop reason");
   starts++;latest=params;
   if(params.getJobId()==1){check("original".equals(params.getExtras().getString("payload")),"extras mutated after build");return false;}
   return true;
  }
  public boolean onStopJob(JobParameters params){stops++;lastStopReason=params.getStopReason();check(lastStopReason==JobParameters.STOP_REASON_CANCELLED_BY_APP||lastStopReason==JobParameters.STOP_REASON_SYSTEM_PROCESSING,"stop reason missing");return false;}
  public void onDestroy(){destroys++;}
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void drain(){for(int i=0;i<8;i++)Looper.myLooper().shirazDispatchDue(100);}
 static void verifyPeriodic(HostServices host,ComponentName service)throws Exception{
  long period=JobInfo.getMinPeriodMillis(),flex=JobInfo.getMinFlexMillis();
  JobInfo.Builder builder=new JobInfo.Builder(101,service).setPeriodic(1,1);
  JobInfo clamped=builder.build();builder.setPeriodic(2*period,2*period);
  check(clamped.isPeriodic()&&clamped.getIntervalMillis()==period&&clamped.getFlexMillis()==flex,"periodic minimum/immutability");
  check(new JobInfo.Builder(101,service).setPeriodic(period,Long.MAX_VALUE).build().getFlexMillis()==period,"flex exceeds period");
  check(new JobInfo.Builder(101,service).setPeriodic(Long.MAX_VALUE,1).build().getFlexMillis()==Long.MAX_VALUE/20,"flex percent overflow");
  try{new JobInfo.Builder(101,service).setPeriodic(period).setMinimumLatency(0).build();throw new AssertionError("periodic latency accepted");}catch(IllegalArgumentException expected){}
  try{new JobInfo.Builder(101,service).setOverrideDeadline(0).setPeriodic(period).build();throw new AssertionError("periodic deadline accepted");}catch(IllegalArgumentException expected){}
  try{new JobInfo.Builder(101,service).setPeriodic(period).addTriggerContentUri(new JobInfo.TriggerContentUri(android.net.Uri.parse("content://test/events"),0)).build();throw new AssertionError("periodic trigger accepted");}catch(IllegalArgumentException expected){}
  JobScheduler periodic=new JobScheduler(host);
  try{
   int before=starts;
   check(periodic.schedule(clamped)==JobScheduler.RESULT_SUCCESS,"periodic admission");drain();check(starts==before,"ran before flex window");periodic.cancel(101);
   JobInfo full=new JobInfo.Builder(101,service).setPeriodic(period).build();
   check(periodic.schedule(full)==JobScheduler.RESULT_SUCCESS,"full flex admission");drain();check(starts==before+1,"first periodic run missing");
   Worker old=worker;JobParameters oldParams=latest;
   old.jobFinished(oldParams,false);drain();
   check(periodic.getPendingJob(101)==full&&periodic.shirazHasWork()&&!host.hasWork(),"successful period not rescheduled");
   drain();check(starts==before+1,"periodic tight loop");old.jobFinished(oldParams,true);drain();check(starts==before+1,"stale periodic completion");
   periodic.cancel(101);drain();check(!periodic.shirazHasWork(),"periodic cancellation");
   periodic.schedule(full);drain();old=worker;oldParams=latest;
   JobInfo replacement=new JobInfo.Builder(101,service).setMinimumLatency(60000).build();periodic.schedule(replacement);drain();
   old.jobFinished(oldParams,false);drain();check(periodic.getPendingJob(101)==replacement,"old period replaced new job");periodic.cancel(101);
   PersistableBundle extras=new PersistableBundle();extras.putString("payload","original");
   JobInfo synchronous=new JobInfo.Builder(1,service).setExtras(extras).setPeriodic(period).build();periodic.schedule(synchronous);drain();
   check(periodic.getPendingJob(1)==synchronous&&!host.hasWork(),"synchronous period completion");periodic.cancelAll();
   check(!periodic.shirazHasWork(),"cancel all periodic work");
   System.out.println("JOB_PERIODIC_LIFECYCLE_OK");
  }finally{periodic.shirazClose();}
 }
 static void verifyBackoff(HostServices host,ComponentName service)throws Exception{
  JobInfo defaults=new JobInfo.Builder(99,service).build();
  check(defaults.getInitialBackoffMillis()==30000&&defaults.getBackoffPolicy()==JobInfo.BACKOFF_POLICY_EXPONENTIAL,"backoff defaults");
  JobInfo.Builder builder=new JobInfo.Builder(99,service).setPeriodic(JobInfo.getMinPeriodMillis()).setBackoffCriteria(1,JobInfo.BACKOFF_POLICY_LINEAR);
  JobInfo linear=builder.build();builder.setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_EXPONENTIAL);
  check(linear.getInitialBackoffMillis()==10000&&linear.getBackoffPolicy()==JobInfo.BACKOFF_POLICY_LINEAR,"backoff minimum/immutability");
  try{new JobInfo.Builder(99,service).setBackoffCriteria(10000,42);throw new AssertionError("invalid policy");}catch(IllegalArgumentException expected){}
  try{new JobInfo.Builder(99,service).setBackoffCriteria(10000,0).setRequiresDeviceIdle(true).build();throw new AssertionError("idle/backoff conflict");}catch(IllegalArgumentException expected){}
  java.lang.reflect.Method delay=JobScheduler.class.getDeclaredMethod("retryDelay",JobInfo.class,int.class);delay.setAccessible(true);
  check(((Long)delay.invoke(null,linear,2))==30000,"linear retry growth");
  check(((Long)delay.invoke(null,defaults,2))==120000,"exponential retry growth");
  check(((Long)delay.invoke(null,linear,Integer.MAX_VALUE))==JobInfo.MAX_BACKOFF_DELAY_MILLIS,"linear saturation");
  check(((Long)delay.invoke(null,defaults,Integer.MAX_VALUE))==JobInfo.MAX_BACKOFF_DELAY_MILLIS,"exponential saturation");
  JobInfo huge=new JobInfo.Builder(99,service).setBackoffCriteria(Long.MAX_VALUE,0).build();
  check(((Long)delay.invoke(null,huge,0))==JobInfo.MAX_BACKOFF_DELAY_MILLIS,"initial overflow");
  JobScheduler retry=new JobScheduler(host);
  try{
   int before=starts;check(retry.schedule(linear)==JobScheduler.RESULT_SUCCESS,"custom backoff rejected");drain();check(starts==before+1,"first retry job launch");
   long requested=SystemClock.uptimeMillis();Worker original=worker;JobParameters originalParams=latest;
   original.jobFinished(originalParams,true);drain();check(retry.getPendingJob(99)==linear&&!host.hasWork(),"retry admission or service cleanup");
   original.jobFinished(originalParams,true);drain();
   while(starts==before+1&&SystemClock.uptimeMillis()-requested<15000){drain();Thread.sleep(10);}
   long elapsed=SystemClock.uptimeMillis()-requested;
   check(starts==before+2&&elapsed>=10000&&elapsed<15000,"retry deadline: "+elapsed);
   worker.jobFinished(latest,false);drain();check(retry.getPendingJob(99)==linear&&!host.hasWork(),"periodic retry did not return to interval scheduling");retry.cancel(99);drain();
   check(!retry.shirazHasWork()&&!host.hasWork(),"retry cancellation leaked");
   System.out.println("JOB_CUSTOM_BACKOFF_OK elapsed_ms="+elapsed);
  }finally{retry.shirazClose();}
 }
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();
  Context context=new ContextWrapper(null){public String getPackageName(){return "org.shiraz.jobs";}public ClassLoader getClassLoader(){return JobExecutionSmoke.class.getClassLoader();}};
  Properties metadata=new Properties();metadata.setProperty("service.count","1");metadata.setProperty("service.0.name",Worker.class.getName());metadata.setProperty("service.0.permission",JobService.PERMISSION_BIND);
  HostServices host=new HostServices(context,new Application(),metadata);JobScheduler scheduler=new JobScheduler(host);
  ComponentName service=new ComponentName(context.getPackageName(),Worker.class.getName());
  try{
   PersistableBundle extras=new PersistableBundle();extras.putString("payload","original");
   JobInfo first=new JobInfo.Builder(1,service).setExtras(extras).build();extras.putString("payload","changed");
   check(scheduler.schedule(first)==JobScheduler.RESULT_SUCCESS,"schedule failed");check(starts==0,"inline callback");drain();
   check(starts==1&&destroys==1&&!scheduler.shirazHasWork()&&!host.hasWork(),"synchronous completion leaked");
   JobInfo delayed=new JobInfo.Builder(3,service).setMinimumLatency(60000).build();
   scheduler.schedule(delayed);drain();check(starts==1,"early job");scheduler.cancel(3);check(!scheduler.shirazHasWork(),"cancel pending");
   scheduler.schedule(new JobInfo.Builder(2,service).build());drain();check(starts==2&&host.hasWork(),"async job missing");
   final Worker saved=worker;final JobParameters params=latest;
   Thread complete=new Thread(new Runnable(){public void run(){saved.jobFinished(params,false);}});complete.start();complete.join();drain();
   check(destroys==2&&!scheduler.shirazHasWork(),"worker completion leaked");check(params.getStopReason()==JobParameters.STOP_REASON_UNDEFINED,"normal completion got stop reason");
   saved.jobFinished(params,false);drain();check(destroys==2,"duplicate completion destroyed twice");
   scheduler.schedule(new JobInfo.Builder(4,service).build());scheduler.schedule(new JobInfo.Builder(5,service).build());drain();
   check(starts==4,"concurrent jobs missing");scheduler.cancel(4);drain();check(stops==1&&destroys==2,"shared service destroyed early");check(lastStopReason==JobParameters.STOP_REASON_CANCELLED_BY_APP,"cancel reason");
   scheduler.cancel(5);drain();check(stops==2&&destroys==3&&!host.hasWork(),"cancel active lifecycle");
   check(scheduler.schedule(new JobInfo.Builder(6,service).setRequiresCharging(true).build())==0,"unsupported charging accepted");
   check(scheduler.schedule(new JobInfo.Builder(6,service).setPersisted(true).build())==0,"unsupported persistence accepted");
   check(scheduler.schedule(new JobInfo.Builder(6,service).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build())==0,"unsupported network accepted");
   check(new JobScheduler().schedule(first)==0,"unattached scheduler faked success");
   scheduler.schedule(new JobInfo.Builder(7,service).build());drain();scheduler.shirazClose();
   check(stops==3&&destroys==4&&!host.hasWork(),"close failed to stop running job");check(lastStopReason==JobParameters.STOP_REASON_SYSTEM_PROCESSING,"session shutdown reason");
   JobScheduler cancelThenClose=new JobScheduler(host);cancelThenClose.schedule(new JobInfo.Builder(202,service).build());drain();cancelThenClose.cancel(202);cancelThenClose.shirazClose();
   check(lastStopReason==JobParameters.STOP_REASON_CANCELLED_BY_APP&&!host.hasWork(),"shutdown replaced queued cancellation reason");drain();
   verifyBackoff(host,service);
   verifyPeriodic(host,service);
   System.out.println("JOB_EXECUTION_OK");
  }finally{scheduler.shirazClose();host.close();Looper.shirazShutdownMain();}
 }
}
