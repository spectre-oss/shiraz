// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class WifiAwareDataPathSecurityConfig implements Parcelable {
    public static final Creator<WifiAwareDataPathSecurityConfig> CREATOR =
            new Creator<WifiAwareDataPathSecurityConfig>() {
                @Override
                public WifiAwareDataPathSecurityConfig createFromParcel(Parcel in) {
                    int cipherSuite = in.readInt();
                    byte[] pmk = in.createByteArray();
                    String passphrase = in.readString();
                    byte[] pmkId = in.createByteArray();
                    return new WifiAwareDataPathSecurityConfig(
                            cipherSuite, pmk, passphrase, pmkId);
                }

                @Override
                public WifiAwareDataPathSecurityConfig[] newArray(int size) {
                    return new WifiAwareDataPathSecurityConfig[size];
                }
            };

    private final int mCipherSuite;
    private final byte[] mPmk;
    private final String mPskPassphrase;
    private final byte[] mPmkId;

    WifiAwareDataPathSecurityConfig(
            int cipherSuite, byte[] pmk, String pskPassphrase, byte[] pmkId) {
        this.mCipherSuite = cipherSuite;
        this.mPmk = pmk == null ? null : pmk.clone();
        this.mPskPassphrase = pskPassphrase;
        this.mPmkId = pmkId == null ? null : pmkId.clone();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCipherSuite);
        dest.writeByteArray(mPmk);
        dest.writeString(mPskPassphrase);
        dest.writeByteArray(mPmkId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WifiAwareDataPathSecurityConfig)) {
            return false;
        }
        WifiAwareDataPathSecurityConfig lhs = (WifiAwareDataPathSecurityConfig) obj;
        return mCipherSuite == lhs.mCipherSuite
                && Arrays.equals(mPmk, lhs.mPmk)
                && Objects.equals(mPskPassphrase, lhs.mPskPassphrase)
                && Arrays.equals(mPmkId, lhs.mPmkId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mCipherSuite),
                Integer.valueOf(Arrays.hashCode(mPmk)),
                mPskPassphrase,
                Integer.valueOf(Arrays.hashCode(mPmkId)));
    }

    @Override
    public String toString() {
        return "WifiAwareDataPathSecurityConfig{cipherSuite=" + mCipherSuite + "}";
    }

    public int getCipherSuite() {
        return mCipherSuite;
    }

    public byte[] getPmk() {
        return mPmk == null ? null : mPmk.clone();
    }

    public byte[] getPmkId() {
        return mPmkId == null ? null : mPmkId.clone();
    }

    public String getPskPassphrase() {
        return mPskPassphrase;
    }

    public static final class Builder {
        private final int mCipherSuite;
        private byte[] mPmk;
        private byte[] mPmkId;
        private String mPskPassphrase;

        public Builder(int cipherSuite) {
            this.mCipherSuite = cipherSuite;
        }

        public Builder setPskPassphrase(String pskPassphrase) {
            this.mPskPassphrase = Objects.requireNonNull(pskPassphrase, "pskPassphrase");
            return this;
        }

        public Builder setPmk(byte[] pmk) {
            this.mPmk = Objects.requireNonNull(pmk, "pmk").clone();
            return this;
        }

        public Builder setPmkId(byte[] pmkId) {
            this.mPmkId = Objects.requireNonNull(pmkId, "pmkId").clone();
            return this;
        }

        public WifiAwareDataPathSecurityConfig build() {
            return new WifiAwareDataPathSecurityConfig(
                    mCipherSuite, mPmk, mPskPassphrase, mPmkId);
        }
    }
}
