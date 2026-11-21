// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class BluetoothCodecConfig implements Parcelable {
    public static final int SOURCE_CODEC_TYPE_SBC = 0;
    public static final int SOURCE_CODEC_TYPE_AAC = 1;
    public static final int SOURCE_CODEC_TYPE_APTX = 2;
    public static final int SOURCE_CODEC_TYPE_APTX_HD = 3;
    public static final int SOURCE_CODEC_TYPE_LDAC = 4;
    public static final int SOURCE_CODEC_TYPE_LC3 = 5;
    public static final int SOURCE_CODEC_TYPE_OPUS = 6;
    public static final int SOURCE_CODEC_TYPE_INVALID = 1000000;

    public static final int CODEC_PRIORITY_DISABLED = -1;
    public static final int CODEC_PRIORITY_DEFAULT = 0;
    public static final int CODEC_PRIORITY_HIGHEST = 1000000;

    public static final int SAMPLE_RATE_NONE = 0;
    public static final int SAMPLE_RATE_44100 = 1;
    public static final int SAMPLE_RATE_48000 = 2;
    public static final int SAMPLE_RATE_88200 = 4;
    public static final int SAMPLE_RATE_96000 = 8;
    public static final int SAMPLE_RATE_176400 = 16;
    public static final int SAMPLE_RATE_192000 = 32;

    public static final int BITS_PER_SAMPLE_NONE = 0;
    public static final int BITS_PER_SAMPLE_16 = 1;
    public static final int BITS_PER_SAMPLE_24 = 2;
    public static final int BITS_PER_SAMPLE_32 = 4;

    public static final int CHANNEL_MODE_NONE = 0;
    public static final int CHANNEL_MODE_MONO = 1;
    public static final int CHANNEL_MODE_STEREO = 2;

    private final int mCodecType;
    private final BluetoothCodecType mExtendedCodecType;
    private final int mCodecPriority;
    private final int mSampleRate;
    private final int mBitsPerSample;
    private final int mChannelMode;
    private final long mCodecSpecific1;
    private final long mCodecSpecific2;
    private final long mCodecSpecific3;
    private final long mCodecSpecific4;

    BluetoothCodecConfig(int codecType, BluetoothCodecType extendedCodecType, int codecPriority,
            int sampleRate, int bitsPerSample, int channelMode,
            long codecSpecific1, long codecSpecific2, long codecSpecific3, long codecSpecific4) {
        mCodecType = codecType;
        mExtendedCodecType = extendedCodecType != null ? extendedCodecType : BluetoothCodecType.createFromType(codecType);
        mCodecPriority = codecPriority;
        mSampleRate = sampleRate;
        mBitsPerSample = bitsPerSample;
        mChannelMode = channelMode;
        mCodecSpecific1 = codecSpecific1;
        mCodecSpecific2 = codecSpecific2;
        mCodecSpecific3 = codecSpecific3;
        mCodecSpecific4 = codecSpecific4;
    }

    public boolean equals(Object o) {
        if (!(o instanceof BluetoothCodecConfig)) {
            return false;
        }
        BluetoothCodecConfig other = (BluetoothCodecConfig) o;
        return other.mCodecType == mCodecType
                && Objects.equals(other.mExtendedCodecType, mExtendedCodecType)
                && other.mCodecPriority == mCodecPriority
                && other.mSampleRate == mSampleRate
                && other.mBitsPerSample == mBitsPerSample
                && other.mChannelMode == mChannelMode
                && other.mCodecSpecific1 == mCodecSpecific1
                && other.mCodecSpecific2 == mCodecSpecific2
                && other.mCodecSpecific3 == mCodecSpecific3
                && other.mCodecSpecific4 == mCodecSpecific4;
    }

    public int hashCode() {
        return Objects.hash(mCodecType, mExtendedCodecType, mCodecPriority, mSampleRate,
                mBitsPerSample, mChannelMode, mCodecSpecific1, mCodecSpecific2,
                mCodecSpecific3, mCodecSpecific4);
    }

    public String toString() {
        return "{codecType:" + mCodecType + ",extendedCodecType:" + mExtendedCodecType
                + ",codecPriority:" + mCodecPriority + ",sampleRate:" + mSampleRate
                + ",bitsPerSample:" + mBitsPerSample + ",channelMode:" + mChannelMode + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mCodecType);
        out.writeTypedObject(mExtendedCodecType, flags);
        out.writeInt(mCodecPriority);
        out.writeInt(mSampleRate);
        out.writeInt(mBitsPerSample);
        out.writeInt(mChannelMode);
        out.writeLong(mCodecSpecific1);
        out.writeLong(mCodecSpecific2);
        out.writeLong(mCodecSpecific3);
        out.writeLong(mCodecSpecific4);
    }

    public static final Parcelable.Creator<BluetoothCodecConfig> CREATOR = new Parcelable.Creator<BluetoothCodecConfig>() {
        public BluetoothCodecConfig createFromParcel(Parcel in) {
            int codecType = in.readInt();
            BluetoothCodecType extType = in.readTypedObject(BluetoothCodecType.CREATOR);
            int priority = in.readInt();
            int sampleRate = in.readInt();
            int bits = in.readInt();
            int channel = in.readInt();
            long s1 = in.readLong();
            long s2 = in.readLong();
            long s3 = in.readLong();
            long s4 = in.readLong();
            return new BluetoothCodecConfig(codecType, extType, priority, sampleRate, bits, channel, s1, s2, s3, s4);
        }

        public BluetoothCodecConfig[] newArray(int size) {
            return new BluetoothCodecConfig[size];
        }
    };

    public int getCodecType() {
        return mCodecType;
    }

    public BluetoothCodecType getExtendedCodecType() {
        return mExtendedCodecType;
    }

    public boolean isMandatoryCodec() {
        return mCodecType == SOURCE_CODEC_TYPE_SBC
                || (mExtendedCodecType != null && mExtendedCodecType.isMandatoryCodec());
    }

    public int getCodecPriority() {
        return mCodecPriority;
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getBitsPerSample() {
        return mBitsPerSample;
    }

    public int getChannelMode() {
        return mChannelMode;
    }

    public long getCodecSpecific1() {
        return mCodecSpecific1;
    }

    public long getCodecSpecific2() {
        return mCodecSpecific2;
    }

    public long getCodecSpecific3() {
        return mCodecSpecific3;
    }

    public long getCodecSpecific4() {
        return mCodecSpecific4;
    }

    public static final class Builder {
        private int mCodecType = SOURCE_CODEC_TYPE_INVALID;
        private BluetoothCodecType mExtendedCodecType;
        private int mCodecPriority = CODEC_PRIORITY_DEFAULT;
        private int mSampleRate = SAMPLE_RATE_NONE;
        private int mBitsPerSample = BITS_PER_SAMPLE_NONE;
        private int mChannelMode = CHANNEL_MODE_NONE;
        private long mCodecSpecific1;
        private long mCodecSpecific2;
        private long mCodecSpecific3;
        private long mCodecSpecific4;

        public Builder() {}

        public Builder setCodecType(int codecType) {
            mCodecType = codecType;
            mExtendedCodecType = BluetoothCodecType.createFromType(codecType);
            return this;
        }

        public Builder setExtendedCodecType(BluetoothCodecType codecType) {
            mExtendedCodecType = codecType;
            mCodecType = codecType != null ? codecType.getNativeCodecType() : SOURCE_CODEC_TYPE_INVALID;
            return this;
        }

        public Builder setCodecPriority(int codecPriority) {
            mCodecPriority = codecPriority;
            return this;
        }

        public Builder setSampleRate(int sampleRate) {
            mSampleRate = sampleRate;
            return this;
        }

        public Builder setBitsPerSample(int bitsPerSample) {
            mBitsPerSample = bitsPerSample;
            return this;
        }

        public Builder setChannelMode(int channelMode) {
            mChannelMode = channelMode;
            return this;
        }

        public Builder setCodecSpecific1(long codecSpecific1) {
            mCodecSpecific1 = codecSpecific1;
            return this;
        }

        public Builder setCodecSpecific2(long codecSpecific2) {
            mCodecSpecific2 = codecSpecific2;
            return this;
        }

        public Builder setCodecSpecific3(long codecSpecific3) {
            mCodecSpecific3 = codecSpecific3;
            return this;
        }

        public Builder setCodecSpecific4(long codecSpecific4) {
            mCodecSpecific4 = codecSpecific4;
            return this;
        }

        public BluetoothCodecConfig build() {
            return new BluetoothCodecConfig(mCodecType, mExtendedCodecType, mCodecPriority,
                    mSampleRate, mBitsPerSample, mChannelMode,
                    mCodecSpecific1, mCodecSpecific2, mCodecSpecific3, mCodecSpecific4);
        }
    }
}
