// SPDX-License-Identifier: GPL-3.0-only
package android.app.job;
import android.content.ComponentName;
import android.net.NetworkRequest;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.PersistableBundle;
import java.util.ArrayList;
import java.util.List;
public class JobInfo {
 public static final int NETWORK_TYPE_NONE=0,NETWORK_TYPE_ANY=1,NETWORK_TYPE_UNMETERED=2,NETWORK_TYPE_NOT_ROAMING=3,NETWORK_TYPE_CELLULAR=4;
 public static final int NETWORK_BYTES_UNKNOWN=-1;
 public static final int PRIORITY_MIN=100,PRIORITY_LOW=200,PRIORITY_DEFAULT=300,PRIORITY_HIGH=400,PRIORITY_MAX=500;
 public static final int BACKOFF_POLICY_LINEAR=0,BACKOFF_POLICY_EXPONENTIAL=1;
 public static final long DEFAULT_INITIAL_BACKOFF_MILLIS=30000L,MAX_BACKOFF_DELAY_MILLIS=18000000L;
 public static final class TriggerContentUri {
  public static final int FLAG_NOTIFY_FOR_DESCENDANTS=1;
  private final Uri uri;private final int flags;
  public TriggerContentUri(Uri uri,int flags){this.uri=uri;this.flags=flags;}
  public Uri getUri(){return uri;}
  public int getFlags(){return flags;}
 }
 private final int constraints;private final TriggerContentUri[] triggers;
 private final boolean deadlineSet;private final boolean periodic;private final long interval,flex;
 private final long initialBackoff;private final int backoffPolicy;
 private final int id;private final ComponentName service;private final PersistableBundle extras;private final boolean persisted,expedited;private final long minimumLatency,deadline;private final NetworkRequest requiredNetwork;private final boolean unsupported;
 private JobInfo(Builder b){constraints=b.constraints;triggers=b.triggers.isEmpty()?null:b.triggers.toArray(new TriggerContentUri[b.triggers.size()]);deadlineSet=b.deadlineSet;periodic=b.periodic;interval=b.interval;flex=b.flex;initialBackoff=b.initialBackoff;backoffPolicy=b.backoffPolicy;id=b.id;service=b.service;extras=b.extras.deepCopy();unsupported=b.unsupported||!b.triggers.isEmpty();persisted=b.persisted;expedited=b.expedited;minimumLatency=b.minimumLatency;deadline=b.deadline;requiredNetwork=b.requiredNetwork==null?null:new NetworkRequest(b.requiredNetwork);}
 public static long getMinPeriodMillis(){return 900000L;}
 public static long getMinFlexMillis(){return 300000L;}
 public boolean isPeriodic(){return periodic;}
 public long getIntervalMillis(){return interval;}
 public long getFlexMillis(){return flex;}
 public int getId(){return id;}
 public ComponentName getService(){return service;}
 public PersistableBundle getExtras(){return extras.deepCopy();}
 public boolean shirazSupported(){return !persisted&&shirazExecutionSupported();}
 public boolean shirazExecutionSupported(){return constraints==0&&shirazDefinitionSupported();}
 public boolean shirazHostExecutionSupported(){return shirazDefinitionSupported();}
 public boolean shirazDefinitionSupported(){return !unsupported&&(constraints&~16)==0&&!expedited&&requiredNetwork==null;}
 public boolean isRequireBatteryNotLow(){return (constraints&2)!=0;}
 public boolean isRequireCharging(){return (constraints&4)!=0;}
 public boolean isRequireDeviceIdle(){return (constraints&8)!=0;}
 public boolean isRequireStorageNotLow(){return (constraints&16)!=0;}
 public TriggerContentUri[] getTriggerContentUris(){return triggers==null?null:triggers.clone();}
 public boolean isPersisted(){return persisted;}
 public boolean isExpedited(){return expedited;}
 public long getMinLatencyMillis(){return minimumLatency;}
 public long getMaxExecutionDelayMillis(){return deadline;}
 public boolean shirazHasDeadline(){return deadlineSet;}
 public NetworkRequest getRequiredNetwork(){return requiredNetwork==null?null:new NetworkRequest(requiredNetwork);}
 public int getNetworkType(){
  if(requiredNetwork==null)return NETWORK_TYPE_NONE;
  if(requiredNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))return NETWORK_TYPE_UNMETERED;
  if(requiredNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING))return NETWORK_TYPE_NOT_ROAMING;
  if(requiredNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))return NETWORK_TYPE_CELLULAR;
  return NETWORK_TYPE_ANY;
 }
 public long getInitialBackoffMillis(){return initialBackoff;}
 public int getBackoffPolicy(){return backoffPolicy;}
 public static final class Builder {
  private boolean periodic,latencySet,deadlineSet;private long interval,flex;
  public Builder setPeriodic(long intervalMillis){return setPeriodic(intervalMillis,intervalMillis);}
  public Builder setPeriodic(long intervalMillis,long flexMillis){
   periodic=true;interval=Math.max(getMinPeriodMillis(),intervalMillis);
   flex=Math.min(interval,Math.max(Math.max(getMinFlexMillis(),interval/20),flexMillis));return this;
  }
  private long initialBackoff=DEFAULT_INITIAL_BACKOFF_MILLIS;private int backoffPolicy=BACKOFF_POLICY_EXPONENTIAL;private boolean backoffSet;
  private final int id;private final ComponentName service;private PersistableBundle extras=new PersistableBundle();private boolean persisted,expedited;private long minimumLatency,deadline=Long.MAX_VALUE;private NetworkRequest requiredNetwork;private boolean unsupported;private int constraints;
  private final List<TriggerContentUri> triggers=new ArrayList<TriggerContentUri>();
  public Builder(int id,ComponentName service){this.id=id;this.service=java.util.Objects.requireNonNull(service);}
  public Builder setExtras(PersistableBundle extras){this.extras=extras;return this;}
  public Builder setPersisted(boolean value){persisted=value;return this;}
  public Builder setExpedited(boolean value){expedited=value;return this;}
  public Builder setOverrideDeadline(long millis){if(millis<0)throw new IllegalArgumentException("Deadline");deadline=millis;deadlineSet=true;return this;}
  public Builder setMinimumLatency(long millis){if(millis<0)throw new IllegalArgumentException("Latency");minimumLatency=millis;latencySet=true;return this;}
  public Builder setRequiredNetworkType(int type){
   if(type==NETWORK_TYPE_NONE)return setRequiredNetwork(null);
   if(type<NETWORK_TYPE_NONE||type>NETWORK_TYPE_CELLULAR)throw new IllegalArgumentException("Network type");
   NetworkRequest.Builder request=new NetworkRequest.Builder()
    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED);
   if(type==NETWORK_TYPE_UNMETERED)request.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
   if(type==NETWORK_TYPE_NOT_ROAMING)request.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
   if(type==NETWORK_TYPE_CELLULAR)request.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
   return setRequiredNetwork(request.build());
  }
  public Builder setRequiredNetwork(NetworkRequest request){requiredNetwork=request==null?null:new NetworkRequest(request);return this;}
  public Builder setBackoffCriteria(long initialBackoffMillis,int policy){if(policy!=BACKOFF_POLICY_LINEAR&&policy!=BACKOFF_POLICY_EXPONENTIAL)throw new IllegalArgumentException("Backoff policy");initialBackoff=Math.max(10000L,initialBackoffMillis);backoffPolicy=policy;backoffSet=true;return this;}
  public Builder setImportantWhileForeground(boolean value){constraints=value?constraints|1:constraints&~1;return this;}
  public Builder setRequiresBatteryNotLow(boolean value){constraints=value?constraints|2:constraints&~2;return this;}
  public Builder setRequiresCharging(boolean value){constraints=value?constraints|4:constraints&~4;return this;}
  public Builder setRequiresDeviceIdle(boolean value){constraints=value?constraints|8:constraints&~8;return this;}
  public Builder setRequiresStorageNotLow(boolean value){constraints=value?constraints|16:constraints&~16;return this;}
  public Builder setTraceTag(String tag){return this;}
  public Builder setTriggerContentMaxDelay(long millis){unsupported=true;return this;}
  public Builder setTriggerContentUpdateDelay(long millis){unsupported=true;return this;}
  public Builder addTriggerContentUri(TriggerContentUri uri){triggers.add(uri);return this;}
  public JobInfo build(){if(periodic&&(latencySet||deadlineSet||!triggers.isEmpty()||expedited))throw new IllegalArgumentException("Periodic job has incompatible timing or triggers");if(backoffSet&&(constraints&8)!=0)throw new IllegalArgumentException("Idle jobs cannot specify backoff");return new JobInfo(this);}
 }
}
