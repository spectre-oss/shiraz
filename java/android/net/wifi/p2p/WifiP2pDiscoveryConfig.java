// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p;

import android.os.Parcel;
import android.os.Parcelable;

public final class WifiP2pDiscoveryConfig implements Parcelable {
    public static final Creator<WifiP2pDiscoveryConfig> CREATOR =
            new Creator<WifiP2pDiscoveryConfig>() {
                @Override
                public WifiP2pDiscoveryConfig createFromParcel(Parcel in) {
                    int scanType = in.readInt();
                    int frequencyMhz = in.readInt();
                    return new WifiP2pDiscoveryConfig(scanType, frequencyMhz);
                }

                @Override
                public WifiP2pDiscoveryConfig[] newArray(int size) {
                    return new WifiP2pDiscoveryConfig[size];
                }
            };

    private final int mScanType;
    private final int mFrequencyMhz;

    private WifiP2pDiscoveryConfig(int scanType, int frequencyMhz) {
        mScanType = scanType;
        mFrequencyMhz = frequencyMhz;
    }

    public int getScanType() {
        return mScanType;
    }

    public int getFrequencyMhz() {
        return mFrequencyMhz;
    }

    @Override
    public String toString() {
        return "WifiP2pDiscoveryConfig{"
                + "scanType="
                + mScanType
                + ", frequencyMhz="
                + mFrequencyMhz
                + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mScanType);
        dest.writeInt(mFrequencyMhz);
    }

    public static final class Builder {
        private final int mScanType;
        private int mFrequencyMhz = 0;

        public Builder(int scanType) {
            if (scanType != WifiP2pManager.WIFI_P2P_SCAN_FULL
                    && scanType != WifiP2pManager.WIFI_P2P_SCAN_SOCIAL
                    && scanType != WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ) {
                throw new IllegalArgumentException("Invalid scan type " + scanType);
            }
            mScanType = scanType;
        }

        public Builder setFrequencyMhz(int frequencyMhz) {
            if (frequencyMhz <= 0) {
                throw new IllegalArgumentException("Frequency must be positive");
            }
            mFrequencyMhz = frequencyMhz;
            return this;
        }

        public WifiP2pDiscoveryConfig build() {
            if (mScanType == WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ && mFrequencyMhz <= 0) {
                throw new IllegalArgumentException(
                        "Frequency must be specified for WIFI_P2P_SCAN_SINGLE_FREQ");
            }
            return new WifiP2pDiscoveryConfig(mScanType, mFrequencyMhz);
        }
    }
}
