// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Metadata describing an archived application stub for unarchival/reinstallation. */
public final class ArchivedPackageInfo {
    private String mPackageName;
    private SigningInfo mSigningInfo;
    private int mVersionCode;
    private int mVersionCodeMajor;
    private int mTargetSdkVersion;
    private String mDefaultToDeviceProtectedStorage;
    private String mRequestLegacyExternalStorage;
    private String mUserDataFragile;
    private List<ArchivedActivityInfo> mLauncherActivities;

    public ArchivedPackageInfo(
            String packageName,
            SigningInfo signingInfo,
            List<ArchivedActivityInfo> launcherActivities) {
        mPackageName = Objects.requireNonNull(packageName);
        mSigningInfo = Objects.requireNonNull(signingInfo);
        mLauncherActivities = new ArrayList<ArchivedActivityInfo>(
                Objects.requireNonNull(launcherActivities));
    }

    public String getPackageName() {
        return mPackageName;
    }

    public SigningInfo getSigningInfo() {
        return mSigningInfo;
    }

    public int getVersionCode() {
        return mVersionCode;
    }

    public int getVersionCodeMajor() {
        return mVersionCodeMajor;
    }

    public int getTargetSdkVersion() {
        return mTargetSdkVersion;
    }

    public String getDefaultToDeviceProtectedStorage() {
        return mDefaultToDeviceProtectedStorage;
    }

    public String getRequestLegacyExternalStorage() {
        return mRequestLegacyExternalStorage;
    }

    public String getUserDataFragile() {
        return mUserDataFragile;
    }

    public List<ArchivedActivityInfo> getLauncherActivities() {
        return mLauncherActivities;
    }

    public ArchivedPackageInfo setPackageName(String value) {
        mPackageName = Objects.requireNonNull(value);
        return this;
    }

    public ArchivedPackageInfo setSigningInfo(SigningInfo value) {
        mSigningInfo = Objects.requireNonNull(value);
        return this;
    }

    public ArchivedPackageInfo setVersionCode(int value) {
        mVersionCode = value;
        return this;
    }

    public ArchivedPackageInfo setVersionCodeMajor(int value) {
        mVersionCodeMajor = value;
        return this;
    }

    public ArchivedPackageInfo setTargetSdkVersion(int value) {
        mTargetSdkVersion = value;
        return this;
    }

    public ArchivedPackageInfo setDefaultToDeviceProtectedStorage(String value) {
        mDefaultToDeviceProtectedStorage = value;
        return this;
    }

    public ArchivedPackageInfo setRequestLegacyExternalStorage(String value) {
        mRequestLegacyExternalStorage = value;
        return this;
    }

    public ArchivedPackageInfo setUserDataFragile(String value) {
        mUserDataFragile = value;
        return this;
    }

    public ArchivedPackageInfo setLauncherActivities(List<ArchivedActivityInfo> value) {
        mLauncherActivities = new ArrayList<ArchivedActivityInfo>(Objects.requireNonNull(value));
        return this;
    }
}
