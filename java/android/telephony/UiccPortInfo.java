// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class UiccPortInfo implements Parcelable {
    public static final String ICCID_REDACTED = "FFFFFFFFFFFFFFFFFFFF";

    private final String mIccId;
    private final int mPortIndex;
    private final int mLogicalSlotIndex;
    private final boolean mIsActive;

    UiccPortInfo(String iccId, int portIndex, int logicalSlotIndex, boolean isActive) {
        mIccId = iccId;
        mPortIndex = portIndex;
        mLogicalSlotIndex = logicalSlotIndex;
        mIsActive = isActive;
    }

    private UiccPortInfo(Parcel in) {
        mIccId = in.readString();
        mPortIndex = in.readInt();
        mLogicalSlotIndex = in.readInt();
        mIsActive = in.readInt() != 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mIccId);
        dest.writeInt(mPortIndex);
        dest.writeInt(mLogicalSlotIndex);
        dest.writeInt(mIsActive ? 1 : 0);
    }

    public int describeContents() {
        return 0;
    }

    public String getIccId() {
        return mIccId;
    }

    public int getPortIndex() {
        return mPortIndex;
    }

    public boolean isActive() {
        return mIsActive;
    }

    public int getLogicalSlotIndex() {
        return mLogicalSlotIndex;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UiccPortInfo)) {
            return false;
        }
        UiccPortInfo that = (UiccPortInfo) obj;
        return Objects.equals(mIccId, that.mIccId)
                && mPortIndex == that.mPortIndex
                && mLogicalSlotIndex == that.mLogicalSlotIndex
                && mIsActive == that.mIsActive;
    }

    public int hashCode() {
        return Objects.hash(
                mIccId,
                Integer.valueOf(mPortIndex),
                Integer.valueOf(mLogicalSlotIndex),
                Boolean.valueOf(mIsActive));
    }

    public String toString() {
        return "UiccPortInfo (isActive=" + mIsActive
                + ", iccId=" + mIccId
                + ", portIndex=" + mPortIndex
                + ", mLogicalSlotIndex=" + mLogicalSlotIndex + ")";
    }

    public static final Parcelable.Creator<UiccPortInfo> CREATOR =
            new Parcelable.Creator<UiccPortInfo>() {
                public UiccPortInfo createFromParcel(Parcel in) {
                    return new UiccPortInfo(in);
                }

                public UiccPortInfo[] newArray(int size) {
                    return new UiccPortInfo[size];
                }
            };
}
