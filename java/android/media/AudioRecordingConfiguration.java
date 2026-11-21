// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.media.audiofx.AudioEffect;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AudioRecordingConfiguration implements Parcelable {
    private final int mClientSessionId;
    private final int mClientSource;
    private final int mDeviceSource;
    private final AudioFormat mClientFormat;
    private final AudioFormat mDeviceFormat;
    private final AudioDeviceInfo mAudioDevice;
    private final boolean mClientSilenced;
    private final List<AudioEffect.Descriptor> mClientEffects;
    private final List<AudioEffect.Descriptor> mDeviceEffects;

    AudioRecordingConfiguration(
            int sessionId,
            int clientSource,
            AudioFormat clientFormat,
            AudioFormat devFormat,
            AudioDeviceInfo audioDevice,
            boolean silenced) {
        mClientSessionId = sessionId;
        mClientSource = clientSource;
        mDeviceSource = clientSource;
        mClientFormat = clientFormat;
        mDeviceFormat = devFormat != null ? devFormat : clientFormat;
        mAudioDevice = audioDevice;
        mClientSilenced = silenced;
        mClientEffects = Collections.emptyList();
        mDeviceEffects = Collections.emptyList();
    }

    private AudioRecordingConfiguration(Parcel in) {
        mClientSessionId = in.readInt();
        mClientSource = in.readInt();
        mDeviceSource = in.readInt();
        mClientFormat = in.readInt() != 0 ? AudioFormat.CREATOR.createFromParcel(in) : null;
        mDeviceFormat = in.readInt() != 0 ? AudioFormat.CREATOR.createFromParcel(in) : null;
        mAudioDevice = null;
        mClientSilenced = in.readInt() != 0;
        mClientEffects = Collections.emptyList();
        mDeviceEffects = Collections.emptyList();
    }

    public int getClientAudioSource() {
        return mClientSource;
    }

    public int getClientAudioSessionId() {
        return mClientSessionId;
    }

    public AudioFormat getFormat() {
        return mDeviceFormat;
    }

    public AudioFormat getClientFormat() {
        return mClientFormat;
    }

    public AudioDeviceInfo getAudioDevice() {
        return mAudioDevice;
    }

    public boolean isClientSilenced() {
        return mClientSilenced;
    }

    public int getAudioSource() {
        return mDeviceSource;
    }

    public List<AudioEffect.Descriptor> getClientEffects() {
        return new ArrayList<AudioEffect.Descriptor>(mClientEffects);
    }

    public List<AudioEffect.Descriptor> getEffects() {
        return new ArrayList<AudioEffect.Descriptor>(mDeviceEffects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mClientSessionId, mClientSource);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mClientSessionId);
        dest.writeInt(mClientSource);
        dest.writeInt(mDeviceSource);
        if (mClientFormat != null) {
            dest.writeInt(1);
            mClientFormat.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        if (mDeviceFormat != null) {
            dest.writeInt(1);
            mDeviceFormat.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mClientSilenced ? 1 : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AudioRecordingConfiguration)) return false;
        AudioRecordingConfiguration that = (AudioRecordingConfiguration) o;
        return mClientSessionId == that.mClientSessionId
                && mClientSource == that.mClientSource
                && mDeviceSource == that.mDeviceSource
                && mClientSilenced == that.mClientSilenced
                && Objects.equals(mClientFormat, that.mClientFormat)
                && Objects.equals(mDeviceFormat, that.mDeviceFormat);
    }

    public static final Parcelable.Creator<AudioRecordingConfiguration> CREATOR =
            new Parcelable.Creator<AudioRecordingConfiguration>() {
                @Override
                public AudioRecordingConfiguration createFromParcel(Parcel in) {
                    return new AudioRecordingConfiguration(in);
                }

                @Override
                public AudioRecordingConfiguration[] newArray(int size) {
                    return new AudioRecordingConfiguration[size];
                }
            };
}
