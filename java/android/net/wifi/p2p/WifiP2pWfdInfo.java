// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Locale;

public final class WifiP2pWfdInfo implements Parcelable {
    public static final int DEVICE_TYPE_WFD_SOURCE = 0;
    public static final int DEVICE_TYPE_PRIMARY_SINK = 1;
    public static final int DEVICE_TYPE_SECONDARY_SINK = 2;
    public static final int DEVICE_TYPE_SOURCE_OR_PRIMARY_SINK = 3;

    public static final int PREFERRED_CONNECTIVITY_P2P = 0;
    public static final int PREFERRED_CONNECTIVITY_TDLS = 1;

    public static final int DEVICE_INFO_DEVICE_TYPE_MASK = 3;
    public static final int DEVICE_INFO_COUPLED_SINK_SUPPORT_AT_SOURCE = 4;
    public static final int DEVICE_INFO_COUPLED_SINK_SUPPORT_AT_SINK = 8;
    public static final int DEVICE_INFO_SESSION_AVAILABLE_MASK = 48;
    public static final int DEVICE_INFO_WFD_SERVICE_DISCOVERY_SUPPORT = 64;
    public static final int DEVICE_INFO_PREFERRED_CONNECTIVITY_MASK = 128;
    public static final int DEVICE_INFO_CONTENT_PROTECTION_SUPPORT = 256;
    public static final int DEVICE_INFO_TIME_SYNCHRONIZATION_SUPPORT = 512;
    public static final int DEVICE_INFO_AUDIO_UNSUPPORTED_AT_PRIMARY_SINK = 1024;
    public static final int DEVICE_INFO_AUDIO_ONLY_SUPPORT_AT_SOURCE = 2048;
    public static final int DEVICE_INFO_TDLS_PERSISTENT_GROUP = 4096;
    public static final int DEVICE_INFO_TDLS_PERSISTENT_GROUP_REINVOKE = 8192;

    private static final int SESSION_AVAILABLE_BIT1 = 16;

    public static final Creator<WifiP2pWfdInfo> CREATOR =
            new Creator<WifiP2pWfdInfo>() {
                @Override
                public WifiP2pWfdInfo createFromParcel(Parcel in) {
                    WifiP2pWfdInfo info = new WifiP2pWfdInfo();
                    info.mWfdEnabled = in.readInt() == 1;
                    info.mDeviceInfo = in.readInt();
                    info.mCtrlPort = in.readInt();
                    info.mMaxThroughput = in.readInt();
                    info.mR2DeviceInfo = in.readInt();
                    return info;
                }

                @Override
                public WifiP2pWfdInfo[] newArray(int size) {
                    return new WifiP2pWfdInfo[size];
                }
            };

    private boolean mWfdEnabled;
    private int mDeviceInfo;
    private int mCtrlPort;
    private int mMaxThroughput;
    private int mR2DeviceInfo = -1;

    public WifiP2pWfdInfo() {}

    public WifiP2pWfdInfo(WifiP2pWfdInfo source) {
        if (source != null) {
            mWfdEnabled = source.mWfdEnabled;
            mDeviceInfo = source.mDeviceInfo;
            mCtrlPort = source.mCtrlPort;
            mMaxThroughput = source.mMaxThroughput;
            mR2DeviceInfo = source.mR2DeviceInfo;
        }
    }

    public int getDeviceInfo() {
        return mDeviceInfo;
    }

    public int getR2DeviceInfo() {
        return mR2DeviceInfo;
    }

    public boolean isEnabled() {
        return mWfdEnabled;
    }

    public boolean isR2Supported() {
        return mR2DeviceInfo >= 0;
    }

    public void setEnabled(boolean enabled) {
        mWfdEnabled = enabled;
    }

    public boolean setR2DeviceType(int deviceType) {
        if (deviceType != DEVICE_TYPE_WFD_SOURCE
                && deviceType != DEVICE_TYPE_PRIMARY_SINK
                && deviceType != DEVICE_TYPE_SOURCE_OR_PRIMARY_SINK) {
            return false;
        }
        if (mR2DeviceInfo < 0) {
            mR2DeviceInfo = 0;
        }
        mR2DeviceInfo &= ~DEVICE_INFO_DEVICE_TYPE_MASK;
        mR2DeviceInfo |= deviceType;
        return true;
    }

