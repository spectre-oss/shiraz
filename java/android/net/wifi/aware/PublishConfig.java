// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import android.net.wifi.ScanResult;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PublishConfig implements Parcelable {
    public static final int PUBLISH_TYPE_UNSOLICITED = 0;
    public static final int PUBLISH_TYPE_SOLICITED = 1;

    public static final Creator<PublishConfig> CREATOR =
            new Creator<PublishConfig>() {
                @Override
                public PublishConfig createFromParcel(Parcel in) {
                    String serviceName = in.readString();
                    byte[] ssi = in.createByteArray();
                    int mfCount = in.readInt();
                    List<byte[]> mf = null;
                    if (mfCount >= 0) {
                        mf = new ArrayList<byte[]>(mfCount);
                        for (int i = 0; i < mfCount; i++) {
                            mf.add(in.createByteArray());
                        }
                    }
                    int publishType = in.readInt();
                    int ttlSec = in.readInt();
                    boolean termNotif = in.readInt() != 0;
                    boolean ranging = in.readInt() != 0;
                    boolean instantMode = in.readInt() != 0;
                    int instantBand = in.readInt();
                    WifiAwareDataPathSecurityConfig sec =
                            in.readTypedObject(WifiAwareDataPathSecurityConfig.CREATOR);
                    AwarePairingConfig pairing =
                            in.readTypedObject(AwarePairingConfig.CREATOR);
                    return new PublishConfig(
                            serviceName,
                            ssi,
                            mf,
                            publishType,
                            ttlSec,
                            termNotif,
                            ranging,
                            instantMode,
                            instantBand,
                            sec,
                            pairing);
                }

                @Override
                public PublishConfig[] newArray(int size) {
                    return new PublishConfig[size];
                }
            };

    final String mServiceName;
    final byte[] mServiceSpecificInfo;
    final List<byte[]> mMatchFilter;
    final int mPublishType;
    final int mTtlSec;
    final boolean mEnableTerminateNotification;
    final boolean mEnableRanging;
    final boolean mEnableInstantMode;
    final int mInstantCommunicationBand;
    final WifiAwareDataPathSecurityConfig mSecurityConfig;
    final AwarePairingConfig mPairingConfig;

    PublishConfig(
            String serviceName,
            byte[] serviceSpecificInfo,
            List<byte[]> matchFilter,
            int publishType,
            int ttlSec,
            boolean enableTerminateNotification,
            boolean enableRanging,
            boolean enableInstantMode,
            int instantCommunicationBand,
            WifiAwareDataPathSecurityConfig securityConfig,
            AwarePairingConfig pairingConfig) {
        mServiceName = serviceName;
        mServiceSpecificInfo = serviceSpecificInfo == null ? null : serviceSpecificInfo.clone();
        if (matchFilter == null) {
            mMatchFilter = null;
        } else {
            List<byte[]> copy = new ArrayList<byte[]>(matchFilter.size());
            for (int i = 0; i < matchFilter.size(); i++) {
                byte[] elem = matchFilter.get(i);
                copy.add(elem == null ? null : elem.clone());
            }
            mMatchFilter = Collections.unmodifiableList(copy);
        }
        mPublishType = publishType;
        mTtlSec = ttlSec;
        mEnableTerminateNotification = enableTerminateNotification;
        mEnableRanging = enableRanging;
        mEnableInstantMode = enableInstantMode;
        mInstantCommunicationBand = instantCommunicationBand;
        mSecurityConfig = securityConfig;
        mPairingConfig = pairingConfig;
    }

    @Override
    public String toString() {
        return "PublishConfig [mServiceName='"
                + mServiceName
                + "', mPublishType="
                + mPublishType
                + ", mTtlSec="
                + mTtlSec
                + ", mEnableTerminateNotification="
                + mEnableTerminateNotification
                + ", mEnableRanging="
                + mEnableRanging
                + ", mEnableInstantMode="
                + mEnableInstantMode
                + ", mInstantCommunicationBand="
                + mInstantCommunicationBand
                + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mServiceName);
        dest.writeByteArray(mServiceSpecificInfo);
        if (mMatchFilter == null) {
            dest.writeInt(-1);
        } else {
            dest.writeInt(mMatchFilter.size());
            for (int i = 0; i < mMatchFilter.size(); i++) {
                dest.writeByteArray(mMatchFilter.get(i));
            }
        }
        dest.writeInt(mPublishType);
        dest.writeInt(mTtlSec);
        dest.writeInt(mEnableTerminateNotification ? 1 : 0);
        dest.writeInt(mEnableRanging ? 1 : 0);
        dest.writeInt(mEnableInstantMode ? 1 : 0);
        dest.writeInt(mInstantCommunicationBand);
        dest.writeTypedObject(mSecurityConfig, flags);
        dest.writeTypedObject(mPairingConfig, flags);
    }

    private static boolean filterListEquals(List<byte[]> a, List<byte[]> b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!Arrays.equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static int filterListHash(List<byte[]> list) {
        if (list == null) {
            return 0;
        }
        int h = 1;
        for (int i = 0; i < list.size(); i++) {
            h = 31 * h + Arrays.hashCode(list.get(i));
        }
        return h;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PublishConfig)) {
            return false;
        }
        PublishConfig lhs = (PublishConfig) o;
        return Objects.equals(mServiceName, lhs.mServiceName)
                && Arrays.equals(mServiceSpecificInfo, lhs.mServiceSpecificInfo)
                && filterListEquals(mMatchFilter, lhs.mMatchFilter)
                && mPublishType == lhs.mPublishType
                && mTtlSec == lhs.mTtlSec
                && mEnableTerminateNotification == lhs.mEnableTerminateNotification
                && mEnableRanging == lhs.mEnableRanging
                && mEnableInstantMode == lhs.mEnableInstantMode
                && mInstantCommunicationBand == lhs.mInstantCommunicationBand
                && Objects.equals(mSecurityConfig, lhs.mSecurityConfig)
                && Objects.equals(mPairingConfig, lhs.mPairingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mServiceName,
                Integer.valueOf(Arrays.hashCode(mServiceSpecificInfo)),
                Integer.valueOf(filterListHash(mMatchFilter)),
                Integer.valueOf(mPublishType),
                Integer.valueOf(mTtlSec),
                Boolean.valueOf(mEnableTerminateNotification),
                Boolean.valueOf(mEnableRanging),
                Boolean.valueOf(mEnableInstantMode),
                Integer.valueOf(mInstantCommunicationBand),
                mSecurityConfig,
                mPairingConfig);
    }

    public boolean isInstantCommunicationModeEnabled() {
        return mEnableInstantMode;
    }

    public int getInstantCommunicationBand() {
        return mInstantCommunicationBand;
    }

    public WifiAwareDataPathSecurityConfig getSecurityConfig() {
        return mSecurityConfig;
    }

    public AwarePairingConfig getPairingConfig() {
        return mPairingConfig;
    }

    public static final class Builder {
        private String mServiceName;
        private byte[] mServiceSpecificInfo;
        private List<byte[]> mMatchFilter;
        private int mPublishType = PUBLISH_TYPE_UNSOLICITED;
        private int mTtlSec = 0;
        private boolean mEnableTerminateNotification = true;
        private boolean mEnableRanging = false;
        private boolean mEnableInstantMode = false;
        private int mInstantCommunicationBand = ScanResult.WIFI_BAND_24_GHZ;
        private WifiAwareDataPathSecurityConfig mSecurityConfig;
        private AwarePairingConfig mPairingConfig;

        public Builder() {}

        public Builder setServiceName(String serviceName) {
            if (serviceName == null) {
                throw new IllegalArgumentException("Invalid service name - must not be null");
            }
            mServiceName = serviceName;
            return this;
        }

        public Builder setServiceSpecificInfo(byte[] serviceSpecificInfo) {
            mServiceSpecificInfo = serviceSpecificInfo == null ? null : serviceSpecificInfo.clone();
            return this;
        }

        public Builder setMatchFilter(List<byte[]> matchFilter) {
            mMatchFilter = matchFilter;
            return this;
        }

        public Builder setPublishType(int publishType) {
            if (publishType < PUBLISH_TYPE_UNSOLICITED || publishType > PUBLISH_TYPE_SOLICITED) {
                throw new IllegalArgumentException("Invalid publishType - " + publishType);
            }
            mPublishType = publishType;
            return this;
        }

        public Builder setTtlSec(int ttlSec) {
            if (ttlSec < 0) {
                throw new IllegalArgumentException("Invalid ttlSec - must be non-negative");
            }
            mTtlSec = ttlSec;
            return this;
        }

        public Builder setTerminateNotificationEnabled(boolean enable) {
            mEnableTerminateNotification = enable;
            return this;
        }

        public Builder setRangingEnabled(boolean enable) {
            mEnableRanging = enable;
            return this;
        }

        public Builder setInstantCommunicationModeEnabled(boolean enabled, int band) {
            mEnableInstantMode = enabled;
            mInstantCommunicationBand = band;
            return this;
        }

        public Builder setDataPathSecurityConfig(WifiAwareDataPathSecurityConfig securityConfig) {
            mSecurityConfig = Objects.requireNonNull(securityConfig, "securityConfig");
            return this;
        }

        public Builder setPairingConfig(AwarePairingConfig config) {
            mPairingConfig = config;
            return this;
        }

        public PublishConfig build() {
            if (mServiceName == null) {
                throw new IllegalArgumentException("Service name must be specified");
            }
            return new PublishConfig(
                    mServiceName,
                    mServiceSpecificInfo,
                    mMatchFilter,
                    mPublishType,
                    mTtlSec,
                    mEnableTerminateNotification,
                    mEnableRanging,
                    mEnableInstantMode,
                    mInstantCommunicationBand,
                    mSecurityConfig,
                    mPairingConfig);
        }
    }
}
