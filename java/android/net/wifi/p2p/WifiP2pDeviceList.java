// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class WifiP2pDeviceList implements Parcelable {
    public static final Creator<WifiP2pDeviceList> CREATOR =
            new Creator<WifiP2pDeviceList>() {
                @Override
                public WifiP2pDeviceList createFromParcel(Parcel in) {
                    WifiP2pDeviceList deviceList = new WifiP2pDeviceList();
                    int deviceCount = in.readInt();
                    for (int i = 0; i < deviceCount; i++) {
                        WifiP2pDevice d = in.readTypedObject(WifiP2pDevice.CREATOR);
                        if (d != null && d.deviceAddress != null) {
                            deviceList.mDevices.put(d.deviceAddress, d);
                        }
                    }
                    return deviceList;
                }

                @Override
                public WifiP2pDeviceList[] newArray(int size) {
                    return new WifiP2pDeviceList[size];
                }
            };

    private final Map<String, WifiP2pDevice> mDevices =
            new LinkedHashMap<String, WifiP2pDevice>();

    public WifiP2pDeviceList() {}

    public WifiP2pDeviceList(WifiP2pDeviceList source) {
        if (source != null) {
            for (WifiP2pDevice d : source.getDeviceList()) {
                if (d != null && d.deviceAddress != null) {
                    mDevices.put(d.deviceAddress, new WifiP2pDevice(d));
                }
            }
        }
    }

    public WifiP2pDevice get(String deviceAddress) {
        if (deviceAddress == null || deviceAddress.isEmpty()) {
            return null;
        }
        return mDevices.get(deviceAddress);
    }

    public Collection<WifiP2pDevice> getDeviceList() {
        return Collections.unmodifiableCollection(mDevices.values());
    }

    @Override
    public String toString() {
        StringBuilder sbuf = new StringBuilder();
        for (WifiP2pDevice device : mDevices.values()) {
            sbuf.append("\n").append(device);
        }
        return sbuf.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDevices.size());
        for (WifiP2pDevice device : mDevices.values()) {
            dest.writeTypedObject(device, flags);
        }
    }
}
