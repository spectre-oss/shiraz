// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.net.LinkProperties;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.data.ApnSetting;
import java.util.Objects;

public final class PreciseDataConnectionState implements Parcelable {
    public static final int NETWORK_VALIDATION_UNSUPPORTED = 0;
    public static final int NETWORK_VALIDATION_NOT_REQUESTED = 1;
    public static final int NETWORK_VALIDATION_IN_PROGRESS = 2;
    public static final int NETWORK_VALIDATION_SUCCESS = 3;
    public static final int NETWORK_VALIDATION_FAILURE = 4;

    private final int mTransportType;
    private final int mId;
    private final int mState;
    private final int mNetworkType;
    private final LinkProperties mLinkProperties;
    private final int mFailCause;
    private final ApnSetting mApnSetting;
    private final int mNetworkValidationStatus;

    PreciseDataConnectionState() {
        mTransportType = -1;
        mId = -1;
        mState = -1;
        mNetworkType = 0;
        mLinkProperties = null;
        mFailCause = 0;
        mApnSetting = null;
        mNetworkValidationStatus = NETWORK_VALIDATION_UNSUPPORTED;
    }

    PreciseDataConnectionState(int transportType, int id, int state, int networkType,
            LinkProperties linkProperties, int failCause, ApnSetting apnSetting,
            int networkValidationStatus) {
        mTransportType = transportType;
        mId = id;
        mState = state;
        mNetworkType = networkType;
        mLinkProperties = linkProperties == null ? null : new LinkProperties(linkProperties);
        mFailCause = failCause;
        mApnSetting = apnSetting;
        mNetworkValidationStatus = networkValidationStatus;
    }

    private PreciseDataConnectionState(Parcel in) {
        mTransportType = in.readInt();
        mId = in.readInt();
        mState = in.readInt();
        mNetworkType = in.readInt();
        if (in.readInt() != 0) {
            mLinkProperties = LinkProperties.CREATOR.createFromParcel(in);
        } else {
            mLinkProperties = null;
        }
        mFailCause = in.readInt();
        if (in.readInt() != 0) {
            mApnSetting = ApnSetting.CREATOR.createFromParcel(in);
        } else {
            mApnSetting = null;
        }
        mNetworkValidationStatus = in.readInt();
    }

    public int getTransportType() {
        return mTransportType;
    }

    public int getId() {
        return mId;
    }

    public int getState() {
        return mState;
    }

    public int getNetworkType() {
        return mNetworkType;
    }

    public LinkProperties getLinkProperties() {
        return mLinkProperties == null ? null : new LinkProperties(mLinkProperties);
    }

    public int getLastCauseCode() {
        return mFailCause;
    }

    public ApnSetting getApnSetting() {
        return mApnSetting;
    }

    public int getNetworkValidationStatus() {
        return mNetworkValidationStatus;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mTransportType);
        out.writeInt(mId);
        out.writeInt(mState);
        out.writeInt(mNetworkType);
        if (mLinkProperties != null) {
            out.writeInt(1);
            mLinkProperties.writeToParcel(out, flags);
        } else {
            out.writeInt(0);
        }
        out.writeInt(mFailCause);
        if (mApnSetting != null) {
            out.writeInt(1);
            mApnSetting.writeToParcel(out, flags);
        } else {
            out.writeInt(0);
        }
        out.writeInt(mNetworkValidationStatus);
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mTransportType),
                Integer.valueOf(mId),
                Integer.valueOf(mState),
                Integer.valueOf(mNetworkType),
                Integer.valueOf(mFailCause),
                mLinkProperties,
                mApnSetting,
                Integer.valueOf(mNetworkValidationStatus));
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PreciseDataConnectionState)) {
            return false;
        }
        PreciseDataConnectionState other = (PreciseDataConnectionState) o;
        return mTransportType == other.mTransportType
                && mId == other.mId
                && mState == other.mState
                && mNetworkType == other.mNetworkType
                && mFailCause == other.mFailCause
                && Objects.equals(mLinkProperties, other.mLinkProperties)
                && Objects.equals(mApnSetting, other.mApnSetting)
                && mNetworkValidationStatus == other.mNetworkValidationStatus;
    }

    public String toString() {
        return "State: " + mState
                + ", Transport: " + mTransportType
                + ", Id: " + mId
                + ", NetworkType: " + mNetworkType
                + ", APN Setting: " + mApnSetting
                + ", LinkProperties: " + mLinkProperties
                + ", FailCause: " + DataFailCause.toString(mFailCause)
                + ", NetworkValidationStatus: " + mNetworkValidationStatus;
    }

    public static final Parcelable.Creator<PreciseDataConnectionState> CREATOR =
            new Parcelable.Creator<PreciseDataConnectionState>() {
                public PreciseDataConnectionState createFromParcel(Parcel in) {
                    return new PreciseDataConnectionState(in);
                }

                public PreciseDataConnectionState[] newArray(int size) {
                    return new PreciseDataConnectionState[size];
                }
            };
}
