// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p;

import android.net.wifi.ScanResult;
import android.os.Parcel;
import android.os.Parcelable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class WifiP2pDevice implements Parcelable {
    public static final int CONNECTED = 0;
    public static final int INVITED = 1;
    public static final int FAILED = 2;
    public static final int AVAILABLE = 3;
    public static final int UNAVAILABLE = 4;

    private static final int WPS_CONFIG_DISPLAY = 0x0008;
    private static final int WPS_CONFIG_PUSHBUTTON = 0x0080;
    private static final int WPS_CONFIG_KEYPAD = 0x0100;
    private static final int DEVICE_CAPAB_SERVICE_DISCOVERY = 1;
    private static final int GROUP_CAPAB_GROUP_OWNER = 1;

    public static final Creator<WifiP2pDevice> CREATOR =
            new Creator<WifiP2pDevice>() {
                @Override
                public WifiP2pDevice createFromParcel(Parcel in) {
                    WifiP2pDevice device = new WifiP2pDevice();
                    device.deviceName = in.readString();
                    device.deviceAddress = in.readString();
                    device.primaryDeviceType = in.readString();
                    device.secondaryDeviceType = in.readString();
                    device.wpsConfigMethodsSupported = in.readInt();
                    device.deviceCapability = in.readInt();
                    device.groupCapability = in.readInt();
                    device.status = in.readInt();
                    if (in.readInt() == 1) {
                        device.wfdInfo = WifiP2pWfdInfo.CREATOR.createFromParcel(in);
                    }
                    int ieCount = in.readInt();
                    if (ieCount > 0) {
                        List<ScanResult.InformationElement> ies =
                                new ArrayList<ScanResult.InformationElement>(ieCount);
                        for (int i = 0; i < ieCount; i++) {
                            ScanResult.InformationElement ie =
                                    in.readTypedObject(ScanResult.InformationElement.CREATOR);
                            if (ie != null) {
                                ies.add(ie);
                            }
                        }
                        device.mVendorElements = ies;
                    }
                    byte[] ipBytes = in.createByteArray();
                    if (ipBytes != null) {
                        try {
                            device.mIpAddr = InetAddress.getByAddress(ipBytes);
                        } catch (UnknownHostException ignored) {
                            device.mIpAddr = null;
                        }
                    }
                    return device;
                }

                @Override
                public WifiP2pDevice[] newArray(int size) {
                    return new WifiP2pDevice[size];
                }
            };

    public String deviceName = "";
    public String deviceAddress = "";
    public String primaryDeviceType;
    public String secondaryDeviceType;
    public int status = UNAVAILABLE;

    int wpsConfigMethodsSupported;
    int deviceCapability;
    int groupCapability;
    WifiP2pWfdInfo wfdInfo;
    List<ScanResult.InformationElement> mVendorElements = Collections.emptyList();
    InetAddress mIpAddr;

    public WifiP2pDevice() {}

    public WifiP2pDevice(WifiP2pDevice source) {
        if (source != null) {
            deviceName = source.deviceName;
            deviceAddress = source.deviceAddress;
            primaryDeviceType = source.primaryDeviceType;
            secondaryDeviceType = source.secondaryDeviceType;
            wpsConfigMethodsSupported = source.wpsConfigMethodsSupported;
            deviceCapability = source.deviceCapability;
            groupCapability = source.groupCapability;
            status = source.status;
            if (source.wfdInfo != null) {
                wfdInfo = new WifiP2pWfdInfo(source.wfdInfo);
            }
            if (source.mVendorElements != null) {
                mVendorElements =
                        new ArrayList<ScanResult.InformationElement>(source.mVendorElements);
            }
            mIpAddr = source.mIpAddr;
        }
    }

    public WifiP2pWfdInfo getWfdInfo() {
        return wfdInfo;
    }

    public boolean wpsPbcSupported() {
        return (wpsConfigMethodsSupported & WPS_CONFIG_PUSHBUTTON) != 0;
    }

    public boolean wpsKeypadSupported() {
        return (wpsConfigMethodsSupported & WPS_CONFIG_KEYPAD) != 0;
    }

    public boolean wpsDisplaySupported() {
        return (wpsConfigMethodsSupported & WPS_CONFIG_DISPLAY) != 0;
    }

    public boolean isServiceDiscoveryCapable() {
        return (deviceCapability & DEVICE_CAPAB_SERVICE_DISCOVERY) != 0;
    }

    public boolean isGroupOwner() {
        return (groupCapability & GROUP_CAPAB_GROUP_OWNER) != 0;
    }

    public void update(WifiP2pDevice device) {
        if (device == null || device.deviceAddress == null) {
            return;
        }
        if (device.deviceName != null) {
            deviceName = device.deviceName;
        }
        primaryDeviceType = device.primaryDeviceType;
        secondaryDeviceType = device.secondaryDeviceType;
        wpsConfigMethodsSupported = device.wpsConfigMethodsSupported;
        deviceCapability = device.deviceCapability;
        groupCapability = device.groupCapability;
        wfdInfo = device.wfdInfo;
        mVendorElements =
                device.mVendorElements == null
                        ? Collections.<ScanResult.InformationElement>emptyList()
                        : new ArrayList<ScanResult.InformationElement>(device.mVendorElements);
        mIpAddr = device.mIpAddr;
    }

    public List<ScanResult.InformationElement> getVendorElements() {
        return mVendorElements == null
                ? Collections.<ScanResult.InformationElement>emptyList()
                : Collections.unmodifiableList(mVendorElements);
    }

    public InetAddress getIpAddress() {
        return mIpAddr;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WifiP2pDevice)) {
            return false;
        }
        WifiP2pDevice other = (WifiP2pDevice) obj;
        if (other.deviceAddress == null) {
            return deviceAddress == null;
        }
        return other.deviceAddress.equals(deviceAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(deviceAddress);
    }

    @Override
    public String toString() {
        return "Device: "
                + deviceName
                + "\n deviceAddress: "
                + deviceAddress
                + "\n primary type: "
                + primaryDeviceType
                + "\n secondary type: "
                + secondaryDeviceType
                + "\n status: "
                + status;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(deviceName);
        dest.writeString(deviceAddress);
        dest.writeString(primaryDeviceType);
        dest.writeString(secondaryDeviceType);
        dest.writeInt(wpsConfigMethodsSupported);
        dest.writeInt(deviceCapability);
        dest.writeInt(groupCapability);
        dest.writeInt(status);
        if (wfdInfo != null) {
            dest.writeInt(1);
            wfdInfo.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        if (mVendorElements == null || mVendorElements.isEmpty()) {
            dest.writeInt(0);
        } else {
            dest.writeInt(mVendorElements.size());
            for (int i = 0; i < mVendorElements.size(); i++) {
                dest.writeTypedObject(mVendorElements.get(i), flags);
            }
        }
        dest.writeByteArray(mIpAddr == null ? null : mIpAddr.getAddress());
    }
}
