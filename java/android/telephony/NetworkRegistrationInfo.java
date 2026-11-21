// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class NetworkRegistrationInfo implements Parcelable {
    public static final int DOMAIN_UNKNOWN = 0;
    public static final int DOMAIN_CS = 1;
    public static final int DOMAIN_PS = 2;
    public static final int DOMAIN_CS_PS = 3;

    public static final int SERVICE_TYPE_UNKNOWN = 0;
    public static final int SERVICE_TYPE_VOICE = 1;
    public static final int SERVICE_TYPE_DATA = 2;
    public static final int SERVICE_TYPE_SMS = 3;
    public static final int SERVICE_TYPE_VIDEO = 4;
    public static final int SERVICE_TYPE_EMERGENCY = 5;
    public static final int SERVICE_TYPE_MMS = 6;

    public static final int NR_STATE_NONE = 0;
    public static final int NR_STATE_RESTRICTED = 1;
    public static final int NR_STATE_NOT_RESTRICTED = 2;
    public static final int NR_STATE_CONNECTED = 3;

    static final int REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING = 0;
    static final int REGISTRATION_STATE_HOME = 1;
    static final int REGISTRATION_STATE_NOT_REGISTERED_SEARCHING = 2;
    static final int REGISTRATION_STATE_DENIED = 3;
    static final int REGISTRATION_STATE_UNKNOWN = 4;
    static final int REGISTRATION_STATE_ROAMING = 5;

    private final int mDomain;
    private final int mTransportType;
    private final int mRegistrationState;
    private final int mRoamingType;
    private final int mAccessNetworkTechnology;
    private final int mRejectCause;
    private final boolean mEmergencyOnly;
    private final ArrayList<Integer> mAvailableServices;
    private final CellIdentity mCellIdentity;
    private final String mRplmn;
    private final boolean mIsNonTerrestrialNetwork;

    NetworkRegistrationInfo(int domain, int transportType, int registrationState,
            int accessNetworkTechnology, int rejectCause, boolean emergencyOnly,
            List<Integer> availableServices, CellIdentity cellIdentity, String rplmn,
            boolean isNonTerrestrialNetwork) {
        mDomain = domain;
        mTransportType = transportType;
        mRegistrationState = registrationState;
        mRoamingType = (registrationState == REGISTRATION_STATE_ROAMING) ? 1 : 0;
        mAccessNetworkTechnology = accessNetworkTechnology;
        mRejectCause = rejectCause;
        mEmergencyOnly = emergencyOnly;
        mAvailableServices = availableServices != null
                ? new ArrayList<Integer>(availableServices) : new ArrayList<Integer>();
        mCellIdentity = cellIdentity;
        mRplmn = rplmn;
        mIsNonTerrestrialNetwork = isNonTerrestrialNetwork;
    }

    private NetworkRegistrationInfo(Parcel source) {
        mDomain = source.readInt();
        mTransportType = source.readInt();
        mRegistrationState = source.readInt();
        mRoamingType = source.readInt();
        mAccessNetworkTechnology = source.readInt();
        mRejectCause = source.readInt();
        mEmergencyOnly = source.readInt() != 0;
        int svcCount = source.readInt();
        mAvailableServices = new ArrayList<Integer>(Math.max(0, svcCount));
        for (int i = 0; i < svcCount; i++) {
            mAvailableServices.add(Integer.valueOf(source.readInt()));
        }
        if (source.readInt() != 0) {
            mCellIdentity = CellIdentity.CREATOR.createFromParcel(source);
        } else {
            mCellIdentity = null;
        }
        mRplmn = source.readString();
        mIsNonTerrestrialNetwork = source.readInt() != 0;
    }

    public int getTransportType() {
        return mTransportType;
    }

    public int getDomain() {
        return mDomain;
    }

    @Deprecated
    public boolean isRegistered() {
        return isNetworkRegistered();
    }

    public boolean isNetworkRegistered() {
        return mRegistrationState == REGISTRATION_STATE_HOME
                || mRegistrationState == REGISTRATION_STATE_ROAMING;
    }

    @Deprecated
    public boolean isSearching() {
        return isNetworkSearching();
    }

    public boolean isNetworkSearching() {
        return mRegistrationState == REGISTRATION_STATE_NOT_REGISTERED_SEARCHING;
    }

    public String getRegisteredPlmn() {
        return mRplmn;
    }

    @Deprecated
    public boolean isRoaming() {
        return isNetworkRoaming();
    }

    public boolean isNetworkRoaming() {
        return mRoamingType != 0;
    }

    public List<Integer> getAvailableServices() {
        return Collections.unmodifiableList(mAvailableServices);
    }

    public int getAccessNetworkTechnology() {
        return mAccessNetworkTechnology;
    }

    public int getRejectCause() {
        return mRejectCause;
    }

    public CellIdentity getCellIdentity() {
        return mCellIdentity;
    }

    public boolean isNonTerrestrialNetwork() {
        return mIsNonTerrestrialNetwork;
    }

    public int describeContents() {
        return 0;
    }

    public String toString() {
        return "NetworkRegistrationInfo{domain=" + mDomain
                + " transportType=" + mTransportType
                + " registrationState=" + mRegistrationState
                + " accessNetworkTechnology=" + mAccessNetworkTechnology
                + " rejectCause=" + mRejectCause
                + " emergencyEnabled=" + mEmergencyOnly
                + " availableServices=" + mAvailableServices
                + " cellIdentity=" + mCellIdentity
                + " rplmn=" + mRplmn
                + " isNonTerrestrialNetwork=" + mIsNonTerrestrialNetwork + "}";
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mDomain),
                Integer.valueOf(mTransportType),
                Integer.valueOf(mRegistrationState),
                Integer.valueOf(mRoamingType),
                Integer.valueOf(mAccessNetworkTechnology),
                Integer.valueOf(mRejectCause),
                Boolean.valueOf(mEmergencyOnly),
                mAvailableServices,
                mCellIdentity,
                mRplmn,
                Boolean.valueOf(mIsNonTerrestrialNetwork));
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NetworkRegistrationInfo)) {
            return false;
        }
        NetworkRegistrationInfo other = (NetworkRegistrationInfo) o;
        return mDomain == other.mDomain
                && mTransportType == other.mTransportType
                && mRegistrationState == other.mRegistrationState
                && mRoamingType == other.mRoamingType
                && mAccessNetworkTechnology == other.mAccessNetworkTechnology
                && mRejectCause == other.mRejectCause
                && mEmergencyOnly == other.mEmergencyOnly
                && Objects.equals(mAvailableServices, other.mAvailableServices)
                && Objects.equals(mCellIdentity, other.mCellIdentity)
                && Objects.equals(mRplmn, other.mRplmn)
                && mIsNonTerrestrialNetwork == other.mIsNonTerrestrialNetwork;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDomain);
        dest.writeInt(mTransportType);
        dest.writeInt(mRegistrationState);
        dest.writeInt(mRoamingType);
        dest.writeInt(mAccessNetworkTechnology);
        dest.writeInt(mRejectCause);
        dest.writeInt(mEmergencyOnly ? 1 : 0);
        dest.writeInt(mAvailableServices.size());
        for (int i = 0; i < mAvailableServices.size(); i++) {
            dest.writeInt(mAvailableServices.get(i).intValue());
        }
        if (mCellIdentity != null) {
            dest.writeInt(1);
            mCellIdentity.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeString(mRplmn);
        dest.writeInt(mIsNonTerrestrialNetwork ? 1 : 0);
    }

    public static final Parcelable.Creator<NetworkRegistrationInfo> CREATOR =
            new Parcelable.Creator<NetworkRegistrationInfo>() {
                public NetworkRegistrationInfo createFromParcel(Parcel source) {
                    return new NetworkRegistrationInfo(source);
                }

                public NetworkRegistrationInfo[] newArray(int size) {
                    return new NetworkRegistrationInfo[size];
                }
            };
}
