// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
import android.os.*;
public class ServiceInfo extends ComponentInfo implements Parcelable {
 public static final int FLAG_STOP_WITH_TASK=1,FLAG_ISOLATED_PROCESS=2,FLAG_EXTERNAL_SERVICE=4,FLAG_USE_APP_ZYGOTE=8,FLAG_ALLOW_SHARED_ISOLATED_PROCESS=16,FLAG_SINGLE_USER=0x40000000;
 public String permission;public int flags;
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("ServiceInfo parcel transport is not implemented");}
}
