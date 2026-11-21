// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.usb;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Locale;

public class UsbDevice implements Parcelable {
    private static final String USB_BUS_PREFIX = "/dev/bus/usb/";

    private final String mName;
    private final String mManufacturerName;
    private final String mProductName;
    private final String mVersion;
    private final String mSerialNumber;
    private final int mVendorId;
    private final int mProductId;
    private final int mClass;
    private final int mSubclass;
    private final int mProtocol;
    private UsbConfiguration[] mConfigurations;
    private UsbInterface[] mInterfaces;

    /** @hide */
    public UsbDevice(
            String name,
            int vendorId,
            int productId,
            int Class,
            int subClass,
            int protocol,
            String manufacturerName,
            String productName,
            String version,
            UsbConfiguration[] configurations,
            String serialNumber) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        mName = name;
        mVendorId = vendorId;
        mProductId = productId;
        mClass = Class;
        mSubclass = subClass;
        mProtocol = protocol;
        mManufacturerName = manufacturerName;
        mProductName = productName;
        mVersion = version != null ? version : "";
        mSerialNumber = serialNumber;
        mConfigurations = configurations != null
                ? configurations.clone()
                : new UsbConfiguration[0];
        rebuildInterfaces();
    }

    private void rebuildInterfaces() {
        ArrayList<UsbInterface> all = new ArrayList<UsbInterface>();
        for (int i = 0; i < mConfigurations.length; i++) {
            UsbConfiguration cfg = mConfigurations[i];
            if (cfg != null) {
                int count = cfg.getInterfaceCount();
                for (int j = 0; j < count; j++) {
                    all.add(cfg.getInterface(j));
                }
            }
        }
        mInterfaces = all.toArray(new UsbInterface[all.size()]);
    }

    public String getDeviceName() {
        return mName;
    }

    public String getManufacturerName() {
        return mManufacturerName;
    }

    public String getProductName() {
        return mProductName;
    }

    public String getVersion() {
        return mVersion;
    }

    public String getSerialNumber() {
        return mSerialNumber;
    }

    public int getDeviceId() {
        return getDeviceId(mName);
    }

    public int getVendorId() {
        return mVendorId;
    }

    public int getProductId() {
        return mProductId;
    }

    public int getDeviceClass() {
        return mClass;
    }

    public int getDeviceSubclass() {
        return mSubclass;
    }

    public int getDeviceProtocol() {
        return mProtocol;
    }

    public int getConfigurationCount() {
        return mConfigurations.length;
    }

    public UsbConfiguration getConfiguration(int index) {
        return mConfigurations[index];
    }

    public int getInterfaceCount() {
        return mInterfaces.length;
    }

    public UsbInterface getInterface(int index) {
        return mInterfaces[index];
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof UsbDevice) {
            return ((UsbDevice) o).mName.equals(mName);
        }
        if (o instanceof String) {
            return o.equals(mName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mName.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("UsbDevice[mName=" + mName
                + ",mVendorId=" + mVendorId
                + ",mProductId=" + mProductId
                + ",mClass=" + mClass
                + ",mSubclass=" + mSubclass
                + ",mProtocol=" + mProtocol
                + ",mManufacturerName=" + mManufacturerName
                + ",mProductName=" + mProductName
                + ",mVersion=" + mVersion
                + ",mSerialNumber=" + mSerialNumber
                + ",mConfigurations=[");
        for (int i = 0; i < mConfigurations.length; i++) {
            builder.append("\n");
            builder.append(mConfigurations[i].toString());
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
        parcel.writeString(mName);
        parcel.writeInt(mVendorId);
        parcel.writeInt(mProductId);
        parcel.writeInt(mClass);
        parcel.writeInt(mSubclass);
        parcel.writeInt(mProtocol);
        parcel.writeString(mManufacturerName);
        parcel.writeString(mProductName);
        parcel.writeString(mVersion);
        parcel.writeString(mSerialNumber);
        parcel.writeTypedArray(mConfigurations, flags);
    }

    public static int getDeviceId(String name) {
        if (name == null) {
            return 0;
        }
        if (name.startsWith(USB_BUS_PREFIX)) {
            String rest = name.substring(USB_BUS_PREFIX.length());
            int slash = rest.indexOf('/');
            if (slash > 0 && slash + 1 < rest.length()) {
                try {
                    int bus = Integer.parseInt(rest.substring(0, slash));
                    int dev = Integer.parseInt(rest.substring(slash + 1));
                    if (bus >= 0 && dev >= 0 && dev < 1000) {
                        return bus * 1000 + dev;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return name.hashCode() & 0x7fffffff;
    }

    public static String getDeviceName(int id) {
        int bus = (id / 1000) % 1000;
        int dev = id % 1000;
        return String.format(Locale.US, "/dev/bus/usb/%03d/%03d",
                Integer.valueOf(Math.max(0, bus)),
                Integer.valueOf(Math.max(0, dev)));
    }

    public static final Parcelable.Creator<UsbDevice> CREATOR =
            new Parcelable.Creator<UsbDevice>() {
                @Override
                public UsbDevice createFromParcel(Parcel in) {
                    String name = in.readString();
                    int vendorId = in.readInt();
                    int productId = in.readInt();
                    int Class = in.readInt();
                    int subClass = in.readInt();
                    int protocol = in.readInt();
                    String manufacturerName = in.readString();
                    String productName = in.readString();
                    String version = in.readString();
                    String serialNumber = in.readString();
                    UsbConfiguration[] configurations =
                            in.createTypedArray(UsbConfiguration.CREATOR);
                    return new UsbDevice(
                            name,
                            vendorId,
                            productId,
                            Class,
                            subClass,
                            protocol,
                            manufacturerName,
                            productName,
                            version,
                            configurations,
                            serialNumber);
                }

                @Override
                public UsbDevice[] newArray(int size) {
                    return new UsbDevice[size];
                }
            };
}
