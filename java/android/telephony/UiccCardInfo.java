// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class UiccCardInfo implements Parcelable {
    private final boolean mIsEuicc;
    private final int mCardId;
    private final String mEid;
    private final String mIccId;
    private final int mPhysicalSlotIndex;
    private final boolean mIsRemovable;
    private final boolean mIsMultipleEnabledProfilesSupported;
    private final List<UiccPortInfo> mPortList;

    UiccCardInfo(boolean isEuicc, int cardId, String eid, int physicalSlotIndex,
            boolean isRemovable, boolean isMultipleEnabledProfilesSupported,
            List<UiccPortInfo> portList) {
        mIsEuicc = isEuicc;
        mCardId = cardId;
        mEid = eid;
        mIccId = (portList != null && !portList.isEmpty()) ? portList.get(0).getIccId() : null;
        mPhysicalSlotIndex = physicalSlotIndex;
        mIsRemovable = isRemovable;
        mIsMultipleEnabledProfilesSupported = isMultipleEnabledProfilesSupported;
        mPortList = portList != null
                ? Collections.unmodifiableList(new ArrayList<UiccPortInfo>(portList))
                : Collections.<UiccPortInfo>emptyList();
    }

    private UiccCardInfo(Parcel in) {
        mIsEuicc = in.readInt() != 0;
        mCardId = in.readInt();
        mEid = in.readString();
        mIccId = in.readString();
        mPhysicalSlotIndex = in.readInt();
        mIsRemovable = in.readInt() != 0;
        mIsMultipleEnabledProfilesSupported = in.readInt() != 0;
        ArrayList<UiccPortInfo> ports = new ArrayList<UiccPortInfo>();
        in.readTypedList(ports, UiccPortInfo.CREATOR);
        mPortList = Collections.unmodifiableList(ports);
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mIsEuicc ? 1 : 0);
        dest.writeInt(mCardId);
        dest.writeString(mEid);
        dest.writeString(mIccId);
        dest.writeInt(mPhysicalSlotIndex);
        dest.writeInt(mIsRemovable ? 1 : 0);
        dest.writeInt(mIsMultipleEnabledProfilesSupported ? 1 : 0);
        dest.writeTypedList(mPortList);
    }

    public int describeContents() {
        return 0;
    }

    public boolean isEuicc() {
        return mIsEuicc;
    }

    public int getCardId() {
        return mCardId;
    }

    public String getEid() {
        return mIsEuicc ? mEid : null;
    }

    @Deprecated
    public String getIccId() {
        return mIccId;
    }

    @Deprecated
    public int getSlotIndex() {
        return mPhysicalSlotIndex;
    }

    public int getPhysicalSlotIndex() {
        return mPhysicalSlotIndex;
    }

    public boolean isRemovable() {
        return mIsRemovable;
    }

    public boolean isMultipleEnabledProfilesSupported() {
        return mIsMultipleEnabledProfilesSupported;
    }

    public Collection<UiccPortInfo> getPorts() {
        return mPortList;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UiccCardInfo)) {
            return false;
        }
        UiccCardInfo that = (UiccCardInfo) obj;
        return mIsEuicc == that.mIsEuicc
                && mCardId == that.mCardId
                && Objects.equals(mEid, that.mEid)
                && Objects.equals(mIccId, that.mIccId)
                && mPhysicalSlotIndex == that.mPhysicalSlotIndex
                && mIsRemovable == that.mIsRemovable
                && mIsMultipleEnabledProfilesSupported == that.mIsMultipleEnabledProfilesSupported
                && Objects.equals(mPortList, that.mPortList);
    }

    public int hashCode() {
        return Objects.hash(
                Boolean.valueOf(mIsEuicc),
                Integer.valueOf(mCardId),
                mEid,
                mIccId,
                Integer.valueOf(mPhysicalSlotIndex),
                Boolean.valueOf(mIsRemovable),
                Boolean.valueOf(mIsMultipleEnabledProfilesSupported),
                mPortList);
    }

    public String toString() {
        return "UiccCardInfo (mIsEuicc=" + mIsEuicc
                + ", mCardId=" + mCardId
                + ", mEid=" + mEid
                + ", mPhysicalSlotIndex=" + mPhysicalSlotIndex
                + ", mIsRemovable=" + mIsRemovable
                + ", mIsMultipleEnabledProfilesSupported=" + mIsMultipleEnabledProfilesSupported
                + ", mPortList=" + mPortList + ")";
    }

    public static final Parcelable.Creator<UiccCardInfo> CREATOR =
            new Parcelable.Creator<UiccCardInfo>() {
                public UiccCardInfo createFromParcel(Parcel in) {
                    return new UiccCardInfo(in);
                }

                public UiccCardInfo[] newArray(int size) {
                    return new UiccCardInfo[size];
                }
            };
}
