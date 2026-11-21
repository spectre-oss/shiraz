// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public final class Characteristics implements Parcelable {
    public static final int WIFI_AWARE_CIPHER_SUITE_NONE = 0;
    public static final int WIFI_AWARE_CIPHER_SUITE_NCS_SK_128 = 1;
    public static final int WIFI_AWARE_CIPHER_SUITE_NCS_SK_256 = 2;
    public static final int WIFI_AWARE_CIPHER_SUITE_NCS_PK_128 = 4;
    public static final int WIFI_AWARE_CIPHER_SUITE_NCS_PK_256 = 8;
    public static final int WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128 = 16;
    public static final int WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_256 = 32;

    public static final Creator<Characteristics> CREATOR =
            new Creator<Characteristics>() {
                @Override
                public Characteristics createFromParcel(Parcel in) {
                    Bundle b = in.readBundle(Characteristics.class.getClassLoader());
                    return new Characteristics(b == null ? new Bundle() : b);
                }

                @Override
                public Characteristics[] newArray(int size) {
                    return new Characteristics[size];
                }
            };

    private final Bundle mCharacteristics;

    Characteristics(Bundle characteristics) {
        this.mCharacteristics = characteristics;
    }

    public int getMaxServiceNameLength() {
        return mCharacteristics.getInt("key_max_service_name_length", 255);
    }

    public int getMaxServiceSpecificInfoLength() {
        return mCharacteristics.getInt("key_max_service_specific_info_length", 255);
    }

    public int getMaxMatchFilterLength() {
        return mCharacteristics.getInt("key_max_match_filter_length", 255);
    }

    public int getNumberOfSupportedDataInterfaces() {
        return mCharacteristics.getInt("key_number_of_supported_data_interfaces", 0);
    }

    public int getNumberOfSupportedPublishSessions() {
        return mCharacteristics.getInt("key_number_of_supported_publish_sessions", 0);
    }

    public int getNumberOfSupportedSubscribeSessions() {
        return mCharacteristics.getInt("key_number_of_supported_subscribe_sessions", 0);
    }

    public int getNumberOfSupportedDataPaths() {
        return mCharacteristics.getInt("key_number_of_supported_data_paths", 0);
    }

    public boolean isInstantCommunicationModeSupported() {
        return mCharacteristics.getBoolean("key_is_instant_communication_mode_supported", false);
    }

    public boolean isAwarePairingSupported() {
        return mCharacteristics.getBoolean("key_is_aware_pairing_supported", false);
    }

    public boolean isSuspensionSupported() {
        return mCharacteristics.getBoolean("key_is_suspension_supported", false);
    }

    public int getSupportedCipherSuites() {
        return mCharacteristics.getInt(
                "key_supported_cipher_suites", WIFI_AWARE_CIPHER_SUITE_NCS_SK_128);
    }

    public int getSupportedPairingCipherSuites() {
        return mCharacteristics.getInt("key_supported_pairing_cipher_suites", 0);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeBundle(mCharacteristics);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
