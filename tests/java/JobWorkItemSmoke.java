// SPDX-License-Identifier: GPL-3.0-only
import android.app.job.JobParameters;
import android.app.job.JobWorkItem;
import android.content.Intent;
import android.os.Parcel;
import android.os.PersistableBundle;

public final class JobWorkItemSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static JobWorkItem copy(JobWorkItem item){Parcel p=Parcel.obtain();try{item.writeToParcel(p,0);p.setDataPosition(0);return JobWorkItem.CREATOR.createFromParcel(p);}finally{p.recycle();}}
 public static void main(String[] args){
  Intent intent=new Intent("work.action").setPackage("sample.app").putExtra("sequence",37);
  JobWorkItem original=new JobWorkItem(intent);original.setWorkId(7);original.bumpDeliveryCount();original.bumpDeliveryCount();
  final JobWorkItem delivered=copy(original);
  intent.putExtra("sequence",99);
  check(delivered.getIntent().getIntExtra("sequence",0)==37&&"work.action".equals(delivered.getIntent().getAction()),"parcel isolates payload");
  check(delivered.getWorkId()==7&&delivered.getDeliveryCount()==2,"delivery metadata round trip");
  check(copy(new JobWorkItem((Intent)null)).getIntent()==null,"absent intent round trip");
  final JobParameters params=new JobParameters(9,new PersistableBundle());
  check(params.dequeueWork()==null,"ordinary job has no queued work");
  try{params.completeWork(delivered);throw new AssertionError("completion without owner");}catch(IllegalArgumentException expected){}
  final int[] state={0};
  params.shirazWork(new JobParameters.Work(){
   public JobWorkItem dequeue(JobParameters value){check(value==params,"dequeue parameter identity");if(state[0]!=0)return null;state[0]=1;return delivered;}
   public boolean complete(JobParameters value,int id){check(value==params,"completion parameter identity");if(state[0]!=1||id!=7)return false;state[0]=2;return true;}
  });
  check(params.dequeueWork()==delivered&&params.dequeueWork()==null,"callback delivery");
  JobWorkItem wrong=new JobWorkItem((Intent)null);wrong.setWorkId(8);
  try{params.completeWork(wrong);throw new AssertionError("foreign work accepted");}catch(IllegalArgumentException expected){}
  params.completeWork(delivered);check(state[0]==2,"completion callback");
  try{params.completeWork(delivered);throw new AssertionError("duplicate completion accepted");}catch(IllegalArgumentException expected){}
  for(int presence:new int[]{-1,2}){Parcel p=Parcel.obtain();try{p.writeInt(presence);p.setDataPosition(0);try{JobWorkItem.CREATOR.createFromParcel(p);throw new AssertionError("invalid presence");}catch(IllegalArgumentException expected){}}finally{p.recycle();}}
  Parcel bad=Parcel.obtain();try{bad.writeInt(0);bad.writeInt(-1);bad.writeInt(1);bad.setDataPosition(0);try{JobWorkItem.CREATOR.createFromParcel(bad);throw new AssertionError("invalid delivery count");}catch(IllegalArgumentException expected){}}finally{bad.recycle();}
  System.out.println("JOB_WORK_ITEM_OK");
 }
}
