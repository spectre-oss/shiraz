// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.usb;

import android.os.Parcel;
import android.os.Parcelable;

public class UsbInterface implements Parcelable {
    private final int mId;
    private final int mAlternateSetting;
    private final String mName;
    private final int mClass;
    private final int mSubclass;
    private final int mProtocol;
    private UsbEndpoint[] mEndpoints;

    /** @hide */
    public UsbInterface(
            int id,
            int alternateSetting,
            String name,
            int Class,
            int subClass,
            int protocol) {
        mId = id;
        mAlternateSetting = alternateSetting;
        mName = name;
        mClass = Class;
        mSubclass = subClass;
        mProtocol = protocol;
        mEndpoints = new UsbEndpoint[0];
    }

    /** @hide */
    public void setEndpoints(UsbEndpoint[] endpoints) {
        mEndpoints = endpoints != null ? endpoints.clone() : new UsbEndpoint[0];
    }

    public int getId() {
        return mId;
    }

    public int getAlternateSetting() {
        return mAlternateSetting;
    }

    public String getName() {
        return mName;
    }

    public int getInterfaceClass() {
        return mClass;
    }

    public int getInterfaceSubclass() {
        return mSubclass;
    }

    public int getInterfaceProtocol() {
        return mProtocol;
    }

    public int getEndpointCount() {
        return mEndpoints.length;
    }

    public UsbEndpoint getEndpoint(int index) {
        return mEndpoints[index];
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("UsbInterface[mId=" + mId
                + ",mAlternateSetting=" + mAlternateSetting
                + ",mName=" + mName
                + ",mClass=" + mClass
                + ",mSubclass=" + mSubclass
                + ",mProtocol=" + mProtocol
                + ",mEndpoints=[");
        for (int i = 0; i < mEndpoints.length; i++) {
            builder.append("\n");
            builder.append(mEndpoints[i].toString());
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
        parcel.writeInt(mAlternateSetting);
        parcel.writeString(mName);
        parcel.writeInt(mClass);
        parcel.writeInt(mSubclass);
        parcel.writeInt(mProtocol);
        parcel.writeTypedArray(mEndpoints, flags);
    }

    public static final Parcelable.Creator<UsbInterface> CREATOR =
            new Parcelable.Creator<UsbInterface>() {
                @Override
                public UsbInterface createFromParcel(Parcel in) {
                    int id = in.readInt();
                    int alternateSetting = in.readInt();
                    String name = in.readString();
                    int Class = in.readInt();
                    int subClass = in.readInt();
                    int protocol = in.readInt();
                    UsbEndpoint[] endpoints = in.createTypedArray(UsbEndpoint.CREATOR);
                    UsbInterface iface = new UsbInterface(
                            id, alternateSetting, name, Class, subClass, protocol);
                    iface.setEndpoints(endpoints);
                    return iface;
                }

                @Override
                public UsbInterface[] newArray(int size) {
                    return new UsbInterface[size];
                }
            };
}