    public int getDeviceType() {
        return mDeviceInfo & DEVICE_INFO_DEVICE_TYPE_MASK;
    }

    public int getR2DeviceType() {
        return mR2DeviceInfo & DEVICE_INFO_DEVICE_TYPE_MASK;
    }

    public boolean setDeviceType(int deviceType) {
        if (deviceType >= DEVICE_TYPE_WFD_SOURCE
                && deviceType <= DEVICE_TYPE_SOURCE_OR_PRIMARY_SINK) {
            mDeviceInfo &= ~DEVICE_INFO_DEVICE_TYPE_MASK;
            mDeviceInfo |= deviceType;
            return true;
        }
        return false;
    }

    public boolean isSessionAvailable() {
        return (mDeviceInfo & DEVICE_INFO_SESSION_AVAILABLE_MASK) != 0;
    }

    public void setSessionAvailable(boolean enabled) {
        if (enabled) {
            mDeviceInfo |= SESSION_AVAILABLE_BIT1;
            mDeviceInfo &= ~(SESSION_AVAILABLE_BIT1 << 1);
        } else {
            mDeviceInfo &= ~DEVICE_INFO_SESSION_AVAILABLE_MASK;
        }
    }

    public boolean isContentProtectionSupported() {
        return (mDeviceInfo & DEVICE_INFO_CONTENT_PROTECTION_SUPPORT) != 0;
    }

    public void setContentProtectionSupported(boolean enabled) {
        if (enabled) {
            mDeviceInfo |= DEVICE_INFO_CONTENT_PROTECTION_SUPPORT;
        } else {
            mDeviceInfo &= ~DEVICE_INFO_CONTENT_PROTECTION_SUPPORT;
        }
    }

    public boolean isCoupledSinkSupportedAtSource() {
        return (mDeviceInfo & DEVICE_INFO_COUPLED_SINK_SUPPORT_AT_SOURCE) != 0;
    }

    public void setCoupledSinkSupportAtSource(boolean enabled) {
        if (enabled) {
            mDeviceInfo |= DEVICE_INFO_COUPLED_SINK_SUPPORT_AT_SOURCE;
        } else {
            mDeviceInfo &= ~DEVICE_INFO_COUPLED_SINK_SUPPORT_AT_SOURCE;
        }
    }

    public boolean isCoupledSinkSupportedAtSink() {
        return (mDeviceInfo & DEVICE_INFO_COUPLED_SINK_SUPPORT_AT_SINK) != 0;
    }

    public void setCoupledSinkSupportAtSink(boolean enabled) {
        if (enabled) {
            mDeviceInfo |= DEVICE_INFO_COUPLED_SINK_SUPPORT_AT_SINK;
        } else {
            mDeviceInfo &= ~DEVICE_INFO_COUPLED_SINK_SUPPORT_AT_SINK;
        }
    }

    public int getControlPort() {
        return mCtrlPort;
    }

    public void setControlPort(int port) {
        mCtrlPort = port;
    }

    public void setMaxThroughput(int maxThroughput) {
        mMaxThroughput = maxThroughput;
    }

    public int getMaxThroughput() {
        return mMaxThroughput;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ROOT,
                "WFD enabled: %bWFD DeviceInfo: %d\n WFD CtrlPort: %d\n WFD MaxThroughput: %d\n"
                        + " WFD R2 DeviceInfo: %d\n",
                Boolean.valueOf(mWfdEnabled),
                Integer.valueOf(mDeviceInfo),
                Integer.valueOf(mCtrlPort),
                Integer.valueOf(mMaxThroughput),
                Integer.valueOf(mR2DeviceInfo));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mWfdEnabled ? 1 : 0);
        dest.writeInt(mDeviceInfo);
        dest.writeInt(mCtrlPort);
        dest.writeInt(mMaxThroughput);
        dest.writeInt(mR2DeviceInfo);
    }
}
