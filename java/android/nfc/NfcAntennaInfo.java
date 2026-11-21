// SPDX-License-Identifier: GPL-3.0-only
package android.nfc;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NfcAntennaInfo implements Parcelable {
    private final int mDeviceWidth;
    private final int mDeviceHeight;
    private final boolean mDeviceFoldable;
    private final List<AvailableNfcAntenna> mAvailableNfcAntennas;

    public NfcAntennaInfo(
            int deviceWidth,
            int deviceHeight,
            boolean deviceFoldable,
            List<AvailableNfcAntenna> availableNfcAntennas) {
        if (availableNfcAntennas == null) {
            throw new NullPointerException("availableNfcAntennas");
        }
        mDeviceWidth = deviceWidth;
        mDeviceHeight = deviceHeight;
        mDeviceFoldable = deviceFoldable;
        mAvailableNfcAntennas = Collections.unmodifiableList(
                new ArrayList<AvailableNfcAntenna>(availableNfcAntennas));
    }

    public int getDeviceWidth() {
        return mDeviceWidth;
    }

    public int getDeviceHeight() {
        return mDeviceHeight;
    }

    public boolean isDeviceFoldable() {
        return mDeviceFoldable;
    }

    public List<AvailableNfcAntenna> getAvailableNfcAntennas() {
        return mAvailableNfcAntennas;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDeviceWidth);
        dest.writeInt(mDeviceHeight);
        dest.writeByte((byte) (mDeviceFoldable ? 1 : 0));
        dest.writeTypedList(mAvailableNfcAntennas);
    }

    public static final Parcelable.Creator<NfcAntennaInfo> CREATOR =
            new Parcelable.Creator<NfcAntennaInfo>() {
                @Override
                public NfcAntennaInfo createFromParcel(Parcel in) {
                    int width = in.readInt();
                    int height = in.readInt();
                    boolean foldable = in.readByte() != 0;
                    ArrayList<AvailableNfcAntenna> antennas =
                            in.createTypedArrayList(AvailableNfcAntenna.CREATOR);
                    return new NfcAntennaInfo(
                            width,
                            height,
                            foldable,
                            antennas != null
                                    ? antennas
                                    : Collections.<AvailableNfcAntenna>emptyList());
                }

                @Override
                public NfcAntennaInfo[] newArray(int size) {
                    return new NfcAntennaInfo[size];
                }
            };
}
