// SPDX-License-Identifier: GPL-3.0-only
package android.app.job;
import android.net.Network;
import android.net.Uri;
import android.os.PersistableBundle;
public class JobParameters {
 private final int id;private final PersistableBundle extras;
 public JobParameters(int id,PersistableBundle extras){this.id=id;this.extras=extras;}
 public int getJobId(){return id;}
 public PersistableBundle getExtras(){return extras;}
 public Network getNetwork(){return null;}
 public int getStopReason(){return 0;}
 public String[] getTriggeredContentAuthorities(){return null;}
 public Uri[] getTriggeredContentUris(){return null;}
}
