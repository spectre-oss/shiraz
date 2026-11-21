// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Subscription metadata; no active instances are exposed until a host modem provider exists. */
public class SubscriptionInfo implements Parcelable {
    private final int mId;
    private final String mIccId;
    private final int mSimSlotIndex;
    private final CharSequence mDisplayName;
    private final CharSequence mCarrierName;
    private final int mIconTint;
    private final String mNumber;
    private final int mDataRoaming;
    private final String mMcc;
    private final String mMnc;
    private final String mCountryIso;
    private final boolean mIsEmbedded;
    private final int mCardId;
    private final boolean mIsOpportunistic;
    private final String mGroupUUID;
    private final int mCarrierId;
    private final int mSubscriptionType;
    private final int mPortIndex;
    private final int mUsageSetting;
    private final boolean mIsOnlyNonTerrestrialNetwork;
    private final Set<Integer> mServiceCapabilities;

    SubscriptionInfo() {
        mId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        mIccId = "";
        mSimSlotIndex = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
        mDisplayName = "";
        mCarrierName = "";
        mIconTint = 0xFF000000;
        mNumber = "";
        mDataRoaming = SubscriptionManager.DATA_ROAMING_DISABLE;
        mMcc = null;
        mMnc = null;
        mCountryIso = "";
        mIsEmbedded = false;
        mCardId = -1;
        mIsOpportunistic = false;
        mGroupUUID = null;
        mCarrierId = -1;
        mSubscriptionType = SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM;
        mPortIndex = 0;
        mUsageSetting = SubscriptionManager.USAGE_SETTING_DEFAULT;
        mIsOnlyNonTerrestrialNetwork = false;
        mServiceCapabilities = Collections.emptySet();
    }

    private SubscriptionInfo(Parcel in) {
        mId = in.readInt();
        mIccId = in.readString();
        mSimSlotIndex = in.readInt();
        mDisplayName = in.readString();
        mCarrierName = in.readString();
        mIconTint = in.readInt();
        mNumber = in.readString();
        mDataRoaming = in.readInt();
        mMcc = in.readString();
        mMnc = in.readString();
        mCountryIso = in.readString();
        mIsEmbedded = in.readInt() != 0;
        mCardId = in.readInt();
        mIsOpportunistic = in.readInt() != 0;
        mGroupUUID = in.readString();
        mCarrierId = in.readInt();
        mSubscriptionType = in.readInt();
        mPortIndex = in.readInt();
        mUsageSetting = in.readInt();
        mIsOnlyNonTerrestrialNetwork = in.readInt() != 0;
        int capCount = in.readInt();
        if (capCount <= 0) {
            mServiceCapabilities = Collections.emptySet();
        } else {
            LinkedHashSet<Integer> caps = new LinkedHashSet<Integer>(capCount);
            for (int i = 0; i < capCount; i++) {
                caps.add(Integer.valueOf(in.readInt()));
            }
            mServiceCapabilities = Collections.unmodifiableSet(caps);
        }
    }

    public int getSubscriptionId() {
        return mId;
    }

    public String getIccId() {
        return mIccId;
    }

    public int getSimSlotIndex() {
        return mSimSlotIndex;
    }

    public int getCarrierId() {
        return mCarrierId;
    }

    public CharSequence getDisplayName() {
        return mDisplayName;
    }

    public CharSequence getCarrierName() {
        return mCarrierName;
    }

    public Bitmap createIconBitmap(Context context) {
        Bitmap bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(mIconTint);
        return bmp;
    }

    public int getIconTint() {
        return mIconTint;
    }

    @Deprecated
    public String getNumber() {
        return mNumber;
    }

    public int getDataRoaming() {
        return mDataRoaming;
    }

