// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
import android.os.*;
public class ProviderInfo extends ComponentInfo implements Parcelable {
 public String authority,readPermission,writePermission;public boolean grantUriPermissions,multiprocess;public int flags,initOrder;
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("ProviderInfo parcel transport is not implemented");}
}
