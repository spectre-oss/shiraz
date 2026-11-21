// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class TelephonyDisplayInfo implements Parcelable {
    public static final int OVERRIDE_NETWORK_TYPE_NONE = 0;
    public static final int OVERRIDE_NETWORK_TYPE_LTE_CA = 1;
    public static final int OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO = 2;
    public static final int OVERRIDE_NETWORK_TYPE_NR_NSA = 3;
    @Deprecated
    public static final int OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE = 4;
    public static final int OVERRIDE_NETWORK_TYPE_NR_ADVANCED = 5;

    private final int mNetworkType;
    private final int mOverrideNetworkType;
    private final boolean mIsRoaming;

    TelephonyDisplayInfo(int networkType, int overrideNetworkType, boolean isRoaming) {
        mNetworkType = networkType;
        mOverrideNetworkType = overrideNetworkType;
        mIsRoaming = isRoaming;
    }

    private TelephonyDisplayInfo(Parcel in) {
        mNetworkType = in.readInt();
        mOverrideNetworkType = in.readInt();
        mIsRoaming = in.readInt() != 0;
    }

    public int getNetworkType() {
        return mNetworkType;
    }

    public int getOverrideNetworkType() {
        return mOverrideNetworkType;
    }

    public boolean isRoaming() {
        return mIsRoaming;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mNetworkType);
        dest.writeInt(mOverrideNetworkType);
        dest.writeInt(mIsRoaming ? 1 : 0);
    }

    public int describeContents() {
        return 0;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TelephonyDisplayInfo)) {
            return false;
        }
        TelephonyDisplayInfo that = (TelephonyDisplayInfo) o;
        return mNetworkType == that.mNetworkType
                && mOverrideNetworkType == that.mOverrideNetworkType
                && mIsRoaming == that.mIsRoaming;
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mNetworkType),
                Integer.valueOf(mOverrideNetworkType),
                Boolean.valueOf(mIsRoaming));
    }

    public String toString() {
        return "TelephonyDisplayInfo {network=" + mNetworkType
                + ", overrideNetwork=" + mOverrideNetworkType
                + ", isRoaming=" + mIsRoaming + "}";
    }

    public static final Parcelable.Creator<TelephonyDisplayInfo> CREATOR =
            new Parcelable.Creator<TelephonyDisplayInfo>() {
                public TelephonyDisplayInfo createFromParcel(Parcel source) {
                    return new TelephonyDisplayInfo(source);
                }

                public TelephonyDisplayInfo[] newArray(int size) {
                    return new TelephonyDisplayInfo[size];
                }
            };
}
