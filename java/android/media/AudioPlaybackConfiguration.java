// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class AudioPlaybackConfiguration implements Parcelable {
    private final int mPlayerIId;
    private final AudioAttributes mAudioAttributes;
    private final AudioDeviceInfo mDeviceInfo;

    AudioPlaybackConfiguration(int playerIId, AudioAttributes attributes, AudioDeviceInfo deviceInfo) {
        mPlayerIId = playerIId;
        mAudioAttributes = attributes != null ? attributes : new AudioAttributes.Builder().build();
        mDeviceInfo = deviceInfo;
    }

    private AudioPlaybackConfiguration(Parcel in) {
        mPlayerIId = in.readInt();
        mAudioAttributes = AudioAttributes.CREATOR.createFromParcel(in);
        mDeviceInfo = null;
    }

    public AudioAttributes getAudioAttributes() {
        return mAudioAttributes;
    }

    public AudioDeviceInfo getAudioDeviceInfo() {
        return mDeviceInfo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPlayerIId, mAudioAttributes);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPlayerIId);
        mAudioAttributes.writeToParcel(dest, flags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AudioPlaybackConfiguration)) return false;
        AudioPlaybackConfiguration that = (AudioPlaybackConfiguration) o;
        return mPlayerIId == that.mPlayerIId
                && Objects.equals(mAudioAttributes, that.mAudioAttributes);
    }

    @Override
    public String toString() {
        return "AudioPlaybackConfiguration{playerIId=" + mPlayerIId
                + ", audioAttributes=" + mAudioAttributes + "}";
    }

    public static final Parcelable.Creator<AudioPlaybackConfiguration> CREATOR =
            new Parcelable.Creator<AudioPlaybackConfiguration>() {
                @Override
                public AudioPlaybackConfiguration createFromParcel(Parcel p) {
                    return new AudioPlaybackConfiguration(p);
                }

                @Override
                public AudioPlaybackConfiguration[] newArray(int size) {
                    return new AudioPlaybackConfiguration[size];
                }
            };
}
