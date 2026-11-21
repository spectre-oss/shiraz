// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class BluetoothLeAudioCodecConfig implements Parcelable {
    public static final int SOURCE_CODEC_TYPE_LC3 = 0;
    public static final int SOURCE_CODEC_TYPE_INVALID = 1000000;

    public static final int CODEC_PRIORITY_DISABLED = -1;
    public static final int CODEC_PRIORITY_DEFAULT = 0;
    public static final int CODEC_PRIORITY_HIGHEST = 1000000;

    public static final int SAMPLE_RATE_NONE = 0;
    public static final int SAMPLE_RATE_8000 = 1;
    public static final int SAMPLE_RATE_11025 = 2;
    public static final int SAMPLE_RATE_16000 = 4;
    public static final int SAMPLE_RATE_22050 = 8;
    public static final int SAMPLE_RATE_24000 = 16;
    public static final int SAMPLE_RATE_32000 = 32;
    public static final int SAMPLE_RATE_44100 = 64;
    public static final int SAMPLE_RATE_48000 = 128;
    public static final int SAMPLE_RATE_88200 = 256;
    public static final int SAMPLE_RATE_96000 = 512;
    public static final int SAMPLE_RATE_176400 = 1024;
    public static final int SAMPLE_RATE_192000 = 2048;
    public static final int SAMPLE_RATE_384000 = 4096;

    public static final int BITS_PER_SAMPLE_NONE = 0;
    public static final int BITS_PER_SAMPLE_16 = 1;
    public static final int BITS_PER_SAMPLE_24 = 2;
    public static final int BITS_PER_SAMPLE_32 = 8;

    public static final int CHANNEL_COUNT_NONE = 0;
    public static final int CHANNEL_COUNT_1 = 1;
    public static final int CHANNEL_COUNT_2 = 2;

    public static final int FRAME_DURATION_NONE = 0;
    public static final int FRAME_DURATION_7500 = 1;
    public static final int FRAME_DURATION_10000 = 2;

    private final int mCodecType;
    private final int mCodecPriority;
    private final int mSampleRate;
    private final int mBitsPerSample;
    private final int mChannelCount;
    private final int mFrameDuration;
    private final int mOctetsPerFrame;
    private final int mMinOctetsPerFrame;
    private final int mMaxOctetsPerFrame;

    BluetoothLeAudioCodecConfig(int codecType, int codecPriority, int sampleRate, int bitsPerSample,
            int channelCount, int frameDuration, int octetsPerFrame,
            int minOctetsPerFrame, int maxOctetsPerFrame) {
        mCodecType = codecType;
        mCodecPriority = codecPriority;
        mSampleRate = sampleRate;
        mBitsPerSample = bitsPerSample;
        mChannelCount = channelCount;
        mFrameDuration = frameDuration;
        mOctetsPerFrame = octetsPerFrame;
        mMinOctetsPerFrame = minOctetsPerFrame;
        mMaxOctetsPerFrame = maxOctetsPerFrame;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mCodecType);
        out.writeInt(mCodecPriority);
        out.writeInt(mSampleRate);
        out.writeInt(mBitsPerSample);
        out.writeInt(mChannelCount);
        out.writeInt(mFrameDuration);
        out.writeInt(mOctetsPerFrame);
        out.writeInt(mMinOctetsPerFrame);
        out.writeInt(mMaxOctetsPerFrame);
    }

    public static final Parcelable.Creator<BluetoothLeAudioCodecConfig> CREATOR = new Parcelable.Creator<BluetoothLeAudioCodecConfig>() {
        public BluetoothLeAudioCodecConfig createFromParcel(Parcel in) {
            return new BluetoothLeAudioCodecConfig(
                    in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt(), in.readInt(),
                    in.readInt(), in.readInt(), in.readInt());
        }

        public BluetoothLeAudioCodecConfig[] newArray(int size) {
            return new BluetoothLeAudioCodecConfig[size];
        }
    };

    public String toString() {
        return "{codecName:" + getCodecName() + ",mCodecType:" + mCodecType
                + ",mCodecPriority:" + mCodecPriority + ",mSampleRate:" + mSampleRate
                + ",mBitsPerSample:" + mBitsPerSample + ",mChannelCount:" + mChannelCount
                + ",mFrameDuration:" + mFrameDuration + ",mOctetsPerFrame:" + mOctetsPerFrame
                + ",mMinOctetsPerFrame:" + mMinOctetsPerFrame
                + ",mMaxOctetsPerFrame:" + mMaxOctetsPerFrame + "}";
    }

    public int getCodecType() {
        return mCodecType;
    }

    public String getCodecName() {
        if (mCodecType == SOURCE_CODEC_TYPE_LC3) {
            return "LC3";
        }
        return "INVALID CODEC";
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

    public int getChannelCount() {
        return mChannelCount;
    }

    public int getFrameDuration() {
        return mFrameDuration;
    }

    public int getOctetsPerFrame() {
        return mOctetsPerFrame;
    }

    public int getMinOctetsPerFrame() {
        return mMinOctetsPerFrame;
    }

    public int getMaxOctetsPerFrame() {
        return mMaxOctetsPerFrame;
    }

    public boolean equals(Object o) {
        if (!(o instanceof BluetoothLeAudioCodecConfig)) {
            return false;
        }
        BluetoothLeAudioCodecConfig other = (BluetoothLeAudioCodecConfig) o;
        return other.mCodecType == mCodecType
                && other.mCodecPriority == mCodecPriority
                && other.mSampleRate == mSampleRate
                && other.mBitsPerSample == mBitsPerSample
                && other.mChannelCount == mChannelCount
                && other.mFrameDuration == mFrameDuration
                && other.mOctetsPerFrame == mOctetsPerFrame
                && other.mMinOctetsPerFrame == mMinOctetsPerFrame
                && other.mMaxOctetsPerFrame == mMaxOctetsPerFrame;
    }

    public int hashCode() {
        return Objects.hash(mCodecType, mCodecPriority, mSampleRate, mBitsPerSample,
                mChannelCount, mFrameDuration, mOctetsPerFrame, mMinOctetsPerFrame, mMaxOctetsPerFrame);
    }

    public static final class Builder {
        private int mCodecType = SOURCE_CODEC_TYPE_INVALID;
        private int mCodecPriority = CODEC_PRIORITY_DEFAULT;
        private int mSampleRate = SAMPLE_RATE_NONE;
        private int mBitsPerSample = BITS_PER_SAMPLE_NONE;
        private int mChannelCount = CHANNEL_COUNT_NONE;
        private int mFrameDuration = FRAME_DURATION_NONE;
        private int mOctetsPerFrame;
        private int mMinOctetsPerFrame;
        private int mMaxOctetsPerFrame;

        public Builder() {}

        public Builder(BluetoothLeAudioCodecConfig config) {
            mCodecType = config.getCodecType();
            mCodecPriority = config.getCodecPriority();
            mSampleRate = config.getSampleRate();
            mBitsPerSample = config.getBitsPerSample();
            mChannelCount = config.getChannelCount();
            mFrameDuration = config.getFrameDuration();
            mOctetsPerFrame = config.getOctetsPerFrame();
            mMinOctetsPerFrame = config.getMinOctetsPerFrame();
            mMaxOctetsPerFrame = config.getMaxOctetsPerFrame();
        }

        public Builder setCodecType(int codecType) {
            mCodecType = codecType;
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

        public Builder setChannelCount(int channelCount) {
            mChannelCount = channelCount;
            return this;
        }

        public Builder setFrameDuration(int frameDuration) {
            mFrameDuration = frameDuration;
            return this;
        }

        public Builder setOctetsPerFrame(int octetsPerFrame) {
            mOctetsPerFrame = octetsPerFrame;
            return this;
        }

        public Builder setMinOctetsPerFrame(int minOctetsPerFrame) {
            mMinOctetsPerFrame = minOctetsPerFrame;
            return this;
        }

        public Builder setMaxOctetsPerFrame(int maxOctetsPerFrame) {
            mMaxOctetsPerFrame = maxOctetsPerFrame;
            return this;
        }

        public BluetoothLeAudioCodecConfig build() {
            return new BluetoothLeAudioCodecConfig(mCodecType, mCodecPriority, mSampleRate,
                    mBitsPerSample, mChannelCount, mFrameDuration, mOctetsPerFrame,
                    mMinOctetsPerFrame, mMaxOctetsPerFrame);
        }
    }
}
