// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.data;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class ApnSetting implements Parcelable {
    public static final int TYPE_DEFAULT = 17;
    public static final int TYPE_MMS = 1 << 1;
    public static final int TYPE_SUPL = 1 << 2;
    public static final int TYPE_DUN = 1 << 3;
    public static final int TYPE_HIPRI = 1 << 4;
    public static final int TYPE_FOTA = 1 << 5;
    public static final int TYPE_IMS = 1 << 6;
    public static final int TYPE_CBS = 1 << 7;
    public static final int TYPE_IA = 1 << 8;
    public static final int TYPE_EMERGENCY = 1 << 9;
    public static final int TYPE_MCX = 1 << 10;
    public static final int TYPE_XCAP = 1 << 11;
    public static final int TYPE_VSIM = 1 << 12;
    public static final int TYPE_BIP = 1 << 13;
    public static final int TYPE_ENTERPRISE = 1 << 14;
    public static final int TYPE_RCS = 1 << 15;

    public static final int AUTH_TYPE_NONE = 0;
    public static final int AUTH_TYPE_PAP = 1;
    public static final int AUTH_TYPE_CHAP = 2;
    public static final int AUTH_TYPE_PAP_OR_CHAP = 3;

    public static final int PROTOCOL_IP = 0;
    public static final int PROTOCOL_IPV6 = 1;
    public static final int PROTOCOL_IPV4V6 = 2;
    public static final int PROTOCOL_PPP = 3;
    public static final int PROTOCOL_NON_IP = 4;
    public static final int PROTOCOL_UNSTRUCTURED = 5;

    public static final int MVNO_TYPE_SPN = 0;
    public static final int MVNO_TYPE_IMSI = 1;
    public static final int MVNO_TYPE_GID = 2;
    public static final int MVNO_TYPE_ICCID = 3;

    private final String mEntryName;
    private final String mApnName;
    private final String mProxyAddress;
    private final int mProxyPort;
    private final Uri mMmsc;
    private final String mMmsProxyAddress;
    private final int mMmsProxyPort;
    private final String mUser;
    private final String mPassword;
    private final int mAuthType;
    private final int mApnTypeBitmask;
    private final int mId;
    private final String mOperatorNumeric;
    private final int mProtocol;
    private final int mRoamingProtocol;
    private final int mMtuV4;
    private final int mMtuV6;
    private final boolean mCarrierEnabled;
    private final int mNetworkTypeBitmask;
    private final int mProfileId;
    private final boolean mPersistent;
    private final int mMvnoType;
    private final int mCarrierId;
    private final boolean mAlwaysOn;

    private ApnSetting(Builder builder) {
        mEntryName = builder.mEntryName;
        mApnName = builder.mApnName;
        mProxyAddress = builder.mProxyAddress;
        mProxyPort = builder.mProxyPort;
        mMmsc = builder.mMmsc;
        mMmsProxyAddress = builder.mMmsProxyAddress;
        mMmsProxyPort = builder.mMmsProxyPort;
        mUser = builder.mUser;
        mPassword = builder.mPassword;
        mAuthType = builder.mAuthType;
        mApnTypeBitmask = builder.mApnTypeBitmask;
        mId = builder.mId;
        mOperatorNumeric = builder.mOperatorNumeric;
        mProtocol = builder.mProtocol;
        mRoamingProtocol = builder.mRoamingProtocol;
        mMtuV4 = builder.mMtuV4;
        mMtuV6 = builder.mMtuV6;
        mCarrierEnabled = builder.mCarrierEnabled;
        mNetworkTypeBitmask = builder.mNetworkTypeBitmask;
        mProfileId = builder.mProfileId;
        mPersistent = builder.mPersistent;
        mMvnoType = builder.mMvnoType;
        mCarrierId = builder.mCarrierId;
        mAlwaysOn = builder.mAlwaysOn;
    }

    private ApnSetting(Parcel in) {
        mId = in.readInt();
        mOperatorNumeric = in.readString();
        mEntryName = in.readString();
        mApnName = in.readString();
        mProxyAddress = in.readString();
        mProxyPort = in.readInt();
        String mmscStr = in.readString();
        mMmsc = mmscStr != null ? Uri.parse(mmscStr) : null;
        mMmsProxyAddress = in.readString();
        mMmsProxyPort = in.readInt();
        mUser = in.readString();
        mPassword = in.readString();
        mAuthType = in.readInt();
        mApnTypeBitmask = in.readInt();
        mProtocol = in.readInt();
        mRoamingProtocol = in.readInt();
        mCarrierEnabled = in.readBoolean();
        mNetworkTypeBitmask = in.readInt();
        mProfileId = in.readInt();
        mPersistent = in.readBoolean();
        mMtuV4 = in.readInt();
        mMtuV6 = in.readInt();
        mMvnoType = in.readInt();
        mCarrierId = in.readInt();
        mAlwaysOn = in.readBoolean();
    }

    public int getMtuV4() {
        return mMtuV4;
    }

    public int getMtuV6() {
        return mMtuV6;
    }

    public int getProfileId() {
        return mProfileId;
    }

    public boolean isPersistent() {
        return mPersistent;
    }

    public String getEntryName() {
        return mEntryName;
    }

    public String getApnName() {
        return mApnName;
    }

    @Deprecated
    public InetAddress getProxyAddress() {
        return inetAddressFromString(mProxyAddress);
    }

    public String getProxyAddressAsString() {
        return mProxyAddress;
    }

    public int getProxyPort() {
        return mProxyPort;
    }

    public Uri getMmsc() {
        return mMmsc;
    }

    @Deprecated
    public InetAddress getMmsProxyAddress() {
        return inetAddressFromString(mMmsProxyAddress);
    }

    public String getMmsProxyAddressAsString() {
        return mMmsProxyAddress;
    }

    public int getMmsProxyPort() {
        return mMmsProxyPort;
    }

    public String getUser() {
        return mUser;
    }

    public String getPassword() {
        return mPassword;
    }

    public int getAuthType() {
        return mAuthType;
    }

    public int getApnTypeBitmask() {
        return mApnTypeBitmask;
    }

    public int getId() {
        return mId;
    }

    public String getOperatorNumeric() {
        return mOperatorNumeric;
    }

    public int getProtocol() {
        return mProtocol;
    }

    public int getRoamingProtocol() {
        return mRoamingProtocol;
    }

    public boolean isEnabled() {
        return mCarrierEnabled;
    }

    public int getNetworkTypeBitmask() {
        return mNetworkTypeBitmask;
    }

    public int getMvnoType() {
        return mMvnoType;
    }

    public int getCarrierId() {
        return mCarrierId;
    }

    public boolean isAlwaysOn() {
        return mAlwaysOn;
    }

    private static InetAddress inetAddressFromString(String addr) {
        if (TextUtils.isEmpty(addr)) {
            return null;
        }
        try {
            return InetAddress.getByName(addr);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public String toString() {
        return "[ApnSetting] " + mEntryName
                + ", " + mId
                + ", " + mOperatorNumeric
                + ", " + mApnName
                + ", " + mProxyAddress
                + ", " + mMmsc
                + ", " + mMmsProxyAddress
                + ", " + mMmsProxyPort
                + ", " + mProxyPort
                + ", " + mAuthType
                + ", 0x" + Integer.toHexString(mApnTypeBitmask)
                + ", " + mProtocol
                + ", " + mRoamingProtocol
                + ", " + mCarrierEnabled
                + ", " + mProfileId
                + ", " + mPersistent
                + ", " + mMtuV4
                + ", " + mMtuV6
                + ", " + mNetworkTypeBitmask
                + ", " + mMvnoType
                + ", " + mCarrierId
                + ", " + mAlwaysOn;
    }

    public int hashCode() {
        return Objects.hash(
                mApnName,
                mEntryName,
                Integer.valueOf(mAuthType),
                Integer.valueOf(mApnTypeBitmask),
                mProxyAddress,
                Integer.valueOf(mProxyPort),
                mMmsc,
                mMmsProxyAddress,
                Integer.valueOf(mMmsProxyPort),
                Integer.valueOf(mProtocol),
                Integer.valueOf(mRoamingProtocol),
                Boolean.valueOf(mCarrierEnabled),
                Integer.valueOf(mProfileId),
                Boolean.valueOf(mPersistent),
                Integer.valueOf(mMtuV4),
                Integer.valueOf(mMtuV6),
                Integer.valueOf(mNetworkTypeBitmask),
                Integer.valueOf(mMvnoType),
                mOperatorNumeric,
                Integer.valueOf(mCarrierId),
                Boolean.valueOf(mAlwaysOn));
    }

    public boolean equals(Object o) {
        if (!(o instanceof ApnSetting)) {
            return false;
        }
        ApnSetting other = (ApnSetting) o;
        return Objects.equals(mEntryName, other.mEntryName)
                && mId == other.mId
                && Objects.equals(mOperatorNumeric, other.mOperatorNumeric)
                && Objects.equals(mApnName, other.mApnName)
                && Objects.equals(mProxyAddress, other.mProxyAddress)
                && Objects.equals(mMmsc, other.mMmsc)
                && Objects.equals(mMmsProxyAddress, other.mMmsProxyAddress)
                && mMmsProxyPort == other.mMmsProxyPort
                && mProxyPort == other.mProxyPort
                && Objects.equals(mUser, other.mUser)
                && Objects.equals(mPassword, other.mPassword)
                && mAuthType == other.mAuthType
                && mApnTypeBitmask == other.mApnTypeBitmask
                && mProtocol == other.mProtocol
                && mRoamingProtocol == other.mRoamingProtocol
                && mCarrierEnabled == other.mCarrierEnabled
                && mProfileId == other.mProfileId
                && mPersistent == other.mPersistent
                && mMtuV4 == other.mMtuV4
                && mMtuV6 == other.mMtuV6
                && mNetworkTypeBitmask == other.mNetworkTypeBitmask
                && mMvnoType == other.mMvnoType
                && mCarrierId == other.mCarrierId
                && mAlwaysOn == other.mAlwaysOn;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeString(mOperatorNumeric);
        dest.writeString(mEntryName);
        dest.writeString(mApnName);
        dest.writeString(mProxyAddress);
        dest.writeInt(mProxyPort);
        dest.writeString(mMmsc != null ? mMmsc.toString() : null);
        dest.writeString(mMmsProxyAddress);
        dest.writeInt(mMmsProxyPort);
        dest.writeString(mUser);
        dest.writeString(mPassword);
        dest.writeInt(mAuthType);
        dest.writeInt(mApnTypeBitmask);
        dest.writeInt(mProtocol);
        dest.writeInt(mRoamingProtocol);
        dest.writeBoolean(mCarrierEnabled);
        dest.writeInt(mNetworkTypeBitmask);
        dest.writeInt(mProfileId);
        dest.writeBoolean(mPersistent);
        dest.writeInt(mMtuV4);
        dest.writeInt(mMtuV6);
        dest.writeInt(mMvnoType);
        dest.writeInt(mCarrierId);
        dest.writeBoolean(mAlwaysOn);
    }

    public static final Parcelable.Creator<ApnSetting> CREATOR =
            new Parcelable.Creator<ApnSetting>() {
                public ApnSetting createFromParcel(Parcel in) {
                    return new ApnSetting(in);
                }

                public ApnSetting[] newArray(int size) {
                    return new ApnSetting[size];
                }
            };

    public static class Builder {
        private String mEntryName;
        private String mApnName;
        private String mProxyAddress;
        private int mProxyPort = -1;
        private Uri mMmsc;
        private String mMmsProxyAddress;
        private int mMmsProxyPort = -1;
        private String mUser;
        private String mPassword;
        private int mAuthType = AUTH_TYPE_NONE;
        private int mApnTypeBitmask;
        private int mId;
        private String mOperatorNumeric;
        private int mProtocol = -1;
        private int mRoamingProtocol = -1;
        private int mMtuV4;
        private int mMtuV6;
        private boolean mCarrierEnabled;
        private int mNetworkTypeBitmask;
        private int mProfileId;
        private boolean mPersistent;
        private int mMvnoType = -1;
        private int mCarrierId = -1;
        private boolean mAlwaysOn;

        public Builder() {}

        public Builder setMtuV4(int mtuV4) {
            mMtuV4 = mtuV4;
            return this;
        }

        public Builder setMtuV6(int mtuV6) {
            mMtuV6 = mtuV6;
            return this;
        }

        public Builder setProfileId(int profileId) {
            mProfileId = profileId;
            return this;
        }

        public Builder setPersistent(boolean isPersistent) {
            mPersistent = isPersistent;
            return this;
        }

        public Builder setEntryName(String entryName) {
            mEntryName = entryName;
            return this;
        }

        public Builder setApnName(String apnName) {
            mApnName = apnName;
            return this;
        }

        @Deprecated
        public Builder setProxyAddress(InetAddress proxy) {
            mProxyAddress = proxy != null ? proxy.getHostAddress() : null;
            return this;
        }

        public Builder setProxyAddress(String proxy) {
            mProxyAddress = proxy;
            return this;
        }

        public Builder setProxyPort(int port) {
            mProxyPort = port;
            return this;
        }

        public Builder setMmsc(Uri mmsc) {
            mMmsc = mmsc;
            return this;
        }

        @Deprecated
        public Builder setMmsProxyAddress(InetAddress mmsProxy) {
            mMmsProxyAddress = mmsProxy != null ? mmsProxy.getHostAddress() : null;
            return this;
        }

        public Builder setMmsProxyAddress(String mmsProxy) {
            mMmsProxyAddress = mmsProxy;
            return this;
        }

        public Builder setMmsProxyPort(int mmsPort) {
            mMmsProxyPort = mmsPort;
            return this;
        }

        public Builder setUser(String user) {
            mUser = user;
            return this;
        }

        public Builder setPassword(String password) {
            mPassword = password;
            return this;
        }

        public Builder setAuthType(int authType) {
            mAuthType = authType;
            return this;
        }

        public Builder setApnTypeBitmask(int apnTypeBitmask) {
            mApnTypeBitmask = apnTypeBitmask;
            return this;
        }

        public Builder setOperatorNumeric(String operatorNumeric) {
            mOperatorNumeric = operatorNumeric;
            return this;
        }

        public Builder setProtocol(int protocol) {
            mProtocol = protocol;
            return this;
        }

        public Builder setRoamingProtocol(int roamingProtocol) {
            mRoamingProtocol = roamingProtocol;
            return this;
        }

        public Builder setCarrierEnabled(boolean carrierEnabled) {
            mCarrierEnabled = carrierEnabled;
            return this;
        }

        public Builder setNetworkTypeBitmask(int networkTypeBitmask) {
            mNetworkTypeBitmask = networkTypeBitmask;
            return this;
        }

        public Builder setMvnoType(int mvnoType) {
            mMvnoType = mvnoType;
            return this;
        }

        public Builder setCarrierId(int carrierId) {
            mCarrierId = carrierId;
            return this;
        }

        public Builder setAlwaysOn(boolean alwaysOn) {
            mAlwaysOn = alwaysOn;
            return this;
        }

        public ApnSetting build() {
            if ((mApnTypeBitmask & ((1 << 16) - 1)) == 0
                    || TextUtils.isEmpty(mApnName)
                    || TextUtils.isEmpty(mEntryName)) {
                return null;
            }
            if ((mApnTypeBitmask & TYPE_MMS) != 0 && !TextUtils.isEmpty(mMmsProxyAddress)
                    && mMmsProxyAddress.startsWith("http")) {
                throw new IllegalArgumentException("mmsProxy(" + mMmsProxyAddress + ") should be a hostname, not a url");
            }
            return new ApnSetting(this);
        }
    }
}
