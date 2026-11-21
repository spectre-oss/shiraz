// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ServiceState implements Parcelable {
    public static final int STATE_IN_SERVICE = 0;
    public static final int STATE_OUT_OF_SERVICE = 1;
    public static final int STATE_EMERGENCY_ONLY = 2;
    public static final int STATE_POWER_OFF = 3;

    public static final int DUPLEX_MODE_UNKNOWN = 0;
    public static final int DUPLEX_MODE_FDD = 1;
    public static final int DUPLEX_MODE_TDD = 2;

    public static final int UNKNOWN_ID = -1;

    private int mVoiceRegState = STATE_OUT_OF_SERVICE;
    private int mDataRegState = STATE_OUT_OF_SERVICE;
    private boolean mRoaming;
    private String mOperatorAlphaLong;
    private String mOperatorAlphaShort;
    private String mOperatorNumeric;
    private boolean mIsManualNetworkSelection;
    private int mCdmaSystemId = UNKNOWN_ID;
    private int mCdmaNetworkId = UNKNOWN_ID;
    private int mChannelNumber = UNKNOWN_ID;
    private int[] mCellBandwidths = new int[0];
    private int mDuplexMode = DUPLEX_MODE_UNKNOWN;
    private final List<NetworkRegistrationInfo> mNetworkRegistrationInfos =
            new ArrayList<NetworkRegistrationInfo>();
    private boolean mIsSearching;
    private boolean mIsUsingNonTerrestrialNetwork;

    public ServiceState() {
    }

    public ServiceState(ServiceState s) {
        copyFrom(s);
    }

    @Deprecated
    public ServiceState(Parcel in) {
        mVoiceRegState = in.readInt();
        mDataRegState = in.readInt();
        mRoaming = in.readInt() != 0;
        mOperatorAlphaLong = in.readString();
        mOperatorAlphaShort = in.readString();
        mOperatorNumeric = in.readString();
        mIsManualNetworkSelection = in.readInt() != 0;
        mCdmaSystemId = in.readInt();
        mCdmaNetworkId = in.readInt();
        mChannelNumber = in.readInt();
        int[] bw = in.createIntArray();
        mCellBandwidths = bw != null ? bw : new int[0];
        mDuplexMode = in.readInt();
        mNetworkRegistrationInfos.clear();
        in.readTypedList(mNetworkRegistrationInfos, NetworkRegistrationInfo.CREATOR);
        mIsSearching = in.readInt() != 0;
        mIsUsingNonTerrestrialNetwork = in.readInt() != 0;
    }

    protected void copyFrom(ServiceState s) {
        mVoiceRegState = s.mVoiceRegState;
        mDataRegState = s.mDataRegState;
        mRoaming = s.mRoaming;
        mOperatorAlphaLong = s.mOperatorAlphaLong;
        mOperatorAlphaShort = s.mOperatorAlphaShort;
        mOperatorNumeric = s.mOperatorNumeric;
        mIsManualNetworkSelection = s.mIsManualNetworkSelection;
        mCdmaSystemId = s.mCdmaSystemId;
        mCdmaNetworkId = s.mCdmaNetworkId;
        mChannelNumber = s.mChannelNumber;
        mCellBandwidths = s.mCellBandwidths != null ? s.mCellBandwidths.clone() : new int[0];
        mDuplexMode = s.mDuplexMode;
        synchronized (mNetworkRegistrationInfos) {
            mNetworkRegistrationInfos.clear();
            mNetworkRegistrationInfos.addAll(s.getNetworkRegistrationInfoList());
        }
        mIsSearching = s.mIsSearching;
        mIsUsingNonTerrestrialNetwork = s.mIsUsingNonTerrestrialNetwork;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mVoiceRegState);
        out.writeInt(mDataRegState);
        out.writeInt(mRoaming ? 1 : 0);
        out.writeString(mOperatorAlphaLong);
        out.writeString(mOperatorAlphaShort);
        out.writeString(mOperatorNumeric);
        out.writeInt(mIsManualNetworkSelection ? 1 : 0);
        out.writeInt(mCdmaSystemId);
        out.writeInt(mCdmaNetworkId);
        out.writeInt(mChannelNumber);
        out.writeIntArray(mCellBandwidths);
        out.writeInt(mDuplexMode);
        synchronized (mNetworkRegistrationInfos) {
            out.writeTypedList(mNetworkRegistrationInfos);
        }
        out.writeInt(mIsSearching ? 1 : 0);
        out.writeInt(mIsUsingNonTerrestrialNetwork ? 1 : 0);
    }

    public int describeContents() {
        return 0;
    }

    public int getState() {
        return mVoiceRegState;
    }

    public int getDuplexMode() {
        return mDuplexMode;
    }

    public int getChannelNumber() {
        return mChannelNumber;
    }

    public int[] getCellBandwidths() {
        return mCellBandwidths != null ? mCellBandwidths.clone() : new int[0];
    }

    public boolean getRoaming() {
        return mRoaming;
    }

    public String getOperatorAlphaLong() {
        return mOperatorAlphaLong;
    }

    public String getOperatorAlphaShort() {
        return mOperatorAlphaShort;
    }

    public String getOperatorNumeric() {
        return mOperatorNumeric;
    }

    public boolean getIsManualSelection() {
        return mIsManualNetworkSelection;
    }

    public int hashCode() {
        synchronized (mNetworkRegistrationInfos) {
            return Objects.hash(
                    Integer.valueOf(mVoiceRegState),
                    Integer.valueOf(mDataRegState),
                    Boolean.valueOf(mRoaming),
                    mOperatorAlphaLong,
                    mOperatorAlphaShort,
                    mOperatorNumeric,
                    Boolean.valueOf(mIsManualNetworkSelection),
                    Integer.valueOf(mCdmaSystemId),
                    Integer.valueOf(mCdmaNetworkId),
                    Integer.valueOf(mChannelNumber),
                    Integer.valueOf(Arrays.hashCode(mCellBandwidths)),
                    Integer.valueOf(mDuplexMode),
                    mNetworkRegistrationInfos,
                    Boolean.valueOf(mIsSearching),
                    Boolean.valueOf(mIsUsingNonTerrestrialNetwork));
        }
    }

    public boolean equals(Object o) {
        if (!(o instanceof ServiceState)) {
            return false;
        }
        ServiceState s = (ServiceState) o;
        return mVoiceRegState == s.mVoiceRegState
                && mDataRegState == s.mDataRegState
                && mRoaming == s.mRoaming
                && Objects.equals(mOperatorAlphaLong, s.mOperatorAlphaLong)
                && Objects.equals(mOperatorAlphaShort, s.mOperatorAlphaShort)
                && Objects.equals(mOperatorNumeric, s.mOperatorNumeric)
                && mIsManualNetworkSelection == s.mIsManualNetworkSelection
                && mCdmaSystemId == s.mCdmaSystemId
                && mCdmaNetworkId == s.mCdmaNetworkId
                && mChannelNumber == s.mChannelNumber
                && Arrays.equals(mCellBandwidths, s.mCellBandwidths)
                && mDuplexMode == s.mDuplexMode
                && Objects.equals(getNetworkRegistrationInfoList(),
                        s.getNetworkRegistrationInfoList())
                && mIsSearching == s.mIsSearching
                && mIsUsingNonTerrestrialNetwork == s.mIsUsingNonTerrestrialNetwork;
    }

    public String toString() {
        return "{mVoiceRegState=" + mVoiceRegState
                + ", mDataRegState=" + mDataRegState
                + ", mRoaming=" + mRoaming
                + ", mOperatorAlphaLong=" + mOperatorAlphaLong
                + ", mOperatorAlphaShort=" + mOperatorAlphaShort
                + ", mOperatorNumeric=" + mOperatorNumeric
                + ", mIsManualNetworkSelection=" + mIsManualNetworkSelection
                + ", mNetworkRegistrationInfos=" + getNetworkRegistrationInfoList()
                + ", mIsSearching=" + mIsSearching
                + ", mIsUsingNonTerrestrialNetwork=" + mIsUsingNonTerrestrialNetwork + "}";
    }

    public void setStateOutOfService() {
        setNullState(STATE_OUT_OF_SERVICE);
    }

    public void setStateOff() {
        setNullState(STATE_POWER_OFF);
    }

    private void setNullState(int state) {
        mVoiceRegState = state;
        mDataRegState = state;
        mRoaming = false;
        mOperatorAlphaLong = null;
        mOperatorAlphaShort = null;
        mOperatorNumeric = null;
        mIsManualNetworkSelection = false;
        mCdmaSystemId = UNKNOWN_ID;
        mCdmaNetworkId = UNKNOWN_ID;
        mChannelNumber = UNKNOWN_ID;
        mCellBandwidths = new int[0];
        mDuplexMode = DUPLEX_MODE_UNKNOWN;
        synchronized (mNetworkRegistrationInfos) {
            mNetworkRegistrationInfos.clear();
        }
        mIsSearching = false;
        mIsUsingNonTerrestrialNetwork = false;
    }

    public void setState(int state) {
        mVoiceRegState = state;
    }

    public void setRoaming(boolean roaming) {
        mRoaming = roaming;
    }

    public void setOperatorName(String longName, String shortName, String numeric) {
        mOperatorAlphaLong = longName;
        mOperatorAlphaShort = shortName;
        mOperatorNumeric = numeric;
    }

    public void setIsManualSelection(boolean isManual) {
        mIsManualNetworkSelection = isManual;
    }

    public int getCdmaNetworkId() {
        return mCdmaNetworkId;
    }

    public int getCdmaSystemId() {
        return mCdmaSystemId;
    }

    public List<NetworkRegistrationInfo> getNetworkRegistrationInfoList() {
        synchronized (mNetworkRegistrationInfos) {
            return Collections.unmodifiableList(
                    new ArrayList<NetworkRegistrationInfo>(mNetworkRegistrationInfos));
        }
    }

    void addNetworkRegistrationInfo(NetworkRegistrationInfo nri) {
        if (nri == null) {
            return;
        }
        synchronized (mNetworkRegistrationInfos) {
            mNetworkRegistrationInfos.add(nri);
            if (nri.isNetworkSearching()) {
                mIsSearching = true;
            }
            if (nri.isNonTerrestrialNetwork()) {
                mIsUsingNonTerrestrialNetwork = true;
            }
        }
    }

    public boolean isSearching() {
        if (mIsSearching) {
            return true;
        }
        List<NetworkRegistrationInfo> list = getNetworkRegistrationInfoList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).isNetworkSearching()) {
                return true;
            }
        }
        return false;
    }

    public boolean isUsingNonTerrestrialNetwork() {
        return mIsUsingNonTerrestrialNetwork;
    }

    public static final Parcelable.Creator<ServiceState> CREATOR =
            new Parcelable.Creator<ServiceState>() {
                public ServiceState createFromParcel(Parcel in) {
                    return new ServiceState(in);
                }

                public ServiceState[] newArray(int size) {
                    return new ServiceState[size];
                }
            };
}
