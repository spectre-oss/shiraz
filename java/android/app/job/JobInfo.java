// SPDX-License-Identifier: GPL-3.0-only
package android.app.job;
import android.content.ComponentName;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.PersistableBundle;
import java.util.ArrayList;
import java.util.List;
public class JobInfo {
 public static final int NETWORK_TYPE_NONE=0,NETWORK_TYPE_ANY=1,NETWORK_TYPE_UNMETERED=2,NETWORK_TYPE_NOT_ROAMING=3,NETWORK_TYPE_CELLULAR=4;
 public static final int BACKOFF_POLICY_LINEAR=0,BACKOFF_POLICY_EXPONENTIAL=1;
 public static final class TriggerContentUri {
  public static final int FLAG_NOTIFY_FOR_DESCENDANTS=1;
  private final Uri uri;private final int flags;
  public TriggerContentUri(Uri uri,int flags){this.uri=uri;this.flags=flags;}
  public Uri getUri(){return uri;}
  public int getFlags(){return flags;}
 }
 private final int id;private final ComponentName service;private final PersistableBundle extras;private final boolean persisted,expedited;private final long minimumLatency,deadline;private final int networkType;
 private JobInfo(Builder b){id=b.id;service=b.service;extras=b.extras;persisted=b.persisted;expedited=b.expedited;minimumLatency=b.minimumLatency;deadline=b.deadline;networkType=b.networkType;}
 public int getId(){return id;}
 public ComponentName getService(){return service;}
 public PersistableBundle getExtras(){return extras;}
 public boolean isPersisted(){return persisted;}
 public boolean isExpedited(){return expedited;}
 public long getMinLatencyMillis(){return minimumLatency;}
 public long getMaxExecutionDelayMillis(){return deadline;}
 public int getNetworkType(){return networkType;}
 public static final class Builder {
  private final int id;private final ComponentName service;private PersistableBundle extras=new PersistableBundle();private boolean persisted,expedited;private long minimumLatency,deadline=Long.MAX_VALUE;private int networkType;
  private final List<TriggerContentUri> triggers=new ArrayList<TriggerContentUri>();
  public Builder(int id,ComponentName service){this.id=id;this.service=service;}
  public Builder setExtras(PersistableBundle extras){this.extras=extras;return this;}
  public Builder setPersisted(boolean value){persisted=value;return this;}
  public Builder setExpedited(boolean value){expedited=value;return this;}
  public Builder setOverrideDeadline(long millis){if(millis<0)throw new IllegalArgumentException("Deadline");deadline=millis;return this;}
  public Builder setMinimumLatency(long millis){minimumLatency=millis;return this;}
  public Builder setRequiredNetworkType(int type){networkType=type;return this;}
  public Builder setRequiredNetwork(NetworkRequest request){networkType=request==null?NETWORK_TYPE_NONE:NETWORK_TYPE_ANY;return this;}
  public Builder setBackoffCriteria(long initialBackoffMillis,int policy){return this;}
  public Builder setImportantWhileForeground(boolean value){return this;}
  public Builder setRequiresBatteryNotLow(boolean value){return this;}
  public Builder setRequiresCharging(boolean value){return this;}
  public Builder setRequiresDeviceIdle(boolean value){return this;}
  public Builder setRequiresStorageNotLow(boolean value){return this;}
  public Builder setTraceTag(String tag){return this;}
  public Builder setTriggerContentMaxDelay(long millis){return this;}
  public Builder setTriggerContentUpdateDelay(long millis){return this;}
  public Builder addTriggerContentUri(TriggerContentUri uri){triggers.add(uri);return this;}
  public JobInfo build(){return new JobInfo(this);}
 }
}
