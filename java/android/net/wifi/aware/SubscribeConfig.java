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

public final class SubscribeConfig implements Parcelable {
    public static final int SUBSCRIBE_TYPE_PASSIVE = 0;
    public static final int SUBSCRIBE_TYPE_ACTIVE = 1;

    public static final Creator<SubscribeConfig> CREATOR =
            new Creator<SubscribeConfig>() {
                @Override
                public SubscribeConfig createFromParcel(Parcel in) {
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
                    int subscribeType = in.readInt();
                    int ttlSec = in.readInt();
                    boolean termNotif = in.readInt() != 0;
                    boolean minDistanceSet = in.readInt() != 0;
                    int minDistanceMm = in.readInt();
                    boolean maxDistanceSet = in.readInt() != 0;
                    int maxDistanceMm = in.readInt();
                    boolean instantMode = in.readInt() != 0;
                    int instantBand = in.readInt();
                    AwarePairingConfig pairing =
                            in.readTypedObject(AwarePairingConfig.CREATOR);
                    return new SubscribeConfig(
                            serviceName,
                            ssi,
                            mf,
                            subscribeType,
                            ttlSec,
                            termNotif,
                            minDistanceSet,
                            minDistanceMm,
                            maxDistanceSet,
                            maxDistanceMm,
                            instantMode,
                            instantBand,
                            pairing);
                }

                @Override
                public SubscribeConfig[] newArray(int size) {
                    return new SubscribeConfig[size];
                }
            };

    final String mServiceName;
    final byte[] mServiceSpecificInfo;
    final List<byte[]> mMatchFilter;
    final int mSubscribeType;
    final int mTtlSec;
    final boolean mEnableTerminateNotification;
    final boolean mMinDistanceMmSet;
    final int mMinDistanceMm;
    final boolean mMaxDistanceMmSet;
    final int mMaxDistanceMm;
    final boolean mEnableInstantMode;
    final int mInstantCommunicationBand;
    final AwarePairingConfig mPairingConfig;

    SubscribeConfig(
            String serviceName,
            byte[] serviceSpecificInfo,
            List<byte[]> matchFilter,
            int subscribeType,
            int ttlSec,
            boolean enableTerminateNotification,
            boolean minDistanceMmSet,
            int minDistanceMm,
            boolean maxDistanceMmSet,
            int maxDistanceMm,
            boolean enableInstantMode,
            int instantCommunicationBand,
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
        mSubscribeType = subscribeType;
        mTtlSec = ttlSec;
        mEnableTerminateNotification = enableTerminateNotification;
        mMinDistanceMmSet = minDistanceMmSet;
        mMinDistanceMm = minDistanceMm;
        mMaxDistanceMmSet = maxDistanceMmSet;
        mMaxDistanceMm = maxDistanceMm;
        mEnableInstantMode = enableInstantMode;
        mInstantCommunicationBand = instantCommunicationBand;
        mPairingConfig = pairingConfig;
    }

