// SPDX-License-Identifier: GPL-3.0-only
import android.app.Application;
import android.app.job.*;
import android.content.*;
import android.os.Looper;
import java.util.Properties;
import shiraz.runtime.HostServices;
import shiraz.runtime.ServiceBrokerClient;

public final class JobBrokerSmoke {
 static int starts,destroys;
 public static final class Worker extends JobService {
  public boolean onStartJob(JobParameters params){starts++;return false;}
  public boolean onStopJob(JobParameters params){throw new AssertionError("Unexpected stop");}
  public void onDestroy(){destroys++;}
 }
 public static void run(ServiceBrokerClient client)throws Exception {run(client,false);}
 public static void run(ServiceBrokerClient client,boolean recover)throws Exception {
  Looper.prepareMainLooper();
  java.lang.reflect.Field field=Class.forName("shiraz.runtime.RuntimeBroker").getDeclaredField("client");field.setAccessible(true);field.set(null,client);
  Context context=new ContextWrapper(null){public String getPackageName(){return "test.jobs";}public ClassLoader getClassLoader(){return JobBrokerSmoke.class.getClassLoader();}};
  Properties metadata=new Properties();metadata.setProperty("service.count","2");
  metadata.setProperty("service.0.process","test.jobs:worker");
  metadata.setProperty("service.0.name",Worker.class.getName());metadata.setProperty("service.0.permission",JobService.PERMISSION_BIND);
  metadata.setProperty("service.1.name","test.jobs.GuestOnly");metadata.setProperty("service.1.permission",JobService.PERMISSION_BIND);
  HostServices host=new HostServices(context,new Application(),metadata);JobScheduler scheduler=new JobScheduler(host);
  try{
   ComponentName component=new ComponentName("test.jobs",Worker.class.getName());
   if(recover){
    JobInfo saved=scheduler.getPendingJob(-18);
    if(saved==null||!saved.isPersisted()||saved.getMinLatencyMillis()!=60000||scheduler.getAllPendingJobs().size()!=2)throw new AssertionError("Fresh process did not recover durable job");
    JobInfo transientJob=scheduler.getPendingJob(-19);if(transientJob==null||transientJob.isPersisted())throw new AssertionError("Transient job lost across process exit");
    scheduler.cancel(-19);
    scheduler.cancel(-18);if(scheduler.getPendingJob(-18)!=null)throw new AssertionError("Recovered cancellation");
    System.out.println("ART_PUBLIC_DURABLE_JOB_PROCESS_RECOVERY_OK");return;
   }
   JobInfo durable=new JobInfo.Builder(-17,component).setPersisted(true).setMinimumLatency(60000).build();
   ServiceBrokerClient.JobMutation stored=client.schedulePersistedJob(durable,"test.jobs");
   if(stored.revision==null||stored.cleanupPending)throw new AssertionError("Persisted admission");
   int[] ids=client.listPersistedJobIds();if(ids.length!=1||ids[0]!=-17)throw new AssertionError("Persisted listing");
   JobInfo recovered=client.getPersistedJob(-17,"test.jobs");
   if(recovered==null||!recovered.isPersisted()||recovered.getMinLatencyMillis()!=60000||!component.equals(recovered.getService()))throw new AssertionError("Persisted retrieval");
   ServiceBrokerClient.JobMutation canceled=client.cancelPersistedJob(-17);
   if(!canceled.removed||canceled.cleanupPending||client.listPersistedJobIds().length!=0||client.getPersistedJob(-17,"test.jobs")!=null||client.cancelPersistedJob(-17).removed)throw new AssertionError("Persisted cancellation");
   System.out.println("ART_JOB_BROKER_PERSISTED_CLIENT_OK");
   if(scheduler.schedule(durable)!=JobScheduler.RESULT_SUCCESS||scheduler.shirazHasWork())throw new AssertionError("Public durable submission");
   if(scheduler.getPendingJob(-17)==null||scheduler.getAllPendingJobs().size()!=1)throw new AssertionError("Public durable query");
   scheduler.shirazClose();
   scheduler=new JobScheduler(host);
   if(scheduler.getPendingJob(-17)==null)throw new AssertionError("Teardown deleted durable job");
   JobInfo replacement=new JobInfo.Builder(-17,component).setMinimumLatency(60000).build();
   if(scheduler.schedule(replacement)!=JobScheduler.RESULT_SUCCESS||client.getPersistedJob(-17,"test.jobs")==null||scheduler.getPendingJob(-17).isPersisted())throw new AssertionError("Durable to local replacement");
   if(scheduler.schedule(durable)!=JobScheduler.RESULT_SUCCESS||scheduler.shirazHasWork()||!scheduler.getPendingJob(-17).isPersisted())throw new AssertionError("Local to durable replacement");
   scheduler.cancelAll();
   if(!scheduler.getAllPendingJobs().isEmpty()||client.getPersistedJob(-17,"test.jobs")!=null)throw new AssertionError("Public durable cancelAll");
   System.out.println("ART_PUBLIC_DURABLE_JOB_LIFECYCLE_OK");
   JobInfo workJob=new JobInfo.Builder(45,component).setMinimumLatency(60000).build();
   if(scheduler.enqueue(workJob,new JobWorkItem(new Intent("test.work").putExtra("sequence",41)))!=JobScheduler.RESULT_SUCCESS)throw new AssertionError("Work enqueue failed");
   if(scheduler.enqueue(workJob,new JobWorkItem(new Intent("test.work").putExtra("sequence",42)))!=JobScheduler.RESULT_SUCCESS)throw new AssertionError("Work append failed");
   if(scheduler.getPendingJob(45)==null)throw new AssertionError("Queued job missing");
   try{scheduler.enqueue(workJob,new JobWorkItem(new Intent("test.work").addFlags(1)));throw new AssertionError("URI grants silently accepted");}catch(UnsupportedOperationException expected){}
   scheduler.cancel(45);
   System.out.println("ART_PUBLIC_JOB_WORK_ENQUEUE_OK");
   JobInfo pending=new JobInfo.Builder(1,component).setMinimumLatency(60000).build();
   if(scheduler.schedule(pending)!=JobScheduler.RESULT_SUCCESS)throw new AssertionError("Admission failed");
   try{scheduler.schedule(new JobInfo.Builder(1,new ComponentName("test.jobs","test.jobs.GuestOnly")).build());throw new AssertionError("Guest-only declaration admitted");}catch(SecurityException expected){}
   if(scheduler.getPendingJob(1)==null||scheduler.getPendingJob(1).getMinLatencyMillis()!=60000)throw new AssertionError("Denied replacement removed pending job");
   for(JobInfo rejected:new JobInfo[]{
       new JobInfo.Builder(1,component).setRequiresCharging(true).build(),
       new JobInfo.Builder(1,component).setRequiresBatteryNotLow(true).build(),
       new JobInfo.Builder(1,component).setRequiresDeviceIdle(true).build(),
       new JobInfo.Builder(1,component).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build(),
       new JobInfo.Builder(1,component).addTriggerContentUri(new JobInfo.TriggerContentUri(android.net.Uri.parse("content://test/items"),0)).build()}){
    if(scheduler.schedule(rejected)!=JobScheduler.RESULT_FAILURE||scheduler.getPendingJob(1).getMinLatencyMillis()!=60000)throw new AssertionError("Unsupported replacement altered stored job");
   }
   scheduler.cancelAll();
   scheduler.schedule(new JobInfo.Builder(2,component).build());
   for(int i=0;i<8;i++)Looper.myLooper().shirazDispatchDue(100);
   if(starts!=0||destroys!=0||scheduler.shirazHasWork()||host.hasWork())throw new AssertionError("Admitted lifecycle");
   scheduler.cancelAll();
   scheduler.schedule(new JobInfo.Builder(-19,component).setMinimumLatency(60000).build());
   if(scheduler.schedule(new JobInfo.Builder(-18,component).setPersisted(true).setMinimumLatency(60000).build())!=JobScheduler.RESULT_SUCCESS)throw new AssertionError("Durable handoff");
   client.close();field.set(null,null);
   Looper.myLooper().shirazDispatchDue(100);
   if(starts!=0||scheduler.shirazHasWork()||host.hasWork())throw new AssertionError("Denied dispatch leaked work");
   try{scheduler.schedule(pending);throw new AssertionError("Closed broker used local fallback");}catch(IllegalStateException expected){}
   System.out.println("ART_JOB_BROKER_ADMISSION_OK");
  }finally{scheduler.shirazClose();host.close();field.set(null,null);Looper.shirazShutdownMain();}
 }
}
