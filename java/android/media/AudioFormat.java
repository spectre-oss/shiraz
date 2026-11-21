// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class AudioFormat implements Parcelable {
    public static final int ENCODING_INVALID = 0;
    public static final int ENCODING_DEFAULT = 1;
    public static final int ENCODING_PCM_16BIT = 2;
    public static final int ENCODING_PCM_8BIT = 3;
    public static final int ENCODING_PCM_FLOAT = 4;
    public static final int ENCODING_AC3 = 5;
    public static final int ENCODING_E_AC3 = 6;
    public static final int ENCODING_DTS = 7;
    public static final int ENCODING_DTS_HD = 8;
    public static final int ENCODING_MP3 = 9;
    public static final int ENCODING_AAC_LC = 10;
    public static final int ENCODING_AAC_HE_V1 = 11;
    public static final int ENCODING_AAC_HE_V2 = 12;
    public static final int ENCODING_IEC61937 = 13;
    public static final int ENCODING_DOLBY_TRUEHD = 14;
    public static final int ENCODING_AAC_ELD = 15;
    public static final int ENCODING_AAC_XHE = 16;
    public static final int ENCODING_AC4 = 17;
    public static final int ENCODING_E_AC3_JOC = 18;
    public static final int ENCODING_DOLBY_MAT = 19;
    public static final int ENCODING_OPUS = 20;
    public static final int ENCODING_PCM_24BIT_PACKED = 21;
    public static final int ENCODING_PCM_32BIT = 22;
    public static final int ENCODING_MPEGH_BL_L3 = 23;
    public static final int ENCODING_MPEGH_BL_L4 = 24;
    public static final int ENCODING_MPEGH_LC_L3 = 25;
    public static final int ENCODING_MPEGH_LC_L4 = 26;
    public static final int ENCODING_DTS_UHD = 27;
    public static final int ENCODING_DTS_UHD_P1 = 27;
    public static final int ENCODING_DRA = 28;
    public static final int ENCODING_DTS_HD_MA = 29;
    public static final int ENCODING_DTS_UHD_P2 = 30;
    public static final int ENCODING_DSD = 31;

    @Deprecated public static final int CHANNEL_CONFIGURATION_INVALID = 0;
    @Deprecated public static final int CHANNEL_CONFIGURATION_DEFAULT = 1;
    @Deprecated public static final int CHANNEL_CONFIGURATION_MONO = 2;
    @Deprecated public static final int CHANNEL_CONFIGURATION_STEREO = 3;

    public static final int CHANNEL_INVALID = 0;
    public static final int CHANNEL_OUT_DEFAULT = 1;
    public static final int CHANNEL_OUT_FRONT_LEFT = 4;
    public static final int CHANNEL_OUT_FRONT_RIGHT = 8;
    public static final int CHANNEL_OUT_FRONT_CENTER = 16;
    public static final int CHANNEL_OUT_LOW_FREQUENCY = 32;
    public static final int CHANNEL_OUT_BACK_LEFT = 64;
    public static final int CHANNEL_OUT_BACK_RIGHT = 128;
    public static final int CHANNEL_OUT_FRONT_LEFT_OF_CENTER = 256;
    public static final int CHANNEL_OUT_FRONT_RIGHT_OF_CENTER = 512;
    public static final int CHANNEL_OUT_BACK_CENTER = 1024;
    public static final int CHANNEL_OUT_SIDE_LEFT = 2048;
    public static final int CHANNEL_OUT_SIDE_RIGHT = 4096;
    public static final int CHANNEL_OUT_TOP_CENTER = 8192;
    public static final int CHANNEL_OUT_TOP_FRONT_LEFT = 16384;
    public static final int CHANNEL_OUT_TOP_FRONT_CENTER = 32768;
    public static final int CHANNEL_OUT_TOP_FRONT_RIGHT = 65536;
    public static final int CHANNEL_OUT_TOP_BACK_LEFT = 131072;
    public static final int CHANNEL_OUT_TOP_BACK_CENTER = 262144;
    public static final int CHANNEL_OUT_TOP_BACK_RIGHT = 524288;
    public static final int CHANNEL_OUT_TOP_SIDE_LEFT = 1048576;
    public static final int CHANNEL_OUT_TOP_SIDE_RIGHT = 2097152;
    public static final int CHANNEL_OUT_BOTTOM_FRONT_LEFT = 4194304;
    public static final int CHANNEL_OUT_BOTTOM_FRONT_CENTER = 8388608;
    public static final int CHANNEL_OUT_BOTTOM_FRONT_RIGHT = 16777216;
    public static final int CHANNEL_OUT_LOW_FREQUENCY_2 = 33554432;
    public static final int CHANNEL_OUT_FRONT_WIDE_LEFT = 67108864;
    public static final int CHANNEL_OUT_FRONT_WIDE_RIGHT = 134217728;

    public static final int CHANNEL_OUT_MONO = 4;
    public static final int CHANNEL_OUT_STEREO = 12;
    public static final int CHANNEL_OUT_QUAD = 204;
    public static final int CHANNEL_OUT_SURROUND = 1052;
    public static final int CHANNEL_OUT_5POINT1 = 252;
    public static final int CHANNEL_OUT_5POINT1POINT2 = 3145980;
    public static final int CHANNEL_OUT_5POINT1POINT4 = 737532;
    public static final int CHANNEL_OUT_6POINT1 = 1276;
    public static final int CHANNEL_OUT_7POINT1 = 1020;
    public static final int CHANNEL_OUT_7POINT1_SURROUND = 6396;
    public static final int CHANNEL_OUT_7POINT1POINT2 = 3152124;
    public static final int CHANNEL_OUT_7POINT1POINT4 = 743676;
    public static final int CHANNEL_OUT_9POINT1POINT4 = 202070268;
    public static final int CHANNEL_OUT_9POINT1POINT6 = 205215996;

    public static final int CHANNEL_IN_DEFAULT = 1;
    public static final int CHANNEL_IN_LEFT = 4;
    public static final int CHANNEL_IN_RIGHT = 8;
    public static final int CHANNEL_IN_FRONT = 16;
    public static final int CHANNEL_IN_BACK = 32;
    public static final int CHANNEL_IN_LEFT_PROCESSED = 64;
    public static final int CHANNEL_IN_RIGHT_PROCESSED = 128;
    public static final int CHANNEL_IN_FRONT_PROCESSED = 256;
    public static final int CHANNEL_IN_BACK_PROCESSED = 512;
    public static final int CHANNEL_IN_PRESSURE = 1024;
    public static final int CHANNEL_IN_X_AXIS = 2048;
    public static final int CHANNEL_IN_Y_AXIS = 4096;
    public static final int CHANNEL_IN_Z_AXIS = 8192;
    public static final int CHANNEL_IN_VOICE_UPLINK = 16384;
    public static final int CHANNEL_IN_VOICE_DNLINK = 32768;
    public static final int CHANNEL_IN_MONO = 16;
    public static final int CHANNEL_IN_STEREO = 12;

    public static final int SAMPLE_RATE_UNSPECIFIED = 0;

    private final int rate;
    private final int mask;
    private final int indexMask;
    private final int encoding;

    private AudioFormat(Builder b) {
        rate = b.rate;
        mask = b.mask;
        indexMask = b.indexMask;
        encoding = b.encoding;
    }

    private AudioFormat(Parcel in) {
        encoding = in.readInt();
        rate = in.readInt();
        mask = in.readInt();
        indexMask = in.readInt();
    }

    public int getSampleRate() {
        return rate;
    }

    public int getChannelMask() {
        return mask;
    }

    public int getChannelIndexMask() {
        return indexMask;
    }

    public int getEncoding() {
        return encoding;
    }

    public int getChannelCount() {
        if (indexMask != 0) {
            return Integer.bitCount(indexMask);
        }
        if (mask == CHANNEL_IN_MONO || mask == CHANNEL_OUT_MONO || mask == CHANNEL_OUT_DEFAULT) {
            return mask == CHANNEL_OUT_DEFAULT ? 0 : 1;
        }
        if (mask == 12) {
            return 2;
        }
        return Integer.bitCount(mask >> 2);
    }

    public int getFrameSizeInBytes() {
        int channels = Math.max(1, getChannelCount());
        switch (encoding) {
            case ENCODING_PCM_8BIT:
                return channels;
            case ENCODING_PCM_16BIT:
            case ENCODING_IEC61937:
            case ENCODING_DEFAULT:
                return channels * 2;
            case ENCODING_PCM_24BIT_PACKED:
                return channels * 3;
            case ENCODING_PCM_32BIT:
            case ENCODING_PCM_FLOAT:
                return channels * 4;
            default:
                return 1;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AudioFormat)) return false;
        AudioFormat that = (AudioFormat) o;
        return rate == that.rate
                && mask == that.mask
                && indexMask == that.indexMask
                && encoding == that.encoding;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(encoding),
                Integer.valueOf(rate),
                Integer.valueOf(mask),
                Integer.valueOf(indexMask));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(encoding);
        dest.writeInt(rate);
        dest.writeInt(mask);
        dest.writeInt(indexMask);
    }

    @Override
    public String toString() {
        return "AudioFormat: enc=" + encoding + " ch=" + mask + " idx=" + indexMask + " sr=" + rate;
    }

    public static final Parcelable.Creator<AudioFormat> CREATOR =
            new Parcelable.Creator<AudioFormat>() {
                @Override
                public AudioFormat createFromParcel(Parcel in) {
                    return new AudioFormat(in);
                }

                @Override
                public AudioFormat[] newArray(int size) {
                    return new AudioFormat[size];
                }
            };

    public static class Builder {
        private int rate;
        private int mask;
        private int indexMask;
        private int encoding = ENCODING_PCM_16BIT;

        public Builder() {}

        public Builder(AudioFormat f) {
            Objects.requireNonNull(f, "format");
            rate = f.rate;
            mask = f.mask;
            indexMask = f.indexMask;
            encoding = f.encoding;
        }

        public Builder setSampleRate(int value) throws IllegalArgumentException {
            if (value != SAMPLE_RATE_UNSPECIFIED && (value < 4000 || value > 192000)) {
                throw new IllegalArgumentException("Invalid sample rate " + value);
            }
            rate = value;
            return this;
        }

        public Builder setChannelMask(int value) {
            if (value == CHANNEL_INVALID) {
                throw new IllegalArgumentException("Invalid zero channel mask");
            }
            mask = value;
            return this;
        }

        public Builder setChannelIndexMask(int value) {
            if (value == 0) {
                throw new IllegalArgumentException("Invalid zero channel index mask");
            }
            indexMask = value;
            return this;
        }

        public Builder setEncoding(int value) throws IllegalArgumentException {
            if (value == ENCODING_DEFAULT) {
                encoding = ENCODING_PCM_16BIT;
                return this;
            }
            if (value < ENCODING_PCM_16BIT || value > ENCODING_DSD) {
                throw new IllegalArgumentException("Invalid audio encoding " + value);
            }
            encoding = value;
            return this;
        }

        public AudioFormat build() {
            return new AudioFormat(this);
        }
    }
}
