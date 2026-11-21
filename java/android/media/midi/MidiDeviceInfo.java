// SPDX-License-Identifier: GPL-3.0-only
package android.media.midi;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class MidiDeviceInfo implements Parcelable {
    public static final int TYPE_USB = 1;
    public static final int TYPE_VIRTUAL = 2;
    public static final int TYPE_BLUETOOTH = 3;

    public static final String PROPERTY_NAME = "name";
    public static final String PROPERTY_MANUFACTURER = "manufacturer";
    public static final String PROPERTY_PRODUCT = "product";
    public static final String PROPERTY_VERSION = "version";
    public static final String PROPERTY_SERIAL_NUMBER = "serial_number";
    public static final String PROPERTY_USB_DEVICE = "usb_device";
    public static final String PROPERTY_BLUETOOTH_DEVICE = "bluetooth_device";

    public static final int PROTOCOL_UNKNOWN = -1;
    public static final int PROTOCOL_UMP_USE_MIDI_CI = 0;
    public static final int PROTOCOL_UMP_MIDI_1_0_UP_TO_64_BITS = 1;
    public static final int PROTOCOL_UMP_MIDI_1_0_UP_TO_64_BITS_AND_JRTS = 2;
    public static final int PROTOCOL_UMP_MIDI_1_0_UP_TO_128_BITS = 3;
    public static final int PROTOCOL_UMP_MIDI_1_0_UP_TO_128_BITS_AND_JRTS = 4;
    public static final int PROTOCOL_UMP_MIDI_2_0 = 17;
    public static final int PROTOCOL_UMP_MIDI_2_0_AND_JRTS = 18;

    public static final class PortInfo {
        public static final int TYPE_INPUT = 1;
        public static final int TYPE_OUTPUT = 2;

        private final int mPortType;
        private final int mPortNumber;
        private final String mName;

        PortInfo(int type, int portNumber, String name) {
            mPortType = type;
            mPortNumber = portNumber;
            mName = name != null ? name : "";
        }

        public int getType() {
            return mPortType;
        }

        public int getPortNumber() {
            return mPortNumber;
        }

        public String getName() {
            return mName;
        }
    }

    private final int mType;
    private final int mId;
    private final int mInputPortCount;
    private final int mOutputPortCount;
    private final String[] mInputPortNames;
    private final String[] mOutputPortNames;
    private final Bundle mProperties;
    private final boolean mIsPrivate;
    private final int mDefaultProtocol;

    MidiDeviceInfo(
            int type,
            int id,
            int numInputPorts,
            int numOutputPorts,
            String[] inputPortNames,
            String[] outputPortNames,
            Bundle properties,
            boolean isPrivate,
            int defaultProtocol) {
        mType = type;
        mId = id;
        mInputPortCount = Math.max(0, numInputPorts);
        mOutputPortCount = Math.max(0, numOutputPorts);
        mInputPortNames = new String[mInputPortCount];
        for (int i = 0; i < mInputPortCount; i++) {
            mInputPortNames[i] = (inputPortNames != null && i < inputPortNames.length
                    && inputPortNames[i] != null) ? inputPortNames[i] : "";
        }
        mOutputPortNames = new String[mOutputPortCount];
        for (int i = 0; i < mOutputPortCount; i++) {
            mOutputPortNames[i] = (outputPortNames != null && i < outputPortNames.length
                    && outputPortNames[i] != null) ? outputPortNames[i] : "";
        }
        mProperties = properties != null ? new Bundle(properties) : new Bundle();
        mIsPrivate = isPrivate;
        mDefaultProtocol = defaultProtocol;
    }

    public int getType() {
        return mType;
    }

    public int getId() {
        return mId;
    }

    public int getInputPortCount() {
        return mInputPortCount;
    }

    public int getOutputPortCount() {
        return mOutputPortCount;
    }

    public PortInfo[] getPorts() {
        PortInfo[] ports = new PortInfo[mInputPortCount + mOutputPortCount];
        int index = 0;
        for (int i = 0; i < mInputPortCount; i++) {
            ports[index++] = new PortInfo(PortInfo.TYPE_INPUT, i, mInputPortNames[i]);
        }
        for (int i = 0; i < mOutputPortCount; i++) {
            ports[index++] = new PortInfo(PortInfo.TYPE_OUTPUT, i, mOutputPortNames[i]);
        }
        return ports;
    }

    public Bundle getProperties() {
        return new Bundle(mProperties);
    }

    public boolean isPrivate() {
        return mIsPrivate;
    }

    public int getDefaultProtocol() {
        return mDefaultProtocol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MidiDeviceInfo)) return false;
        MidiDeviceInfo other = (MidiDeviceInfo) o;
        return mId == other.mId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mId);
    }

    @Override
    public String toString() {
        return "MidiDeviceInfo[mType=" + mType
                + ",mId=" + mId
                + ",mInputPortCount=" + mInputPortCount
                + ",mOutputPortCount=" + mOutputPortCount
                + ",mProperties=" + mProperties
                + ",mIsPrivate=" + mIsPrivate
                + ",mDefaultProtocol=" + mDefaultProtocol
                + ",mInputPortNames=" + Arrays.toString(mInputPortNames)
                + ",mOutputPortNames=" + Arrays.toString(mOutputPortNames)
                + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mType);
        parcel.writeInt(mId);
        parcel.writeInt(mInputPortCount);
        parcel.writeInt(mOutputPortCount);
        for (int i = 0; i < mInputPortCount; i++) {
            parcel.writeString(mInputPortNames[i]);
        }
        for (int i = 0; i < mOutputPortCount; i++) {
            parcel.writeString(mOutputPortNames[i]);
        }
        parcel.writeBundle(mProperties);
        parcel.writeInt(mIsPrivate ? 1 : 0);
        parcel.writeInt(mDefaultProtocol);
    }

    public static final Parcelable.Creator<MidiDeviceInfo> CREATOR =
            new Parcelable.Creator<MidiDeviceInfo>() {
                @Override
                public MidiDeviceInfo createFromParcel(Parcel source) {
                    int type = source.readInt();
                    int id = source.readInt();
                    int inputCount = Math.max(0, source.readInt());
                    int outputCount = Math.max(0, source.readInt());
                    String[] inputNames = new String[inputCount];
                    for (int i = 0; i < inputCount; i++) {
                        inputNames[i] = source.readString();
                    }
                    String[] outputNames = new String[outputCount];
                    for (int i = 0; i < outputCount; i++) {
                        outputNames[i] = source.readString();
                    }
                    Bundle props = source.readBundle();
                    boolean isPrivate = source.readInt() != 0;
                    int defaultProtocol = source.readInt();
                    return new MidiDeviceInfo(
                            type, id, inputCount, outputCount,
                            inputNames, outputNames, props, isPrivate, defaultProtocol);
                }

                @Override
                public MidiDeviceInfo[] newArray(int size) {
                    return new MidiDeviceInfo[size];
                }
            };
}
