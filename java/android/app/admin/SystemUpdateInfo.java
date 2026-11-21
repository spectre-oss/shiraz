// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class SystemUpdateInfo implements Parcelable {
    public static final int SECURITY_PATCH_STATE_UNKNOWN = 0;
    public static final int SECURITY_PATCH_STATE_FALSE = 1;
    public static final int SECURITY_PATCH_STATE_TRUE = 2;

    private final long mReceivedTime;
    private final int mSecurityPatchState;

    private SystemUpdateInfo(long receivedTime, int securityPatchState) {
        mReceivedTime = receivedTime;
        mSecurityPatchState = securityPatchState;
    }

    SystemUpdateInfo(Parcel in) {
        mReceivedTime = in.readLong();
        mSecurityPatchState = in.readInt();
    }

    public static SystemUpdateInfo of(long receivedTime) {
        return receivedTime == -1L
                ? null
                : new SystemUpdateInfo(receivedTime, SECURITY_PATCH_STATE_UNKNOWN);
    }

    public static SystemUpdateInfo of(long receivedTime, boolean isSecurityPatch) {
        return receivedTime == -1L
                ? null
                : new SystemUpdateInfo(
                        receivedTime,
                        isSecurityPatch ? SECURITY_PATCH_STATE_TRUE : SECURITY_PATCH_STATE_FALSE);
    }

    public long getReceivedTime() {
        return mReceivedTime;
    }

    public int getSecurityPatchState() {
        return mSecurityPatchState;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mReceivedTime);
        dest.writeInt(mSecurityPatchState);
    }

    @Override
    public String toString() {
        return String.format(
                "SystemUpdateInfo (receivedTime = %d, securityPatchState = %d)",
                Long.valueOf(mReceivedTime),
                Integer.valueOf(mSecurityPatchState));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SystemUpdateInfo)) return false;
        SystemUpdateInfo that = (SystemUpdateInfo) o;
        return mReceivedTime == that.mReceivedTime
                && mSecurityPatchState == that.mSecurityPatchState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Long.valueOf(mReceivedTime), Integer.valueOf(mSecurityPatchState));
    }

    public static final Parcelable.Creator<SystemUpdateInfo> CREATOR =
            new Parcelable.Creator<SystemUpdateInfo>() {
                @Override
                public SystemUpdateInfo createFromParcel(Parcel in) {
                    return new SystemUpdateInfo(in);
                }

                @Override
                public SystemUpdateInfo[] newArray(int size) {
                    return new SystemUpdateInfo[size];
                }
            };
}
