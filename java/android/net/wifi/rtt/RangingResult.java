// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.rtt;

import android.net.MacAddress;
import android.net.wifi.aware.ParcelablePeerHandle;
import android.net.wifi.aware.PeerHandle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class RangingResult implements Parcelable {
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_FAIL = 1;
    public static final int STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC = 2;
    public static final int UNSPECIFIED = -1;

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    public static final Creator<RangingResult> CREATOR =
            new Creator<RangingResult>() {
                @Override
                public RangingResult createFromParcel(Parcel in) {
                    int status = in.readInt();
                    MacAddress mac = in.readTypedObject(MacAddress.CREATOR);
                    ParcelablePeerHandle peerHandle =
                            in.readTypedObject(ParcelablePeerHandle.CREATOR);
                    int distanceMm = in.readInt();
                    int distanceStdDevMm = in.readInt();
                    int rssi = in.readInt();
                    int numAttempted = in.readInt();
                    int numSuccessful = in.readInt();
                    byte[] lci = in.createByteArray();
                    byte[] lcr = in.createByteArray();
                    ResponderLocation responderLocation =
                            in.readTypedObject(ResponderLocation.CREATOR);
                    long timestamp = in.readLong();
                    boolean is80211mc = in.readBoolean();
                    int frequencyMHz = in.readInt();
                    int packetBw = in.readInt();
                    boolean is80211azNtb = in.readBoolean();
                    long ntbMinMeasurementTime = in.readLong();
                    long ntbMaxMeasurementTime = in.readLong();
                    int i2rTxLtf = in.readInt();
                    int r2iTxLtf = in.readInt();
                    int numTxSs = in.readInt();
                    int numRxSs = in.readInt();
                    return new RangingResult(
                            status,
                            mac,
                            peerHandle,
                            distanceMm,
                            distanceStdDevMm,
                            rssi,
                            numAttempted,
                            numSuccessful,
                            lci,
                            lcr,
                            responderLocation,
                            timestamp,
                            is80211mc,
                            frequencyMHz,
                            packetBw,
                            is80211azNtb,
                            ntbMinMeasurementTime,
                            ntbMaxMeasurementTime,
                            i2rTxLtf,
                            r2iTxLtf,
                            numTxSs,
                            numRxSs);
                }

                @Override
                public RangingResult[] newArray(int size) {
                    return new RangingResult[size];
                }
            };

    private final int mStatus;
    private final MacAddress mMac;
    private final PeerHandle mPeerHandle;
    private final int mDistanceMm;
    private final int mDistanceStdDevMm;
    private final int mRssi;
    private final int mNumAttemptedMeasurements;
    private final int mNumSuccessfulMeasurements;
    private final byte[] mLci;
    private final byte[] mLcr;
    private final ResponderLocation mResponderLocation;
    private final long mTimestamp;
    private final boolean mIs80211mcMeasurement;
    private final int mFrequencyMHz;
    private final int mPacketBw;
    private final boolean mIs80211azNtbMeasurement;
    private final long mNtbMinMeasurementTime;
    private final long mNtbMaxMeasurementTime;
    private final int mI2rTxLtfRepetitions;
    private final int mR2iTxLtfRepetitions;
    private final int mNumTxSpatialStreams;
    private final int mNumRxSpatialStreams;

    RangingResult(
            int status,
            MacAddress mac,
            PeerHandle peerHandle,
            int distanceMm,
            int distanceStdDevMm,
            int rssi,
            int numAttemptedMeasurements,
            int numSuccessfulMeasurements,
            byte[] lci,
            byte[] lcr,
            ResponderLocation responderLocation,
            long timestamp,
            boolean is80211mcMeasurement,
            int frequencyMHz,
            int packetBw,
            boolean is80211azNtbMeasurement,
            long ntbMinMeasurementTime,
            long ntbMaxMeasurementTime,
            int i2rTxLtfRepetitions,
            int r2iTxLtfRepetitions,
            int numTxSpatialStreams,
            int numRxSpatialStreams) {
        mStatus = status;
        mMac = mac;
        mPeerHandle = peerHandle;
        mDistanceMm = distanceMm;
        mDistanceStdDevMm = distanceStdDevMm;
        mRssi = rssi;
        mNumAttemptedMeasurements = numAttemptedMeasurements;
        mNumSuccessfulMeasurements = numSuccessfulMeasurements;
        mLci = lci == null ? EMPTY_BYTE_ARRAY : lci.clone();
        mLcr = lcr == null ? EMPTY_BYTE_ARRAY : lcr.clone();
        mResponderLocation = responderLocation;
        mTimestamp = timestamp;
        mIs80211mcMeasurement = is80211mcMeasurement;
        mFrequencyMHz = frequencyMHz;
        mPacketBw = packetBw;
        mIs80211azNtbMeasurement = is80211azNtbMeasurement;
        mNtbMinMeasurementTime = ntbMinMeasurementTime;
        mNtbMaxMeasurementTime = ntbMaxMeasurementTime;
        mI2rTxLtfRepetitions = i2rTxLtfRepetitions;
        mR2iTxLtfRepetitions = r2iTxLtfRepetitions;
        mNumTxSpatialStreams = numTxSpatialStreams;
        mNumRxSpatialStreams = numRxSpatialStreams;
    }

    private void ensureValidSuccess(String methodName) {
        if (mStatus != STATUS_SUCCESS) {
            throw new IllegalStateException(
                    methodName + "(): invoked on an invalid result: getStatus()=" + mStatus);
        }
    }

    public int getStatus() {
        return mStatus;
    }

    public MacAddress getMacAddress() {
        return mMac;
    }

    public PeerHandle getPeerHandle() {
        return mPeerHandle;
    }

    public int getDistanceMm() {
        ensureValidSuccess("getDistanceMm");
        return mDistanceMm;
    }

    public int getDistanceStdDevMm() {
        ensureValidSuccess("getDistanceStdDevMm");
        return mDistanceStdDevMm;
    }

    public int getRssi() {
        ensureValidSuccess("getRssi");
        return mRssi;
    }

    public int getNumAttemptedMeasurements() {
        ensureValidSuccess("getNumAttemptedMeasurements");
        return mNumAttemptedMeasurements;
    }

    public int getNumSuccessfulMeasurements() {
        ensureValidSuccess("getNumSuccessfulMeasurements");
        return mNumSuccessfulMeasurements;
    }

    public ResponderLocation getUnverifiedResponderLocation() {
        ensureValidSuccess("getUnverifiedResponderLocation");
        return mResponderLocation;
    }

    public byte[] getLci() {
        ensureValidSuccess("getLci");
        return mLci.clone();
    }

    public byte[] getLcr() {
        ensureValidSuccess("getLcr");
        return mLcr.clone();
    }

    public long getRangingTimestampMillis() {
        ensureValidSuccess("getRangingTimestampMillis");
        return mTimestamp;
    }

    public boolean is80211mcMeasurement() {
        ensureValidSuccess("is80211mcMeasurement");
        return mIs80211mcMeasurement;
    }

    public boolean is80211azNtbMeasurement() {
        ensureValidSuccess("is80211azNtbMeasurement");
        return mIs80211azNtbMeasurement;
    }

    public long getMinTimeBetweenNtbMeasurementsMicros() {
        ensureValidSuccess("getMinTimeBetweenNtbMeasurementsMicros");
        return mNtbMinMeasurementTime;
    }

    public long getMaxTimeBetweenNtbMeasurementsMicros() {
        ensureValidSuccess("getMaxTimeBetweenNtbMeasurementsMicros");
        return mNtbMaxMeasurementTime;
    }

    public int get80211azResponderTxLtfRepetitionsCount() {
        ensureValidSuccess("get80211azResponderTxLtfRepetitionsCount");
        return mR2iTxLtfRepetitions;
    }

    public int get80211azInitiatorTxLtfRepetitionsCount() {
        ensureValidSuccess("get80211azInitiatorTxLtfRepetitionsCount");
        return mI2rTxLtfRepetitions;
    }

    public int get80211azNumberOfTxSpatialStreams() {
        ensureValidSuccess("get80211azNumberOfTxSpatialStreams");
        return mNumTxSpatialStreams;
    }

    public int get80211azNumberOfRxSpatialStreams() {
        ensureValidSuccess("get80211azNumberOfRxSpatialStreams");
        return mNumRxSpatialStreams;
    }

    public int getMeasurementChannelFrequencyMHz() {
        ensureValidSuccess("getMeasurementChannelFrequencyMHz");
        return mFrequencyMHz;
    }

    public int getMeasurementBandwidth() {
        ensureValidSuccess("getMeasurementBandwidth");
        return mPacketBw;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mStatus);
        dest.writeTypedObject(mMac, flags);
        dest.writeTypedObject(
                mPeerHandle == null ? null : new ParcelablePeerHandle(mPeerHandle), flags);
        dest.writeInt(mDistanceMm);
        dest.writeInt(mDistanceStdDevMm);
        dest.writeInt(mRssi);
        dest.writeInt(mNumAttemptedMeasurements);
        dest.writeInt(mNumSuccessfulMeasurements);
        dest.writeByteArray(mLci);
        dest.writeByteArray(mLcr);
        dest.writeTypedObject(mResponderLocation, flags);
        dest.writeLong(mTimestamp);
        dest.writeBoolean(mIs80211mcMeasurement);
        dest.writeInt(mFrequencyMHz);
        dest.writeInt(mPacketBw);
        dest.writeBoolean(mIs80211azNtbMeasurement);
        dest.writeLong(mNtbMinMeasurementTime);
        dest.writeLong(mNtbMaxMeasurementTime);
        dest.writeInt(mI2rTxLtfRepetitions);
        dest.writeInt(mR2iTxLtfRepetitions);
        dest.writeInt(mNumTxSpatialStreams);
        dest.writeInt(mNumRxSpatialStreams);
    }

    @Override
    public String toString() {
        return "RangingResult: [status="
                + mStatus
                + ", mac="
                + mMac
                + ", peerHandle="
                + mPeerHandle
                + ", distanceMm="
                + mDistanceMm
                + ", distanceStdDevMm="
                + mDistanceStdDevMm
                + ", rssi="
                + mRssi
                + ", numAttemptedMeasurements="
                + mNumAttemptedMeasurements
                + ", numSuccessfulMeasurements="
                + mNumSuccessfulMeasurements
                + ", timestamp="
                + mTimestamp
                + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RangingResult)) {
            return false;
        }
        RangingResult lhs = (RangingResult) o;
        return mStatus == lhs.mStatus
                && Objects.equals(mMac, lhs.mMac)
                && Objects.equals(mPeerHandle, lhs.mPeerHandle)
                && mDistanceMm == lhs.mDistanceMm
                && mDistanceStdDevMm == lhs.mDistanceStdDevMm
                && mRssi == lhs.mRssi
                && mNumAttemptedMeasurements == lhs.mNumAttemptedMeasurements
                && mNumSuccessfulMeasurements == lhs.mNumSuccessfulMeasurements
                && Arrays.equals(mLci, lhs.mLci)
                && Arrays.equals(mLcr, lhs.mLcr)
                && Objects.equals(mResponderLocation, lhs.mResponderLocation)
                && mTimestamp == lhs.mTimestamp
                && mIs80211mcMeasurement == lhs.mIs80211mcMeasurement
                && mFrequencyMHz == lhs.mFrequencyMHz
                && mPacketBw == lhs.mPacketBw
                && mIs80211azNtbMeasurement == lhs.mIs80211azNtbMeasurement
                && mNtbMinMeasurementTime == lhs.mNtbMinMeasurementTime
                && mNtbMaxMeasurementTime == lhs.mNtbMaxMeasurementTime
                && mI2rTxLtfRepetitions == lhs.mI2rTxLtfRepetitions
                && mR2iTxLtfRepetitions == lhs.mR2iTxLtfRepetitions
                && mNumTxSpatialStreams == lhs.mNumTxSpatialStreams
                && mNumRxSpatialStreams == lhs.mNumRxSpatialStreams;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mStatus),
                mMac,
                mPeerHandle,
                Integer.valueOf(mDistanceMm),
                Integer.valueOf(mDistanceStdDevMm),
                Integer.valueOf(mRssi),
                Integer.valueOf(mNumAttemptedMeasurements),
                Integer.valueOf(mNumSuccessfulMeasurements),
                Integer.valueOf(Arrays.hashCode(mLci)),
                Integer.valueOf(Arrays.hashCode(mLcr)),
                mResponderLocation,
                Long.valueOf(mTimestamp),
                Boolean.valueOf(mIs80211mcMeasurement),
                Integer.valueOf(mFrequencyMHz),
                Integer.valueOf(mPacketBw),
                Boolean.valueOf(mIs80211azNtbMeasurement),
                Long.valueOf(mNtbMinMeasurementTime),
                Long.valueOf(mNtbMaxMeasurementTime),
                Integer.valueOf(mI2rTxLtfRepetitions),
                Integer.valueOf(mR2iTxLtfRepetitions),
                Integer.valueOf(mNumTxSpatialStreams),
                Integer.valueOf(mNumRxSpatialStreams));
    }

    public static final class Builder {
        private int mStatus = STATUS_SUCCESS;
        private MacAddress mMac;
        private PeerHandle mPeerHandle;
        private int mDistanceMm;
        private int mDistanceStdDevMm;
        private int mRssi;
        private int mNumAttemptedMeasurements;
        private int mNumSuccessfulMeasurements;
        private byte[] mLci = EMPTY_BYTE_ARRAY;
        private byte[] mLcr = EMPTY_BYTE_ARRAY;
        private ResponderLocation mResponderLocation;
        private long mTimestamp;
        private boolean mIs80211mcMeasurement = false;
        private int mFrequencyMHz = UNSPECIFIED;
        private int mPacketBw = UNSPECIFIED;
        private boolean mIs80211azNtbMeasurement = false;
        private long mNtbMinMeasurementTime = UNSPECIFIED;
        private long mNtbMaxMeasurementTime = UNSPECIFIED;
        private int mI2rTxLtfRepetitions = UNSPECIFIED;
        private int mR2iTxLtfRepetitions = UNSPECIFIED;
        private int mNumTxSpatialStreams = UNSPECIFIED;
        private int mNumRxSpatialStreams = UNSPECIFIED;

        public Builder() {}

        public Builder setStatus(int status) {
            mStatus = status;
            return this;
        }

        public Builder setMacAddress(MacAddress macAddress) {
            mMac = macAddress;
            return this;
        }

        public Builder setPeerHandle(PeerHandle peerHandle) {
            mPeerHandle = peerHandle;
            return this;
        }

        public Builder setDistanceMm(int distanceMm) {
            mDistanceMm = distanceMm;
            return this;
        }

        public Builder setDistanceStdDevMm(int distanceStdDevMm) {
            mDistanceStdDevMm = distanceStdDevMm;
            return this;
        }

        public Builder setRssi(int rssi) {
            mRssi = rssi;
            return this;
        }

        public Builder setNumAttemptedMeasurements(int numAttemptedMeasurements) {
            if (numAttemptedMeasurements < 0) {
                throw new IllegalArgumentException(
                        "numAttemptedMeasurements must be non-negative");
            }
            mNumAttemptedMeasurements = numAttemptedMeasurements;
            return this;
        }

        public Builder setNumSuccessfulMeasurements(int numSuccessfulMeasurements) {
            if (numSuccessfulMeasurements < 0) {
                throw new IllegalArgumentException(
                        "numSuccessfulMeasurements must be non-negative");
            }
            mNumSuccessfulMeasurements = numSuccessfulMeasurements;
            return this;
        }

        public Builder setLci(byte[] lci) {
            mLci = lci == null ? EMPTY_BYTE_ARRAY : lci.clone();
            return this;
        }

        public Builder setLcr(byte[] lcr) {
            mLcr = lcr == null ? EMPTY_BYTE_ARRAY : lcr.clone();
            return this;
        }

        public Builder setUnverifiedResponderLocation(ResponderLocation responderLocation) {
            mResponderLocation = responderLocation;
            return this;
        }

        public Builder setRangingTimestampMillis(long timestamp) {
            mTimestamp = timestamp;
            return this;
        }

        public Builder set80211mcMeasurement(boolean is80211mcMeasurement) {
            mIs80211mcMeasurement = is80211mcMeasurement;
            return this;
        }

        public Builder setMeasurementChannelFrequencyMHz(int frequencyMHz) {
            mFrequencyMHz = frequencyMHz;
            return this;
        }

        public Builder setMeasurementBandwidth(int measurementBandwidth) {
            mPacketBw = measurementBandwidth;
            return this;
        }

        public Builder set80211azNtbMeasurement(boolean is80211azNtbMeasurement) {
            mIs80211azNtbMeasurement = is80211azNtbMeasurement;
            return this;
        }

        public Builder setMinTimeBetweenNtbMeasurementsMicros(long micros) {
            mNtbMinMeasurementTime = micros;
            return this;
        }

        public Builder setMaxTimeBetweenNtbMeasurementsMicros(long micros) {
            mNtbMaxMeasurementTime = micros;
            return this;
        }

        public Builder set80211azInitiatorTxLtfRepetitionsCount(int i2rTxLtfRepetitions) {
            mI2rTxLtfRepetitions = i2rTxLtfRepetitions;
            return this;
        }

        public Builder set80211azResponderTxLtfRepetitionsCount(int r2iTxLtfRepetitions) {
            mR2iTxLtfRepetitions = r2iTxLtfRepetitions;
            return this;
        }

        public Builder set80211azNumberOfTxSpatialStreams(int numTxSpatialStreams) {
            mNumTxSpatialStreams = numTxSpatialStreams;
            return this;
        }

        public Builder set80211azNumberOfRxSpatialStreams(int numRxSpatialStreams) {
            mNumRxSpatialStreams = numRxSpatialStreams;
            return this;
        }

        public RangingResult build() {
            if (mMac == null && mPeerHandle == null) {
                throw new IllegalArgumentException(
                        "Either a MAC address or a PeerHandle must be specified");
            }
            if (mMac != null && mPeerHandle != null) {
                throw new IllegalArgumentException(
                        "Cannot specify both a MAC address and a PeerHandle");
            }
            return new RangingResult(
                    mStatus,
                    mMac,
                    mPeerHandle,
                    mDistanceMm,
                    mDistanceStdDevMm,
                    mRssi,
                    mNumAttemptedMeasurements,
                    mNumSuccessfulMeasurements,
                    mLci,
                    mLcr,
                    mResponderLocation,
                    mTimestamp,
                    mIs80211mcMeasurement,
                    mFrequencyMHz,
                    mPacketBw,
                    mIs80211azNtbMeasurement,
                    mNtbMinMeasurementTime,
                    mNtbMaxMeasurementTime,
                    mI2rTxLtfRepetitions,
                    mR2iTxLtfRepetitions,
                    mNumTxSpatialStreams,
                    mNumRxSpatialStreams);
        }
    }
}
