// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Information about the calling application requesting a credential operation. */
public final class CallingAppInfo implements Parcelable {
    private final String mPackageName;
    private final SigningInfo mSigningInfo;
    private final String mOrigin;

    public CallingAppInfo(String packageName, SigningInfo signingInfo) {
        this(packageName, signingInfo, null);
    }

    public CallingAppInfo(String packageName, SigningInfo signingInfo, String origin) {
        if (packageName == null || packageName.isEmpty()) {
            throw new IllegalArgumentException("packageName must not be null or empty");
        }
        this.mPackageName = packageName;
        this.mSigningInfo = Objects.requireNonNull(signingInfo, "signingInfo must not be null");
        this.mOrigin = origin;
    }

    private CallingAppInfo(Parcel in) {
        this.mPackageName = Objects.requireNonNull(in.readString(), "packageName");
        int signerCount = in.readInt();
        Signature[] signers = new Signature[signerCount < 0 ? 0 : signerCount];
        for (int i = 0; i < signers.length; i++) {
            byte[] raw = in.createByteArray();
            signers[i] = new Signature(raw != null ? raw : new byte[0]);
        }
        this.mSigningInfo = new SigningInfo(signers);
        this.mOrigin = in.readString();
    }

    public String getPackageName() {
        return mPackageName;
    }

    public SigningInfo getSigningInfo() {
        return mSigningInfo;
    }

    public String getOrigin() {
        return mOrigin;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackageName);
        Signature[] signers = mSigningInfo.getApkContentsSigners();
        dest.writeInt(signers.length);
        for (int i = 0; i < signers.length; i++) {
            dest.writeByteArray(signers[i].toByteArray());
        }
        dest.writeString(mOrigin);
    }

    @Override
    public String toString() {
        return "CallingAppInfo {packageName="
                + mPackageName
                + ", signingInfo="
                + mSigningInfo
                + ", origin="
                + mOrigin
                + "}";
    }

    public static final Parcelable.Creator<CallingAppInfo> CREATOR =
            new Parcelable.Creator<CallingAppInfo>() {
                @Override
                public CallingAppInfo createFromParcel(Parcel in) {
                    return new CallingAppInfo(in);
                }

                @Override
                public CallingAppInfo[] newArray(int size) {
                    return new CallingAppInfo[size];
                }
            };
}
