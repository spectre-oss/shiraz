// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class AwarePairingConfig implements Parcelable {
    public static final int PAIRING_BOOTSTRAPPING_OPPORTUNISTIC = 1;
    public static final int PAIRING_BOOTSTRAPPING_PIN_CODE_DISPLAY = 2;
    public static final int PAIRING_BOOTSTRAPPING_PASSPHRASE_DISPLAY = 4;
    public static final int PAIRING_BOOTSTRAPPING_QR_DISPLAY = 8;
    public static final int PAIRING_BOOTSTRAPPING_NFC_TAG = 16;
    public static final int PAIRING_BOOTSTRAPPING_PIN_CODE_KEYPAD = 32;
    public static final int PAIRING_BOOTSTRAPPING_PASSPHRASE_KEYPAD = 64;
    public static final int PAIRING_BOOTSTRAPPING_QR_SCAN = 128;
    public static final int PAIRING_BOOTSTRAPPING_NFC_READER = 256;

    public static final Creator<AwarePairingConfig> CREATOR =
            new Creator<AwarePairingConfig>() {
                @Override
                public AwarePairingConfig createFromParcel(Parcel in) {
                    boolean setup = in.readInt() != 0;
                    boolean cache = in.readInt() != 0;
                    boolean verification = in.readInt() != 0;
                    int methods = in.readInt();
                    return new AwarePairingConfig(setup, cache, verification, methods);
                }

                @Override
                public AwarePairingConfig[] newArray(int size) {
                    return new AwarePairingConfig[size];
                }
            };

    private final boolean mPairingSetup;
    private final boolean mPairingCache;
    private final boolean mPairingVerification;
    private final int mBootstrappingMethods;

    AwarePairingConfig(
            boolean setup, boolean cache, boolean verification, int bootstrappingMethods) {
        this.mPairingSetup = setup;
        this.mPairingCache = cache;
        this.mPairingVerification = verification;
        this.mBootstrappingMethods = bootstrappingMethods;
    }

    public boolean isPairingCacheEnabled() {
        return mPairingCache;
    }

    public boolean isPairingSetupEnabled() {
        return mPairingSetup;
    }

    public boolean isPairingVerificationEnabled() {
        return mPairingVerification;
    }

    public int getBootstrappingMethods() {
        return mBootstrappingMethods;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AwarePairingConfig)) {
            return false;
        }
        AwarePairingConfig that = (AwarePairingConfig) o;
        return mPairingSetup == that.mPairingSetup
                && mPairingCache == that.mPairingCache
                && mPairingVerification == that.mPairingVerification
                && mBootstrappingMethods == that.mBootstrappingMethods;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Boolean.valueOf(mPairingSetup),
                Boolean.valueOf(mPairingCache),
                Boolean.valueOf(mPairingVerification),
                Integer.valueOf(mBootstrappingMethods));
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPairingSetup ? 1 : 0);
        dest.writeInt(mPairingCache ? 1 : 0);
        dest.writeInt(mPairingVerification ? 1 : 0);
        dest.writeInt(mBootstrappingMethods);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final class Builder {
        private boolean mPairingSetup;
        private boolean mPairingCache;
        private boolean mPairingVerification;
        private int mBootstrappingMethods;

        public Builder() {}

        public Builder setPairingSetupEnabled(boolean enabled) {
            this.mPairingSetup = enabled;
            return this;
        }

        public Builder setPairingVerificationEnabled(boolean enabled) {
            this.mPairingVerification = enabled;
            return this;
        }

        public Builder setPairingCacheEnabled(boolean enabled) {
            this.mPairingCache = enabled;
            return this;
        }

        public Builder setBootstrappingMethods(int methods) {
            this.mBootstrappingMethods = methods;
            return this;
        }

        public AwarePairingConfig build() {
            return new AwarePairingConfig(
                    mPairingSetup, mPairingCache, mPairingVerification, mBootstrappingMethods);
        }
    }
}
