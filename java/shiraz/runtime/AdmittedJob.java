// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.os.Handler;
import android.os.Looper;

/** One host-admitted invocation. Admission, deadlines and recurrence belong to the host.
 * This is not a scheduling API and does not authorize persistence or launch a process.
 */
public final class AdmittedJob {
 private AdmittedJob() {}
 /** Private host control hook; implementations must detach their listener on close. */
 public interface Control {void attach(java.util.function.IntConsumer stop);void detach();default void stopped(int reason){}default void bindWork(JobParameters params){}}
 public static boolean run(HostServices host,JobInfo job){return run(host,job,null);}
 public static boolean run(HostServices host,JobInfo job,Control control){return run(host,job,control,false);}
 public static boolean run(HostServices host,JobInfo job,Control control,boolean deadlineExpired){
  if(host==null||job==null)throw new NullPointerException();
  if(Looper.myLooper()==null||Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Job main thread");
  if(control==null?!job.shirazExecutionSupported():!job.shirazHostExecutionSupported())throw new UnsupportedOperationException("Job execution constraints unsupported");
  Invocation invocation=new Invocation(host,job,control,deadlineExpired);
  try{
   if(control!=null)control.attach(new java.util.function.IntConsumer(){public void accept(int reason){invocation.stop(reason);}});
   if(control!=null)control.bindWork(invocation.params);
   if(!host.runJob(job,invocation.params))invocation.finish(invocation.params,false);
   Looper.myLooper().shirazLoopWhile(invocation);
   if(!invocation.completed)throw new IllegalStateException("Job loop stopped before completion");
   return invocation.retry;
  }finally{try{if(control!=null)control.detach();}finally{invocation.close();}}
 }
 private static final class Invocation implements JobParameters.Completion,java.util.function.BooleanSupplier {
  final Control control;final HostServices host;final JobParameters params;final Handler main=new Handler(Looper.getMainLooper());
  boolean closed,queued,completed,retry;Runnable completion;
  Invocation(HostServices host,JobInfo job,Control control,boolean deadlineExpired){this.control=control;this.host=host;params=new JobParameters(job.getId(),job.getExtras(),deadlineExpired);params.shirazCompletion(this);}
  public synchronized boolean getAsBoolean(){return !closed;}
  public synchronized void finish(JobParameters value,final boolean wantsRetry){
   if(value!=params)throw new IllegalArgumentException("Job parameters identity");
   if(closed||queued)return;
   completion=new Runnable(){public void run(){
    synchronized(Invocation.this){if(closed)return;closed=true;}
    // Never destroy the service inside its onStartJob callback. A failure here
    // propagates instead of reporting successful completion to the host.
    host.finishJob(params,false);
    retry=wantsRetry;completed=true;
   }};
   if(!main.post(completion))throw new IllegalStateException("Job main queue stopped");
   queued=true;
  }
  synchronized void stop(final int reason){
   if(reason!=JobParameters.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW&&reason!=JobParameters.STOP_REASON_SYSTEM_PROCESSING)throw new IllegalArgumentException("Unsupported host job stop reason");
   if(closed||queued)return;
   completion=new Runnable(){public void run(){
    synchronized(Invocation.this){if(closed)return;closed=true;}
    retry=host.stopJob(params,reason);if(control!=null)control.stopped(reason);completed=true;
   }};
   if(!main.post(completion))throw new IllegalStateException("Job main queue stopped");
   queued=true;
  }
  void close(){
   boolean stop;synchronized(this){stop=!closed;closed=true;if(completion!=null)main.removeCallbacks(completion);}
   if(stop)host.finishJob(params,JobParameters.STOP_REASON_SYSTEM_PROCESSING);
  }
 }
}
