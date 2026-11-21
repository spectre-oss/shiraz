// SPDX-License-Identifier: GPL-3.0-only
package android.app.job;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import shiraz.runtime.HostServices;
/** Broker-owned jobs; standalone sessions use local jobs. Unsupported constraints fail explicitly. */
public class JobScheduler {
 public static final int RESULT_FAILURE=0,RESULT_SUCCESS=1;
 private final Map<Integer,Entry> pending=new LinkedHashMap<Integer,Entry>();
 private final java.util.HashSet<Entry> stopping=new java.util.HashSet<Entry>();
 private final HostServices host;
 private final Handler main;
 private boolean closed;
 public JobScheduler(){host=null;main=null;}
 public JobScheduler(HostServices host){if(host==null)throw new NullPointerException("host");this.host=host;main=new Handler(Looper.getMainLooper());}
 public JobScheduler forNamespace(String namespace){throw new UnsupportedOperationException("Job namespaces are not implemented");}
 private final class Entry implements Runnable,JobParameters.Completion {
  final JobInfo info;JobParameters params;final long submittedAt;final int attempts;boolean running;int stopReason=JobParameters.STOP_REASON_SYSTEM_PROCESSING;
  Entry(JobInfo info,int attempts){this.info=info;this.attempts=attempts;submittedAt=SystemClock.elapsedRealtime();}
  public void run(){
   synchronized(JobScheduler.this){
    if(closed||pending.get(info.getId())!=this)return;
    boolean expired=attempts==0&&info.shirazHasDeadline()&&SystemClock.elapsedRealtime()-submittedAt>=info.getMaxExecutionDelayMillis();
    params=new JobParameters(info.getId(),info.getExtras(),expired);params.shirazCompletion(this);running=true;
   }
   try{if(!host.runJob(info,params))finish(params,false);}
   catch(RuntimeException|Error error){synchronized(JobScheduler.this){if(pending.get(info.getId())==this)pending.remove(info.getId());}throw error;}
  }
  public void finish(JobParameters value,final boolean retry){
   if(value!=params)throw new IllegalArgumentException("Job parameters identity");
   Runnable completion=new Runnable(){public void run(){
    synchronized(JobScheduler.this){if(closed||pending.get(info.getId())!=Entry.this||!running)return;pending.remove(info.getId());running=false;}
    host.finishJob(params,false);
    if(retry||info.isPeriodic())synchronized(JobScheduler.this){
     if(!closed&&!pending.containsKey(info.getId())){
      long delay=retry?retryDelay(info,attempts):info.getIntervalMillis();
      int nextAttempt=retry?(attempts==Integer.MAX_VALUE?attempts:attempts+1):0;
      submit(info,delay,nextAttempt);
     }
    }
   }};
   // Always queue completion: jobFinished may be called inside onStartJob.
   if(!main.post(completion))throw new IllegalStateException("Job main queue stopped");
  }
 }
 private static long retryDelay(JobInfo info,int attempts){
  long multiplier=info.getBackoffPolicy()==JobInfo.BACKOFF_POLICY_LINEAR?(long)attempts+1:(attempts>=31?Long.MAX_VALUE:1L<<attempts);
  long initial=info.getInitialBackoffMillis(),limit=JobInfo.MAX_BACKOFF_DELAY_MILLIS;
  return initial>limit/multiplier?limit:initial*multiplier;
 }
 private int submit(JobInfo job,long delay,int attempts){
  Entry entry=new Entry(job,attempts);
  if(!main.postDelayed(entry,delay))return RESULT_FAILURE;
  pending.put(job.getId(),entry);return RESULT_SUCCESS;
 }
 public synchronized int schedule(JobInfo job){
  if(job==null)throw new NullPointerException("job");
  if(closed)throw new IllegalStateException("Job session closed");
  if(host==null||(host.hasJobBroker()?!job.shirazHostExecutionSupported():!job.shirazExecutionSupported()||job.isPersisted()))return RESULT_FAILURE;
  host.validateJob(job);
  java.util.HashSet<Integer> ids=new java.util.HashSet<Integer>(pending.keySet());
  for(int id:host.persistedJobIds())ids.add(id);
  if(!ids.contains(job.getId())&&ids.size()>=100)return RESULT_FAILURE;
  if(host.hasJobBroker()){
   host.persistJob(job); // A committed write stays successful even if old worker cleanup is pending.
   cancelLocal(job.getId());return RESULT_SUCCESS;
  }
  cancelLocal(job.getId());
  return submit(job,job.isPeriodic()?job.getIntervalMillis()-job.getFlexMillis():Math.min(job.getMinLatencyMillis(),job.getMaxExecutionDelayMillis()),0);
 }
 public synchronized int enqueue(JobInfo job,JobWorkItem work){
  if(job==null||work==null)throw new NullPointerException("job work");
  if(closed)throw new IllegalStateException("Job session closed");
  if(job.isPersisted()||job.isPeriodic())throw new IllegalArgumentException("Intent work requires a nonpersisted one-off job");
  if(host==null||!host.hasJobBroker()||!job.shirazHostExecutionSupported())return RESULT_FAILURE;
  host.validateJob(job);host.enqueueJob(job,work);return RESULT_SUCCESS;
 }
 public synchronized void cancel(int id){
  if(closed)throw new IllegalStateException("Job session closed");
  if(host!=null)host.cancelPersistedJob(id);
  cancelLocal(id);
 }
 private void cancelLocal(int id){
  final Entry entry=pending.remove(id);if(entry==null)return;
  main.removeCallbacks(entry);
  if(entry.running){
   entry.stopReason=JobParameters.STOP_REASON_CANCELLED_BY_APP;stopping.add(entry);
   if(!main.post(new Runnable(){public void run(){synchronized(JobScheduler.this){if(!stopping.remove(entry))return;}host.finishJob(entry.params,entry.stopReason);}}))throw new IllegalStateException("Job main queue stopped");
  }
 }
 public synchronized void cancelAll(){
  if(closed)throw new IllegalStateException("Job session closed");
  if(host!=null)host.cancelAllSharedJobs();
  for(Integer id:new ArrayList<Integer>(pending.keySet()))cancelLocal(id);
 }
 public synchronized List<JobInfo> getAllPendingJobs(){
  if(closed)throw new IllegalStateException("Job session closed");
  Map<Integer,JobInfo> jobs=new LinkedHashMap<Integer,JobInfo>();
  if(host!=null)for(int id:host.persistedJobIds()){JobInfo job=host.persistedJob(id);if(job!=null)jobs.put(id,job);}
  for(Entry entry:pending.values())jobs.put(entry.info.getId(),entry.info);
  return new ArrayList<JobInfo>(jobs.values());
 }
 public synchronized JobInfo getPendingJob(int id){
  if(closed)throw new IllegalStateException("Job session closed");
  Entry entry=pending.get(id);return entry==null?(host==null?null:host.persistedJob(id)):entry.info;
 }
 public synchronized boolean shirazHasWork(){return !closed&&!pending.isEmpty();}
 public void shirazClose(){
  if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Job teardown main thread");
  List<Entry> entries;synchronized(this){if(closed)return;closed=true;entries=new ArrayList<Entry>(pending.values());entries.addAll(stopping);stopping.clear();pending.clear();}
  for(Entry entry:entries){main.removeCallbacks(entry);if(entry.running)host.finishJob(entry.params,entry.stopReason);}
 }
}