    @Override
    public String toString() {
        return "SubscribeConfig [mServiceName='"
                + mServiceName
                + "', mSubscribeType="
                + mSubscribeType
                + ", mTtlSec="
                + mTtlSec
                + ", mEnableTerminateNotification="
                + mEnableTerminateNotification
                + ", mMinDistanceMm="
                + mMinDistanceMm
                + ", mMaxDistanceMm="
                + mMaxDistanceMm
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
        dest.writeInt(mSubscribeType);
        dest.writeInt(mTtlSec);
        dest.writeInt(mEnableTerminateNotification ? 1 : 0);
        dest.writeInt(mMinDistanceMmSet ? 1 : 0);
        dest.writeInt(mMinDistanceMm);
        dest.writeInt(mMaxDistanceMmSet ? 1 : 0);
        dest.writeInt(mMaxDistanceMm);
        dest.writeInt(mEnableInstantMode ? 1 : 0);
        dest.writeInt(mInstantCommunicationBand);
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
        if (!(o instanceof SubscribeConfig)) {
            return false;
        }
        SubscribeConfig lhs = (SubscribeConfig) o;
        return Objects.equals(mServiceName, lhs.mServiceName)
                && Arrays.equals(mServiceSpecificInfo, lhs.mServiceSpecificInfo)
                && filterListEquals(mMatchFilter, lhs.mMatchFilter)
                && mSubscribeType == lhs.mSubscribeType
                && mTtlSec == lhs.mTtlSec
                && mEnableTerminateNotification == lhs.mEnableTerminateNotification
                && mMinDistanceMmSet == lhs.mMinDistanceMmSet
                && (!mMinDistanceMmSet || mMinDistanceMm == lhs.mMinDistanceMm)
                && mMaxDistanceMmSet == lhs.mMaxDistanceMmSet
                && (!mMaxDistanceMmSet || mMaxDistanceMm == lhs.mMaxDistanceMm)
                && mEnableInstantMode == lhs.mEnableInstantMode
                && mInstantCommunicationBand == lhs.mInstantCommunicationBand
                && Objects.equals(mPairingConfig, lhs.mPairingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mServiceName,
                Integer.valueOf(Arrays.hashCode(mServiceSpecificInfo)),
                Integer.valueOf(filterListHash(mMatchFilter)),
                Integer.valueOf(mSubscribeType),
                Integer.valueOf(mTtlSec),
                Boolean.valueOf(mEnableTerminateNotification),
                Boolean.valueOf(mMinDistanceMmSet),
                Integer.valueOf(mMinDistanceMmSet ? mMinDistanceMm : 0),
                Boolean.valueOf(mMaxDistanceMmSet),
                Integer.valueOf(mMaxDistanceMmSet ? mMaxDistanceMm : 0),
                Boolean.valueOf(mEnableInstantMode),
                Integer.valueOf(mInstantCommunicationBand),
                mPairingConfig);
    }

    public boolean isInstantCommunicationModeEnabled() {
        return mEnableInstantMode;
    }

    public int getInstantCommunicationBand() {
        return mInstantCommunicationBand;
    }

    public AwarePairingConfig getPairingConfig() {
        return mPairingConfig;
    }

    public static final class Builder {
        private String mServiceName;
        private byte[] mServiceSpecificInfo;
        private List<byte[]> mMatchFilter;
        private int mSubscribeType = SUBSCRIBE_TYPE_PASSIVE;
        private int mTtlSec = 0;
        private boolean mEnableTerminateNotification = true;
        private boolean mMinDistanceMmSet = false;
        private int mMinDistanceMm;
        private boolean mMaxDistanceMmSet = false;
        private int mMaxDistanceMm;
        private boolean mEnableInstantMode = false;
        private int mInstantCommunicationBand = ScanResult.WIFI_BAND_24_GHZ;
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

        public Builder setSubscribeType(int subscribeType) {
            if (subscribeType < SUBSCRIBE_TYPE_PASSIVE || subscribeType > SUBSCRIBE_TYPE_ACTIVE) {
                throw new IllegalArgumentException("Invalid subscribeType - " + subscribeType);
            }
            mSubscribeType = subscribeType;
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

        public Builder setMinDistanceMm(int minDistanceMm) {
            mMinDistanceMm = minDistanceMm;
            mMinDistanceMmSet = true;
            return this;
        }

        public Builder setMaxDistanceMm(int maxDistanceMm) {
            mMaxDistanceMm = maxDistanceMm;
            mMaxDistanceMmSet = true;
            return this;
        }

        public Builder setInstantCommunicationModeEnabled(boolean enabled, int band) {
            mEnableInstantMode = enabled;
            mInstantCommunicationBand = band;
            return this;
        }

        public Builder setPairingConfig(AwarePairingConfig config) {
            mPairingConfig = config;
            return this;
        }

        public SubscribeConfig build() {
            if (mServiceName == null) {
                throw new IllegalArgumentException("Service name must be specified");
            }
            return new SubscribeConfig(
                    mServiceName,
                    mServiceSpecificInfo,
                    mMatchFilter,
                    mSubscribeType,
                    mTtlSec,
                    mEnableTerminateNotification,
                    mMinDistanceMmSet,
                    mMinDistanceMm,
                    mMaxDistanceMmSet,
                    mMaxDistanceMm,
                    mEnableInstantMode,
                    mInstantCommunicationBand,
                    mPairingConfig);
        }
    }
}
