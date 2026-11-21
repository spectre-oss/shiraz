// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.jobworkfixture;
import android.app.job.*;
import android.content.*;
public final class Worker extends JobService {
 public boolean onStartJob(JobParameters params){
  if(params.getJobId()==1){
   JobScheduler scheduler=(JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
   JobInfo job=new JobInfo.Builder(2,new ComponentName(this,Worker.class)).setMinimumLatency(1).build();
   for(int i=1;i<=2;i++){
    Intent intent=new Intent("fixture.work").putExtra("sequence",i).putExtra("large",Long.MAX_VALUE);
    if(scheduler.enqueue(job,new JobWorkItem(intent))!=JobScheduler.RESULT_SUCCESS)throw new AssertionError("Enqueue failed");
    intent.putExtra("sequence",99);
   }
   System.out.println("JOB_WORK_APK_ENQUEUED");return false;
  }
  if(params.getJobId()!=2)throw new AssertionError("Unexpected job");
  int count=0;JobWorkItem work;
  while((work=params.dequeueWork())!=null){
   Intent intent=work.getIntent();count++;
   if(!"fixture.work".equals(intent.getAction())||intent.getIntExtra("sequence",0)!=count||intent.getLongExtra("large",0)!=Long.MAX_VALUE||work.getDeliveryCount()!=1)throw new AssertionError("Delivery mismatch");
   params.completeWork(work);
  }
  if(count!=2)throw new AssertionError("Missing work");
  System.out.println("JOB_WORK_APK_ACKNOWLEDGED count="+count);return true;
 }
 public boolean onStopJob(JobParameters params){throw new AssertionError("Unexpected stop");}
 public void onDestroy(){System.out.println("JOB_WORK_APK_DESTROYED");}
}
