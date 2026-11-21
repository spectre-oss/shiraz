// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Snapshot of packet, byte, and replay counters for an {@link IpSecTransform}. */
public final class IpSecTransformState implements Parcelable {
    private final long mTimestampMillis;
    private final long mTxHighestSequenceNumber;
    private final long mRxHighestSequenceNumber;
    private final long mPacketCount;
    private final long mByteCount;
    private final byte[] mReplayBitmap;

    IpSecTransformState(
            long timestampMillis,
            long txHighestSequenceNumber,
            long rxHighestSequenceNumber,
            long packetCount,
            long byteCount,
            byte[] replayBitmap) {
        this.mTimestampMillis = timestampMillis;
        this.mTxHighestSequenceNumber = txHighestSequenceNumber;
        this.mRxHighestSequenceNumber = rxHighestSequenceNumber;
        this.mPacketCount = packetCount;
        this.mByteCount = byteCount;
        this.mReplayBitmap =
                Objects.requireNonNull(replayBitmap, "replayBitmap").clone();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(mTimestampMillis);
        out.writeLong(mTxHighestSequenceNumber);
        out.writeLong(mRxHighestSequenceNumber);
        out.writeLong(mPacketCount);
        out.writeLong(mByteCount);
        out.writeByteArray(mReplayBitmap);
    }

    public long getTimestampMillis() {
        return mTimestampMillis;
    }

    public long getTxHighestSequenceNumber() {
        return mTxHighestSequenceNumber;
    }

    public long getRxHighestSequenceNumber() {
        return mRxHighestSequenceNumber;
    }

    public long getPacketCount() {
        return mPacketCount;
    }

    public long getByteCount() {
        return mByteCount;
    }

    public byte[] getReplayBitmap() {
        return mReplayBitmap.clone();
    }

    public static final class Builder {
        private long mTimestampMillis;
        private long mTxHighestSequenceNumber;
        private long mRxHighestSequenceNumber;
        private long mPacketCount;
        private long mByteCount;
        private byte[] mReplayBitmap = new byte[0];

        public Builder() {
        }

        public Builder setTimestampMillis(long timestampMillis) {
            this.mTimestampMillis = timestampMillis;
            return this;
        }

        public Builder setTxHighestSequenceNumber(long seqNum) {
            this.mTxHighestSequenceNumber = seqNum;
            return this;
        }

        public Builder setRxHighestSequenceNumber(long seqNum) {
            this.mRxHighestSequenceNumber = seqNum;
            return this;
        }

        public Builder setPacketCount(long packetCount) {
            this.mPacketCount = packetCount;
            return this;
        }

        public Builder setByteCount(long byteCount) {
            this.mByteCount = byteCount;
            return this;
        }

        public Builder setReplayBitmap(byte[] bitMap) {
            this.mReplayBitmap = Objects.requireNonNull(bitMap, "bitMap").clone();
            return this;
        }

        public IpSecTransformState build() {
            return new IpSecTransformState(
                    mTimestampMillis,
                    mTxHighestSequenceNumber,
                    mRxHighestSequenceNumber,
                    mPacketCount,
                    mByteCount,
                    mReplayBitmap);
        }
    }

    public static final Parcelable.Creator<IpSecTransformState> CREATOR =
            new Parcelable.Creator<IpSecTransformState>() {
                @Override
                public IpSecTransformState createFromParcel(Parcel in) {
                    return new IpSecTransformState(
                            in.readLong(),
                            in.readLong(),
                            in.readLong(),
                            in.readLong(),
                            in.readLong(),
                            in.createByteArray());
                }

                @Override
                public IpSecTransformState[] newArray(int size) {
                    return new IpSecTransformState[size];
                }
            };
}
