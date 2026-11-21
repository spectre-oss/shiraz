// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class BluetoothCodecStatus implements Parcelable {
    public static final String EXTRA_CODEC_STATUS = "android.bluetooth.extra.CODEC_STATUS";

    private final BluetoothCodecConfig mCodecConfig;
    private final List<BluetoothCodecConfig> mCodecsLocalCapabilities;
    private final List<BluetoothCodecConfig> mCodecsSelectableCapabilities;

    BluetoothCodecStatus(BluetoothCodecConfig codecConfig,
            List<BluetoothCodecConfig> codecsLocalCapabilities,
            List<BluetoothCodecConfig> codecsSelectableCapabilities) {
        mCodecConfig = codecConfig;
        mCodecsLocalCapabilities = codecsLocalCapabilities != null
                ? Collections.unmodifiableList(new ArrayList<BluetoothCodecConfig>(codecsLocalCapabilities))
                : Collections.<BluetoothCodecConfig>emptyList();
        mCodecsSelectableCapabilities = codecsSelectableCapabilities != null
                ? Collections.unmodifiableList(new ArrayList<BluetoothCodecConfig>(codecsSelectableCapabilities))
                : Collections.<BluetoothCodecConfig>emptyList();
    }

    public boolean equals(Object o) {
        if (!(o instanceof BluetoothCodecStatus)) {
            return false;
        }
        BluetoothCodecStatus other = (BluetoothCodecStatus) o;
        return Objects.equals(other.mCodecConfig, mCodecConfig)
                && Objects.equals(other.mCodecsLocalCapabilities, mCodecsLocalCapabilities)
                && Objects.equals(other.mCodecsSelectableCapabilities, mCodecsSelectableCapabilities);
    }

    private static boolean hasSingleBit(int mask) {
        return mask == 0 || (mask & (mask - 1)) == 0;
    }

    public boolean isCodecConfigSelectable(BluetoothCodecConfig codecConfig) {
        if (codecConfig == null || !hasSingleBit(codecConfig.getSampleRate())
                || !hasSingleBit(codecConfig.getBitsPerSample())
                || !hasSingleBit(codecConfig.getChannelMode())) {
            return false;
        }
        for (BluetoothCodecConfig selectable : mCodecsSelectableCapabilities) {
            if (codecConfig.getCodecType() != selectable.getCodecType()) {
                continue;
            }
            int sr = codecConfig.getSampleRate();
            if (sr != BluetoothCodecConfig.SAMPLE_RATE_NONE && (sr & selectable.getSampleRate()) == 0) {
                continue;
            }
            int bps = codecConfig.getBitsPerSample();
            if (bps != BluetoothCodecConfig.BITS_PER_SAMPLE_NONE && (bps & selectable.getBitsPerSample()) == 0) {
                continue;
            }
            int cm = codecConfig.getChannelMode();
            if (cm != BluetoothCodecConfig.CHANNEL_MODE_NONE && (cm & selectable.getChannelMode()) == 0) {
                continue;
            }
            return true;
        }
        return false;
    }

    public int hashCode() {
        return Objects.hash(mCodecConfig, mCodecsLocalCapabilities, mCodecsSelectableCapabilities);
    }

    public String toString() {
        return "{mCodecConfig:" + mCodecConfig
                + ",mCodecsLocalCapabilities:" + mCodecsLocalCapabilities
                + ",mCodecsSelectableCapabilities:" + mCodecsSelectableCapabilities + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeTypedObject(mCodecConfig, flags);
        out.writeTypedList(mCodecsLocalCapabilities);
        out.writeTypedList(mCodecsSelectableCapabilities);
    }

    public static final Parcelable.Creator<BluetoothCodecStatus> CREATOR = new Parcelable.Creator<BluetoothCodecStatus>() {
        public BluetoothCodecStatus createFromParcel(Parcel in) {
            BluetoothCodecConfig cfg = in.readTypedObject(BluetoothCodecConfig.CREATOR);
            List<BluetoothCodecConfig> local = in.createTypedArrayList(BluetoothCodecConfig.CREATOR);
            List<BluetoothCodecConfig> selectable = in.createTypedArrayList(BluetoothCodecConfig.CREATOR);
            return new BluetoothCodecStatus(cfg, local, selectable);
        }

        public BluetoothCodecStatus[] newArray(int size) {
            return new BluetoothCodecStatus[size];
        }
    };

    public BluetoothCodecConfig getCodecConfig() {
        return mCodecConfig;
    }

    public List<BluetoothCodecConfig> getCodecsLocalCapabilities() {
        return mCodecsLocalCapabilities;
    }

    public List<BluetoothCodecConfig> getCodecsSelectableCapabilities() {
        return mCodecsSelectableCapabilities;
    }

    public static final class Builder {
        private BluetoothCodecConfig mCodecConfig;
        private List<BluetoothCodecConfig> mCodecsLocalCapabilities;
        private List<BluetoothCodecConfig> mCodecsSelectableCapabilities;

        public Builder() {}

        public Builder setCodecConfig(BluetoothCodecConfig codecConfig) {
            mCodecConfig = codecConfig;
            return this;
        }

        public Builder setCodecsLocalCapabilities(List<BluetoothCodecConfig> codecsLocalCapabilities) {
            mCodecsLocalCapabilities = codecsLocalCapabilities;
            return this;
        }

        public Builder setCodecsSelectableCapabilities(List<BluetoothCodecConfig> codecsSelectableCapabilities) {
            mCodecsSelectableCapabilities = codecsSelectableCapabilities;
            return this;
        }

        public BluetoothCodecStatus build() {
            return new BluetoothCodecStatus(mCodecConfig, mCodecsLocalCapabilities, mCodecsSelectableCapabilities);
        }
    }
}
