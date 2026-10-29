// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
import android.os.Parcel;
import android.os.Parcelable;
public class PackageInfo implements Parcelable {
    public String packageName, versionName;
    /** Null for the monolithic APKs currently supported by Shiraz. */
    public String[] splitNames;
    public int versionCode, baseRevisionCode, sharedUserLabel;
    public String sharedUserId;
    public int installLocation=-1;
    public int[] splitRevisionCodes, gids;
    public boolean isApex;
    public long firstInstallTime,lastUpdateTime;
    public ApplicationInfo applicationInfo;
    public Signature[] signatures;
    public SigningInfo signingInfo;
    public ActivityInfo[] activities, receivers;
    public ProviderInfo[] providers;
    public ConfigurationInfo[] configPreferences;
    public FeatureInfo[] reqFeatures;
    public FeatureGroupInfo[] featureGroups;
    public InstrumentationInfo[] instrumentation;
    public ServiceInfo[] services;
    public PermissionInfo[] permissions;
    public static final int REQUESTED_PERMISSION_REQUIRED=1,REQUESTED_PERMISSION_GRANTED=2;
    public String[] requestedPermissions;
    public int[] requestedPermissionsFlags;
    private int versionCodeMajor;
    public long getLongVersionCode() { return ((long) versionCodeMajor << 32) | (versionCode & 0xffffffffL); }
    public void setLongVersionCode(long version) { versionCode = (int) version; versionCodeMajor = (int) (version >>> 32); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel destination, int flags) { throw new UnsupportedOperationException("PackageInfo parcel transport is not implemented"); }
}
