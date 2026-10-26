// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
import android.os.Parcel;
import android.os.Parcelable;
public class PackageInfo implements Parcelable {
    public String packageName, versionName;
    public int versionCode;
    public long firstInstallTime,lastUpdateTime;
    public ApplicationInfo applicationInfo;
    public Signature[] signatures;
    public ServiceInfo[] services;
    public String[] requestedPermissions;
    public int[] requestedPermissionsFlags;
    private int versionCodeMajor;
    public long getLongVersionCode() { return ((long) versionCodeMajor << 32) | (versionCode & 0xffffffffL); }
    public void setLongVersionCode(long version) { versionCode = (int) version; versionCodeMajor = (int) (version >>> 32); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel destination, int flags) { throw new UnsupportedOperationException("PackageInfo parcel transport is not implemented"); }
}
