// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class AudioMixerAttributes implements Parcelable {
    public static final int MIXER_BEHAVIOR_DEFAULT = 0;
    public static final int MIXER_BEHAVIOR_BIT_PERFECT = 1;

    private final AudioFormat mFormat;
    private final int mMixerBehavior;

    private AudioMixerAttributes(AudioFormat format, int mixerBehavior) {
        mFormat = Objects.requireNonNull(format, "format");
        mMixerBehavior = mixerBehavior;
    }

    private AudioMixerAttributes(Parcel in) {
        mFormat = AudioFormat.CREATOR.createFromParcel(in);
        mMixerBehavior = in.readInt();
    }

    public AudioFormat getFormat() {
        return mFormat;
    }

    public int getMixerBehavior() {
        return mMixerBehavior;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFormat, mMixerBehavior);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AudioMixerAttributes)) return false;
        AudioMixerAttributes that = (AudioMixerAttributes) o;
        return mMixerBehavior == that.mMixerBehavior && Objects.equals(mFormat, that.mFormat);
    }

    @Override
    public String toString() {
        return "AudioMixerAttributes{format=" + mFormat + ", mixerBehavior=" + mMixerBehavior + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        mFormat.writeToParcel(dest, flags);
        dest.writeInt(mMixerBehavior);
    }

    public static final Parcelable.Creator<AudioMixerAttributes> CREATOR =
            new Parcelable.Creator<AudioMixerAttributes>() {
                @Override
                public AudioMixerAttributes createFromParcel(Parcel in) {
                    return new AudioMixerAttributes(in);
                }

                @Override
                public AudioMixerAttributes[] newArray(int size) {
                    return new AudioMixerAttributes[size];
                }
            };

    public static final class Builder {
        private final AudioFormat mFormat;
        private int mMixerBehavior = MIXER_BEHAVIOR_DEFAULT;

        public Builder(AudioFormat format) {
            mFormat = Objects.requireNonNull(format, "format");
        }

        public Builder setMixerBehavior(int mixerBehavior) {
            if (mixerBehavior != MIXER_BEHAVIOR_DEFAULT && mixerBehavior != MIXER_BEHAVIOR_BIT_PERFECT) {
                throw new IllegalArgumentException("Invalid mixer behavior: " + mixerBehavior);
            }
            mMixerBehavior = mixerBehavior;
            return this;
        }

        public AudioMixerAttributes build() {
            return new AudioMixerAttributes(mFormat, mMixerBehavior);
        }
    }
}
