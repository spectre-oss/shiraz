// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

/** Provenance metadata describing which installer/originating package installed an application. */
public final class InstallSourceInfo implements Parcelable {
    private final String mInitiatingPackageName;
    private final SigningInfo mInitiatingPackageSigningInfo;
    private final String mOriginatingPackageName;
    private final String mInstallingPackageName;
    private final String mUpdateOwnerPackageName;
    private final int mPackageSource;

    public InstallSourceInfo(
            String initiatingPackageName,
            SigningInfo initiatingPackageSigningInfo,
            String originatingPackageName,
            String installingPackageName) {
        this(initiatingPackageName, initiatingPackageSigningInfo, originatingPackageName,
                installingPackageName, null, PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED);
    }

    public InstallSourceInfo(
            String initiatingPackageName,
            SigningInfo initiatingPackageSigningInfo,
            String originatingPackageName,
            String installingPackageName,
            String updateOwnerPackageName,
            int packageSource) {
        mInitiatingPackageName = initiatingPackageName;
        mInitiatingPackageSigningInfo = initiatingPackageSigningInfo;
        mOriginatingPackageName = originatingPackageName;
        mInstallingPackageName = installingPackageName;
        mUpdateOwnerPackageName = updateOwnerPackageName;
        mPackageSource = packageSource;
    }

    private InstallSourceInfo(Parcel source) {
        mInitiatingPackageName = source.readString();
        mInitiatingPackageSigningInfo = source.readParcelable(SigningInfo.class.getClassLoader());
        mOriginatingPackageName = source.readString();
        mInstallingPackageName = source.readString();
        mUpdateOwnerPackageName = source.readString();
        mPackageSource = source.readInt();
    }

    @Override
    public int describeContents() {
        return mInitiatingPackageSigningInfo == null
                ? 0 : mInitiatingPackageSigningInfo.describeContents();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mInitiatingPackageName);
        dest.writeParcelable(mInitiatingPackageSigningInfo, flags);
        dest.writeString(mOriginatingPackageName);
        dest.writeString(mInstallingPackageName);
        dest.writeString(mUpdateOwnerPackageName);
        dest.writeInt(mPackageSource);
    }

    public String getInitiatingPackageName() {
        return mInitiatingPackageName;
    }

    public SigningInfo getInitiatingPackageSigningInfo() {
        return mInitiatingPackageSigningInfo;
    }

    public String getOriginatingPackageName() {
        return mOriginatingPackageName;
    }

    public String getInstallingPackageName() {
        return mInstallingPackageName;
    }

    public String getUpdateOwnerPackageName() {
        return mUpdateOwnerPackageName;
    }

    public int getPackageSource() {
        return mPackageSource;
    }

    public static final Parcelable.Creator<InstallSourceInfo> CREATOR =
            new Parcelable.Creator<InstallSourceInfo>() {
                @Override
                public InstallSourceInfo createFromParcel(Parcel source) {
                    return new InstallSourceInfo(source);
                }

                @Override
                public InstallSourceInfo[] newArray(int size) {
                    return new InstallSourceInfo[size];
                }
            };
}
