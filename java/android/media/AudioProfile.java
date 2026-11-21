// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public class AudioProfile implements Parcelable {
    public static final int AUDIO_ENCAPSULATION_TYPE_NONE = 0;
    public static final int AUDIO_ENCAPSULATION_TYPE_IEC61937 = 1;
    public static final int AUDIO_ENCAPSULATION_TYPE_PCM = 2;

    private final int mFormat;
    private final int[] mSamplingRates;
    private final int[] mChannelMasks;
    private final int[] mChannelIndexMasks;
    private final int mEncapsulationType;

    AudioProfile(int format, int[] samplingRates, int[] channelMasks, int[] channelIndexMasks, int encapsulationType) {
        mFormat = format;
        mSamplingRates = samplingRates != null ? samplingRates.clone() : new int[0];
        mChannelMasks = channelMasks != null ? channelMasks.clone() : new int[0];
        mChannelIndexMasks = channelIndexMasks != null ? channelIndexMasks.clone() : new int[0];
        mEncapsulationType = encapsulationType;
    }

    private AudioProfile(Parcel in) {
        mFormat = in.readInt();
        int[] rates = in.createIntArray();
        mSamplingRates = rates != null ? rates : new int[0];
        int[] masks = in.createIntArray();
        mChannelMasks = masks != null ? masks : new int[0];
        int[] indexMasks = in.createIntArray();
        mChannelIndexMasks = indexMasks != null ? indexMasks : new int[0];
        mEncapsulationType = in.readInt();
    }

    public int getFormat() {
        return mFormat;
    }

    public int[] getChannelMasks() {
        return mChannelMasks.clone();
    }

    public int[] getChannelIndexMasks() {
        return mChannelIndexMasks.clone();
    }

    public int[] getSampleRates() {
        return mSamplingRates.clone();
    }

    public int getEncapsulationType() {
        return mEncapsulationType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mFormat,
                Arrays.hashCode(mSamplingRates),
                Arrays.hashCode(mChannelMasks),
                Arrays.hashCode(mChannelIndexMasks),
                mEncapsulationType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AudioProfile)) return false;
        AudioProfile that = (AudioProfile) o;
        return mFormat == that.mFormat
                && Arrays.equals(mSamplingRates, that.mSamplingRates)
                && Arrays.equals(mChannelMasks, that.mChannelMasks)
                && Arrays.equals(mChannelIndexMasks, that.mChannelIndexMasks)
                && mEncapsulationType == that.mEncapsulationType;
    }

    @Override
    public String toString() {
        return "AudioProfile{format=" + mFormat
                + ", sampleRates=" + Arrays.toString(mSamplingRates)
                + ", channelMasks=" + Arrays.toString(mChannelMasks)
                + ", channelIndexMasks=" + Arrays.toString(mChannelIndexMasks)
                + ", encapsulationType=" + mEncapsulationType + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mFormat);
        dest.writeIntArray(mSamplingRates);
        dest.writeIntArray(mChannelMasks);
        dest.writeIntArray(mChannelIndexMasks);
        dest.writeInt(mEncapsulationType);
    }

    public static final Parcelable.Creator<AudioProfile> CREATOR =
            new Parcelable.Creator<AudioProfile>() {
                @Override
                public AudioProfile createFromParcel(Parcel in) {
                    return new AudioProfile(in);
                }

                @Override
                public AudioProfile[] newArray(int size) {
                    return new AudioProfile[size];
                }
            };
}