    @Deprecated
    public int getMcc() {
        if (mMcc == null) {
            return 0;
        }
        try {
            return Integer.parseInt(mMcc);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Deprecated
    public int getMnc() {
        if (mMnc == null) {
            return 0;
        }
        try {
            return Integer.parseInt(mMnc);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getMccString() {
        return mMcc;
    }

    public String getMncString() {
        return mMnc;
    }

    public String getCountryIso() {
        return mCountryIso;
    }

    public boolean isEmbedded() {
        return mIsEmbedded;
    }

    public boolean isOpportunistic() {
        return mIsOpportunistic;
    }

    public ParcelUuid getGroupUuid() {
        return mGroupUUID == null ? null : ParcelUuid.fromString(mGroupUUID);
    }

    public int getSubscriptionType() {
        return mSubscriptionType;
    }

    public int getCardId() {
        return mCardId;
    }

    public int getPortIndex() {
        return mPortIndex;
    }

    public int getUsageSetting() {
        return mUsageSetting;
    }

    public boolean isOnlyNonTerrestrialNetwork() {
        return mIsOnlyNonTerrestrialNetwork;
    }

    public Set<Integer> getServiceCapabilities() {
        return mServiceCapabilities;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeString(mIccId);
        dest.writeInt(mSimSlotIndex);
        dest.writeString(mDisplayName != null ? mDisplayName.toString() : null);
        dest.writeString(mCarrierName != null ? mCarrierName.toString() : null);
        dest.writeInt(mIconTint);
        dest.writeString(mNumber);
        dest.writeInt(mDataRoaming);
        dest.writeString(mMcc);
        dest.writeString(mMnc);
        dest.writeString(mCountryIso);
        dest.writeInt(mIsEmbedded ? 1 : 0);
        dest.writeInt(mCardId);
        dest.writeInt(mIsOpportunistic ? 1 : 0);
        dest.writeString(mGroupUUID);
        dest.writeInt(mCarrierId);
        dest.writeInt(mSubscriptionType);
        dest.writeInt(mPortIndex);
        dest.writeInt(mUsageSetting);
        dest.writeInt(mIsOnlyNonTerrestrialNetwork ? 1 : 0);
        dest.writeInt(mServiceCapabilities.size());
        for (Integer cap : mServiceCapabilities) {
            dest.writeInt(cap.intValue());
        }
    }

    public int describeContents() {
        return 0;
    }

    public String toString() {
        return "{id=" + mId
                + " iccId=" + mIccId
                + " simSlotIndex=" + mSimSlotIndex
                + " carrierId=" + mCarrierId
                + " displayName=" + mDisplayName
                + " carrierName=" + mCarrierName
                + " mcc=" + mMcc
                + " mnc=" + mMnc
                + " countryIso=" + mCountryIso
                + " isEmbedded=" + mIsEmbedded
                + " isOpportunistic=" + mIsOpportunistic
                + " portIndex=" + mPortIndex
                + " usageSetting=" + mUsageSetting
                + " isOnlyNonTerrestrialNetwork=" + mIsOnlyNonTerrestrialNetwork + "}";
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SubscriptionInfo)) {
            return false;
        }
        SubscriptionInfo toCompare = (SubscriptionInfo) o;
        return mId == toCompare.mId
                && mSimSlotIndex == toCompare.mSimSlotIndex
                && mIconTint == toCompare.mIconTint
                && mDataRoaming == toCompare.mDataRoaming
                && mIsEmbedded == toCompare.mIsEmbedded
                && mIsOpportunistic == toCompare.mIsOpportunistic
                && mCarrierId == toCompare.mCarrierId
                && mCardId == toCompare.mCardId
                && mSubscriptionType == toCompare.mSubscriptionType
                && mPortIndex == toCompare.mPortIndex
                && mUsageSetting == toCompare.mUsageSetting
                && mIsOnlyNonTerrestrialNetwork == toCompare.mIsOnlyNonTerrestrialNetwork
                && Objects.equals(mGroupUUID, toCompare.mGroupUUID)
                && Objects.equals(mIccId, toCompare.mIccId)
                && Objects.equals(mNumber, toCompare.mNumber)
                && Objects.equals(mMcc, toCompare.mMcc)
                && Objects.equals(mMnc, toCompare.mMnc)
                && Objects.equals(mCountryIso, toCompare.mCountryIso)
                && Objects.equals(mDisplayName, toCompare.mDisplayName)
                && Objects.equals(mCarrierName, toCompare.mCarrierName)
                && Objects.equals(mServiceCapabilities, toCompare.mServiceCapabilities);
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mId),
                Integer.valueOf(mSimSlotIndex),
                mDisplayName,
                mCarrierName,
                Integer.valueOf(mIconTint),
                mNumber,
                Integer.valueOf(mDataRoaming),
                mIccId,
                mMcc,
                mMnc,
                mCountryIso,
                Boolean.valueOf(mIsEmbedded),
                Integer.valueOf(mCardId),
                Boolean.valueOf(mIsOpportunistic),
                mGroupUUID,
                Integer.valueOf(mCarrierId),
                Integer.valueOf(mSubscriptionType),
                Integer.valueOf(mPortIndex),
                Integer.valueOf(mUsageSetting),
                Boolean.valueOf(mIsOnlyNonTerrestrialNetwork),
                mServiceCapabilities);
    }

    public static final Parcelable.Creator<SubscriptionInfo> CREATOR =
            new Parcelable.Creator<SubscriptionInfo>() {
                public SubscriptionInfo createFromParcel(Parcel source) {
                    return new SubscriptionInfo(source);
                }

                public SubscriptionInfo[] newArray(int size) {
                    return new SubscriptionInfo[size];
                }
            };
}
