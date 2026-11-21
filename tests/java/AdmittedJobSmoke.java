// SPDX-License-Identifier: GPL-3.0-only
import android.app.Application;
import android.app.job.*;
import android.content.*;
import android.os.*;
import java.util.Properties;
import shiraz.runtime.AdmittedJob;
import shiraz.runtime.HostServices;

public final class AdmittedJobSmoke {
 static int starts,destroys,stops;static boolean inStart;static Worker worker;static JobParameters saved;static java.util.function.IntConsumer stop;
 static final AdmittedJob.Control control=new AdmittedJob.Control(){public void attach(java.util.function.IntConsumer value){stop=value;}public void detach(){stop=null;}};
 public static final class Worker extends JobService {
  public boolean onStartJob(final JobParameters params){
   starts++;worker=this;saved=params;inStart=true;
   try{
    check("payload".equals(params.getExtras().getString("value")),"extras");
    if(params.getJobId()==1)return false;
    if(params.getJobId()==2){jobFinished(params,true);jobFinished(params,false);return true;}
    if(params.getJobId()==3){new Thread(new Runnable(){public void run(){jobFinished(params,false);}}).start();return true;}
    if(params.getJobId()==4)throw new IllegalStateException("start failure");
    if(params.getJobId()==5){jobFinished(params,false);return true;}
    if(params.getJobId()==10){jobFinished(params,false);stop.accept(JobParameters.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW);return true;}
    if(params.getJobId()>=8){
     final java.util.function.IntConsumer request=stop;
     Thread sender=new Thread(new Runnable(){public void run(){request.accept(JobParameters.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW);request.accept(JobParameters.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW);jobFinished(params,true);}});
     sender.start();try{sender.join();}catch(InterruptedException error){throw new AssertionError(error);}return true;
    }
    if(params.getJobId()==6){new Handler().post(new Runnable(){public void run(){Looper.shirazRequestMainExit();}});return true;}
    throw new AssertionError("unexpected job");
   }finally{inStart=false;}
  }
  public boolean onStopJob(JobParameters params){
   stops++;check(Looper.myLooper()==Looper.getMainLooper()&&!inStart,"stop callback thread/order");
   check(params.getStopReason()==(params.getJobId()>=8?JobParameters.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW:JobParameters.STOP_REASON_SYSTEM_PROCESSING),"stop reason");
   jobFinished(params,true); // Reentrant completion cannot override the stop return value.
   return params.getJobId()!=9;
  }
  public void onDestroy(){check(!inStart,"destroyed within onStartJob");destroys++;if(saved.getJobId()==5)throw new IllegalStateException("destroy failure");}
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static JobInfo job(int id,ComponentName service){PersistableBundle extras=new PersistableBundle();extras.putString("value","payload");return new JobInfo.Builder(id,service).setPersisted(true).setExtras(extras).build();}
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();
  Context context=new ContextWrapper(null){public String getPackageName(){return "org.shiraz.admitted";}public ClassLoader getClassLoader(){return AdmittedJobSmoke.class.getClassLoader();}};
  Properties metadata=new Properties();metadata.setProperty("service.count","1");metadata.setProperty("service.0.name",Worker.class.getName());metadata.setProperty("service.0.permission",JobService.PERMISSION_BIND);
  HostServices host=new HostServices(context,new Application(),metadata);ComponentName service=new ComponentName(context.getPackageName(),Worker.class.getName());
  try{
   check(new JobScheduler(host).schedule(job(1,service))==JobScheduler.RESULT_FAILURE,"local persistence enabled");
   check(new JobScheduler(host).schedule(new JobInfo.Builder(20,service).setRequiresStorageNotLow(true).build())==JobScheduler.RESULT_FAILURE,"standalone storage accepted");
   check(!AdmittedJob.run(host,job(1,service)),"synchronous retry");
   check(destroys==1&&!host.hasWork(),"synchronous cleanup");
   check(AdmittedJob.run(host,job(2,service)),"first completion/retry lost");
   worker.jobFinished(saved,false);Looper.myLooper().shirazDispatchDue(100);
   check(destroys==2&&!host.hasWork(),"late callback repeated cleanup");
   check(!AdmittedJob.run(host,job(3,service)),"async retry");check(destroys==3,"async cleanup");
   for(int id=4;id<=5;id++)try{AdmittedJob.run(host,job(id,service));throw new AssertionError("failure reported success");}catch(IllegalStateException expected){check(expected.getMessage().contains("failure"),"unexpected failure");}
   check(destroys==5&&!host.hasWork(),"failed lifecycle leak");
   int before=starts;
   try{AdmittedJob.run(host,new JobInfo.Builder(7,service).setRequiresCharging(true).build());throw new AssertionError("constraint accepted");}catch(UnsupportedOperationException expected){}
   check(starts==before,"unsupported job started");
   check(AdmittedJob.run(host,job(8,service),control),"stop retry lost");
   check(!AdmittedJob.run(host,job(9,service),control),"stop false ignored");
   check(!AdmittedJob.run(host,job(10,service),control),"late stop replaced completed job");
   check(stop==null&&stops==2&&destroys==8&&!host.hasWork(),"stop detach or duplicate cleanup");
   try{AdmittedJob.run(host,job(6,service));throw new AssertionError("stopped loop reported success");}catch(IllegalStateException expected){check(expected.getMessage().contains("before completion"),"wrong shutdown failure");}
   check(stops==3&&destroys==9&&!host.hasWork(),"interrupted cleanup");
   worker.jobFinished(saved,true); // Late callbacks after the queue has stopped are harmless.
   System.out.println("ADMITTED_JOB_OK");
  }finally{host.close();Looper.shirazShutdownMain();}
 }
}
