// SPDX-License-Identifier: GPL-3.0-only
import android.app.job.JobInfo;
import android.content.ComponentName;
import android.net.Uri;
import android.net.NetworkRequest;
import android.net.NetworkCapabilities;
public final class JobConstraintSnapshotSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  JobInfo.Builder builder=new JobInfo.Builder(1,new ComponentName("test.jobs","test.jobs.Worker"));
  JobInfo empty=builder.build();
  check(!empty.isRequireCharging()&&!empty.isRequireBatteryNotLow()&&!empty.isRequireDeviceIdle()&&!empty.isRequireStorageNotLow(),"defaults");
  check(empty.getTriggerContentUris()==null,"absent triggers");
  JobInfo.TriggerContentUri first=new JobInfo.TriggerContentUri(Uri.parse("content://test/first"),1);
  JobInfo snapshot=builder.setRequiresCharging(true).setRequiresBatteryNotLow(true).setRequiresDeviceIdle(true).setRequiresStorageNotLow(true).addTriggerContentUri(first).build();
  check(snapshot.isRequireCharging()&&snapshot.isRequireBatteryNotLow()&&snapshot.isRequireDeviceIdle()&&snapshot.isRequireStorageNotLow(),"constraint snapshot");
  check(!snapshot.shirazExecutionSupported(),"unsupported constraints admitted");
  builder.setRequiresCharging(false).setRequiresBatteryNotLow(false).setRequiresDeviceIdle(false).setRequiresStorageNotLow(false);
  builder.addTriggerContentUri(new JobInfo.TriggerContentUri(Uri.parse("content://test/second"),0));
  check(snapshot.isRequireCharging()&&snapshot.isRequireBatteryNotLow()&&snapshot.isRequireDeviceIdle()&&snapshot.isRequireStorageNotLow(),"builder changed snapshot");
  check(snapshot.getTriggerContentUris().length==1&&snapshot.getTriggerContentUris()[0]==first,"trigger snapshot");
  snapshot.getTriggerContentUris()[0]=null;
  check(snapshot.getTriggerContentUris()[0]==first,"returned array mutation");
  check(builder.build().getTriggerContentUris().length==2,"later builder state");
  for(int bit=0;bit<4;bit++){
   JobInfo.Builder single=new JobInfo.Builder(bit,new ComponentName("test.jobs","test.jobs.Worker"));
   if(bit==0)single.setRequiresCharging(true);if(bit==1)single.setRequiresBatteryNotLow(true);if(bit==2)single.setRequiresDeviceIdle(true);if(bit==3)single.setRequiresStorageNotLow(true);
   JobInfo value=single.build();check(!value.shirazExecutionSupported(),"single unsupported constraint admitted");
   check(value.isRequireCharging()==(bit==0)&&value.isRequireBatteryNotLow()==(bit==1)&&value.isRequireDeviceIdle()==(bit==2)&&value.isRequireStorageNotLow()==(bit==3),"independent bits");
  }
  check(empty.getRequiredNetwork()==null,"default network");
  for(int type=0;type<=4;type++){
   JobInfo job=new JobInfo.Builder(20+type,empty.getService()).setRequiredNetworkType(type).build();
   check(job.getNetworkType()==type,"legacy network mapping");
   check(job.shirazHostExecutionSupported()==(type==0),"network admitted without enforcement");
   if(type!=0){
    NetworkRequest request=job.getRequiredNetwork();
    check(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&request.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),"legacy network requires validated Internet");
    check(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED),"trusted network requirement");
    check(!request.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)&&!request.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED),"legacy VPN or restricted network excluded");
   }
  }
  NetworkRequest.Builder filter=new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
  JobInfo.Builder networkBuilder=new JobInfo.Builder(30,empty.getService()).setRequiredNetwork(filter.build());
  JobInfo network=networkBuilder.build();
  filter.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
  networkBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR);
  check(network.getNetworkType()==JobInfo.NETWORK_TYPE_UNMETERED,"custom filter collapsed");
  check(network.getRequiredNetwork().hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!network.getRequiredNetwork().hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),"network snapshot mutated");
  check(networkBuilder.build().getNetworkType()==JobInfo.NETWORK_TYPE_CELLULAR,"type must replace custom request");
  check(networkBuilder.setRequiredNetwork(null).build().shirazHostExecutionSupported(),"null request clears constraint");
  check(!networkBuilder.setRequiredNetwork(new NetworkRequest.Builder().build()).build().shirazHostExecutionSupported(),"empty request still requires a network");
  System.out.println("JOB_CONSTRAINT_SNAPSHOT_OK");
 }
}
