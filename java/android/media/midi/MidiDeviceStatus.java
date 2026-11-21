// SPDX-License-Identifier: GPL-3.0-only
package android.media.midi;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;

public final class MidiDeviceStatus implements Parcelable {
    private final MidiDeviceInfo mDeviceInfo;
    private final boolean[] mInputPortOpen;
    private final int[] mOutputPortOpenCount;

    MidiDeviceStatus(
            MidiDeviceInfo deviceInfo,
            boolean[] inputPortOpen,
            int[] outputPortOpenCount) {
        mDeviceInfo = deviceInfo;
        int inputCount = deviceInfo != null ? deviceInfo.getInputPortCount()
                : (inputPortOpen != null ? inputPortOpen.length : 0);
        int outputCount = deviceInfo != null ? deviceInfo.getOutputPortCount()
                : (outputPortOpenCount != null ? outputPortOpenCount.length : 0);
        mInputPortOpen = new boolean[inputCount];
        if (inputPortOpen != null) {
            System.arraycopy(inputPortOpen, 0, mInputPortOpen, 0,
                    Math.min(inputPortOpen.length, inputCount));
        }
        mOutputPortOpenCount = new int[outputCount];
        if (outputPortOpenCount != null) {
            System.arraycopy(outputPortOpenCount, 0, mOutputPortOpenCount, 0,
                    Math.min(outputPortOpenCount.length, outputCount));
        }
    }

    MidiDeviceStatus(MidiDeviceInfo deviceInfo) {
        this(deviceInfo, null, null);
    }

    public MidiDeviceInfo getDeviceInfo() {
        return mDeviceInfo;
    }

    public boolean isInputPortOpen(int portNumber) {
        if (portNumber < 0 || portNumber >= mInputPortOpen.length) {
            return false;
        }
        return mInputPortOpen[portNumber];
    }

    public int getOutputPortOpenCount(int portNumber) {
        if (portNumber < 0 || portNumber >= mOutputPortOpenCount.length) {
            return 0;
        }
        return mOutputPortOpenCount[portNumber];
    }

    @Override
    public String toString() {
        return "mDeviceInfo=" + mDeviceInfo
                + " mInputPortOpen=" + Arrays.toString(mInputPortOpen)
                + " mOutputPortOpenCount=" + Arrays.toString(mOutputPortOpenCount);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        if (mDeviceInfo != null) {
            parcel.writeInt(1);
            mDeviceInfo.writeToParcel(parcel, flags);
        } else {
            parcel.writeInt(0);
        }
        parcel.writeInt(mInputPortOpen.length);
        for (int i = 0; i < mInputPortOpen.length; i++) {
            parcel.writeInt(mInputPortOpen[i] ? 1 : 0);
        }
        parcel.writeInt(mOutputPortOpenCount.length);
        for (int i = 0; i < mOutputPortOpenCount.length; i++) {
            parcel.writeInt(mOutputPortOpenCount[i]);
        }
    }

    public static final Parcelable.Creator<MidiDeviceStatus> CREATOR =
            new Parcelable.Creator<MidiDeviceStatus>() {
                @Override
                public MidiDeviceStatus createFromParcel(Parcel source) {
                    MidiDeviceInfo info = source.readInt() != 0
                            ? MidiDeviceInfo.CREATOR.createFromParcel(source)
                            : null;
                    int inLen = Math.max(0, source.readInt());
                    boolean[] inputOpen = new boolean[inLen];
                    for (int i = 0; i < inLen; i++) {
                        inputOpen[i] = source.readInt() != 0;
                    }
                    int outLen = Math.max(0, source.readInt());
                    int[] outputCounts = new int[outLen];
                    for (int i = 0; i < outLen; i++) {
                        outputCounts[i] = source.readInt();
                    }
                    return new MidiDeviceStatus(info, inputOpen, outputCounts);
                }

                @Override
                public MidiDeviceStatus[] newArray(int size) {
                    return new MidiDeviceStatus[size];
                }
            };
}
