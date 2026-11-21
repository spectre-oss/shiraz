// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
import android.os.Parcel;
import android.os.Parcelable;
public class ApplicationInfo extends PackageItemInfo implements Parcelable {
    public static final int FLAG_DEBUGGABLE = 2, FLAG_HAS_CODE = 4, FLAG_USES_CLEARTEXT_TRAFFIC = 1 << 27;
    public String className, processName, sourceDir, publicSourceDir, dataDir, nativeLibraryDir;
    public String[] splitSourceDirs, splitPublicSourceDirs;
    public String backupAgentName, manageSpaceActivityName, appComponentFactory, permission, taskAffinity;
    public String compileSdkVersionCodename, deviceProtectedDataDir;
    public String[] sharedLibraryFiles, splitNames;
    public java.util.UUID storageUuid;
    public int category=-1, descriptionRes, uiOptions, compileSdkVersion;
    public int requiresSmallestWidthDp, compatibleWidthLimitDp, largestWidthLimitDp;
    /** Requested Android sandbox version, not a claim of host sandbox enforcement. */
    public int targetSandboxVersion=1;
    public int theme;
    public int uid, targetSdkVersion, minSdkVersion, flags;
    public boolean enabled;
    /** Shiraz installs APKs directly; it does not provision Android virtual preloads. */
    public boolean isVirtualPreload(){return false;}
    /** Enrolled packages are full APK installations; instant installs are not supported. */
    public boolean isInstantApp(){return false;}
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel destination, int flags) { throw new UnsupportedOperationException("ApplicationInfo parcel transport is not implemented"); }
    public static class DisplayNameComparator implements java.util.Comparator<ApplicationInfo> {
        private final java.text.Collator sCollator = java.text.Collator.getInstance();
        private final PackageManager mPM;
        public DisplayNameComparator(PackageManager pm) { mPM = pm; }
        @Override
        public final int compare(ApplicationInfo aa, ApplicationInfo ab) {
            CharSequence sa = mPM != null ? mPM.getApplicationLabel(aa) : null;
            if (sa == null) sa = aa.packageName != null ? aa.packageName : "";
            CharSequence sb = mPM != null ? mPM.getApplicationLabel(ab) : null;
            if (sb == null) sb = ab.packageName != null ? ab.packageName : "";
            return sCollator.compare(sa.toString(), sb.toString());
        }
    }
}
