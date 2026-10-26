// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
import android.os.Parcel;
import android.os.Parcelable;
public class ApplicationInfo extends PackageItemInfo implements Parcelable {
    public static final int FLAG_DEBUGGABLE = 2, FLAG_HAS_CODE = 4;
    public String className, processName, sourceDir, publicSourceDir, dataDir, nativeLibraryDir;
    public String[] splitSourceDirs, splitPublicSourceDirs;
    public int theme;
    public int uid, targetSdkVersion, minSdkVersion, flags;
    public boolean enabled;
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel destination, int flags) { throw new UnsupportedOperationException("ApplicationInfo parcel transport is not implemented"); }
}
