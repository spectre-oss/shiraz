// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;
import android.os.Bundle;
public class PhoneAccount {
 public static final int CAPABILITY_SELF_MANAGED=0x800,CAPABILITY_CALL_PROVIDER=0x2,CAPABILITY_VIDEO_CALLING=0x8,CAPABILITY_SUPPORTS_VIDEO_CALLING=0x400;
 private final PhoneAccountHandle handle;private final CharSequence label;private final int capabilities;private final Bundle extras;
 private PhoneAccount(Builder b){handle=b.handle;label=b.label;capabilities=b.capabilities;extras=b.extras;}
 public static Builder builder(PhoneAccountHandle handle,CharSequence label){return new Builder(handle,label);}
 public PhoneAccountHandle getAccountHandle(){return handle;}
 public CharSequence getLabel(){return label;}
 public int getCapabilities(){return capabilities;}
 public Bundle getExtras(){return extras;}
 public static class Builder {
  private final PhoneAccountHandle handle;private final CharSequence label;private int capabilities;private Bundle extras;
  public Builder(PhoneAccountHandle handle,CharSequence label){this.handle=handle;this.label=label;}
  public Builder setCapabilities(int capabilities){this.capabilities=capabilities;return this;}
  public Builder setExtras(Bundle extras){this.extras=extras;return this;}
  public PhoneAccount build(){return new PhoneAccount(this);}
 }
}
