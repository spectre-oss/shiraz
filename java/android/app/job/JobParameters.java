// SPDX-License-Identifier: GPL-3.0-only
package android.app.job;
import android.net.Network;
import android.net.Uri;
import android.os.PersistableBundle;
public class JobParameters {
 public static final int STOP_REASON_UNDEFINED=0,STOP_REASON_CANCELLED_BY_APP=1,STOP_REASON_SYSTEM_PROCESSING=14,STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW=9;
 private volatile int stopReason=STOP_REASON_UNDEFINED;
 public interface Completion {void finish(JobParameters params,boolean retry);}
 private Completion completion;
 public interface Work {JobWorkItem dequeue(JobParameters params);boolean complete(JobParameters params,int workId);}
 private Work work;
 public synchronized void shirazWork(Work value){if(value==null)throw new NullPointerException("work");if(work!=null)throw new IllegalStateException("Job work callback already attached");work=value;}
 public JobWorkItem dequeueWork(){Work callback; synchronized(this){callback=work;}return callback==null?null:callback.dequeue(this);}
 public void completeWork(JobWorkItem item){
  if(item==null)throw new NullPointerException("work");Work callback;synchronized(this){callback=work;}
  if(callback==null||!callback.complete(this,item.getWorkId()))throw new IllegalArgumentException("Job work is not active");
 }
 public void shirazCompletion(Completion value){completion=value;}
 public void shirazFinish(boolean retry){if(completion==null)throw new IllegalStateException("Job not scheduled");completion.finish(this,retry);}
 private final boolean deadlineExpired;
 private final int id;private final PersistableBundle extras;
 public JobParameters(int id,PersistableBundle extras){this(id,extras,false);}
 public JobParameters(int id,PersistableBundle extras,boolean deadlineExpired){this.id=id;this.extras=extras;this.deadlineExpired=deadlineExpired;}
 public boolean isOverrideDeadlineExpired(){return deadlineExpired;}
 public int getJobId(){return id;}
 public PersistableBundle getExtras(){return extras;}
 public Network getNetwork(){return null;}
 public int getStopReason(){return stopReason;}
 public void shirazStopReason(int reason){if(reason!=STOP_REASON_CANCELLED_BY_APP&&reason!=STOP_REASON_SYSTEM_PROCESSING&&reason!=STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW)throw new IllegalArgumentException("Unsupported job stop reason");stopReason=reason;}
 public String[] getTriggeredContentAuthorities(){return null;}
 public Uri[] getTriggeredContentUris(){return null;}
}
