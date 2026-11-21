// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class BluetoothLeAudioCodecStatus implements Parcelable {
    public static final String EXTRA_LE_AUDIO_CODEC_STATUS = "android.bluetooth.extra.LE_AUDIO_CODEC_STATUS";

    private final BluetoothLeAudioCodecConfig mInputCodecConfig;
    private final BluetoothLeAudioCodecConfig mOutputCodecConfig;
    private final List<BluetoothLeAudioCodecConfig> mInputCodecLocalCapabilities;
    private final List<BluetoothLeAudioCodecConfig> mOutputCodecLocalCapabilities;
    private final List<BluetoothLeAudioCodecConfig> mInputCodecSelectableCapabilities;
    private final List<BluetoothLeAudioCodecConfig> mOutputCodecSelectableCapabilities;

    public BluetoothLeAudioCodecStatus(
            BluetoothLeAudioCodecConfig inputCodecConfig,
            BluetoothLeAudioCodecConfig outputCodecConfig,
            List<BluetoothLeAudioCodecConfig> inputCodecLocalCapabilities,
            List<BluetoothLeAudioCodecConfig> outputCodecLocalCapabilities,
            List<BluetoothLeAudioCodecConfig> inputCodecSelectableCapabilities,
            List<BluetoothLeAudioCodecConfig> outputCodecSelectableCapabilities) {
        mInputCodecConfig = inputCodecConfig;
        mOutputCodecConfig = outputCodecConfig;
        mInputCodecLocalCapabilities = copyList(inputCodecLocalCapabilities);
        mOutputCodecLocalCapabilities = copyList(outputCodecLocalCapabilities);
        mInputCodecSelectableCapabilities = copyList(inputCodecSelectableCapabilities);
        mOutputCodecSelectableCapabilities = copyList(outputCodecSelectableCapabilities);
    }

    private static List<BluetoothLeAudioCodecConfig> copyList(List<BluetoothLeAudioCodecConfig> src) {
        if (src == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<BluetoothLeAudioCodecConfig>(src));
    }

    public boolean equals(Object o) {
        if (!(o instanceof BluetoothLeAudioCodecStatus)) {
            return false;
        }
        BluetoothLeAudioCodecStatus other = (BluetoothLeAudioCodecStatus) o;
        return Objects.equals(other.mInputCodecConfig, mInputCodecConfig)
                && Objects.equals(other.mOutputCodecConfig, mOutputCodecConfig)
                && Objects.equals(other.mInputCodecLocalCapabilities, mInputCodecLocalCapabilities)
                && Objects.equals(other.mOutputCodecLocalCapabilities, mOutputCodecLocalCapabilities)
                && Objects.equals(other.mInputCodecSelectableCapabilities, mInputCodecSelectableCapabilities)
                && Objects.equals(other.mOutputCodecSelectableCapabilities, mOutputCodecSelectableCapabilities);
    }

    private static boolean isSelectable(BluetoothLeAudioCodecConfig cfg, List<BluetoothLeAudioCodecConfig> caps) {
        if (cfg == null) {
            return false;
        }
        for (BluetoothLeAudioCodecConfig cap : caps) {
            if (cfg.getCodecType() != cap.getCodecType()) {
                continue;
            }
            if (cfg.getSampleRate() != 0 && (cfg.getSampleRate() & cap.getSampleRate()) == 0) {
                continue;
            }
            if (cfg.getBitsPerSample() != 0 && (cfg.getBitsPerSample() & cap.getBitsPerSample()) == 0) {
                continue;
            }
            if (cfg.getChannelCount() != 0 && (cfg.getChannelCount() & cap.getChannelCount()) == 0) {
                continue;
            }
            if (cfg.getFrameDuration() != 0 && (cfg.getFrameDuration() & cap.getFrameDuration()) == 0) {
                continue;
            }
            return true;
        }
        return false;
    }

    public boolean isInputCodecConfigSelectable(BluetoothLeAudioCodecConfig codecConfig) {
        return isSelectable(codecConfig, mInputCodecSelectableCapabilities);
    }

    public boolean isOutputCodecConfigSelectable(BluetoothLeAudioCodecConfig codecConfig) {
        return isSelectable(codecConfig, mOutputCodecSelectableCapabilities);
    }

    public int hashCode() {
        return Objects.hash(mInputCodecConfig, mOutputCodecConfig,
                mInputCodecLocalCapabilities, mOutputCodecLocalCapabilities,
                mInputCodecSelectableCapabilities, mOutputCodecSelectableCapabilities);
    }

    public String toString() {
        return "{mInputCodecConfig:" + mInputCodecConfig + ",mOutputCodecConfig:" + mOutputCodecConfig + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeTypedObject(mInputCodecConfig, flags);
        out.writeTypedObject(mOutputCodecConfig, flags);
        out.writeTypedList(mInputCodecLocalCapabilities);
        out.writeTypedList(mOutputCodecLocalCapabilities);
        out.writeTypedList(mInputCodecSelectableCapabilities);
        out.writeTypedList(mOutputCodecSelectableCapabilities);
    }

    public static final Parcelable.Creator<BluetoothLeAudioCodecStatus> CREATOR = new Parcelable.Creator<BluetoothLeAudioCodecStatus>() {
        public BluetoothLeAudioCodecStatus createFromParcel(Parcel in) {
            BluetoothLeAudioCodecConfig inputCfg = in.readTypedObject(BluetoothLeAudioCodecConfig.CREATOR);
            BluetoothLeAudioCodecConfig outputCfg = in.readTypedObject(BluetoothLeAudioCodecConfig.CREATOR);
            List<BluetoothLeAudioCodecConfig> inLocal = in.createTypedArrayList(BluetoothLeAudioCodecConfig.CREATOR);
            List<BluetoothLeAudioCodecConfig> outLocal = in.createTypedArrayList(BluetoothLeAudioCodecConfig.CREATOR);
            List<BluetoothLeAudioCodecConfig> inSel = in.createTypedArrayList(BluetoothLeAudioCodecConfig.CREATOR);
            List<BluetoothLeAudioCodecConfig> outSel = in.createTypedArrayList(BluetoothLeAudioCodecConfig.CREATOR);
            return new BluetoothLeAudioCodecStatus(inputCfg, outputCfg, inLocal, outLocal, inSel, outSel);
        }

        public BluetoothLeAudioCodecStatus[] newArray(int size) {
            return new BluetoothLeAudioCodecStatus[size];
        }
    };

    public BluetoothLeAudioCodecConfig getInputCodecConfig() {
        return mInputCodecConfig;
    }

    public BluetoothLeAudioCodecConfig getOutputCodecConfig() {
        return mOutputCodecConfig;
    }

    public List<BluetoothLeAudioCodecConfig> getInputCodecLocalCapabilities() {
        return mInputCodecLocalCapabilities;
    }

    public List<BluetoothLeAudioCodecConfig> getOutputCodecLocalCapabilities() {
        return mOutputCodecLocalCapabilities;
    }

    public List<BluetoothLeAudioCodecConfig> getInputCodecSelectableCapabilities() {
        return mInputCodecSelectableCapabilities;
    }

    public List<BluetoothLeAudioCodecConfig> getOutputCodecSelectableCapabilities() {
        return mOutputCodecSelectableCapabilities;
    }
}
