// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.usb;

import android.os.Parcel;
import android.os.Parcelable;

public class UsbConfiguration implements Parcelable {
    private static final int ATTR_SELF_POWERED = 1 << 6;
    private static final int ATTR_REMOTE_WAKEUP = 1 << 5;

    private final int mId;
    private final String mName;
    private final int mAttributes;
    private final int mMaxPower;
    private UsbInterface[] mInterfaces;

    /** @hide */
    public UsbConfiguration(int id, String name, int attributes, int maxPower) {
        mId = id;
        mName = name;
        mAttributes = attributes;
        mMaxPower = maxPower;
        mInterfaces = new UsbInterface[0];
    }

    /** @hide */
    public void setInterfaces(UsbInterface[] interfaces) {
        mInterfaces = interfaces != null ? interfaces.clone() : new UsbInterface[0];
    }

    public int getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public boolean isSelfPowered() {
        return (mAttributes & ATTR_SELF_POWERED) != 0;
    }

    public boolean isRemoteWakeup() {
        return (mAttributes & ATTR_REMOTE_WAKEUP) != 0;
    }

    public int getMaxPower() {
        return mMaxPower * 2;
    }

    public int getInterfaceCount() {
        return mInterfaces.length;
    }

    public UsbInterface getInterface(int index) {
        return mInterfaces[index];
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("UsbConfiguration[mId=" + mId
                + ",mName=" + mName
                + ",mAttributes=" + mAttributes
                + ",mMaxPower=" + mMaxPower
                + ",mInterfaces=[");
        for (int i = 0; i < mInterfaces.length; i++) {
            builder.append("\n");
            builder.append(mInterfaces[i].toString());
        }
        builder.append("]");
        return builder.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mId);
        parcel.writeString(mName);
        parcel.writeInt(mAttributes);
        parcel.writeInt(mMaxPower);
        parcel.writeTypedArray(mInterfaces, flags);
    }

    public static final Parcelable.Creator<UsbConfiguration> CREATOR =
            new Parcelable.Creator<UsbConfiguration>() {
                @Override
                public UsbConfiguration createFromParcel(Parcel in) {
                    int id = in.readInt();
                    String name = in.readString();
                    int attributes = in.readInt();
                    int maxPower = in.readInt();
                    UsbInterface[] interfaces = in.createTypedArray(UsbInterface.CREATOR);
                    UsbConfiguration config =
                            new UsbConfiguration(id, name, attributes, maxPower);
                    config.setInterfaces(interfaces);
                    return config;
                }

                @Override
                public UsbConfiguration[] newArray(int size) {
                    return new UsbConfiguration[size];
                }
            };
}
